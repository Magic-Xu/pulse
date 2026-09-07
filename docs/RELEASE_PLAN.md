# Pulse 0.5.0 Release Plan

Chinese version: [RELEASE_PLAN.zh-CN.md](./RELEASE_PLAN.zh-CN.md)

> Status: **candidate**. The guarded release target is exact annotated tag `v0.5.0`.

## Scope

This release adds precise mutation outcomes while preserving Boolean callers, and connects Split
executor timing and mutations to the originating Core UI request. Diagnostics remain bounded and
best effort. The Android test host exposes the same diagnostics through an owned probe.
See [release notes](./RELEASE_NOTES_0.5.0.md) and [migration](./MIGRATION_0.4_TO_0.5.md).

Platform/Factory own generated app conventions; consumers own navigation, input feedback, and
feature task policies. Pulse remains independent of that application scaffold.

Public API or artifact changes require API review, framework and compatibility checks, staged
consumers, stress/performance qualification, and managed-device verification on the final candidate.
Previous release evidence is in the versioned release notes and does not qualify this candidate.

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
- `compatibility04Check`: frozen source and archive checks for all seven 0.4 artifacts, including
  the Android Split test host and JVM binary replacement;
- `compatibility03Check`: frozen source compilation and archive comparison for all six 0.3
  artifacts, plus baseline/candidate JVM runs, frozen 0.3 bytecode on the candidate runtime, and the
  legacy `PulseTasks` implementation bridge;
- retained `compatibilityCheck`: the 0.2 five-artifact source/archive fixture and executable
  core-runtime linkage consumer;
- both isolated staged-artifact consumers;
- candidate version consistency.

All three compatibility aggregates consume staged candidate artifacts. Intentional API changes must
also be reviewed against the currently checked-in surface before updating a baseline; the first
baseline for a new artifact requires the same explicit review.

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

## Qualification commands

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

## Future stable publication rules

For a future stable version `X.Y.Z`, remote publication is permitted only when all of the
following are true:

1. The guarded workflow target and GitHub ref are the exact annotated tag `vX.Y.Z`.
2. `POM_VERSION_NAME` is exactly `X.Y.Z` and matches the tag.
3. The version contains no `SNAPSHOT`, `RC`, or other prerelease suffix.
4. The `release-check` and `device-check` jobs pass on JDK 21 for the same commit.
5. `verifyMavenCentralConfig` validates the required metadata.
6. The `publish` job depends on both jobs and publishes that workflow commit.

The publish task must not depend back on `mviReleaseCheck`; workflow job ordering owns the remote
publication barrier and avoids a Gradle dependency cycle.

## Future post-publication verification

After Maven Central reports the deployment as released:

1. Resolve every intended `io.github.magic-xu:*:X.Y.Z` coordinate from Maven Central without a
   local or staging repository.
2. Verify each POM, Gradle module metadata file, sources archive, javadoc archive, binary, and their
   signatures.
3. Build and test both artifact-only consumers with `--refresh-dependencies`.
4. Confirm every internal Pulse dependency is exactly `X.Y.Z`.
5. Only then mark the release public and announce availability.

The guarded workflow performs the artifact polling and runs `publicArtifactSamplesCheck`. A local
staging result cannot replace this evidence.

A failed or partial candidate is not retagged or overwritten. Fix the cause, choose a new version,
and run the complete qualification sequence again.
