# Compatibility Policy

Chinese version: [COMPATIBILITY.zh-CN.md](./COMPATIBILITY.zh-CN.md)

> `0.3.0-SNAPSHOT` is a development candidate and has not been published. `0.2.0` remains the
> current stable line until the `0.3.0` release is announced.

This document distinguishes API compatibility from runtime behavior. A program can remain source
and binary compatible while receiving stronger ordering and failure semantics.

## Release lines

| Line | Status | Intended use |
|---|---|---|
| `0.2.0` | Current stable | Existing applications and the compatibility reference |
| `0.3.0-SNAPSHOT` | Unpublished development candidate | Repository development and release verification only |
| `0.3.0` | Planned stable | Ordered runtime, new API, and retained 0.2 compatibility surface |

Pulse uses `0.x.y` versions. Before 1.0, a minor release may add or revise public APIs. Patch releases
must not intentionally remove public APIs or change documented behavior incompatibly.

## 0.2 to 0.3 compatibility target

| Dimension | 0.3 target |
|---|---|
| Artifact coordinates | Keep the five 0.2 coordinates; add `mvi-testing` |
| Source compatibility | Existing 0.2 public calls compile against the candidate |
| Binary compatibility | A consumer compiled against 0.2 links and runs with candidate artifacts |
| New API | Additive; migration may be performed one feature at a time |
| Behavioral compatibility | Preserve functional intent, with documented ordering and lifecycle changes |
| Persistence compatibility | Application-owned schemas remain the application's responsibility |

The release gate compiles one frozen 0.2 Kotlin surface against both the baseline and candidate forms
of all five existing artifacts, and performs archive-level binary and Java-source compatibility
comparison for each coordinate. A binary-linked core-runtime consumer also executes against the
staged candidate. Controlled public API baselines cover all six published modules. These are release
requirements, not evidence that the unpublished snapshot is already released.

## Published artifacts

All coordinates use the group `io.github.magic-xu`.

| Artifact | Compatibility role |
|---|---|
| `mvi-core-contract` | Keeps 0.2 markers, reducers, `Next`, `Store`, and result types; adds 0.3 contracts |
| `mvi-core-runtime` | Keeps `DefaultStore` and legacy plugins; adds the ordered `PulseStore` runtime |
| `mvi-platform-android` | Keeps 0.2 ViewModel adapters; adds explicit-owner, saved-state, and new Split APIs |
| `mvi-platform-android-compose` | Keeps legacy Store bindings; adds lifecycle-aware host bindings |
| `mvi-extensions` | Keeps legacy plugins and reducer helpers; adds 0.3 state decomposition |
| `mvi-testing` | New in 0.3; public virtual-time probes and Store TCK |

Depending on the highest adapter normally supplies its transitive Pulse dependencies. Consumers
should not mix Pulse module versions within one dependency graph.

## Intentional runtime differences

The 0.2 compatibility API is backed by the 0.3 engine. The following changes are intentional and
must be tested if an application relied on timing rather than documented results:

- accepted inputs, lifecycle controls, and reentrant sends share one FIFO ordering boundary;
- legacy `dispatch` waits for its frame, while callback delivery is isolated from the reducer stack;
- state, effect, and plugin consumer failures are isolated and reported instead of stopping other
  consumers;
- cancellation and fatal JVM errors are not converted into domain or Pulse failures;
- close establishes an admission cutoff and drains work accepted before that cutoff;
- equal candidate state is not emitted again on the new `StateFlow` API;
- new `UiEffect` delivery has replay zero and one active coordinator.

These guarantees do not provide distributed ordering, durable task execution, or exactly-once UI
delivery. Tasks and UI effects are process-local.

## Platform baseline

The 0.3 candidate is built with Java 11 bytecode targets. Android artifacts use `minSdk 23`; the
release project compiles against Android API 36.1. Release and CI gates run on JDK 21. The candidate
dependency set is defined by its published Gradle metadata and POM; consumer dependency resolution
must be checked against the application's own platform and constraints.

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
