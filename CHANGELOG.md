# Changelog

Notable changes to Pulse are recorded here. Versions describe published artifacts; repository-only
snapshot work remains under **Unreleased**.

## Unreleased — 0.3.0-SNAPSHOT

`0.3.0-SNAPSHOT` is under development and has not been published to Maven Central. The latest stable
release remains `0.2.0`.

### Added

- `PulseStore` and `DefaultPulseStore` with `StateFlow` state, ordered transition frames, suspending
  `send`, non-suspending `trySend`, and an ordered close barrier.
- Explicit `ReduceOutcome` branches, correlated `EffectEnvelope` values, typed `PulseFailure`
  diagnostics, and frame-based plugins.
- Replay-zero `UiEffect` coordination with one active consumer session.
- Keyed process-local tasks with `Latest`, `DropWhileRunning`, `Queue`, `Parallel`, and `Conflate`
  admission policies and token validation for late mutations.
- Android Split Store APIs, explicit ViewModel ownership, optional saved-state adapters, and
  lifecycle-aware Compose state selectors and UI-effect collection.
- State decomposition helpers in `mvi-extensions`.
- New `mvi-testing` artifact with virtual-time configuration, probes, `TestPulseStore`, and a reusable
  Store TCK.
- Candidate publication, artifact-only consumer, stress, and performance-floor checks.

### Changed

- The retained 0.2 Store API is adapted to the same ordered engine as the 0.3 API.
- Concurrent and reentrant input, callback isolation, cancellation, and close cutoffs now have
  explicit ordering rules.
- UI effects are defined as transient foreground instructions; durable work must use durable state
  and an application-owned persistence or scheduling mechanism.
- CI and release verification run on JDK 21. Published bytecode continues to target Java 11, and
  Android artifacts keep `minSdk 23`.

### Compatibility and verification scope

- The five 0.2 artifact coordinates remain release targets; `mvi-testing` is the sixth 0.3 artifact.
- Controlled API baselines cover all six candidate artifacts. The current executable 0.2
  source/binary consumer fixture exercises the core-runtime dependency path; Android, Compose, and
  extensions compatibility still depends on their API baselines and module tests.
- The performance harness enforces broad throughput, latency, and retained-memory floors and checks
  bounded mailbox admission. Its selector count is a synthetic metric, not a device or Compose
  performance benchmark.

See [the migration guide](docs/MIGRATION_0.2_TO_0.3.md),
[compatibility policy](docs/COMPATIBILITY.md), and [release plan](docs/RELEASE_PLAN.md).

## 0.2.0 — 2026-04-18

- Added the Split Intent architecture and Android/Compose module baseline.
- Published `mvi-core-contract`, `mvi-core-runtime`, `mvi-platform-android`,
  `mvi-platform-android-compose`, and `mvi-extensions`.

## 0.1.0 — 2026-04-17

- Established the initial contract, runtime, Android, Compose, and extension modules.
