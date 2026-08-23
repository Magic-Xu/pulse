# Pulse 0.4.0 Release Plan

Chinese version: [RELEASE_PLAN.zh-CN.md](./RELEASE_PLAN.zh-CN.md)

> Release status: **candidate — not public yet**. `0.3.0` remains the public stable release. Do not
> announce or consume `0.4.0` until the exact `v0.4.0` workflow has verified Maven Central and the
> public artifact-only consumers.

## Release objective

Publish one coherent v0.4 line that hardens the 0.3 ordered runtime for real Android integrations.
The candidate must resolve end-to-end Split admission, improve task and transition diagnostics, add
deterministic tests for a real Split ViewModel, and correct framework-owned examples without
absorbing application domain, durable-work, or multi-Store orchestration policy.

## Candidate sequence

| Stage | Required result |
|---|---|
| 0. Baseline | The published 0.3 API surface and retained 0.2 compatibility fixtures are identified before public behavior changes |
| 1. Admission | One bounded budget covers Split UI admission through the serial executor decision; suspending and non-suspending contracts are explicit |
| 2. Diagnostics | Split transitions are read-only, task failures retain request correlation, and Android config cannot silently move production work off Main |
| 3. Testing and extensions | The Android Split test host and redacted modern logging build only on the ordered runtime |
| 4. Samples and guidance | Official examples handle admission and task-launch results; integration guidance preserves framework/application ownership |
| 5. API and compatibility | Seven API baselines, executable six-artifact 0.3 checks, and retained five-artifact 0.2 checks pass |
| 6. Candidate artifacts | All seven staged publications and both artifact-only consumers pass |
| 7. Release qualification | Framework, publication, stress, performance, managed-device, and stable-identity gates pass on one commit |

A later stage cannot weaken an earlier contract merely to pass its own check. Any public API or
artifact change after qualification restarts API review, framework, compatibility, staged-consumer,
stress, performance, and managed-device verification.

## Seven published artifacts

All artifacts use group `io.github.magic-xu` and one identical version.

| Artifact | Purpose |
|---|---|
| `mvi-core-contract` | Platform-neutral Store, transition, task, effect, and typed-failure contracts |
| `mvi-core-runtime` | Ordered Store engine, effect coordinator, keyed tasks, configuration, and legacy adapters |
| `mvi-platform-android` | Split ViewModel, explicit owner, Android Main configuration, saved state, and callback ingress |
| `mvi-platform-android-compose` | Lifecycle-aware state selectors and UI-effect coordination |
| `mvi-platform-android-testing` | Real Split ViewModel host, shared virtual-time scheduler, probes, and deterministic cleanup |
| `mvi-extensions` | Optional redacted logging, `StateLens`, and reducer composition |
| `mvi-testing` | Platform-neutral virtual-time helpers, probes, and Store TCK |

The sample app, compatibility fixtures, isolated consumers, and benchmarks are verification inputs;
they are not published library artifacts.

## Required gates

### Pull-request framework gate

```bash
./gradlew mviFrameworkCheck --stacktrace
```

`.github/workflows/ci.yml` runs this task on JDK 21. The aggregate covers:

- contract, runtime, extensions, and pure-JVM testing module checks;
- unit tests and `lintDebug` for all three Android library artifacts;
- sample app unit tests, debug assembly, and lint;
- `apiCheck` for all seven published artifacts;
- `compatibility03Check`: frozen source compilation and archive comparison for all six 0.3
  artifacts, plus baseline/candidate JVM runs, frozen 0.3 bytecode on the candidate runtime, and the
  legacy `PulseTasks` implementation bridge;
- retained `compatibilityCheck`: the 0.2 five-artifact source/archive fixture and executable
  core-runtime linkage consumer;
- both isolated staged-artifact consumers;
- candidate version consistency.

Both compatibility aggregates consume staged candidate artifacts. For the six artifacts already
published in 0.3, `apiDump` changes must also be reviewed against the checked-in 0.3 surface before
accepting the 0.4 baseline. The seventh baseline belongs only to the new Android testing artifact.

### Release aggregate

```bash
./gradlew mviReleaseCheck --stacktrace
```

The release aggregate includes the complete framework gate and additionally requires:

- `verifyPublicationBundle` for seven binaries, source and javadoc archives, POMs, Gradle metadata,
  versions, and internal dependency versions;
- `:mvi-testing:multiSeedStressCheck`;
- `:mvi-benchmarks:performanceRegressionCheck`;
- `verifyMavenCentralConfig`.

The scheduled `.github/workflows/stress.yml` runs the stress and performance tasks independently.
The performance harness is a portable catastrophic-regression floor, not a device-rendering
benchmark.

### Managed-device gate

```bash
./gradlew mviAndroidDeviceCheck --stacktrace
```

This task runs the sample end-to-end instrumentation suite on the managed API 35 device.
`.github/workflows/android-device.yml` runs it for pull requests and pushes. The stable publish
workflow runs the same task in a separate `device-check` job; publication requires both that job
and `release-check`.

A missing task, absent API baseline, skipped artifact consumer, or partial publication bundle is a
release failure. No empty or best-effort gate is accepted.

## Candidate commands

Run the minimal complete local qualification on JDK 21 from a clean checkout with release
credentials absent:

```bash
./gradlew clean mviReleaseCheck --stacktrace
./gradlew mviAndroidDeviceCheck --stacktrace
```

`mviReleaseCheck` already includes `mviFrameworkCheck`, `verifyPublicationBundle`, stress,
performance, and Maven metadata validation.

Update API baselines only during intentional public API review:

```bash
./gradlew apiDump
git diff -- */api/*.api
./gradlew apiCheck
```

An `apiDump` diff is not self-approving. Review removals, signature changes, generic bounds,
visibility, exhaustive sealed surfaces, and all three release-AAR APIs before accepting it.

## Stable publication rules

Remote publication is permitted only when all of the following are true:

1. GitHub is processing the exact annotated tag `v0.4.0`.
2. `POM_VERSION_NAME` is exactly `0.4.0` and matches the tag.
3. The version contains no `SNAPSHOT`, `RC`, or other prerelease suffix.
4. The `release-check` and `device-check` jobs pass on JDK 21 for the same commit.
5. `verifyMavenCentralConfig` validates the required metadata.
6. The `publish` job depends on both jobs and publishes that workflow commit.

The publish task must not depend back on `mviReleaseCheck`; workflow job ordering owns the remote
publication barrier and avoids a Gradle dependency cycle.

## Post-publication verification

After Maven Central reports the deployment as released:

1. Resolve all seven `io.github.magic-xu:*:0.4.0` coordinates from Maven Central without a local or
   staging repository.
2. Verify each POM, Gradle module metadata file, sources archive, javadoc archive, binary, and their
   signatures.
3. Build and test both artifact-only consumers with `--refresh-dependencies`.
4. Confirm every internal Pulse dependency is exactly `0.4.0`.
5. Only then change candidate notices and announce availability.

The guarded workflow performs the artifact polling and runs `publicArtifactSamplesCheck`. A local
staging result cannot replace this evidence.

A failed or partial candidate is not retagged or overwritten. Fix the cause, choose a new version,
and run the complete qualification sequence again.
