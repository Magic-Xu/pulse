# Split Intent Basic Sample

Recommended first v0.3 Split Intent sample.

## Purpose

- show the minimum usable path
- direct `PulseSplitStoreViewModel`, without a `typealias`
- direct `PulseMutationReducer`, without a routing DSL
- one explicit `DropWhileRunning` task key

## What to learn

1. define `State / UiIntent / Mutation / Effect`
2. write one `PulseMutationReducer`
3. create `PulseSplitStoreViewModel` directly
4. launch work from `PulseIntentContext` and mutate only from its task context

Cancellation is rethrown and never converted to a failure mutation. Use this as the base template
when overlapping requests should be dropped.
