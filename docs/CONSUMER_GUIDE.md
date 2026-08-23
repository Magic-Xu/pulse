# Pulse 0.3 Consumer Guide

[简体中文](CONSUMER_GUIDE.zh-CN.md)

This guide targets application developers adopting the stable `0.3.0` API published on Maven Central.

## Start with one main dependency

> [!TIP]
> Most Android Compose apps need only
> `implementation("io.github.magic-xu:mvi-platform-android-compose:0.3.0")`.
> It already brings Android, runtime, and contract transitively.

| Project type | Add this main dependency |
| --- | --- |
| Android + Compose | `mvi-platform-android-compose` |
| Android without Compose | `mvi-platform-android` |
| Pure Kotlin / JVM | `mvi-core-runtime` |

Pick one main entry; do not add all three. Add `mvi-extensions` only for State Lens, reducer
decomposition, logging, or transition helpers. Add `mvi-testing` only to the test configuration.
When multiple Pulse modules are used, keep them on the same version.

At the API level:

1. Use `DefaultPulseStore` for platform-neutral state machines.
2. Use `PulseSplitStoreViewModel` for Android features with UI intents and asynchronous work.
3. Add State Decomposition only when one feature state has distinct domains.

Do not create one ViewModel per visual block. Start with one ViewModel per feature or navigation
owner, then split its reducer by sub-state when complexity justifies it.

## Core store

Reducers return an explicit outcome:

- `ReduceOutcome.Changed(state, effects)` commits a different state.
- `ReduceOutcome.Unchanged(effects)` keeps the current state while allowing effects.
- `ReduceOutcome.Ignored(reason)` records an intentional no-op without effects.

The runtime normalizes `Changed` with an equal state to `Unchanged`. `send` waits for the complete
frame; `trySend` is non-blocking and returns `Enqueued`, `Full`, or `Rejected`.

Collect `store.state` as the state truth. Use `store.transitions` for diagnostics and testing;
frames include processing duration, mailbox depth at start, and the observed mailbox high-water. Bind
exactly one coordinator to `store.effects`; it is replay-zero and bounded.

Always call `close`, then `awaitClosed` where asynchronous cleanup must be observed.

## Failure handling and immutable state

Configure `PulseRuntimeConfig` with a stable `storeId`, a `PulseErrorHandler`, and a
`PulseRedactor`. Every controlled framework failure carries the same `storeId` in its
`FailureContext`, plus the available request, sequence, revision, input type, thread, and component
metadata. The safe default redactor exposes types rather than application values. Keep that boundary
when forwarding diagnostics to logs or telemetry.

Pulse converts ordinary `Exception` values at controlled boundaries into typed `PulseFailure`
values. It does not convert `CancellationException` or fatal JVM errors. Use a domain mutation for a
business failure that the UI must render; use `PulseErrorHandler` for framework diagnostics. Enable
`strictMode` in development when a broken diagnostic handler should stop the owning runtime, and
keep a non-throwing handler in production.

Model state as immutable values: prefer `data class` properties declared with `val`, snapshot
incoming collections with `toList()` or `toMap()`, and never store mutable collections, coroutine
jobs, Android views, or effect handlers in state. Equal state is not emitted again. Use selectors or
`StateLens` to isolate reads and updates instead of mutating a shared object behind `StateFlow`.

## Android Split Intent

Define three feature inputs:

```kotlin
data class ScreenState(val loading: Boolean, val value: String) : MviState

sealed interface ScreenUiIntent : MviUiIntent {
    data object Refresh : ScreenUiIntent
}

sealed interface ScreenMutation : MviMutation {
    data object Loading : ScreenMutation
    data class Loaded(val value: String) : ScreenMutation
}

sealed interface ScreenEffect : UiEffect
```

Create a `PulseSplitStoreViewModel`. UI code only calls suspending `send` or non-blocking `trySend`;
mutation capability is owned by the executor context:

```kotlin
private val LOAD = TaskKey("screen.load")

class ScreenViewModel : PulseSplitStoreViewModel<
    ScreenState,
    ScreenUiIntent,
    ScreenMutation,
    ScreenEffect,
>(
    initialState = ScreenState(false, ""),
    mutationReducer = PulseMutationReducer { state, mutation ->
        when (mutation) {
            ScreenMutation.Loading -> ReduceOutcome.Changed(state.copy(loading = true))
            is ScreenMutation.Loaded -> ReduceOutcome.Changed(
                state.copy(loading = false, value = mutation.value)
            )
        }
    },
    uiIntentExecutor = PulseUiIntentExecutor { intent, context ->
        when (intent) {
            ScreenUiIntent.Refresh -> {
                context.launchTask(LOAD, TaskPolicy.Latest) {
                    mutate(ScreenMutation.Loading)
                    val value = repository.load()
                    mutate(ScreenMutation.Loaded(value))
                }
                PulseIntentExecutionDecision.Completed
            }
        }
    },
)
```

Choose task policy by required behavior:

| Policy | Behavior |
| --- | --- |
| `Latest` | cancel and invalidate the previous generation |
| `DropWhileRunning` | reject overlap |
| `Queue(capacity)` | run admitted blocks in FIFO order with a bounded pending queue |
| `Parallel(maxConcurrency)` | run up to the declared concurrency bound; reject excess work |
| `Conflate` | keep the active block and only the newest pending block |

`launchTask` reports admission immediately. Besides `DroppedWhileRunning` and `Closed`, bounded
policies return `QueueFull` or `ParallelLimitReached`. `TaskLaunchResult.Accepted` carries a narrow handle;
`awaitOutcome()` observes `Completed`, `Replaced`, `Cancelled`, `Closed`, or `Failed` without exposing
a Job or scope. An admitted `Latest` task can later be replaced, and an admitted pending `Conflate`
task can be superseded before it starts. The handle describes process-local execution; represent
durable business completion in state or a durable operation model.

Suspending `viewModel.send(intent)` returns the end-to-end executor result: `Completed`,
`Ignored(reason)`, `Failed`, `Cancelled`, or `Rejected`. `trySend` returns mailbox admission only.
Inside the executor, use `stateAtStart` for a stable decision snapshot, `currentState` for the latest
commit, `intentId` for correlation, and `reportFailure` for an explicitly handled feature failure.
Use `cancelTask` or `cancelAllTasks` instead of retaining coroutine jobs.

Task tokens prevent cancelled or replaced work from committing a late mutation. Never catch
`CancellationException` as a business error; rethrow it before mapping ordinary `Exception` values.

## Owner, lifecycle, and SavedState

Resolve ViewModels from an explicit `ViewModelStoreOwner` and stable key:

```kotlin
val viewModel = pulseViewModel(
    owner = backStackEntry,
    key = "screen",
    modelClass = ScreenViewModel::class.java,
) { extras -> createScreenViewModel(extras) }
```

Use the Activity/Fragment owner only for activity- or fragment-scoped features. Use the destination
`NavBackStackEntry` for navigation-scoped state, and a nested feature's explicit owner when that
feature must have an independent lifetime.

In Compose, pass the lifecycle owner explicitly:

```kotlin
val state by viewModel.collectStateAsStateWithLifecycle(lifecycleOwner)
val loading by viewModel.collectSelectedState(lifecycleOwner, ScreenState::loading)

viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
    // Render navigation, snackbar, or another one-shot UI action.
}
```

Use `PulseSavedStateAdapter` to persist only stable, restorable feature data. The feature owns its
schema version and migration logic. `PulseRestoreFailurePolicy.FALLBACK_TO_INITIAL_STATE` reports a
restore failure and uses the constructor state; `FAIL_CREATION` reports it and aborts ViewModel
construction. Do not persist jobs, task tokens, or UI effects, and do not make the full state
`Parcelable` solely for the framework. Subclasses that need cleanup override `onPulseCleared`;
`onCleared` itself remains framework-owned and final.

## State Decomposition

State Decomposition is an optional `mvi-extensions` feature. A `StateLens` lifts a reducer for a
plain sub-state back into the root state. Sub-state values do not implement `MviState`.

```kotlin
val imageLens = stateLens<RootState, ImageState>(
    get = RootState::image,
    set = { root, image -> root.copy(image = image) },
)

val reducer = pulseMutationReducer<RootState, DashboardMutation, DashboardEffect> {
    onSub<ImageState, DashboardMutation.ImageLoaded>(imageLens) { image, mutation ->
        subStateJust(image.copy(items = mutation.items))
    }
    ignore<DashboardMutation.AnalyticsOnly>("handled outside state")
}
```

Unhandled mutations fail fast. Duplicate or overlapping routes fail while the reducer is built.
Every lens must satisfy Get-Put, Put-Get, and Put-Put.

## Testing

Add `mvi-testing` to test dependencies and use virtual time:

```kotlin
@Test
fun refresh() = runPulseTest {
    val store = testStore(initialState, reducer)
    store.send(Input.Refresh)
    store.stateProbe.assertLatest(expectedState)
    store.failureProbe.assertEmpty()
}
```

Use the transition, effect, and failure probes for ordered assertions. Runtime implementers can run
the reusable `PulseStoreTck` against another `PulseStore` factory. Probe assertion messages include
the latest transition sequence and pass state/effect values through the configured redactor. Use a
custom test redactor only for values that are safe to print. Test task cancellation through
`TaskHandle.awaitOutcome`, close through `awaitClosed`, and Android lifecycle/SavedState behavior in
the platform test modules rather than relying on delays.

## Migration

The v0.2 `Store`, `DefaultStore`, `Reducer`, `Next`, and callback subscriptions remain available
through the same 0.3 engine. New features should use the coroutine-first API. See
[0.2 to 0.3 Migration](MIGRATION_0.2_TO_0.3.md) for behavior changes and staged migration steps.
