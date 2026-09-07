# Migrating from Pulse 0.4 to 0.5

Chinese version: [MIGRATION_0.4_TO_0.5.zh-CN.md](./MIGRATION_0.4_TO_0.5.zh-CN.md)

> Status: **released**. All seven signed bundles and both public consumers passed [release verification](https://github.com/Magic-Xu/pulse/actions/runs/34136328471).

Keep all Pulse modules present in your dependency graph on the same version. With Magic Android Platform, upgrade the
Platform release that has qualified Pulse 0.5 instead of overriding individual transitive modules.
Direct consumers use `mvi-platform-android-compose:0.5.0` and Android tests use
`mvi-platform-android-testing:0.5.0` from Maven Central.

Existing Boolean mutation calls require no source change. Use `mutateResult` when business code must
distinguish an ignored decision from an applied change or an expired task. Do not automatically
retry `StaleTask`: the replaced task no longer owns permission to change state. See the exact result
semantics in the [release notes](./RELEASE_NOTES_0.5.0.md).

Collect `viewModel.diagnostics` inside an owned lifecycle scope before sending inputs. Correlate
records using both `storeId` and `originIntentId`; Core IDs are scoped to one Store. Interpret
sequence gaps as missing telemetry. Retain bounded summaries externally if needed; do not make
navigation or business completion depend on this lossy stream. Existing `transitions` remains
available and its public frame/input shape is unchanged.

Real ViewModel tests may inspect `host.diagnosticProbe.forIntent(id)` or `awaitCount(count)`. The
probe observes only events delivered to its collector; it does not recover dropped diagnostics.
