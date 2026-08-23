# Pulse Iteration Roadmap

[简体中文](ITERATION_ROADMAP.zh-CN.md)

## Current line

`0.3.0` is the current public stable release. `0.4.0` is a release candidate and is **not public
yet**. Applications must continue to resolve `0.3.0` until the guarded `v0.4.0` publication and
public-consumer verification finish.

## 0.3 foundation

Pulse 0.3 established the product boundary that 0.4 retains:

- one bounded FIFO processor owns ordered state reduction, transition publication, effect
  delivery, completion, and close;
- keyed tasks provide explicit overload policies and reject stale mutations with opaque tokens;
- Android and Compose adapters own lifecycle-safe collection and cleanup without changing the
  runtime model;
- State Decomposition, reusable Store tests, controlled API baselines, and retained 0.2
  compatibility are separate modules or verification evidence rather than a second runtime.

## 0.4 candidate scope

### End-to-end Split admission

- One bounded in-flight budget covers UI admission through the serial executor decision.
- Suspending `send(UI)` remains the backpressure path; non-suspending `trySend(UI)` reports
  `Full` or lifecycle rejection instead of promising impossible lossless, non-blocking delivery.
- `callbackIngress(onRejected)` gives listener APIs an explicit overload and lifecycle boundary.
- Split overload is observable as typed admission failure when reporting is enabled.

### Diagnostics and Android safety

- Split ViewModels expose read-only transition frames without exposing Store or mutation
  authority.
- Keyed-task failures carry task identity and the originating UI request context.
- `androidPulseRuntimeConfig(base)` preserves non-dispatcher options while enforcing Android Main
  dispatchers.
- Modern Store and transition-Flow logging is reusable and redacted by default.

### Integration and testing

- The new `mvi-platform-android-testing` artifact drives a real Split ViewModel with one virtual
  time scheduler and deterministic probes and cleanup.
- Official samples handle admission and task-launch results explicitly and keep raw exception
  detail out of State and effects.
- Maintainer guidance covers external Flow/callback bindings, progress, durable work, Store
  ownership, and typed-mutation migration without adding a second runtime path.

### Deliberate boundary

Pulse remains a process-local ordered state runtime. Domain models, durable operation recovery,
WorkManager or Service policy, multi-Store orchestration, and source sampling or retry policy remain
application responsibilities. A SourceRegistry, generic scheduler, Service base class, global bus,
writer DSL/KSP layer, lint suite, and developer panel are outside the 0.4 candidate.

## Candidate evidence

- Seven public artifacts must have controlled API/ABI baselines, including three Android AARs.
- `compatibility03Check` compiles and links the frozen six-artifact 0.3 surface against staged 0.4
  artifacts, including executable JVM replacement and archive comparisons.
- `apiCheck` controls all seven candidate baselines, including the reviewed first baseline of the
  new Android testing artifact.
- The retained 0.2 fixture compiles the frozen five-artifact Kotlin surface, compares those
  archives, and executes the core-runtime linkage consumer against staged 0.4 artifacts.
- Both isolated samples build from staged Maven artifacts; after publication they build again from
  Maven Central only.
- Fixed-seed PR checks, multi-seed stress, performance floors, and managed-device instrumentation
  remain distinct evidence.

## Release boundary

The candidate is releasable only after these commands pass from a clean checkout on JDK 21:

```bash
./gradlew clean mviReleaseCheck --stacktrace
./gradlew mviAndroidDeviceCheck --stacktrace
```

Remote publication additionally requires `POM_VERSION_NAME=0.4.0`, the exact annotated tag
`v0.4.0`, and the guarded `.github/workflows/publish-maven-central.yml` jobs. The publish job waits
for both `release-check` and `device-check`, publishes that same commit, verifies all seven public
artifacts, and runs the public artifact-only consumers. A branch, snapshot, RC, or different tag
cannot publish `0.4.0`.

## After 0.4

Future work must preserve the single ordered runtime and earn framework ownership through repeated
cross-application evidence, domain-independent semantics, and executable compatibility or
conformance checks.
