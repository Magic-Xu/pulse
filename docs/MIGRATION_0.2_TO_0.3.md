# Migrating from Pulse 0.2 to 0.3

Chinese version: [MIGRATION_0.2_TO_0.3.zh-CN.md](./MIGRATION_0.2_TO_0.3.zh-CN.md)

> `0.3.0-SNAPSHOT` is under development and has not been published. Use the version available from
> your configured repository until the stable `0.3.0` release is announced.

Pulse 0.3 keeps the 0.2 public API and artifact coordinates as a compatibility surface. Existing
applications can upgrade first and migrate feature by feature; adopting the new API does not require
an application-wide rewrite.

## Choose a migration path

### Keep the 0.2 API

Keep `Store`, `DefaultStore`, `Reducer`, `Next`, `PulseViewModel`, or `PulseSplitViewModel` if a
feature does not need the new API yet. These types run on the ordered 0.3 engine while preserving the
0.2 call surface.

Re-test code that depends on callback timing or reentrant dispatch. The compatibility layer preserves
API shape, but 0.3 deliberately strengthens ordering, callback isolation, cancellation, and close
behavior.

### Adopt the 0.3 API

Use the new API for features that need suspending backpressure, transition frames, typed failures,
keyed tasks, lifecycle-aware selectors, or replay-zero UI effects.

| 0.2 API | 0.3 API |
|---|---|
| `Reducer` + `Next` | `PulseReducer` + `ReduceOutcome` |
| `Store` / `DefaultStore` | `PulseStore` / `DefaultPulseStore` |
| `dispatch(intent)` | `send(input)` or `trySend(input)` |
| `currentState` / `observeState` | `state: StateFlow<S>` |
| `observeEffect` | single-coordinator `effects` or Android `ObserveUiEffects` |
| `StorePlugin` callbacks | `PulseStorePlugin` with immutable `TransitionFrame` |
| transient `MviEffect` | `UiEffect` |
| `MutationReducer` + `PulseSplitViewModel` | `PulseMutationReducer` + `PulseSplitStoreViewModel` |

## 1. Update dependencies

The five 0.2 coordinates remain, and 0.3 adds `mvi-testing`:

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-core-contract:0.3.0")
    implementation("io.github.magic-xu:mvi-core-runtime:0.3.0")

    // Android projects normally depend on only the highest adapter they use.
    implementation("io.github.magic-xu:mvi-platform-android-compose:0.3.0")

    // Optional.
    implementation("io.github.magic-xu:mvi-extensions:0.3.0")
    testImplementation("io.github.magic-xu:mvi-testing:0.3.0")
}
```

Do not use these stable coordinates until `0.3.0` is published. The Android Compose artifact exposes
the Android, runtime, and contract APIs transitively.

## 2. Make reducer outcomes explicit

0.2 reducers always returned a state through `Next`. A 0.3 reducer states whether an input changed
state, was handled without a state change, or was deliberately ignored:

```kotlin
sealed interface CounterEffect : UiEffect {
    data object LimitReached : CounterEffect
}

val reducer = PulseReducer<CounterState, CounterIntent, CounterEffect> { state, intent ->
    when (intent) {
        CounterIntent.Increment ->
            ReduceOutcome.Changed(state.copy(count = state.count + 1))

        CounterIntent.ShowLimit ->
            ReduceOutcome.Unchanged(listOf(CounterEffect.LimitReached))

        CounterIntent.NotApplicable ->
            ReduceOutcome.Ignored("not-applicable")
    }
}
```

`Changed` with a state equal to the current state is normalized to `Unchanged`. `Ignored` cannot
carry effects. Effect collections are snapshotted when the outcome is created.

## 3. Move from callback dispatch to the ordered store

```kotlin
val store = DefaultPulseStore(
    initialState = CounterState(),
    reducer = reducer,
)

val result = store.send(CounterIntent.Increment)
val current = store.state.value

store.close()
store.awaitClosed()
```

- `send` suspends for mailbox capacity and completes after the frame has been reduced, committed,
  published to transitions, and routed to effect delivery.
- `trySend` never suspends. `EnqueueResult.Enqueued` means admission only, not transition completion;
  `Full` and lifecycle rejection are explicit.
- `state` is the only current-state source on the new API.
- `transitions` exposes every processed input, including `Unchanged`, `Ignored`, and reducer failure.
- `close()` establishes an admission cutoff. Inputs admitted before it drain in order; use
  `awaitClosed()` as the completion barrier.

Do not add a second queue around the store. Concurrent and reentrant sends already enter one bounded
FIFO mailbox.

## 4. Separate UI effects from durable work

Change one-shot foreground instructions to implement `UiEffect`. A UI effect has replay zero and one
active coordinator. If no coordinator is active, or a session ends with pending effects, Pulse
reports `PulseFailure.UndeliveredUiEffect`; it does not replay the effect later.

Use keyed tasks for process-local asynchronous work:

```kotlin
context.launchTask(TaskKey("refresh"), TaskPolicy.Latest) {
    val result = repository.refresh()
    mutate(CounterMutation.Refreshed(result))
}
```

Choose `Latest`, `DropWhileRunning`, `Queue`, `Parallel`, or `Conflate` according to the feature's
concurrency rule. Accepted launches expose a `TaskHandle`; await it only when a coordinator needs the
process-local final outcome. A task is not durable across process death. Work that must survive
recreation needs durable state, an operation identifier, and persistence or an external scheduler.

## 5. Migrate Split Intent on Android

For a new-API feature, replace `MutationReducer` with `PulseMutationReducer`, make UI effects
implement `UiEffect`, and extend `PulseSplitStoreViewModel`:

```kotlin
class CounterViewModel(
    repository: CounterRepository,
) : PulseSplitStoreViewModel<CounterState, CounterUiIntent, CounterMutation, CounterEffect>(
    initialState = CounterState(),
    mutationReducer = PulseMutationReducer { state, mutation ->
        when (mutation) {
            is CounterMutation.Loaded -> ReduceOutcome.Changed(
                state.copy(value = mutation.value),
            )
        }
    },
    uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
        when (intent) {
            CounterUiIntent.Refresh -> context.launchTask(
                key = TaskKey("refresh"),
                policy = TaskPolicy.Latest,
            ) {
                mutate(CounterMutation.Loaded(repository.load()))
            }
        }
    },
)
```

UI code receives only `send(UI)` and `trySend(UI)`. Mutation authority stays inside
`PulseIntentContext` and token-bound task contexts; do not expose a mutation dispatcher or backing
store from the ViewModel.

If committed state should be restored after Android process recreation, provide a
`PulseSavedStateAdapter`. Restore only state values. Tasks, UI effects, subscriptions, and pending
mailbox entries are intentionally not restored.

## 6. Use explicit Android and Compose owners

Acquire ViewModels with an explicit `ViewModelStoreOwner`, stable key, model class, and creator.
Compose state and effect bindings also require an explicit `LifecycleOwner`:

```kotlin
val state by viewModel.collectSelectedState(
    lifecycleOwner = lifecycleOwner,
    selector = CounterState::count,
)

viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
    when (effect) {
        CounterEffect.LimitReached -> showLimitMessage()
    }
}
```

Collection is active at `STARTED` by default. State resumes with the latest committed value; UI
effects emitted outside an active session are not replayed.

The 0.2 `Store.collectStateAsState` and `Store.observeEffects` overloads remain available for legacy
features.

## 7. Move optional composition and tests

- State decomposition lives in `mvi-extensions`. Use `StateLens` and `pulseMutationReducer` only when
  a feature is large enough to benefit from local reducers.
- Add `mvi-testing` for `runPulseTest`, `TestPulseStore`, state/transition/effect/failure probes, and
  the reusable `PulseStoreTck`.
- Test ordering and outcomes instead of relying on callback scheduling. Also test effect coordinator
  start/stop, task policy, late-mutation rejection, and close cutoffs where they affect the feature.

## Migration checklist

- [ ] Choose compatibility mode or new API per feature.
- [ ] Convert transient effects to `UiEffect`; model durable work separately.
- [ ] Replace `Next` with the correct `ReduceOutcome` branch.
- [ ] Replace callback state ownership with `StateFlow` collection.
- [ ] Choose an explicit task key and policy for each asynchronous operation.
- [ ] Pass explicit ViewModel and lifecycle owners on Android.
- [ ] Close owned stores and wait for closure in coordinators and tests.
- [ ] Run source, binary, behavior, Android lifecycle, and artifact-only consumer checks before
      releasing the migrated application.
