# Consumer Guide

面向“使用这个框架”的项目接入说明。

## Android 使用方最小依赖

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

- `mvi-platform-android` 会透传 `mvi-core-runtime` 和 `mvi-core-contract`
- `mvi-platform-android-compose` 会透传 `mvi-platform-android`
- 业务层可以直接使用 `MviState/MviIntent/MviEffect` 等核心类型
- 不需要额外手写 core 模块依赖

## 可选扩展

如果需要日志或状态变化追踪插件，再增加：

```kotlin
dependencies {
    implementation(project(":mvi-extensions"))
}
```

## 推荐接入顺序

1. 先定义 `State/Intent/Effect`
2. 实现 `Reducer`
3. 用 `MviViewModel` 承载 Store
4. Compose 侧使用 `collectStateAsState()` 和 `observeEffects()`
5. 按需接入 `mvi-extensions` 插件
