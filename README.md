# Pulse

Pulse represents each state transition in MVI — a clear, observable signal of change.

Minimal, cross-platform-first MVI framework.

[![GitHub Repo](https://img.shields.io/badge/GitHub-Magic--Xu%2Fpulse-181717?logo=github)](https://github.com/Magic-Xu/pulse)
[![GitHub Stars](https://img.shields.io/github/stars/magic-xu/pulse?style=flat)](https://github.com/Magic-Xu/pulse/stargazers)
[![License](https://img.shields.io/github/license/magic-xu/pulse)](https://github.com/Magic-Xu/pulse/blob/main/LICENSE)
[![Maven Central Core](https://img.shields.io/maven-central/v/io.github.magic-xu/mvi-core-runtime?label=Maven%20Central%20(core))](https://central.sonatype.com/artifact/io.github.magic-xu/mvi-core-runtime)
[![Maven Central Android](https://img.shields.io/maven-central/v/io.github.magic-xu/mvi-platform-android?label=Maven%20Central%20(android))](https://central.sonatype.com/artifact/io.github.magic-xu/mvi-platform-android)
[![Maven Central Compose](https://img.shields.io/maven-central/v/io.github.magic-xu/mvi-platform-android-compose?label=Maven%20Central%20(compose))](https://central.sonatype.com/artifact/io.github.magic-xu/mvi-platform-android-compose)

Current version: `0.1.0`

Chinese README: [README.zh-CN.md](/Users/magic/Desktop/reborn/MVICore/README.zh-CN.md)

## Overview

- Unidirectional flow: `Intent -> Reducer -> State (+ Effect)`
- Clear separation of `State` (replayable) and `Effect` (one-shot)
- Core modules are platform-agnostic
- Android and Compose integrations are optional platform modules

## Modules

- `mvi-core-contract`: core contracts (`MviState`, `MviIntent`, `MviEffect`, `Reducer`, `Store`)
- `mvi-core-runtime`: runtime implementation (`DefaultStore`, `StorePlugin`)
- `mvi-platform-android`: Android `ViewModel` adapter
- `mvi-platform-android-compose`: Compose bindings (`collectStateAsState`, `observeEffects`)
- `mvi-extensions`: optional plugins (logging, state transition tracking)

## Two-Lane Intent Model

Pulse now supports split intents for complex features:

- `MviUiIntent`: external input from UI/user actions
- `MviMutation`: internal state transition messages consumed by reducer
- `PulseSplitViewModel`: `send(uiIntent)` -> executor side effects -> `dispatchMutation(mutation)` -> reducer

This keeps reducers pure and keeps IO/business orchestration out of mutation logic.

## Maven Central Setup

Ensure `mavenCentral()` is configured:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

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

## App Dependency Mode (Local/Remote)

The `app` module supports two modes:

- `local`: depend on included project libs (default)
- `remote`: depend on Maven Central artifacts

One-click switch:

```bash
./gradlew useLocalPulseDeps
./gradlew useRemotePulseDeps
./gradlew printPulseDepMode
```

Manual override for one build:

```bash
./gradlew :app:assembleDebug -PPULSE_APP_DEP_MODE=remote
./gradlew :app:assembleDebug -PPULSE_APP_DEP_MODE=local
```

## Quick Usage (Android + Compose)

```kotlin
import com.magic.mvicore.android.PulseViewModel
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
    override fun reduce(previous: CounterState, intent: CounterIntent): Next<CounterState, CounterEffect> {
        return when (intent) {
            CounterIntent.Increase -> {
                val next = previous.copy(count = previous.count + 1)
                if (next.count == 10) Next.withEffect(next, CounterEffect.ReachTen) else Next.just(next)
            }
            CounterIntent.Decrease -> Next.just(previous.copy(count = previous.count - 1))
        }
    }
}

class CounterViewModel : PulseViewModel<CounterState, CounterIntent, CounterEffect>(
    initialState = CounterState(),
    reducer = CounterReducer
) {
    fun increase() = dispatch(CounterIntent.Increase)
}
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

    Button(onClick = { viewModel.increase() }) {
        Text("Count = ${state.count}")
    }
}
```

## Links

- GitHub: [https://github.com/Magic-Xu/pulse](https://github.com/Magic-Xu/pulse)
- Releases: [https://github.com/Magic-Xu/pulse/releases](https://github.com/Magic-Xu/pulse/releases)
- Issues: [https://github.com/Magic-Xu/pulse/issues](https://github.com/Magic-Xu/pulse/issues)
- API (Contract Source): [https://github.com/Magic-Xu/pulse/tree/main/mvi-core-contract/src/main/kotlin/com/magic/mvicore/contract](https://github.com/Magic-Xu/pulse/tree/main/mvi-core-contract/src/main/kotlin/com/magic/mvicore/contract)
- API (Runtime Source): [https://github.com/Magic-Xu/pulse/tree/main/mvi-core-runtime/src/main/kotlin/com/magic/mvicore/runtime](https://github.com/Magic-Xu/pulse/tree/main/mvi-core-runtime/src/main/kotlin/com/magic/mvicore/runtime)
- API (Android Source): [https://github.com/Magic-Xu/pulse/tree/main/mvi-platform-android/src/main/java/com/magic/mvicore/android](https://github.com/Magic-Xu/pulse/tree/main/mvi-platform-android/src/main/java/com/magic/mvicore/android)

## Docs

- Consumer guide (EN): [docs/CONSUMER_GUIDE.md](/Users/magic/Desktop/reborn/MVICore/docs/CONSUMER_GUIDE.md)
- Consumer guide (中文): [docs/CONSUMER_GUIDE.zh-CN.md](/Users/magic/Desktop/reborn/MVICore/docs/CONSUMER_GUIDE.zh-CN.md)
- Iteration roadmap (EN): [docs/ITERATION_ROADMAP.md](/Users/magic/Desktop/reborn/MVICore/docs/ITERATION_ROADMAP.md)
- Iteration roadmap (中文): [docs/ITERATION_ROADMAP.zh-CN.md](/Users/magic/Desktop/reborn/MVICore/docs/ITERATION_ROADMAP.zh-CN.md)
- Release plan (EN): [docs/RELEASE_PLAN.md](/Users/magic/Desktop/reborn/MVICore/docs/RELEASE_PLAN.md)
- Release plan (中文): [docs/RELEASE_PLAN.zh-CN.md](/Users/magic/Desktop/reborn/MVICore/docs/RELEASE_PLAN.zh-CN.md)
- Maven Central publishing (EN): [docs/PUBLISH_MAVEN_CENTRAL.md](/Users/magic/Desktop/reborn/MVICore/docs/PUBLISH_MAVEN_CENTRAL.md)
- Maven Central publishing (中文): [docs/PUBLISH_MAVEN_CENTRAL.zh-CN.md](/Users/magic/Desktop/reborn/MVICore/docs/PUBLISH_MAVEN_CENTRAL.zh-CN.md)

## Iteration Roadmap

Pulse v0.2 roadmap for production-scale MVI:

1. Reducer entry invariants.
   status: done
   result: `PulseViewModel` + `IntentExecutionScope`

2. Split intent channels (UI intent vs mutation).
   status: done
   result: `MviUiIntent` + `MviMutation` + `PulseSplitViewModel` + `UiIntentExecutor`

3. State decomposition toolkit.
   status: planned
   target: sub-state reducers and state-domain composition

4. Feature/store composition.
   status: planned
   target: parent-child store orchestration for complex screens

5. Effect execution middle layer.
   status: planned
   target: isolate IO/navigation/analytics from reducer

6. Debug tooling.
   status: planned
   target: intent trace + state diff timeline

7. Test DSL.
   status: planned
   target: deterministic reducer/store assertions with low boilerplate
