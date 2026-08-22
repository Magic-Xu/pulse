# Pulse 0.3 接入指南

[English](CONSUMER_GUIDE.md)

本文面向接入 0.3 API 的应用开发者。仓库当前构建稳定版 `0.3.0` 候选；公共制品验证成功前请使用
本地 staging 仓库。

## 选择最小能力面

1. 平台无关的状态机使用 `DefaultPulseStore`。
2. Android 中包含 UI Intent 与异步任务的功能使用 `PulseSplitStoreViewModel`。
3. 只有当一个功能的 State 已出现多个独立领域时，才引入 `mvi-extensions` 的 State Decomposition。

不要按视觉区块拆多个 ViewModel。先按功能或导航 Owner 保持一个 ViewModel，复杂度确实上升后再按
子状态拆 Reducer。

## Core Store

Reducer 必须返回显式结果：

- `ReduceOutcome.Changed(state, effects)`：提交不同的新 State。
- `ReduceOutcome.Unchanged(effects)`：保留当前 State，但可以产生 Effect。
- `ReduceOutcome.Ignored(reason)`：记录有意忽略，且不产生 Effect。

如果 `Changed` 携带的 State 与当前值相等，运行时会归一成 `Unchanged`。`send` 等待完整 Frame
结束；`trySend` 非阻塞返回 `Enqueued`、`Full` 或 `Rejected`。

以 `store.state` 作为状态真相源；`store.transitions` 用于诊断与测试，Frame 会记录处理耗时、开始时
mailbox 深度和已观察到的历史高水位；`store.effects` 只能绑定一个协调者，且 replay=0、有界。

销毁时调用 `close`；需要观察异步清理完成时再调用 `awaitClosed`。

## 失败处理与不可变 State

为 `PulseRuntimeConfig` 配置稳定的 `storeId`、`PulseErrorHandler` 和 `PulseRedactor`。每个受控
框架失败都会在 `FailureContext` 中携带相同的 `storeId`，并尽可能包含 request、sequence、
revision、输入类型、线程和组件信息。默认 Redactor 只暴露类型，不暴露业务值；转发到日志或遥测时
也应保留这条脱敏边界。

Pulse 只把受控边界中的普通 `Exception` 转换成带类型的 `PulseFailure`，不会转换
`CancellationException` 或 JVM 致命错误。需要由 UI 展示的业务失败应建模成领域 Mutation；
框架诊断交给 `PulseErrorHandler`。开发期可启用 `strictMode`，让损坏的诊断处理器终止所属运行时；
生产处理器应保持不抛异常。

State 必须是不可变值：优先使用只含 `val` 的 `data class`，外部集合通过 `toList()` 或 `toMap()`
形成快照；不要把可变集合、Coroutine Job、Android View 或 Effect Handler 放进 State。相等 State
不会重复发射。需要隔离读写时使用 Selector 或 `StateLens`，不要在 `StateFlow` 背后修改共享对象。

## Android Split Intent

先定义三类功能输入：

```kotlin
data class ScreenState(val loading: Boolean, val value: String) : MviState

sealed interface ScreenUiIntent : MviUiIntent {
    data object Refresh : ScreenUiIntent
}

sealed interface ScreenMutation : MviMutation {
    data object Loading : ScreenMutation
    data class Loaded(val value: String) : ScreenMutation
}

sealed interface ScreenEffect : UiEffect
```

创建 `PulseSplitStoreViewModel`。UI 只调用挂起式 `send` 或非阻塞 `trySend`，Mutation 能力只属于
Executor：

```kotlin
private val LOAD = TaskKey("screen.load")

class ScreenViewModel : PulseSplitStoreViewModel<
    ScreenState,
    ScreenUiIntent,
    ScreenMutation,
    ScreenEffect,
>(
    initialState = ScreenState(false, ""),
    mutationReducer = PulseMutationReducer { state, mutation ->
        when (mutation) {
            ScreenMutation.Loading -> ReduceOutcome.Changed(state.copy(loading = true))
            is ScreenMutation.Loaded -> ReduceOutcome.Changed(
                state.copy(loading = false, value = mutation.value)
            )
        }
    },
    uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
        when (intent) {
            ScreenUiIntent.Refresh -> {
                context.launchTask(LOAD, TaskPolicy.Latest) {
                    mutate(ScreenMutation.Loading)
                    val value = repository.load()
                    mutate(ScreenMutation.Loaded(value))
                }
                PulseIntentExecutionDecision.Completed
            }
        }
    },
)
```

按业务语义选择 Task 策略：

| 策略 | 行为 |
| --- | --- |
| `Latest` | 取消并使上一代任务失效 |
| `DropWhileRunning` | 运行中拒绝重叠任务 |
| `Queue(capacity)` | 按 FIFO 运行，并限制待执行队列容量 |
| `Parallel(maxConcurrency)` | 最多并行到声明上限，超出请求立即拒绝 |
| `Conflate` | 保留当前任务，并只保留最新一个待执行任务 |

`launchTask` 会立即返回接纳结果。除 `DroppedWhileRunning` 和 `Closed` 外，有界策略还会返回
`QueueFull` 或 `ParallelLimitReached`。`TaskLaunchResult.Accepted` 携带一个窄 Handle；
`awaitOutcome()` 可观察 `Completed`、`Replaced`、`Cancelled`、`Closed` 或 `Failed`，但不会暴露
Job 或 Scope。已接纳的 `Latest` 任务仍可能被替换，尚未开始的 `Conflate` 任务也可能被覆盖。
Handle 只描述进程内执行；持久业务完成状态仍应建模在 State 或持久操作中。

挂起式 `viewModel.send(intent)` 返回 executor 端到端结果：`Completed`、`Ignored(reason)`、
`Failed`、`Cancelled` 或 `Rejected`；`trySend` 只返回 mailbox 接纳结果。Executor 内使用
`stateAtStart` 做稳定快照决策、用 `currentState` 读取最新提交、用 `intentId` 做关联，并通过
`reportFailure` 上报已经显式处理的功能失败。取消任务时使用 `cancelTask` 或 `cancelAllTasks`，
不要保存 Coroutine Job。

Task Token 会阻止被取消或替换的任务提交迟到 Mutation。绝不能把 `CancellationException` 映射为
业务失败；应先原样抛出，再处理普通 `Exception`。

## Owner、生命周期与 SavedState

通过显式 `ViewModelStoreOwner` 与稳定 key 获取 ViewModel：

```kotlin
val viewModel = pulseViewModel(
    owner = backStackEntry,
    key = "screen",
    modelClass = ScreenViewModel::class.java,
) { extras -> createScreenViewModel(extras) }
```

Activity/Fragment owner 只用于相同作用域的功能；导航目的地状态使用对应 `NavBackStackEntry`，
需要独立生命周期的嵌套功能使用它自己的显式 owner。

Compose 中显式传入 LifecycleOwner：

```kotlin
val state by viewModel.collectStateAsStateWithLifecycle(lifecycleOwner)
val loading by viewModel.collectSelectedState(lifecycleOwner, ScreenState::loading)

viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
    // 执行导航、Snackbar 或其他一次性 UI 行为。
}
```

使用 `PulseSavedStateAdapter` 只保存稳定、可恢复的业务数据，Schema 版本和迁移逻辑由 Feature 管理。
`PulseRestoreFailurePolicy.FALLBACK_TO_INITIAL_STATE` 会上报恢复失败并使用构造参数 State；
`FAIL_CREATION` 会上报后终止 ViewModel 创建。不要保存 Job、Task Token 或 UI Effect，也不需要为了
框架让整个 State 实现 `Parcelable`。子类需要额外清理时覆写 `onPulseCleared`；`onCleared` 由框架
最终实现，不能替换。

## State Decomposition

State Decomposition 是 `mvi-extensions` 中的可选能力。`StateLens` 把普通子状态 Reducer 提升到
Root State；子状态无需实现 `MviState`。

```kotlin
val imageLens = stateLens<RootState, ImageState>(
    get = RootState::image,
    set = { root, image -> root.copy(image = image) },
)

val reducer = pulseMutationReducer<RootState, DashboardMutation, DashboardEffect> {
    onSub<ImageState, DashboardMutation.ImageLoaded>(imageLens) { image, mutation ->
        subStateJust(image.copy(items = mutation.items))
    }
    ignore<DashboardMutation.AnalyticsOnly>("handled outside state")
}
```

未注册 Mutation 默认立即失败；重复或重叠路由在构建 Reducer 时失败。每个 Lens 必须满足
Get-Put、Put-Get 与 Put-Put。

## 测试

测试依赖引入 `mvi-testing`，使用虚拟时间：

```kotlin
@Test
fun refresh() = runPulseTest {
    val store = testStore(initialState, reducer)
    store.send(Input.Refresh)
    store.stateProbe.assertLatest(expectedState)
    store.failureProbe.assertEmpty()
}
```

Transition、Effect 与 Failure Probe 用于有序断言；自定义 Runtime 实现可复用 `PulseStoreTck`
校验 Store 契约。Probe 断言消息会携带最新 Transition sequence，并通过配置的 Redactor 处理 State
和 Effect；只有确认数据可打印时才使用自定义测试 Redactor。Task 取消通过 `TaskHandle.awaitOutcome`
断言，关闭通过 `awaitClosed` 断言；Android 生命周期和 SavedState 行为应在平台测试中验证，不要依赖
延时碰运气。

## 迁移

v0.2 的 `Store`、`DefaultStore`、`Reducer`、`Next` 与回调订阅仍通过同一个 0.3 引擎提供。
新功能优先使用协程 API。行为变化与分步迁移方法见
[0.2 到 0.3 迁移指南](MIGRATION_0.2_TO_0.3.zh-CN.md)。
