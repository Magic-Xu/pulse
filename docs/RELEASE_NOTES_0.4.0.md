# Pulse 0.4.0 Release Notes

Chinese version: [RELEASE_NOTES_0.4.0.zh-CN.md](./RELEASE_NOTES_0.4.0.zh-CN.md)

> Status: **released on 2026-08-24** from the exact annotated tag `v0.4.0`.
> All seven signed Maven Central bundles are public, and both isolated artifact consumers passed.

Pulse 0.4 hardens the 0.3 runtime for real Android integrations. It keeps one ordered,
process-local state runtime and focuses on end-to-end Split admission, actionable diagnostics,
deterministic Android testing, and integration paths that are difficult to misuse.

## Highlights

### End-to-end Split admission

- `PulseSplitStoreViewModel` now applies one bounded in-flight budget from UI admission through the
  serial executor decision. An executor waiting for a mutation can no longer form a backpressure
  cycle with a separately full executor queue.
- Suspending `send(UI)` remains the lossless path within the process: it waits for capacity and then
  for the serial executor decision.
- Non-suspending `trySend(UI)` returns `EnqueueResult.Full` when the complete Split pipeline is at
  capacity, even if the core mailbox alone is not full. With `REJECT_AND_REPORT`, the runtime also
  emits `PulseFailure.SplitAdmissionOverflow`.
- `callbackIngress(onRejected)` adapts listener-style APIs. Its rejection handler is mandatory and
  receives every `Full` or lifecycle `Rejected` result; accepted callbacks retain admission order.

A bounded, non-suspending API cannot guarantee both zero blocking and zero rejection. Applications
must choose suspending backpressure or define an explicit callback overload policy.

### Read-only diagnostics and safer Android configuration

- Split ViewModels expose `transitions` as
  `Flow<TransitionFrame<S, PulseSplitInput<UI, M>, E>>`. Consumers can inspect `Ui.value` and
  `Mutation.value`, but the surface provides no way to submit those observed inputs, obtain the
  backing Store, or acquire mutation authority.
- `androidPulseRuntimeConfig(base)` preserves mailbox, overflow, effect buffer, clock, failure,
  strict-mode, redaction, and Store identity settings while applying `Dispatchers.Main.immediate`
  to Store and consumer work. The no-argument Android default is unchanged.
- Exceptions from keyed tasks are now `PulseFailure.TaskFailure` with `FailurePhase.TASK`, task key,
  token, and the originating UI intent's request ID and input type when launched from
  `PulseIntentContext`.
- If the underlying Store terminates independently—for example because a terminal diagnostic
  handler fails—the Split adapter now converges on the same ViewModel cleanup barrier instead of
  leaving executor or transition jobs alive.

### Real Split ViewModel tests

The new `mvi-platform-android-testing` artifact complements the pure-JVM `mvi-testing` artifact. It
runs a real `PulseSplitStoreViewModel` with one virtual-time scheduler shared by Android Main, the
Pulse runtime, and the explicit execution owner.

`runPulseSplitTest` and `splitHost` provide state, transition, UI-effect, and typed-failure probes,
plus deterministic cleanup. `sendAndDrain` waits for the selected intent's executor decision and
mutations directly awaited by that executor. It deliberately does not wait for keyed tasks,
tickers, or infinite sources. Calls are serialized because Android Main is process-global, and
nested `runPulseSplitTest` calls are rejected before they can replace an outer Main dispatcher.

### Safe logging and corrected examples

- `mvi-extensions` adds `PulseLoggingPlugin` for modern Store transition and failure logging, plus
  `Flow<TransitionFrame<...>>.logPulseTransitions` for read-only facades such as Split ViewModels.
- Logging uses `TypeOnlyPulseRedactor` by default. Application values, diagnostic context strings,
  throwable messages, and stack traces are not exposed by default.
- The Android samples now handle callback admission and task-launch results explicitly, keep
  `Completed` semantics precise, and map exceptions to typed domain/UI values instead of placing raw
  exception messages in State or effects.

## Execution-result semantics

`PulseIntentExecutionResult.Completed` means the serial executor returned
`PulseIntentExecutionDecision.Completed`. If that executor admitted a keyed task, the task may still
be running when `send` returns. Observe `TaskLaunchResult.Accepted.handle.awaitOutcome()` only when a
process-local caller needs the task's terminal outcome; model user-visible or durable completion in
typed State or persisted operation state.

`EnqueueResult.Enqueued` is narrower: it confirms admission only. It does not mean that reduction,
executor work, or a background task has completed.

## Compatibility

Pulse 0.4 is additive over the 0.3 public API. Existing 0.3 Store, Split ViewModel, Compose,
extension, and test integrations do not require an application-wide rewrite. The retained 0.2
compatibility surface remains available.

Re-test code that assumes `trySend` only reflects the core mailbox. Split admission now covers the
complete UI-to-executor path, so overload can be reported earlier and more accurately. Also update
exhaustive handling of `PulseFailure` or `FailurePhase` for `TaskFailure`, `TASK`,
`SplitAdmissionOverflow`, and `ADMISSION`.

See [Migrating from Pulse 0.3 to 0.4](./MIGRATION_0.3_TO_0.4.md).

## Published artifact set

The release publishes seven artifacts under `io.github.magic-xu`:

- `mvi-core-contract`
- `mvi-core-runtime`
- `mvi-platform-android`
- `mvi-platform-android-compose`
- `mvi-platform-android-testing` — new in 0.4
- `mvi-extensions`
- `mvi-testing`

Keep all Pulse modules in one project on version `0.4.0`.

## Explicit boundaries

Pulse keyed tasks remain process-local. Work that must survive process death belongs to an
application-owned durable protocol: persist an operation ID and domain status, execute through
WorkManager or a Service, reconcile on startup, and project the durable record into Pulse through a
typed mutation. Do not persist `Job`, `TaskToken`, pending `UiEffect`, or task-handle outcomes.

Pulse 0.4 does not add a SourceRegistry, generic scheduler/recovery API, unbounded progress sink,
Service base class, global Store bus, writer DSL/KSP layer, lint suite, or developer panel. Source
delivery policy, durable retry/recovery, domain models, and coordination between Store owners remain
application responsibilities. See [Pulse 0.4 Integration Patterns](./INTEGRATION_PATTERNS.md).
