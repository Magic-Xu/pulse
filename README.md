# Pulse

[简体中文](README.zh-CN.md)

Pulse is an ordered, coroutine-first MVI runtime for Kotlin and Android. A store owns one bounded
input mailbox and one processor, publishes state through `StateFlow`, records every processed input
as a correlated transition frame, and delivers replay-zero UI effects through one coordinator.

The latest stable release is **0.3.0**, available from Maven Central.

## Modules

| Artifact | Purpose |
| --- | --- |
| `mvi-core-contract` | Stable state, input, reducer, transition, result, and failure contracts |
| `mvi-core-runtime` | Ordered store engine, effects, plugins, keyed tasks, and v0.2 adapter |
| `mvi-platform-android` | Split Intent ViewModel, explicit owner, SavedState, and Android runtime defaults |
| `mvi-platform-android-compose` | Lifecycle-aware state, selector, effect, and ViewModel bindings |
| `mvi-extensions` | Optional logging, transition helpers, and State Decomposition DSL |
| `mvi-testing` | Virtual-time helpers, probes, `TestPulseStore`, and reusable Store TCK |

All published artifacts use group `io.github.magic-xu`.

## Runtime contract

For each admitted input, Pulse executes one ordered frame:

1. read the current state;
2. reduce to `Changed`, `Unchanged`, or `Ignored`;
3. commit a changed state;
4. publish the transition frame;
5. deliver zero or more correlated UI effects;
6. complete `send`.

`trySend` only reports mailbox admission. `close` establishes an ordered cutoff: inputs accepted
before it drain, while later inputs are rejected. Cancellation and fatal JVM errors are never
converted to business failures; controlled non-fatal boundaries report typed `PulseFailure` values.

## Minimal store

```kotlin
data class CounterState(val value: Int) : MviState

sealed interface CounterInput : MviIntent {
    data object Increment : CounterInput
}

sealed interface CounterEffect : UiEffect

val store = DefaultPulseStore<CounterState, CounterInput, CounterEffect>(
    initialState = CounterState(0),
    reducer = PulseReducer { state, input ->
        when (input) {
            CounterInput.Increment -> ReduceOutcome.Changed(
                state.copy(value = state.value + 1)
            )
        }
    },
)

scope.launch {
    store.send(CounterInput.Increment)
}
```

For Android features, prefer `PulseSplitStoreViewModel`: UI code only receives suspending
`send(UI)`, which returns the executor result, and non-blocking `trySend(UI)`, which returns mailbox
admission. Mutations and keyed tasks are available only inside `PulseIntentContext`.

## Dependency setup

Pulse 0.3.0 is available from Maven Central:

> [!TIP]
> **Most Android Compose apps need one production dependency:**
> `mvi-platform-android-compose`. It already brings the Android, runtime, and contract layers
> transitively. Do not declare those lower layers again.

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("io.github.magic-xu:mvi-platform-android-compose:0.3.0")
}
```

Choose **one** main entry for the platform you use:

| Project type | Main dependency |
| --- | --- |
| Android + Compose | `mvi-platform-android-compose` |
| Android without Compose | `mvi-platform-android` |
| Pure Kotlin / JVM | `mvi-core-runtime` |

Add optional modules only when the feature needs them:

```kotlin
dependencies {
    // Optional: State Lens, reducer decomposition, logging, and transition helpers
    implementation("io.github.magic-xu:mvi-extensions:0.3.0")

    // Optional: virtual time, probes, TestPulseStore, and TCK
    testImplementation("io.github.magic-xu:mvi-testing:0.3.0")
}
```

> [!IMPORTANT]
> Do not add every Pulse module. Pick one main entry, then add only the optional modules you use.
> When multiple Pulse modules are present, keep them on the same version.

## Samples

- `app/.../split_intent_basic`: explicit Split Intent wiring without convenience DSLs.
- `app/.../network`: a standard repository-backed feature.
- `app/.../state_decomposition`: one root store split into image and video sub-state reducers.
- `samples/simple-sync-consumer`: isolated Maven-only synchronous consumer.
- `samples/async-latest-consumer`: isolated Maven-only Latest-task, SavedState, selector consumer.

## Verification

```bash
# Deterministic pull-request gate
./gradlew mviFrameworkCheck

# Full release gate
./gradlew clean mviReleaseCheck
```

The release gate includes standard tests, Store TCK, seven-module API/ABI baselines, six-artifact
v0.3 and five-artifact v0.2 source/binary compatibility fixtures, Android/Compose checks,
publication-bundle verification, artifact-only samples, multi-seed stress, and a portable
performance-floor harness.

See [Consumer Guide](docs/CONSUMER_GUIDE.md),
[0.2 to 0.3 Migration](docs/MIGRATION_0.2_TO_0.3.md), and
[release decisions](docs/decisions/).

## License

Apache License 2.0.
