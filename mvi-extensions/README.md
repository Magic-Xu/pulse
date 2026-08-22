# mvi-extensions

Pulse 的可选扩展模块。状态拆分 API 只位于本模块，不属于 `mvi-core-contract`。

> 当前稳定版为 `0.3.0`，已发布到 Maven Central。

## 依赖

```kotlin
dependencies {
    implementation(project(":mvi-extensions"))
}
```

## State Decomposition

- `StateLens<ROOT, SUB>`、`stateLens`、`updateSubState`：读取和回写局部状态；`SUB` 无需实现 `MviState`。
- `pulseMutationReducer`：构建 v0.3 `PulseMutationReducer`。
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

## v0.2 兼容扩展

`LoggingPlugin` 和 `StateTransitionPlugin` 继续服务 v0.2 `DefaultStore` / `StorePlugin` API。v0.3 Store 插件接口是 `PulseStorePlugin`，不要把两类插件混用。

## 验证

```bash
./gradlew :mvi-extensions:check
```
