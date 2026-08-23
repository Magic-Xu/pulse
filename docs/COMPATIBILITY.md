# Compatibility Policy

Chinese version: [COMPATIBILITY.zh-CN.md](./COMPATIBILITY.zh-CN.md)

> `0.4.0` is the current stable release on Maven Central. `0.3.0` is the direct compatibility
> baseline, and the five-artifact `0.2.0` surface remains a long-term compatibility commitment.

This document distinguishes API compatibility from runtime behavior. A program can remain source
and binary compatible while receiving stronger ordering and failure semantics.

## Release lines

| Line | Status | Intended use |
|---|---|---|
| `0.2.0` | Long-term compatibility surface | Existing five-artifact integrations retained through the legacy adapters |
| `0.3.0` | Direct compatibility baseline | Six-artifact ordered-runtime surface used to qualify 0.4 |
| `0.4.0` | Current stable | Hardened Android integration, diagnostics, logging, and a seventh testing artifact |

Pulse uses `0.x.y` versions. Before 1.0, a minor release may add or revise public APIs. Patch releases
must not intentionally remove public APIs or change documented behavior incompatibly.

## 0.3 to 0.4 direct compatibility target

| Dimension | 0.4 target |
|---|---|
| Artifact coordinates | Keep all six 0.3 coordinates; add `mvi-platform-android-testing` |
| Source compatibility | Existing 0.3 public calls compile against 0.4.0 for all six prior artifacts |
| Binary compatibility | Consumers compiled against 0.3 link and run with the corresponding 0.4.0 artifacts |
| New API | Additive, while exhaustive handling of extended enum and sealed failure surfaces must be updated |
| Behavioral compatibility | Preserve ordered-runtime intent and document the stronger Split admission boundary |
| Persistence compatibility | Application-owned schemas remain the application's responsibility |

The direct baseline is the complete six-artifact 0.3 release, not only the core runtime. See
[Migrating from Pulse 0.3 to 0.4](./MIGRATION_0.3_TO_0.4.md) for the new admission, failure, Android
configuration, and testing surfaces.

## Compatibility evidence for 0.4

The 0.4 release gate requires:

- controlled API/ABI baselines for all seven published artifacts;
- `compatibility03Check`, which compiles the frozen six-artifact 0.3 source surface against both
  baseline and 0.4, compares every prior archive for binary and Java-source compatibility, executes
  the baseline and candidate JVM consumers, runs bytecode compiled against 0.3 on the 0.4 runtime,
  and verifies the default bridge for a `PulseTasks` implementation compiled only against 0.3;
- the retained `compatibilityCheck` for the five 0.2 coordinates, which compiles the frozen source
  surface, compares all five archives for linkage and Java-source compatibility, and executes both
  source and binary core-runtime linkage against 0.4.

These fixtures consume staged 0.4 publications rather than project dependencies, so they validate
the same artifact boundaries exposed to Maven consumers.

## Historical 0.2 to 0.3 compatibility target

| Dimension | 0.3 target |
|---|---|
| Artifact coordinates | Keep the five 0.2 coordinates; add `mvi-testing` |
| Source compatibility | Existing 0.2 public calls compile against 0.3.0 |
| Binary compatibility | A consumer compiled against 0.2 links and runs with 0.3.0 artifacts |
| New API | Additive; migration may be performed one feature at a time |
| Behavioral compatibility | Preserve functional intent, with documented ordering and lifecycle changes |
| Persistence compatibility | Application-owned schemas remain the application's responsibility |

The v0.3 release gate compiled one frozen 0.2 Kotlin surface against both the baseline and 0.3.0
forms of all five existing artifacts and performed archive-level binary and Java-source comparison
for every coordinate. A binary-linked core-runtime consumer also ran against the staged 0.3.0
artifacts. Controlled public API baselines covered all six published modules. The v0.3.0 release
passed these checks before publication, and 0.4 retains the five-artifact 0.2 surface as described
above.

## Published artifacts

All coordinates use the group `io.github.magic-xu`.

| Artifact | Compatibility role |
|---|---|
| `mvi-core-contract` | Keeps 0.2 markers, reducers, `Next`, `Store`, and result types; adds 0.3 contracts |
| `mvi-core-runtime` | Keeps `DefaultStore` and legacy plugins; adds the ordered `PulseStore` runtime |
| `mvi-platform-android` | Keeps 0.2 ViewModel adapters; adds explicit-owner, saved-state, and new Split APIs |
| `mvi-platform-android-compose` | Keeps legacy Store bindings; adds lifecycle-aware host bindings |
| `mvi-platform-android-testing` | New in 0.4; deterministic tests for a real Split ViewModel |
| `mvi-extensions` | Keeps legacy plugins and reducer helpers; adds 0.3 state decomposition |
| `mvi-testing` | New in 0.3; public virtual-time probes and Store TCK |

Depending on the highest adapter normally supplies its transitive Pulse dependencies. Consumers
should not mix Pulse module versions within one dependency graph.

## Intentional runtime differences

The retained 0.2 and 0.3 APIs are backed by the 0.4 ordered engine. For 0.2 consumers, the following
0.3 changes remain intentional and must be tested if an application relied on timing rather than
documented results:

- accepted inputs, lifecycle controls, and reentrant sends share one FIFO ordering boundary;
- legacy `dispatch` waits for its frame, while callback delivery is isolated from the reducer stack;
- state, effect, and plugin consumer failures are isolated and reported instead of stopping other
  consumers;
- cancellation and fatal JVM errors are not converted into domain or Pulse failures;
- close establishes an admission cutoff and drains work accepted before that cutoff;
- equal candidate state is not emitted again on the new `StateFlow` API;
- new `UiEffect` delivery has replay zero and one active coordinator.

Relative to 0.3, 0.4 intentionally makes these observable changes:

- Split `trySend` admission covers the complete UI-to-executor path and can report `Full` before the
  core mailbox alone is full;
- keyed-task exceptions are reported as correlated `TaskFailure` values, and Split overload is
  reported through the admission failure phase when configured;
- Split transition frames are public for read-only observation without exposing a Store or mutation
  capability;
- the Android runtime configuration overlay preserves non-dispatcher options while applying Android
  Main dispatchers.

These guarantees do not provide distributed ordering, durable task execution, or exactly-once UI
delivery. Tasks and UI effects are process-local.

## Platform baseline

Pulse 0.4.0 continues to use Java 11 bytecode targets. Android artifacts retain `minSdk 23`; the
release project compiles against Android API 36.1. Release and CI gates run on JDK 21. The published
dependency set is defined by its Gradle metadata and POM; consumer dependency resolution must be
checked against the application's own platform and constraints.

## Compatibility boundaries

Compatibility does not cover:

- reflection against private, internal, synthetic, or generated declarations;
- relying on callback thread interleavings or undocumented exception messages;
- serializing framework runtime objects, task tokens, effect envelopes, or subscriptions;
- mixing different Pulse versions across modules;
- application-owned saved-state keys, schema versions, data migrations, or durable-operation rules;
- snapshot builds, unpublished commits, or local source substitutions as if they were stable releases.

## Reporting a compatibility issue

Include the exact old and new Pulse versions, affected coordinates, Kotlin/Gradle/AGP versions,
Android API levels when relevant, whether the failure is source, binary, or behavioral, and a minimal
reproducer. For behavioral reports, include the expected and observed transition or lifecycle order.
