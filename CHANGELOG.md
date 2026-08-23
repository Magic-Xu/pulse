# Changelog

Notable changes to Pulse are recorded here.

## 0.4.0 — release candidate

Pulse 0.4.0 is not public yet. Pulse 0.3.0 remains the stable Maven Central release until the
0.4.0 publication and isolated-consumer checks pass.

### Added

- End-to-end Split Store admission: suspending callers wait for capacity, non-suspending callers
  see full-pipeline rejection, callback ingress requires rejection handling, and transition frames
  remain read-only.
- Typed `TASK` failures with task correlation metadata and typed `ADMISSION` failures when the
  complete Split pipeline reaches its configured in-flight limit.
- `mvi-platform-android-testing`, a deterministic host for testing Split Store flows without
  recreating framework internals in consumer projects.
- Safe structured logging helpers that redact payload values by default.

### Changed

- Android runtime configuration is now an explicit overlay on the core runtime defaults.
- Task closure, cancellation, overflow, and late-callback convergence are verified across both
  store-owned and store-independent execution paths.
- Official samples demonstrate admission failure handling, callback bridging, lifecycle-safe UI
  collection, and the new Android testing artifact.
- Release verification now covers seven API baselines, the complete 0.3 six-artifact surface, the
  retained 0.2 five-artifact surface, managed-device tests, and complete signed public bundles.

See [the 0.4 migration guide](docs/MIGRATION_0.3_TO_0.4.md),
[integration patterns](docs/INTEGRATION_PATTERNS.md), and
[release notes](docs/RELEASE_NOTES_0.4.0.md).

## 0.3.0 — 2026-08-23

Pulse 0.3.0 is published to Maven Central.

### Added

- `PulseStore` and `DefaultPulseStore` with `StateFlow` state, ordered transition frames, suspending
  `send`, non-suspending `trySend`, and an ordered close barrier.
- Explicit `ReduceOutcome` branches, correlated `EffectEnvelope` values, typed `PulseFailure`
  diagnostics, and frame-based plugins.
- Replay-zero `UiEffect` coordination with one active consumer session.
- Keyed process-local tasks with `Latest`, `DropWhileRunning`, bounded `Queue(capacity)`, bounded `Parallel(maxConcurrency)`, and `Conflate`
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
- Controlled API baselines cover all six published artifacts. The frozen 0.2 source surface and
  archive-level compatibility checks cover all five existing coordinates; core-runtime binary
  linkage is also executed against 0.3.0.
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
