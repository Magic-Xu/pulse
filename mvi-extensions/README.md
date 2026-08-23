# mvi-extensions

Pulse 的可选扩展模块。状态拆分 API 只位于本模块，不属于 `mvi-core-contract`。

> 当前已发布稳定版为 `0.3.0`；本分支正在准备 `0.4.0`。

## 依赖

```kotlin
dependencies {
    implementation(project(":mvi-extensions"))
}
```

## State Decomposition

- `StateLens<ROOT, SUB>`、`stateLens`、`updateSubState`：读取和回写局部状态；`SUB` 无需实现 `MviState`。
- `pulseMutationReducer`：构建 `PulseMutationReducer`。
- `on`：处理根状态 mutation。
- `onSub`：通过 lens 把局部 reducer 结果提升为根状态结果。
- `ignore(reason)`：显式忽略某一 mutation 类型。
- `SubStateNext`、`subStateJust`、`subStateWithEffect`、`subStateWithEffects`：描述 marker-free 子状态结果。

未注册 mutation 会 fail-fast。重复路由以及父子类型重叠路由会在 reducer 构建时抛错，不会按注册顺序静默覆盖。

```kotlin
val feedLens = stateLens<RootState, FeedState>(
    get = RootState::feed,
    set = { root, feed -> root.copy(feed = feed) },
)

val reducer = pulseMutationReducer<RootState, RootMutation, RootEffect> {
    onSub<FeedState, RootMutation.FeedLoaded>(feedLens) { feed, mutation ->
        subStateJust(feed.copy(items = mutation.items))
    }
    ignore<RootMutation.AnalyticsOnly>("handled outside state reduction")
}
```

### Lens 必须满足的规律

对任意有效的 `root`、`a`、`b`：

1. Get-Put：`lens.set(root, lens.get(root)) == root`
2. Put-Get：`lens.get(lens.set(root, a)) == a`
3. Put-Put：`lens.set(lens.set(root, a), b) == lens.set(root, b)`

不满足这些规律会让局部更新丢失或产生不可预测的根状态。

## 安全日志

`PulseLoggingPlugin` 观察 `DefaultPulseStore` 发布的完整 `TransitionFrame` 和
`PulseFailure`：

```kotlin
val logging = PulseLoggingPlugin<AppState, AppIntent, AppEffect>(
    tag = "Checkout",
    sink = LogSink(logger::debug),
)

val store = DefaultPulseStore(
    initialState = initialState,
    reducer = reducer,
    plugins = listOf(logging),
)
```

默认使用 `TypeOnlyPulseRedactor`。input、state、effect、忽略原因、task key 和诊断上下文中的
字符串只输出类型；异常只输出类型，不输出 message 或 stacktrace。只有确认日志目的地和数据策略后，
才应传入自定义 `PulseRedactor`。

如果持有任意 `Flow<TransitionFrame<...>>`，可以用同一套格式和脱敏规则记录 frame：

```kotlin
transitions
    .logPulseTransitions(tag = "Checkout", sink = LogSink(logger::debug))
    .collect { frame -> consume(frame) }
```

`logPulseTransitions` 是惰性的 `onEach` operator：仅在 flow 被收集时写日志，不修改 frame。
它遵循普通 `onEach` 的异常语义，因此除非确实要终止诊断收集，否则 `LogSink` 不应抛异常。

## v0.2 兼容扩展

`LoggingPlugin` 和 `StateTransitionPlugin` 继续服务 v0.2 `DefaultStore` / `StorePlugin` API。v0.3 Store 插件接口是 `PulseStorePlugin`，不要把两类插件混用。

## 验证

```bash
./gradlew :mvi-extensions:check
```
