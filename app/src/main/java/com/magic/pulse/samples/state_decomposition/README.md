# State Decomposition Sample (Advanced)

This sample demonstrates a large-state feature split into two state domains:

- `ImageDomainState`
- `VideoDomainState`

## What It Shows

- split one large screen state into domain sub-states
- route mutations with `pulseMutationReducer { onSub(...) }` from `mvi-extensions`
- run image and video work under independent `Latest` task keys
- collect state and replay-zero effects with an explicit lifecycle owner

## Why This Structure

- domain state isolation makes reducer logic smaller and easier to debug
- each mutation branch is localized to one domain
- ViewModel keeps one UI entry (`send(uiIntent)`) while task tokens reject late mutations

Use this sample only when basic/standard style starts to become too large.
