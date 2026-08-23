# mvi-platform-android-compose

Pulse 的 Android Compose 绑定层，公开传递 `mvi-platform-android`。

> 当前稳定版为 `0.4.0`，已发布到 Maven Central。最低 Android API 为 23。

## 依赖

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-platform-android-compose:0.4.0")
}
```

## v0.4 Compose API

- `pulseViewModel(owner, key, modelClass, creator)`：使用显式 owner 创建或复用 ViewModel，不做隐式 owner fallback。
- `collectStateAsStateWithLifecycle`：按显式 `LifecycleOwner` 收集完整状态。
- `collectSelectedState`：提供同步初值，只在选区值发生变化时更新 Compose `State`。
- `ObserveUiEffects`：只在指定生命周期处于活跃状态时占有 effect 协调者。

```kotlin
@Composable
fun Screen(
    owner: ViewModelStoreOwner,
    lifecycleOwner: LifecycleOwner,
) {
    val viewModel = pulseViewModel(
        owner = owner,
        key = "screen",
        modelClass = ScreenViewModel::class.java,
        creator = PulseViewModelCreator { ScreenViewModel() },
    )
    val title by viewModel.collectSelectedState(
        lifecycleOwner = lifecycleOwner,
        selector = ScreenState::title,
    )
    viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
        // navigation, snackbar, etc.
    }
}
```

`UiEffectStream` 是 replay-zero 且单协调者的流：STOP 后本次协调会结束，期间产生的 effect 不会在下一次 START 时重放；同一 stream 同时安装第二个协调者会失败。每个 host 应只设置一个 `ObserveUiEffects` 入口，再由该入口分发到 UI。

## v0.2 兼容

`Store.collectStateAsState` 和 `Store.observeEffects` 继续保留。显式 `LifecycleOwner` 重载是首选；无参重载仅用于 v0.2 源码兼容，会读取当前 composition owner。

## 验证

```bash
./gradlew :mvi-platform-android-compose:testDebugUnitTest
```
