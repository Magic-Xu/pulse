# mvi-platform-android

`mvi-platform-android` 是第 3 步的 Android 基础层：只包含 ViewModel 适配，不包含 Compose 依赖。

## 目标

- 提供 Android `ViewModel` 适配层
- 保持 `mvi-core-contract` 与 `mvi-core-runtime` 纯平台无关
- 让 Android 使用方只依赖本模块即可（通过 `api` 透传 core 所需类型）

## 目录结构

- `MviViewModel.kt`
  - `MviViewModel`：用 `ViewModel` 持有 `DefaultStore`，并在 `onCleared` 时自动 `close`
## 实验原理（为什么这样实现）

1. 平台能力后置
   - core 模块不依赖 Android；Android 相关能力都在本模块实现，满足跨平台主线。

2. 生命周期托管给 ViewModel
   - `MviViewModel` 把 Store 生命周期绑定到 `ViewModel`，避免页面销毁后资源泄漏。

3. Compose 绑定独立拆分
   - Compose 相关代码拆到 `mvi-platform-android-compose`，避免非 Compose 项目被动引入 Compose。

## 当前扩展方向

- 增加 `LifecycleOwner` 感知版本（前后台自动 start/stop）
- 增加 `collectStateWithLifecycle` 版本（更细粒度生命周期收集）
- 增加 Android 调试插件（Logcat、StrictMode 监控）

## 示例接入

依赖方式（推荐）：

```kotlin
dependencies {
    implementation(project(":mvi-platform-android"))
}
```

Compose 项目请依赖 `:mvi-platform-android-compose`。
