# mvi-platform-android

`mvi-platform-android` 是 Pulse 的 Android 基础层：只包含 ViewModel 适配，不包含 Compose 依赖。

## 目标

- 提供可继承、可落地的 Android `ViewModel` 基类
- 保持 `mvi-core-contract` 与 `mvi-core-runtime` 纯平台无关
- 让 Android 使用方最小依赖接入（通过 `api` 透传 core 所需类型）

## 目录结构

- `PulseViewModel.kt`
  - `PulseViewModel`：开放继承的主基类，`dispatch(intent)` 为 `final`，保证状态只能经 reducer 变更
- `PulseSplitViewModel.kt`
  - `PulseSplitViewModel`：双通道版，`send(uiIntent)` 输入 UI 意图，内部只分发 `mutation`
  - `UiIntentExecutor` / `UiIntentExecutionScope`：处理 UI 意图副作用，并回写 mutation
- `IntentExecutor.kt`
  - `IntentExecutor` / `IntentExecutionScope`：副作用执行接口与运行作用域

## 实现原理（为什么这样做）

1. 平台能力后置
   - core 不依赖 Android；Android 生命周期与并发能力都在此模块注入。

2. 继承友好 + 不变量约束并存
   - `PulseViewModel` 允许业务继承，适配大型工程的复杂 VM 继承链。
   - 同时 `dispatch` 设为 `final`，防止子类绕开 reducer 破坏单向数据流。

3. 双通道意图模型
   - 复杂业务可用 `PulseSplitViewModel`：UI 输入与 reducer mutation 分离。
   - reducer 只处理 mutation，副作用在 `UiIntentExecutor` 中组织。

4. Compose 绑定独立拆分
   - Compose 相关能力放在 `mvi-platform-android-compose`，避免非 Compose 项目被动引入。

## 推荐使用方式

```kotlin
dependencies {
    implementation(project(":mvi-platform-android"))
}
```

Compose 项目请依赖 `:mvi-platform-android-compose`。
