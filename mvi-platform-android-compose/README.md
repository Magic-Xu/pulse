# mvi-platform-android-compose

`mvi-platform-android-compose` 是 Android Compose 绑定层，依赖 `mvi-platform-android`。

## 目标

- 提供 Compose 对 `Store` 的状态与 effect 绑定能力
- 让 Compose 相关依赖与 Android 基础层解耦

## 目录结构

- `StoreCompose.kt`
  - `collectStateAsState()`：把 `Store` 状态订阅成 Compose `State`
  - `observeEffects()`：在 Compose 中消费一次性 `Effect`

## 依赖方式

```kotlin
dependencies {
    implementation(project(":mvi-platform-android-compose"))
}
```

不需要额外声明 `:mvi-platform-android` 或 core 模块。
