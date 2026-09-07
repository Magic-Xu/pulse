# Pulse 0.5.0 Release Notes

Chinese version: [RELEASE_NOTES_0.5.0.zh-CN.md](./RELEASE_NOTES_0.5.0.zh-CN.md)

> Status: **released**. All seven signed bundles and both public consumers passed [release verification](https://github.com/Magic-Xu/pulse/actions/runs/34136328471).

## Mutation decisions

`PulseIntentContext.mutateResult` and `PulseTaskContext.mutateResult` distinguish `Changed`,
`Unchanged`, business `Ignored`, reducer `Failed`, lifecycle `Rejected`, and `StaleTask`.
`Changed` and `Unchanged` include completion of the reducer's UI-effect commit. Cancellation and
fatal errors still propagate. Existing `mutate(): Boolean` calls remain available: changed,
unchanged, and business-ignored decisions return true; failure, rejection, and stale tokens return
false. A business ignored reason equal to `late-task-mutation` no longer impersonates token rejection.

## Split correlation and timing

`PulseSplitStoreViewModel.diagnostics` connects executor completion and each direct or task mutation
through `originIntentId`, the Core UI frame's request ID. Mutation records carry the mutation Core
frame ID when one exists. `PulseTaskContext.originIntentId` survives task suspension and replacement.
Executor records expose admission wait, executor wait, execution duration, and total elapsed time.
A queued executor cancelled before starting has a null start time and zero execution duration.

This replay-zero stream is best effort. It buffers at most `mailboxCapacity` events, drops the oldest
when observers lag, and never waits for diagnostics consumers. Sequence gaps expose loss. A close
can cancel observation before an admitted Core frame reaches the executor lane; use `send` and its
result for reliable completion, not diagnostics. Records contain types and identifiers, without
copying input payloads, task keys, ignored reasons, or exception messages.

`TestPulseSplitHost.diagnosticProbe` supports snapshots, waiting for a count, and filtering by the
originating intent. It shares the host scheduler and cleanup lifecycle.

The selector benchmark acknowledges each selected bucket before producing the next one and bounds its wait. It retains the expected distinct count and performance thresholds without assuming StateFlow delivers every intermediate state.

## Upgrade and qualification

See [0.4 → 0.5 migration](./MIGRATION_0.4_TO_0.5.md). All seven artifacts retain their coordinates.
Release qualification passed API checks with reviewed baselines, frozen 0.4 source/archive checks for all seven
artifacts, retained 0.3 and 0.2 checks, artifact-only consumers, stress/performance checks, and
managed-device instrumentation. Every signed bundle and both public consumers passed the guarded
release workflow.
