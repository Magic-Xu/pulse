# MVICore

Minimal, cross-platform-first MVI framework.  
极简、跨平台优先的 MVI 框架。

[![GitHub Repo](https://img.shields.io/badge/GitHub-Magic--Xu%2Fpulse-181717?logo=github)](https://github.com/Magic-Xu/pulse)
[![GitHub Stars](https://img.shields.io/github/stars/Magic-Xu/pulse?style=flat)](https://github.com/Magic-Xu/pulse/stargazers)
[![License](https://img.shields.io/github/license/Magic-Xu/pulse)](https://github.com/Magic-Xu/pulse/blob/main/LICENSE)
[![Maven Central Core](https://img.shields.io/maven-central/v/io.github.magic-xu/mvi-core-runtime?label=Maven%20Central%20(core))](https://central.sonatype.com/artifact/io.github.magic-xu/mvi-core-runtime)
[![Maven Central Android](https://img.shields.io/maven-central/v/io.github.magic-xu/mvi-platform-android?label=Maven%20Central%20(android))](https://central.sonatype.com/artifact/io.github.magic-xu/mvi-platform-android)
[![Maven Central Compose](https://img.shields.io/maven-central/v/io.github.magic-xu/mvi-platform-android-compose?label=Maven%20Central%20(compose))](https://central.sonatype.com/artifact/io.github.magic-xu/mvi-platform-android-compose)

Current version / 当前版本: `0.1.0`

## Links

- GitHub: [https://github.com/Magic-Xu/pulse](https://github.com/Magic-Xu/pulse)
- Releases: [https://github.com/Magic-Xu/pulse/releases](https://github.com/Magic-Xu/pulse/releases)
- Issues: [https://github.com/Magic-Xu/pulse/issues](https://github.com/Magic-Xu/pulse/issues)
- API (Contract Source): [https://github.com/Magic-Xu/pulse/tree/main/mvi-core-contract/src/main/kotlin/com/magic/mvicore/contract](https://github.com/Magic-Xu/pulse/tree/main/mvi-core-contract/src/main/kotlin/com/magic/mvicore/contract)
- API (Runtime Source): [https://github.com/Magic-Xu/pulse/tree/main/mvi-core-runtime/src/main/kotlin/com/magic/mvicore/runtime](https://github.com/Magic-Xu/pulse/tree/main/mvi-core-runtime/src/main/kotlin/com/magic/mvicore/runtime)
- API (Android Source): [https://github.com/Magic-Xu/pulse/tree/main/mvi-platform-android/src/main/java/com/magic/mvicore/android](https://github.com/Magic-Xu/pulse/tree/main/mvi-platform-android/src/main/java/com/magic/mvicore/android)

## 中文

### 简介

MVICore 采用最小核心设计：

- 单向数据流：`Intent -> Reducer -> State (+ Effect)`
- `State` 与 `Effect` 分离（状态可重放，事件一次性）
- 核心模块不依赖 Android
- Android/Compose 通过子模块按需接入

### 模块

- `mvi-core-contract`: 跨平台契约（`MviState` / `MviIntent` / `MviEffect` / `Reducer` / `Store`）
- `mvi-core-runtime`: 跨平台运行时（`DefaultStore` + `StorePlugin`）
- `mvi-platform-android`: Android `ViewModel` 适配
- `mvi-platform-android-compose`: Compose 绑定（`collectStateAsState` / `observeEffects`）
- `mvi-extensions`: 可选扩展（日志插件、状态迁移插件）

### 通过 Maven Central 依赖

在 `settings.gradle.kts` 确保有 `mavenCentral()`：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Compose Android 项目（推荐）：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-platform-android-compose:0.1.0")
}
```

非 Compose Android 项目：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-platform-android:0.1.0")
}
```

可选扩展：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-extensions:0.1.0")
}
```

纯核心（跨平台）：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-core-runtime:0.1.0")
}
```

### 最小使用示例（Android + Compose）

```kotlin
import com.magic.mvicore.android.MviViewModel
import com.magic.mvicore.android.compose.collectStateAsState
import com.magic.mvicore.android.compose.observeEffects
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.Reducer

data class CounterState(val count: Int = 0) : MviState

sealed interface CounterIntent : MviIntent {
    data object Increase : CounterIntent
    data object Decrease : CounterIntent
}

sealed interface CounterEffect : MviEffect {
    data object ReachTen : CounterEffect
}

object CounterReducer : Reducer<CounterState, CounterIntent, CounterEffect> {
    override fun reduce(
        previous: CounterState,
        intent: CounterIntent
    ): Next<CounterState, CounterEffect> {
        return when (intent) {
            CounterIntent.Increase -> {
                val next = previous.copy(count = previous.count + 1)
                if (next.count == 10) Next.withEffect(next, CounterEffect.ReachTen) else Next.just(next)
            }
            CounterIntent.Decrease -> Next.just(previous.copy(count = previous.count - 1))
        }
    }
}

class CounterViewModel : MviViewModel<CounterState, CounterIntent, CounterEffect>(
    initialState = CounterState(),
    reducer = CounterReducer
)
```

```kotlin
@Composable
fun CounterScreen(viewModel: CounterViewModel) {
    val state by viewModel.collectStateAsState()

    viewModel.observeEffects { effect ->
        when (effect) {
            CounterEffect.ReachTen -> {
                // show toast / navigate
            }
        }
    }

    Button(onClick = { viewModel.dispatch(CounterIntent.Increase) }) {
        Text("Count = ${state.count}")
    }
}
```

## English

### Overview

MVICore is a minimal MVI framework with a cross-platform core:

- Unidirectional flow: `Intent -> Reducer -> State (+ Effect)`
- Clear separation of `State` (replayable) and `Effect` (one-shot)
- Core modules are platform-agnostic
- Android and Compose integrations are optional platform modules

### Modules

- `mvi-core-contract`: core contracts (`MviState`, `MviIntent`, `MviEffect`, `Reducer`, `Store`)
- `mvi-core-runtime`: runtime implementation (`DefaultStore`, `StorePlugin`)
- `mvi-platform-android`: Android `ViewModel` adapter
- `mvi-platform-android-compose`: Compose bindings (`collectStateAsState`, `observeEffects`)
- `mvi-extensions`: optional plugins (logging, state transition tracking)

### Maven Central Setup

Make sure `mavenCentral()` is configured in `settings.gradle.kts`.

Recommended for Android + Compose:

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-platform-android-compose:0.1.0")
}
```

Android without Compose:

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-platform-android:0.1.0")
}
```

Optional extensions:

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-extensions:0.1.0")
}
```

Core-only usage:

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-core-runtime:0.1.0")
}
```

### Quick Usage

1. Define `State`, `Intent`, `Effect`.
2. Implement a pure `Reducer`.
3. Host the store in `MviViewModel`.
4. In Compose, use `collectStateAsState()` and `observeEffects()`.
5. Dispatch intents via `viewModel.dispatch(intent)`.

## Docs

- Consumer guide: [docs/CONSUMER_GUIDE.md](/Users/magic/Desktop/reborn/MVICore/docs/CONSUMER_GUIDE.md)
- Release plan: [docs/RELEASE_PLAN.md](/Users/magic/Desktop/reborn/MVICore/docs/RELEASE_PLAN.md)
- Maven Central publishing: [docs/PUBLISH_MAVEN_CENTRAL.md](/Users/magic/Desktop/reborn/MVICore/docs/PUBLISH_MAVEN_CENTRAL.md)
