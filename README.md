# MVICore

一个面向个人项目的极简 MVI 框架实验工程。  
目标是先做“核心本质、可运行、可扩展”，再逐步增强。

## 设计总纲

1. `mvi-core-contract`
   - 跨平台契约层（Intent/State/Effect/Reducer/Store）
   - 不依赖 Android

2. `mvi-core-runtime`
   - 跨平台极简运行时（DefaultStore）
   - 串行 dispatch、生命周期控制、effect 分发、插件扩展点

3. `mvi-platform-android`
   - Android 子组件（ViewModel + Compose 绑定）
   - Android 能力后置，避免污染核心模块

4. `mvi-extensions`
   - 可插拔扩展模块（日志插件、状态变化观察插件）
   - 不改核心 API，通过 StorePlugin 扩展能力

5. 后续计划（待实现）
   - 更完整的生命周期策略
   - 多平台适配（如 Desktop / iOS 对接层）

## 模块关系

```text
app
 ├─ mvi-extensions (optional)
 │   └─ mvi-core-runtime
 │       └─ mvi-core-contract
 └─ mvi-platform-android
     └─ mvi-core-runtime
         └─ mvi-core-contract
```

## 架构原则

- 单向数据流：`Intent -> Reducer -> State (+ Effect)`
- State 与 Effect 分离：状态可重放，事件一次性
- 核心跨平台：不绝对依赖 Android
- 扩展优先解耦：插件和平台适配都通过边界接入

## 依赖策略

- Android 业务应用（如 `app`）只需要依赖 `mvi-platform-android`
- `mvi-platform-android` 通过 `api` 透出 `mvi-core-runtime`
- `mvi-core-runtime` 通过 `api` 透出 `mvi-core-contract`

这样使用方不需要写一串 core 依赖，同时仍能直接使用 `MviState/MviIntent/MviEffect` 等核心类型。

## 当前状态

- Step 1 已完成：契约层
- Step 2 已完成：极简运行时
- Step 3 已完成：Android 子组件 + app 最小接入示例
- Step 4 已完成：扩展机制落地（日志插件、状态变化插件）

## 快速验证

```bash
./gradlew :mvi-core-contract:check
./gradlew :mvi-core-runtime:check
./gradlew :mvi-extensions:check
./gradlew :app:assembleDebug
```
