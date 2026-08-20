# Network Sample (Standard)

This sample is the standard production path.

## Purpose

- keep business layering clear (`Repository / DataSource / Service`)
- keep split-intent flow explicit
- make request replacement explicit with one `Latest` task key

## Characteristics

- `PulseSplitStoreViewModel` with `PulseUiIntentExecutor`
- token-gated late mutations after request replacement
- no reducer DSL required
- optional `typealias` is used only to reduce generic noise

Use this style for repository-backed pages where a new request replaces the previous request.
