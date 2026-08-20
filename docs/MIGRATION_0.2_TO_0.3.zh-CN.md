# 从 Pulse 0.2 迁移到 0.3

英文版：[MIGRATION_0.2_TO_0.3.md](./MIGRATION_0.2_TO_0.3.md)

> `0.3.0-SNAPSHOT` 仍在开发中，尚未发布。在稳定版 `0.3.0` 正式公布前，请继续使用当前仓库中
> 可用的版本。

Pulse 0.3 保留 0.2 的公开 API 和制品坐标作为兼容层。应用可以先升级版本，再按功能逐步迁移，
不需要一次性重写全部代码。

## 先选择迁移路径

### 保留 0.2 API

暂不需要新能力的功能，可以继续使用 `Store`、`DefaultStore`、`Reducer`、`Next`、
`PulseViewModel` 或 `PulseSplitViewModel`。这些类型会运行在 0.3 的有序引擎上，同时保留 0.2
调用方式。

依赖回调时机或重入派发的代码需要重新测试。兼容层保留 API 形状，但 0.3 会有意强化顺序、回调
隔离、取消和关闭语义。

### 采用 0.3 API

需要挂起式背压、迁移帧、类型化失败、按键任务、生命周期感知 selector 或 replay-zero UI effect
的功能，应迁移到新 API。

| 0.2 API | 0.3 API |
|---|---|
| `Reducer` + `Next` | `PulseReducer` + `ReduceOutcome` |
| `Store` / `DefaultStore` | `PulseStore` / `DefaultPulseStore` |
| `dispatch(intent)` | `send(input)` 或 `trySend(input)` |
| `currentState` / `observeState` | `state: StateFlow<S>` |
| `observeEffect` | 单协调者 `effects` 或 Android `ObserveUiEffects` |
| `StorePlugin` 回调 | 基于不可变 `TransitionFrame` 的 `PulseStorePlugin` |
| 瞬时 `MviEffect` | `UiEffect` |
| `MutationReducer` + `PulseSplitViewModel` | `PulseMutationReducer` + `PulseSplitStoreViewModel` |

## 1. 更新依赖

0.2 的五个坐标保持不变，0.3 新增 `mvi-testing`：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-core-contract:0.3.0")
    implementation("io.github.magic-xu:mvi-core-runtime:0.3.0")

    // Android 项目通常只需要依赖实际使用的最高层适配器。
    implementation("io.github.magic-xu:mvi-platform-android-compose:0.3.0")

    // 可选。
    implementation("io.github.magic-xu:mvi-extensions:0.3.0")
    testImplementation("io.github.magic-xu:mvi-testing:0.3.0")
}
```

`0.3.0` 正式发布前不要使用以上稳定版坐标。Android Compose 制品会传递暴露 Android、runtime
和 contract API。

## 2. 显式表达 reducer 结果

0.2 reducer 总是通过 `Next` 返回状态。0.3 reducer 需要说明输入是改变了状态、已处理但状态未变，
还是明确不适用：

```kotlin
sealed interface CounterEffect : UiEffect {
    data object LimitReached : CounterEffect
}

val reducer = PulseReducer<CounterState, CounterIntent, CounterEffect> { state, intent ->
    when (intent) {
        CounterIntent.Increment ->
            ReduceOutcome.Changed(state.copy(count = state.count + 1))

        CounterIntent.ShowLimit ->
            ReduceOutcome.Unchanged(listOf(CounterEffect.LimitReached))

        CounterIntent.NotApplicable ->
            ReduceOutcome.Ignored("not-applicable")
    }
}
```

如果 `Changed` 的候选状态与当前状态相等，运行时会将其归一化为 `Unchanged`。`Ignored` 不能携带
effect。创建 outcome 时，effect 集合会被快照化。

## 3. 从回调式派发迁移到有序 Store

```kotlin
val store = DefaultPulseStore(
    initialState = CounterState(),
    reducer = reducer,
)

val result = store.send(CounterIntent.Increment)
val current = store.state.value

store.close()
store.awaitClosed()
```

- `send` 会在容量不足时挂起，并在完成 reduce、commit、transition 发布和 effect 路由后返回。
- `trySend` 永不挂起。`EnqueueResult.Enqueued` 只表示已入队，不表示迁移已完成；队列满和生命周期
  拒绝都有显式结果。
- `state` 是新 API 唯一的当前状态源。
- `transitions` 会暴露每个已处理输入，包括 `Unchanged`、`Ignored` 和 reducer 失败。
- `close()` 建立接收截止点；截止点之前已接收的输入会按序排空，`awaitClosed()` 是完成屏障。

不要在 Store 外再套一层队列。并发和重入 send 已统一进入一个有界 FIFO mailbox。

## 4. 区分 UI effect 与持久工作

一次性前台指令应实现 `UiEffect`。UI effect 的 replay 为 0，并且同一时刻只有一个活动协调者。
如果没有活动协调者，或会话结束时仍有待处理 effect，Pulse 会报告
`PulseFailure.UndeliveredUiEffect`，不会在以后重放。

进程内异步工作使用按键任务：

```kotlin
context.launchTask(TaskKey("refresh"), TaskPolicy.Latest) {
    val result = repository.refresh()
    mutate(CounterMutation.Refreshed(result))
}
```

根据业务并发规则选择 `Latest`、`DropWhileRunning`、`Queue(capacity)`、
`Parallel(maxConcurrency)` 或 `Conflate`。超过有界策略容量时会返回 `QueueFull` 或
`ParallelLimitReached`。已接纳任务会提供 `TaskHandle`；只有协调者需要观察进程内最终结果时才等待它。
取消任务使用 `cancelTask`/`cancelAllTasks`，不要保存 Job。任务不会跨进程死亡
持久化。必须跨重建存活的工作，需要持久状态、操作 ID，以及持久化或外部调度器。

## 5. 迁移 Android Split Intent

新 API 功能应把 `MutationReducer` 换成 `PulseMutationReducer`，让 UI effect 实现 `UiEffect`，
并继承 `PulseSplitStoreViewModel`：

```kotlin
class CounterViewModel(
    repository: CounterRepository,
) : PulseSplitStoreViewModel<CounterState, CounterUiIntent, CounterMutation, CounterEffect>(
    initialState = CounterState(),
    mutationReducer = PulseMutationReducer { state, mutation ->
        when (mutation) {
            is CounterMutation.Loaded -> ReduceOutcome.Changed(
                state.copy(value = mutation.value),
            )
        }
    },
    uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
        when (intent) {
            CounterUiIntent.Refresh -> {
                context.launchTask(
                    key = TaskKey("refresh"),
                    policy = TaskPolicy.Latest,
                ) {
                    mutate(CounterMutation.Loaded(repository.load()))
                }
                PulseIntentExecutionDecision.Completed
            }
        }
    },
)
```

UI 代码只能获得 `send(UI)` 和 `trySend(UI)`。mutation 权限只存在于 `PulseIntentContext` 和
绑定 token 的任务上下文中；不要从 ViewModel 暴露 mutation dispatcher 或底层 Store。

与 Core Store 的 `send` 不同，挂起式 ViewModel `send(UI)` 还会等待串行 executor 的决策，并返回
`Completed`、`Ignored`、`Failed`、`Cancelled` 或 `Rejected`；`trySend(UI)` 仍只表示接纳。
Executor 可用 `intentId` 关联请求、对比稳定的 `stateAtStart` 与最新 `currentState`，并通过
`reportFailure` 上报已经显式处理的失败。

如果提交后的状态需要在 Android 进程重建后恢复，可提供 `PulseSavedStateAdapter`。只恢复状态值；
任务、UI effect、订阅和 mailbox 待处理项都不会恢复。

## 6. 显式传入 Android 和 Compose owner

获取 ViewModel 时应显式传入 `ViewModelStoreOwner`、稳定 key、model class 和 creator。Compose
状态与 effect 绑定也要求显式 `LifecycleOwner`：

```kotlin
val state by viewModel.collectSelectedState(
    lifecycleOwner = lifecycleOwner,
    selector = CounterState::count,
)

viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
    when (effect) {
        CounterEffect.LimitReached -> showLimitMessage()
    }
}
```

默认只在生命周期达到 `STARTED` 时收集。状态恢复时会得到最新提交值；活动会话之外产生的 UI
effect 不会重放。

旧功能仍可继续使用 0.2 的 `Store.collectStateAsState` 和 `Store.observeEffects` 重载。

## 7. 迁移可选组合能力与测试

- 状态拆分位于 `mvi-extensions`。只有功能复杂度确实需要局部 reducer 时，才使用 `StateLens`
  和 `pulseMutationReducer`。
- 使用 `mvi-testing` 获取 `runPulseTest`、`TestPulseStore`、state/transition/effect/failure probe
  和可复用的 `PulseStoreTck`。
- 测试顺序和 outcome，不要依赖回调调度时机。还应覆盖与功能有关的 effect 协调者启停、任务策略、
  过期 mutation 拒绝和关闭截止点。

## 迁移检查清单

- [ ] 按功能选择兼容模式或新 API。
- [ ] 把瞬时 effect 改为 `UiEffect`，把持久工作单独建模。
- [ ] 用正确的 `ReduceOutcome` 分支替换 `Next`。
- [ ] 用 `StateFlow` 收集替换回调式状态所有权。
- [ ] 为每个异步操作选择显式 task key 和 policy。
- [ ] 在 Android 上显式传入 ViewModel owner 和 lifecycle owner。
- [ ] 关闭自己持有的 Store，并在协调器和测试中等待关闭完成。
- [ ] 发布迁移后的应用前，运行源码、二进制、行为、Android 生命周期和纯制品消费检查。
