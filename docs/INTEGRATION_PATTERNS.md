# Pulse 0.4 Integration Patterns

[简体中文](INTEGRATION_PATTERNS.zh-CN.md)

This guide targets application architects integrating Pulse with callback APIs, external streams,
durable work, and multiple state owners. Pulse owns ordered process-local state evolution. The
application still owns source delivery policy, durable scheduling, domain persistence, and
cross-owner coordination.

## Bind an external Flow as a keyed task

Convert a callback API to `Flow` at the data-source boundary. Registration and unregistration must
be paired with `callbackFlow` and `awaitClose`; a failed `trySend` must reach an application-owned
loss or resynchronization policy instead of disappearing silently.

```kotlin
fun DeviceSource.events(
    onUndelivered: (DeviceEvent) -> Unit,
): Flow<DeviceEvent> = callbackFlow {
    val listener = DeviceListener { event ->
        if (trySend(event).isFailure) onUndelivered(event)
    }
    addListener(listener)
    awaitClose { removeListener(listener) }
}
```

Start collection from `PulseIntentContext.launchTask`, not from an application-created scope kept
by the ViewModel. A stable `TaskKey` gives replacement and close one owner; mutation calls inside
the task are bound to its task token.

```kotlin
private val OBSERVE_DEVICE = TaskKey("screen.observe-device")

val executor = PulseUiIntentExecutor<ScreenState, ScreenUiIntent, ScreenMutation> {
        intent, context ->
    when (intent) {
        ScreenUiIntent.StartObserving -> {
            val admission = context.launchTask(OBSERVE_DEVICE, TaskPolicy.Latest) {
                deviceSource.events(onUndelivered = sourceLossHandler)
                    .conflate()
                    .collect { event ->
                        mutate(ScreenMutation.DeviceChanged(event))
                    }
            }
            when (admission) {
                is TaskLaunchResult.Accepted -> PulseIntentExecutionDecision.Completed
                else -> PulseIntentExecutionDecision.Ignored("device-observer-not-admitted")
            }
        }
    }
}
```

Use `conflate()` only when an event is a replaceable snapshot and only the newest value matters.
If every event is a command or ledger entry, do not conflate it: choose an explicit bounded buffer,
overflow action, and authoritative resynchronization strategy in the source adapter. Pulse does not
turn a non-suspending callback into a lossless source.

Sending `StartObserving` again with `TaskPolicy.Latest` cancels the previous generation; cancellation
reaches `awaitClose` and unregisters its listener. Closing the `PulseSplitStoreViewModel` cancels the
task through the same path.

## Model progress as token-bound snapshots

Progress is usually replaceable state, so adapt the callback to a Flow, conflate intermediate
values, and mutate from the keyed task:

```kotlin
private val UPLOAD = TaskKey("upload.observe")

context.launchTask(UPLOAD, TaskPolicy.Latest) {
    uploadSource.progress(operationId, onUndelivered = progressLossHandler)
        .conflate()
        .collect { percent ->
            mutate(UploadMutation.Progress(operationId, percent))
        }
}
```

`PulseTaskContext.mutate` automatically carries the active task token, so a replaced or cancelled
generation cannot commit a late update. Keep `operationId` in the mutation as well when results can
cross process, service, or persisted-operation boundaries. Completion and failure are domain
statuses; do not infer either from the last progress percentage or expose exception messages as UI
state.

## Keep durable work outside the Pulse runtime

Keyed tasks and task tokens are process-local. Work that must survive process death needs a durable
application protocol:

1. Allocate an `operationId` and persist the domain command and status before execution.
2. Let an application-owned WorkManager worker or Service execute idempotently and write status to
   the same durable record.
3. On startup, reconcile non-terminal records and schedule, resume, or fail them according to the
   application's business policy.
4. Let Pulse observe the persisted record through the keyed-Flow pattern and map it to typed
   mutations for presentation.

The application command layer may expose a scheduling port that an executor invokes, but Pulse
itself does not enqueue workers, start services, or define retry and recovery policy. A database or
other durable model is the operation truth; Pulse State is its process-local projection. Never
persist a `Job`, `TaskToken`, pending `UiEffect`, or task-handle outcome as recovery state.

## Give every Store one lifecycle owner

| Owner | Appropriate state | Lifetime and boundary |
| --- | --- | --- |
| Root/app owner | Session and app-wide coordination state | Owned by the application/root navigation scope; features communicate through domain ports or observed repositories, not mutation access |
| Feature owner | Screen or navigation-feature state | Prefer `PulseSplitStoreViewModel`, scoped to the feature's explicit `ViewModelStoreOwner` |
| External actor | Service-local or connector state machine | The Service may own a `DefaultPulseStore`, accept commands through its own API, then call `close()` and observe `awaitClosed()` during owned cleanup |

Do not add a global Store bus. A feature must not send another Store's mutations, and the root Store
must not absorb transient child UI state. Share durable facts through repositories and use typed
domain commands for coordination. `StateFlow`, task handles, and Store references are process-local;
cross-process actors need persistence or explicit IPC.

## Choose callback ingress deliberately

`PulseSplitStoreViewModel.send(intent)` is the default from a coroutine. It applies bounded
backpressure and returns the executor decision. A listener that cannot suspend must use `trySend`
or `callbackIngress`:

```kotlin
val ingress = viewModel.callbackIngress { intent, result ->
    callbackRejectionPolicy.handle(intent, result)
}

listener.onEvent { event ->
    ingress.submit(ScreenUiIntent.ExternalEvent(event))
}
```

`trySend` and `PulseCallbackIngress.submit` return `EnqueueResult.Enqueued`, `Full`, or `Rejected`.
The callback-ingress rejection handler is mandatory and is called for every non-enqueued result.
Its application policy may request an authoritative snapshot, coalesce a replaceable event, stop an
unregistered source, or surface overload. It must not report success for an input Pulse did not
accept. A bounded, non-suspending path cannot guarantee acceptance under overload.

## Treat `Completed` as an executor decision

`PulseIntentExecutionResult.Completed` means the serial executor returned
`PulseIntentExecutionDecision.Completed` for that UI intent. If the executor admitted a keyed task,
the task can still be running when `send` returns. `trySend` returning `Enqueued` is narrower still:
it reports admission, not executor or task completion.

Use `TaskLaunchResult.Accepted.handle.awaitOutcome()` when a caller genuinely needs the
process-local task outcome. Represent user-visible or durable business completion in typed State or
the persisted operation record keyed by `operationId`; do not keep the executor lane open merely to
make `send` represent a background operation.

## Typed-mutation migration exit criteria

A feature has completed the Split Intent migration only when all of these are true:

- UI and external callers can submit only `MviUiIntent`; they cannot obtain a mutation sender or
  construct a mixed UI/mutation input path.
- Async and callback results enter through `PulseIntentContext` or `PulseTaskContext.mutate`, so
  replacement, close, and late-result rejection apply consistently.
- The mutation reducer is exhaustive and every branch intentionally returns `Changed`, `Unchanged`,
  or `Ignored`; no catch-all silently discards unknown mutations.
- Business failures and statuses are typed domain values. Raw exception messages, `Job`, callbacks,
  and mutable collections do not enter State.
- Tests cover the public UI-input path, task replacement or cancellation, late-mutation rejection,
  callback overload policy, and reducer behavior.
- The old mixed-input adapter or public mutation writer is removed after all call sites migrate;
  maintaining two writable paths is not a completed migration.
