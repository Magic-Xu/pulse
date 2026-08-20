# Pulse 0.3.0 Release Plan

Chinese version: [RELEASE_PLAN.zh-CN.md](./RELEASE_PLAN.zh-CN.md)

> Candidate status: `0.3.0-SNAPSHOT`, **not published**. The only public release target in this plan
> is stable `0.3.0`; branches, snapshots, release candidates, and prerelease tags cannot publish.

## Release objective

Publish one coherent v0.3 line in which the ordered runtime, public contracts, Android lifecycle
bindings, extensions, testing tools, compatibility checks, and documentation agree. Convenience DSLs
are not released ahead of the runtime contracts they build on.

## Candidate sequence

| Stage | Required result |
|---|---|
| 0. Baseline | Frozen 0.2 source/binary consumers and failing contract cases exist before behavior changes |
| 1. Public API | Runtime, effect, task, lifecycle, and failure decisions are accepted and reflected in contracts |
| 2. Core runtime | One FIFO processor owns reduce, commit, transition, effect, completion, and close ordering |
| 3. Split, task, effect | UI input boundaries, keyed task policies, token validation, and replay-zero effects are complete |
| 4. Android and Compose | Main-thread, owner, lifecycle, saved-state, and cleanup contracts are verified |
| 5. Testing and extensions | Public probes/TCK and state-decomposition helpers build on the stable runtime surface |
| 6. Documentation | Migration, compatibility, consumer, and release documents match the candidate artifacts |
| 7. Release candidate | All framework, compatibility, artifact, stress, performance, and publication gates pass |

A later stage cannot weaken an earlier contract merely to pass its own check. Any public API change
after candidate qualification restarts API, compatibility, artifact-consumer, stress, and performance
verification.

## Six published artifacts

All artifacts use group `io.github.magic-xu` and one identical version.

| Artifact | Purpose |
|---|---|
| `mvi-core-contract` | Platform-neutral legacy and 0.3 contracts |
| `mvi-core-runtime` | Ordered Store, effect coordinator, tasks, configuration, and legacy adapters |
| `mvi-platform-android` | ViewModel, explicit owner, Main dispatcher, and saved-state integration |
| `mvi-platform-android-compose` | Lifecycle-aware state selectors and UI-effect coordination |
| `mvi-extensions` | Optional plugins, `StateLens`, and reducer composition |
| `mvi-testing` | Virtual-time helpers, probes, and Store TCK |

The sample app, compatibility fixtures, isolated consumers, and benchmarks are verification inputs;
they are not published library artifacts.

## Required gates

### Pull-request framework gate

```bash
./gradlew mviFrameworkCheck
```

This gate must cover:

- `check` for contract, runtime, extensions, and testing modules;
- Android and Compose unit tests plus `lintDebug`;
- sample app unit tests, `assembleDebug`, and `lintDebug`;
- controlled API baselines for all six published artifacts;
- a frozen 0.2 Kotlin source surface and archive-level compatibility comparison for all five
  existing artifacts, plus executable core-runtime binary linkage;
- both isolated sample consumers built from staged Maven artifacts only;
- publication version consistency.

### Release-only gates

```bash
./gradlew mviReleaseCheck
```

The release aggregate must include the complete framework gate plus:

- `verifyPublicationBundle` for all six binaries, source archives, POMs, Gradle metadata, versions,
  and internal dependency versions;
- `:mvi-testing:multiSeedStressCheck` for deterministic multi-seed 10,000-input stress;
- `:mvi-benchmarks:performanceRegressionCheck` for broad throughput, p95 latency, retained-memory,
  and bounded-mailbox floors plus a reported synthetic selector count.

The five-artifact compatibility fixture verifies Android, Compose, and extensions at source and
archive level; executable runtime replacement is covered by the core-runtime consumer. The
performance harness is a portable catastrophic-regression check, not a device benchmark or a real
Compose selector benchmark.

`verifyMavenCentralConfig` is also required before remote publication. A missing task, skipped
artifact consumer, or absent API baseline is a release failure; no empty or best-effort gate is
accepted.

## Candidate commands

Run on JDK 21 from a clean checkout with release credentials absent:

```bash
./gradlew clean
./gradlew mviFrameworkCheck
./gradlew mviReleaseCheck
```

API baselines are updated only during intentional public API review:

```bash
./gradlew apiDump
git diff -- */api/*.api
./gradlew apiCheck
```

An `apiDump` diff is not self-approving. Review removals, signature changes, generic bounds, and
Android release-AAR surfaces before accepting the baseline.

## Stable publication rules

Remote publication is permitted only when all of the following are true:

1. GitHub is processing the exact tag `v0.3.0`.
2. `POM_VERSION_NAME` is exactly `0.3.0` and matches the tag.
3. The version contains no `SNAPSHOT`, `RC`, or other prerelease suffix.
4. The release-check job has passed on JDK 21.
5. `verifyMavenCentralConfig` has validated required metadata.
6. The publish job depends on the release-check job and uses the same commit.

The publish task must not depend back on `mviReleaseCheck`; job ordering owns the remote-publication
barrier and avoids a Gradle dependency cycle.

## Post-publication verification

After Maven Central reports the deployment as released:

1. Resolve all six `io.github.magic-xu:*:0.3.0` coordinates from Maven Central without a local or
   staging repository.
2. Build the synchronous and asynchronous artifact-only consumers against those coordinates.
3. Confirm POM and Gradle metadata expose only `0.3.0` internal dependencies.
4. Publish the release notes and migration links only after resolution succeeds.

A failed or partial candidate is not retagged or overwritten. Fix the cause, choose a new version,
and run the complete qualification sequence again.
