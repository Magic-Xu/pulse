# Pulse

[简体中文](README.zh-CN.md)

Pulse is an ordered, coroutine-first MVI runtime for Kotlin and Android. A store owns one bounded
input mailbox and one processor, publishes state through `StateFlow`, records every processed input
as a correlated transition frame, and delivers replay-zero UI effects through one coordinator.

The repository is currently qualifying the stable **0.3.0** candidate. The latest Maven Central
release remains 0.2.0 until the guarded release workflow passes.

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

The 0.3 artifacts are not public until release. For a locally staged candidate:

```kotlin
repositories {
    maven { url = uri("<checkout>/build/staging-repo") }
    google()
    mavenCentral()
}

val pulseVersion = "0.3.0"
dependencies {
    implementation("io.github.magic-xu:mvi-core-runtime:$pulseVersion")
    implementation("io.github.magic-xu:mvi-platform-android-compose:$pulseVersion")
    implementation("io.github.magic-xu:mvi-extensions:$pulseVersion")
    testImplementation("io.github.magic-xu:mvi-testing:$pulseVersion")
}
```

Use only the modules your feature needs. Android Compose already brings the Android, runtime, and
contract layers transitively.

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

# Full release candidate gate
./gradlew clean mviReleaseCheck
```

The release gate includes standard tests, Store TCK, six-module API/ABI dumps, a five-artifact v0.2
source/binary compatibility fixture, Android/Compose checks, candidate publication verification,
artifact-only samples, multi-seed stress, and a portable performance-floor harness.

See [Consumer Guide](docs/CONSUMER_GUIDE.md),
[0.2 to 0.3 Migration](docs/MIGRATION_0.2_TO_0.3.md), and
[release decisions](docs/decisions/).

## License

Apache License 2.0.
