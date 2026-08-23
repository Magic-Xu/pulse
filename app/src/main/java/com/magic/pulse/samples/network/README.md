# Network Sample (Layered)

This is the layered v0.4 repository sample, not a complete production architecture. It demonstrates
the Pulse integration seam; networking, retry, caching, and domain error policy remain app-owned.

## Purpose

- keep business layering clear (`Repository / DataSource / Service`)
- keep split-intent flow explicit
- make request replacement explicit with one `Latest` task key

## Characteristics

- `PulseSplitStoreViewModel` with `PulseUiIntentExecutor`
- token-gated late mutations after request replacement
- no reducer DSL required
- optional `typealias` is used only to reduce generic noise
- callback admission and task admission are both handled explicitly
- exceptions are mapped to typed domain errors before reaching State or UiEffect

Use this style for repository-backed pages where a new request replaces the previous request.
`PulseIntentExecutionDecision.Completed` acknowledges that the executor handled the intent and
admitted its task; task completion is represented later by mutations, not by that decision.
