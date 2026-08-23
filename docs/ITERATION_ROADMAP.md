# Pulse Iteration Roadmap

[简体中文](ITERATION_ROADMAP.zh-CN.md)

## Current line

`0.4.0` is the current public stable release. It was published on 2026-08-24 from the exact
annotated tag `v0.4.0`; all seven signed bundles and both isolated artifact consumers passed
public verification. `0.3.0` is the preceding stable line.

## 0.3 foundation

Pulse 0.3 established the product boundary that 0.4 retains:

- one bounded FIFO processor owns ordered state reduction, transition publication, effect
  delivery, completion, and close;
- keyed tasks provide explicit overload policies and reject stale mutations with opaque tokens;
- Android and Compose adapters own lifecycle-safe collection and cleanup without changing the
  runtime model;
- State Decomposition, reusable Store tests, controlled API baselines, and retained 0.2
  compatibility are separate modules or verification evidence rather than a second runtime.

## 0.4 delivered scope

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
writer DSL/KSP layer, lint suite, and developer panel are not part of 0.4.

## 0.4 release evidence

- Seven public artifacts have controlled API/ABI baselines, including three Android AARs.
- `compatibility03Check` compiled and linked the frozen six-artifact 0.3 surface against staged
  0.4 artifacts, including executable JVM replacement and archive comparisons.
- `apiCheck` passed for all seven baselines, including the reviewed first baseline of the new
  Android testing artifact.
- The retained 0.2 fixture compiled the frozen five-artifact Kotlin surface, compared those
  archives, and executed the core-runtime linkage consumer against staged 0.4 artifacts.
- Both isolated samples passed against staged artifacts and then against Maven Central only.
- Fixed-seed PR checks, multi-seed stress, performance floors, and managed-device instrumentation
  all passed.

## 0.4 release result

The guarded workflow ran the complete JDK 21 release and managed-device gates:

```bash
./gradlew verifyMavenCentralConfig mviReleaseCheck --stacktrace
./gradlew mviAndroidDeviceCheck --stacktrace \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect
```

`POM_VERSION_NAME=0.4.0` matched the exact annotated tag `v0.4.0`.
[Workflow run 32659106344](https://github.com/Magic-Xu/pulse/actions/runs/32659106344) completed
`release-check`, `device-check`, publication, seven-bundle public verification, and both public
artifact-only consumers.

## Future release boundary

Each future stable release must pass the same clean qualification and managed-device gates. Its
configured stable version, exact annotated tag, and guarded workflow target must match; the publish
job must depend on both qualification jobs, verify every public bundle, and run the public
artifact-only consumers before availability is announced.

## After 0.4

Future work must preserve the single ordered runtime and earn framework ownership through repeated
cross-application evidence, domain-independent semantics, and executable compatibility or
conformance checks.
