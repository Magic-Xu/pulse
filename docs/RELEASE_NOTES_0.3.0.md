# Pulse 0.3.0 Release Notes

Chinese version: [RELEASE_NOTES_0.3.0.zh-CN.md](./RELEASE_NOTES_0.3.0.zh-CN.md)

> Status: **unreleased**. The repository currently identifies the candidate as
> `0.3.0-SNAPSHOT`. Stable `0.3.0` artifacts are not available until the release is announced.

Pulse 0.3 introduces one ordered runtime for both the new coroutine API and the retained 0.2
compatibility API. The release focuses on deterministic input ordering, explicit reducer outcomes,
diagnosable UI-effect delivery, lifecycle ownership, and reusable conformance testing.

## Highlights

### Ordered Store runtime

- New `PulseStore` and `DefaultPulseStore` APIs expose `StateFlow` state, transition frames,
  replay-zero UI effects, keyed tasks, `send`, `trySend`, and an ordered close barrier.
- One bounded FIFO mailbox serializes accepted inputs and lifecycle controls. Concurrent and
  reentrant sends cannot enter the reducer recursively.
- Every processed input produces an immutable `TransitionFrame` with request, sequence, revision,
  state, outcome, effect, timing, and failure correlation.
- `ReduceOutcome.Changed`, `Unchanged`, and `Ignored` make no-op and inapplicable inputs observable.
  Equal candidate state is normalized to `Unchanged`.

### Failure and delivery contracts

- `PulseFailure` reports typed reducer, consumer, plugin, executor, overflow, undelivered-effect,
  late-mutation, and state restore/save failures with redacted request, sequence, input-type,
  thread, and Store correlation.
- A failing controlled consumer or plugin is isolated from other consumers and the mailbox
  processor.
- Cancellation and fatal JVM errors are not converted into Pulse failures.
- `UiEffect` is reserved for one-shot foreground instructions. Delivery has replay zero and a single
  active coordinator; undelivered envelopes are reported rather than silently retained.

### Keyed asynchronous work

- Runtime-owned tasks support `Latest`, `DropWhileRunning`, bounded `Queue(capacity)`, bounded
  `Parallel(maxConcurrency)`, and `Conflate` policies, with explicit overload and final outcomes.
- Task tokens prevent replaced, cancelled, or closed work from emitting late mutations.
- Tasks remain process-local. Durable operations still require durable state, an operation ID, and
  persistence or an external scheduler.

### Android and Compose

- `PulseSplitStoreViewModel` exposes suspending `send(UI)` with an end-to-end executor result and
  non-blocking `trySend(UI)` with admission only, while keeping mutation authority inside its
  executor and token-bound task contexts.
- ViewModel acquisition supports explicit owners, stable keys, `CreationExtras`, and optional
  `SavedStateHandle` adapters.
- `PulseStateHost` exposes only read-only state and UI-effect surfaces.
- Compose adds lifecycle-aware whole-state and selector collection plus `ObserveUiEffects`; owners
  are explicit and collection is `STARTED` by default.
- ViewModel closure is final and idempotent. Saved-state integration restores committed state only,
  not tasks, effects, or pending runtime objects.

### Extensions and testing

- `mvi-extensions` adds marker-free `StateLens` composition and the explicit-outcome
  `pulseMutationReducer` DSL while retaining legacy helpers.
- New `mvi-testing` provides virtual-time runtime configuration, probe-enabled stores, state,
  transition, effect and failure probes, concurrent-send helpers, and a reusable `PulseStoreTck`.

## Published artifacts

The stable release will publish six artifacts under `io.github.magic-xu`:

- `mvi-core-contract`
- `mvi-core-runtime`
- `mvi-platform-android`
- `mvi-platform-android-compose`
- `mvi-extensions`
- `mvi-testing`

The first five coordinates are retained from 0.2; `mvi-testing` is new in 0.3. Do not mix versions
between these modules.

## Compatibility

The 0.2 public types and artifact coordinates remain available as a compatibility surface. Legacy
`DefaultStore`, callback APIs, ViewModel adapters, and Compose Store bindings run over the ordered
engine. Migration can be performed one feature at a time.

Behavior that depended on callback timing should be re-tested. In particular, 0.3 defines FIFO
reentrancy, callback isolation, cancellation handling, and an ordered close cutoff. These stronger
rules can change timing without changing accepted API calls.

See [Compatibility Policy](./COMPATIBILITY.md) and
[Migrating from Pulse 0.2 to 0.3](./MIGRATION_0.2_TO_0.3.md).

## Platform baseline

- Java bytecode target: 11
- Android `minSdk`: 23
- Android compile API: 36.1
- Release and CI JDK: 21

Resolved transitive versions are recorded in each artifact's Gradle metadata and POM.

## Release qualification

Stable `0.3.0` is published only after the candidate passes:

- six-module public API checks and a five-artifact 0.2 source/binary compatibility fixture;
- core, Android, Compose, and sample application tests, lint, and builds;
- isolated consumers built only from staged Maven artifacts;
- multi-seed 10,000-input stress checks;
- portable throughput, latency, memory, and bounded-mailbox performance floors;
- publication bundle, metadata, version, tag, and Maven Central configuration checks.

See [Release Plan](./RELEASE_PLAN.md) for the exact gates. Until those gates pass and the stable tag is
published, this file describes the intended release rather than an available artifact.
