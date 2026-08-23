# Pulse 0.4.0 Real-project Feedback Audit

Chinese version: [REAL_PROJECT_FEEDBACK_0.4.0.zh-CN.md](./REAL_PROJECT_FEEDBACK_0.4.0.zh-CN.md)

This document is for Pulse maintainers and application architects. It applies the OnePage product
boundary to issues reported by five applications after adopting Pulse 0.3.0.

## Product boundary

Pulse owns process-local state evolution after an input reaches a Pulse boundary:

- bounded and ordered admission;
- State, Transition, and replay-zero UiEffect semantics;
- keyed-task cancellation, replacement, and stale-result rejection;
- Android lifecycle ownership and cleanup;
- typed runtime diagnostics and reusable conformance tests.

Applications still own domain meaning and orchestration outside that boundary:

- domain models, repositories, navigation, dependency injection, and UI copy;
- whether an external event should be sampled, retried, conflated, or persisted;
- durable operation IDs, recovery state machines, WorkManager or Service policies;
- coordination between feature stores and external actors.

An API belongs in Pulse only when the problem repeats across applications, its semantics can be
stated without domain knowledge, it preserves the single ordered runtime path, and it can be
verified by tests or compatibility evidence.

## Decisions

| Feedback | Decision | 0.4.0 action |
|---|---|---|
| Split UI admission can stall behind executor work while the executor waits for a mutation | Framework defect | Bound the complete UI-to-executor path with one admission budget; expose typed overflow and regression tests |
| A listener cannot call a suspending API | Framework adapter gap | Add callback ingress with a mandatory rejection handler; keep `send` as the lossless backpressure path |
| Split transitions are unavailable to diagnostics and tests | Framework observability gap | Expose read-only Split transition frames without exposing Store or mutation authority |
| Custom Android runtime options can accidentally move Store work off Main | Framework configuration trap | Add an Android config overlay that preserves non-dispatcher options and applies `Main.immediate` |
| Task exceptions look like executor exceptions and lose the originating UI request | Framework diagnostic defect | Report a distinct task failure with task key, token, request ID, and input type |
| Pure JVM test helpers cannot create a real Split ViewModel | Framework testing gap | Add a separate Android testing artifact driven by one virtual-time scheduler |
| Modern transitions lack safe reusable logging | Framework extension gap | Add redacted transition and failure logging; values and throwable messages remain hidden by default |
| Official samples ignore admission or task-launch results and expose raw exception messages | Framework-owned sample defect | Handle every result and map failures to domain/UI-safe values |
| Bind a callback or Flow for the lifetime of a Store task | Valid recurring need; existing primitive is sufficient | Publish the keyed-task + `callbackFlow`/`awaitClose`/`conflate` pattern and verify replacement and close behavior |
| Emit frequent progress from a callback | Valid recurring need; policy is application-specific | Use a conflated Flow and token-bound mutations; do not add an unbounded progress sink |
| Resume uploads or downloads after process death | Application/durable-runtime responsibility | Persist operation ID and domain state, reconcile through WorkManager or Service, and feed observations back into Pulse |
| Coordinate root, feature, and Service-owned stores | Application architecture responsibility | Document ownership and external-actor boundaries; do not add a global Store bus |
| Complete migration to typed mutations | Application migration responsibility | Document exit criteria; keep explicit reducers and state decomposition as the framework tools |
| Add a SourceRegistry, generic scheduler/recovery API, Service base class, global bus, writer DSL/KSP, lint suite, or dev panel | Rejected for 0.4.0 | Each adds policy or a second runtime path without evidence that its semantics are universal |
| Make a bounded non-suspending API accept every event without loss or rejection | Impossible contract | Callers must choose suspending backpressure or explicitly handle `Full`/`Rejected` |

## Consequences

Pulse 0.4.0 remains one runtime rather than an application framework. It strengthens the boundaries
already promised by 0.3.0 and adds adapters only where Kotlin or Android interfaces make those
boundaries difficult to use correctly. Durable work, business retry rules, and multi-feature
coordination stay visible in application state instead of being hidden behind framework policy.
