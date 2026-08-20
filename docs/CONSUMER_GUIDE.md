# Pulse 0.3 Consumer Guide

[简体中文](CONSUMER_GUIDE.zh-CN.md)

This guide targets application developers adopting the 0.3 API. The repository currently builds
`0.3.0-SNAPSHOT`; use the staged repository until a stable 0.3 artifact is released.

## Choose the smallest surface

1. Use `DefaultPulseStore` for platform-neutral state machines.
2. Use `PulseSplitStoreViewModel` for Android features with UI intents and asynchronous work.
3. Add `mvi-extensions` State Decomposition only when one feature state has distinct domains.

Do not create one ViewModel per visual block. Start with one ViewModel per feature or navigation
owner, then split its reducer by sub-state when complexity justifies it.

## Core store

Reducers return an explicit outcome:

- `ReduceOutcome.Changed(state, effects)` commits a different state.
- `ReduceOutcome.Unchanged(effects)` keeps the current state while allowing effects.
- `ReduceOutcome.Ignored(reason)` records an intentional no-op without effects.

The runtime normalizes `Changed` with an equal state to `Unchanged`. `send` waits for the complete
frame; `trySend` is non-blocking and returns `Enqueued`, `Full`, or `Rejected`.

Collect `store.state` as the state truth. Use `store.transitions` for diagnostics and testing. Bind
exactly one coordinator to `store.effects`; it is replay-zero and bounded.

Always call `close`, then `awaitClosed` where asynchronous cleanup must be observed.

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

Create a `PulseSplitStoreViewModel`. UI code only calls `send` or `trySend`; mutation capability is
owned by the executor context:

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
            ScreenUiIntent.Refresh -> context.launchTask(LOAD, TaskPolicy.Latest) {
                mutate(ScreenMutation.Loading)
                val value = repository.load()
                mutate(ScreenMutation.Loaded(value))
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
| `Queue` | run every admitted block in FIFO order |
| `Parallel` | run all blocks concurrently |
| `Conflate` | keep the active block and only the newest pending block |

`launchTask` reports admission immediately. `TaskLaunchResult.Accepted` carries a narrow handle;
`awaitOutcome()` observes `Completed`, `Replaced`, `Cancelled`, `Closed`, or `Failed` without exposing
a Job or scope. An admitted `Latest` task can later be replaced, and an admitted pending `Conflate`
task can be superseded before it starts. The handle describes process-local execution; represent
durable business completion in state or a durable operation model.

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

In Compose, pass the lifecycle owner explicitly:

```kotlin
val state by viewModel.collectStateAsStateWithLifecycle(lifecycleOwner)
val loading by viewModel.collectSelectedState(lifecycleOwner, ScreenState::loading)

viewModel.ObserveUiEffects(lifecycleOwner) { effect ->
    // Render navigation, snackbar, or another one-shot UI action.
}
```

Use `PulseSavedStateAdapter` to persist only stable, restorable feature data. Do not persist jobs,
task tokens, or UI effects, and do not make the full state `Parcelable` solely for the framework.

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
the reusable `PulseStoreTck` against another `PulseStore` factory.

## Migration

The v0.2 `Store`, `DefaultStore`, `Reducer`, `Next`, and callback subscriptions remain available
through the same 0.3 engine. New features should use the coroutine-first API. See
[0.2 to 0.3 Migration](MIGRATION_0.2_TO_0.3.md) for behavior changes and staged migration steps.
