# mvi-platform-android

`mvi-platform-android` 是第 3 步：把 Android 特有能力作为子组件接入，不污染跨平台 core。

## 目标

- 提供 Android `ViewModel` 适配层
- 提供 Compose 对 `Store` 的状态与 effect 绑定能力
- 保持 `mvi-core-contract` 与 `mvi-core-runtime` 纯平台无关
- 让 Android 使用方只依赖本模块即可（通过 `api` 透传 core 所需类型）

## 目录结构

- `MviViewModel.kt`
  - `MviViewModel`：用 `ViewModel` 持有 `DefaultStore`，并在 `onCleared` 时自动 `close`
- `StoreCompose.kt`
  - `collectStateAsState()`：把 `Store` 状态订阅成 Compose `State`
  - `observeEffects()`：在 Compose 中消费一次性 `Effect`

## 实验原理（为什么这样实现）

1. 平台能力后置
   - core 模块不依赖 Android；Android 相关能力都在本模块实现，满足跨平台主线。

2. 生命周期托管给 ViewModel
   - `MviViewModel` 把 Store 生命周期绑定到 `ViewModel`，避免页面销毁后资源泄漏。

3. Compose 只做绑定，不侵入业务
   - `collectStateAsState` 与 `observeEffects` 只是订阅桥接层，业务 intent/reducer 完全不依赖 Compose。

## 当前扩展方向

- 增加 `LifecycleOwner` 感知版本（前后台自动 start/stop）
- 增加 `collectStateWithLifecycle` 版本（更细粒度生命周期收集）
- 增加 Android 调试插件（Logcat、StrictMode 监控）

## 示例接入

`app` 模块已集成最小 Counter 示例：

- `CounterViewModel` 继承 `MviViewModel`
- Compose 页面使用 `collectStateAsState` 和 `observeEffects`

你可直接参考：

- `/app/src/main/java/com/magic/pulse/mvi/CounterMvi.kt`

依赖方式（推荐）：

```kotlin
dependencies {
    implementation(project(":mvi-platform-android"))
}
```

不需要再显式依赖 `:mvi-core-runtime` 或 `:mvi-core-contract`。
