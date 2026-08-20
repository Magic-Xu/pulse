# mvi-platform-android

Pulse 的 Android ViewModel 适配层，不依赖 Compose。

> 当前版本为 `0.3.0-SNAPSHOT`，仍在开发中，尚未发布到 Maven Central。最低 Android API 为 23。

## 依赖

```kotlin
dependencies {
    implementation(project(":mvi-platform-android"))
}
```

若使用状态拆分 DSL，还需显式依赖 `:mvi-extensions`。

## v0.3 Android API

`PulseSplitStoreViewModel<S, UI, M, E>` 是推荐的 Split Intent owner：

- UI 只能通过挂起式 `send(UI)` 或非阻塞 `trySend(UI)` 输入，不能直接获得 mutation 能力；
  `send` 返回执行层的 `Completed`、`Ignored`、`Failed`、`Cancelled` 或 `Rejected`。
- `PulseUiIntentExecutor` 在对应 UI input frame 完成后执行副作用，并返回
  `PulseIntentExecutionDecision`。
- `PulseIntentContext` 暴露关联 `intentId`、稳定的 `stateAtStart`、最新 `currentState`、
  `reportFailure` 和 mutation 能力。
- `launchTask` 支持 keyed `Latest`、`DropWhileRunning`、`Queue(capacity)`、
  `Parallel(maxConcurrency)`、`Conflate` 策略；已接纳请求的 `TaskHandle` 可观察最终执行结果，
  `cancelTask`/`cancelAllTasks` 显式取消任务。
- task token 失效后的 mutation 会被拒绝并报告 `PulseFailure.LateMutation`。
- `PulseStateHost` 只公开 `StateFlow` 状态和 replay-zero `UiEffectStream`。
- `PulseSavedStateAdapter` 由 feature 决定保存格式，不要求整个状态实现 `Parcelable`。

```kotlin
fun createScreenViewModel(repository: ScreenRepository) =
    PulseSplitStoreViewModel<ScreenState, ScreenIntent, ScreenMutation, ScreenEffect>(
        initialState = ScreenState(),
        mutationReducer = PulseMutationReducer { state, mutation ->
            when (mutation) {
                is ScreenMutation.Loaded -> ReduceOutcome.Changed(
                    state.copy(items = mutation.items),
                )
            }
        },
        uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
            when (intent) {
                ScreenIntent.Refresh -> {
                    context.launchTask(
                        key = TaskKey("refresh"),
                        policy = TaskPolicy.Latest,
                    ) {
                        mutate(ScreenMutation.Loaded(repository.load()))
                    }
                    PulseIntentExecutionDecision.Completed
                }
            }
        },
    )
```

默认 `androidPulseRuntimeConfig()` 在 `Dispatchers.Main.immediate` 上执行 Store 与消费者工作。需要测试或自定义调度时，可显式传入 `PulseRuntimeConfig`。

默认生命周期绑定到 `viewModelScope`。测试或自定义 owner 可通过
`PulseAndroidExecutionOwner.from(scope)` 注入父级生命周期；Pulse 只创建并取消自己的子 Job，
不会取消调用方的 scope，实际执行线程仍由 `PulseRuntimeConfig` 决定。

## 显式 ViewModel owner

`pulseViewModel` 要求显式 `ViewModelStoreOwner` 和稳定 key，不会从 Activity、Fragment 或导航栈做隐式 fallback。`PulseViewModelCreator` 能收到 owner 的真实 `CreationExtras`；SavedState 场景可使用 `pulseSavedStateViewModelFactory`。

```kotlin
val vm = pulseViewModel(
    owner = owner,
    key = "screen",
    modelClass = ScreenViewModel::class.java,
    creator = PulseViewModelCreator { extras -> ScreenViewModel(extras) },
)
```

`owner` 可以是 Activity/Fragment、本目的地的 `NavBackStackEntry`，或嵌套功能自己持有的
`ViewModelStoreOwner`。选择哪个 owner，就选择了 ViewModel、任务与 SavedState 的实际生命周期；
不要把目的地功能隐式提升为 Activity 级。

## v0.2 兼容

`PulseViewModel` 和 `PulseSplitViewModel` 继续保留作为 v0.2 兼容入口。新功能应使用
`PulseSplitStoreViewModel` 与 keyed task，不再另外发布一套基于 legacy Split API 的请求 DSL。

Compose 项目使用 `mvi-platform-android-compose`。

## 验证

```bash
./gradlew :mvi-platform-android:testDebugUnitTest
```
