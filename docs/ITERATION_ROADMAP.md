# Pulse Iteration Roadmap

[简体中文](ITERATION_ROADMAP.zh-CN.md)

## Current line

The current stable line is `0.3.0`. Runtime ordering, Split Intent tasks, Android/Compose lifecycle
support, State Decomposition, testing, compatibility, and public-artifact evidence shipped together.

## 0.3 scope

### Ordered core

- `StateFlow` is the state truth.
- One bounded FIFO mailbox serializes inputs and lifecycle commands.
- One processor owns read, reduce, commit, transition, effect, and completion ordering.
- Outcomes distinguish `Changed`, `Unchanged`, and `Ignored`.
- `close` creates an ordered admission cutoff and drains accepted work.
- Controlled failures are typed; cancellation and fatal errors propagate.
- The v0.2 `DefaultStore` delegates to the same engine.

### Async and effects

- Keyed tasks support Latest, DropWhileRunning, bounded Queue, bounded Parallel, and Conflate, with
  explicit overload and final outcomes.
- Opaque generations reject late mutations after replacement or cancellation.
- UI effects are replay-zero, bounded, and owned by one active coordinator.
- Undelivered effects and consumer failures are observable diagnostics.

### Android and Compose

- Split Intent exposes only execution-result `send(UI)` and admission-result `trySend(UI)` to UI callers.
- Mutation capability is confined to `PulseIntentContext` and task contexts.
- Android defaults run reducer and controlled delivery on `Main.immediate`.
- ViewModel lookup requires an explicit owner and stable key.
- SavedState uses feature-owned adapters instead of requiring Parcelable state.
- Compose collection is lifecycle-aware and supports selected-state equality.

### Extensions and testing

- State Decomposition belongs to `mvi-extensions`, not the contract module.
- Lens sub-state is marker-free and covered by the three lens laws.
- Mutation routing is fail-fast; ignore is explicit; duplicate/overlapping routes are rejected.
- `mvi-testing` publishes virtual-time helpers, probes, `TestPulseStore`, and Store TCK.

### Release evidence

- Six public artifacts have controlled API/ABI dumps, including both Android AARs.
- v0.2 consumer source compilation and binary linkage run against 0.3.0.
- Release publications are verified from an isolated Maven repository before public consumption.
- Two independent samples consume Maven artifacts without project dependencies.
- Fixed-seed PR tests, multi-seed stress, and performance regression gates are separate.

## Release boundary

`0.3.0` is releasable only when:

```bash
./gradlew clean mviReleaseCheck
```

passes from a clean checkout, the release tag equals `v0.3.0`, the configured publication version is
`0.3.0`, and the publish workflow consumes that successful gate. A branch, RC, or SNAPSHOT cannot
release directly to Maven Central.

## After 0.3

Future work is evaluated independently and must not weaken the 0.3 ordering or compatibility
contracts. Candidate topics include multiplatform runtime support, richer diagnostic exporters, and
additional artifact-only navigation samples.
