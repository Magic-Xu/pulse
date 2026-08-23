# Split Intent Basic Sample

Recommended first v0.4 Split Intent sample.

## Purpose

- show the minimum usable path
- direct `PulseSplitStoreViewModel`, without a `typealias`
- direct `PulseMutationReducer`, without a routing DSL
- one explicit `DropWhileRunning` task key
- callback ingress whose `Full` and lifecycle rejection paths are handled by the UI

## What to learn

1. define `State / UiIntent / Mutation / Effect`
2. write one `PulseMutationReducer`
3. create `PulseSplitStoreViewModel` directly
4. launch work from `PulseIntentContext` and mutate only from its task context
5. handle every `TaskLaunchResult` and map application failures to typed errors

Cancellation is rethrown and never converted to a failure mutation. Use this as the base template
when overlapping requests should be dropped.

`PulseIntentExecutionDecision.Completed` means the serial executor finished handling the UI intent.
For a load intent, it acknowledges task admission; it does not mean that the background task
finished. This sample reflects task progress and terminal application outcomes through mutations;
use the accepted task handle when a caller must await `TaskOutcome` directly.
