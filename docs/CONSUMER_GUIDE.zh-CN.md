# 使用方接入指南

英文版：[CONSUMER_GUIDE.md](/Users/magic/Desktop/reborn/MVICore/docs/CONSUMER_GUIDE.md)

面向“使用 Pulse 框架”的项目接入说明。

## Android 最小依赖

### 非 Compose 项目

```kotlin
dependencies {
    implementation(project(":mvi-platform-android"))
}
```

### Compose 项目（推荐）

```kotlin
dependencies {
    implementation(project(":mvi-platform-android-compose"))
}
```

说明：

- `mvi-platform-android` 会透传 `mvi-core-runtime` 与 `mvi-core-contract`
- `mvi-platform-android-compose` 会透传 `mvi-platform-android`
- 业务层可直接使用核心类型（`MviState` / `MviIntent` / `MviEffect`）
- 业务模块通常不需要重复声明 core 依赖

## 可选扩展

如果需要日志或状态迁移追踪插件，可增加：

```kotlin
dependencies {
    implementation(project(":mvi-extensions"))
}
```

## 推荐接入顺序

1. 先定义 `State` / `Intent` / `Effect`
2. 实现 `Reducer`（双通道模式下实现 `MutationReducer`）
3. 使用 `PulseViewModel` 承载 Store
4. 复杂业务优先使用 `PulseSplitViewModel`（`UiIntent` -> 副作用 -> `Mutation`）
5. Compose 侧使用 `collectStateAsState()` 与 `observeEffects()`
6. 按需接入 `mvi-extensions` 插件
