# Iteration Roadmap

Chinese version: [ITERATION_ROADMAP.zh-CN.md](https://github.com/Magic-Xu/pulse/blob/master/docs/ITERATION_ROADMAP.zh-CN.md)

Pulse current architecture baseline:

- `PulseViewModel`: inheritance-friendly base
- `PulseSplitViewModel`: two-lane intent model (`UiIntent` + `Mutation`)
- reducer focuses on pure state transitions

This roadmap defines the next production-oriented iterations.

## Version Evolution List

### v0.1.0 (First Minimal Version)

- Positioning:
  - Minimal usable MVI baseline
- Design:
  - Single intent lane (`MviIntent`)
  - reducer consumes all intents
  - Android base is ViewModel + Store composition
- What worked:
  - Cross-platform-first modularization (`contract/runtime/platform`)
  - Minimal runnable architecture with low learning cost
  - Basic sample can run end-to-end
- Limitations:
  - Trigger intents and pure mutation intents are mixed together
  - reducer can become huge in complex features
  - side-effect orchestration and state mutation boundaries are not explicit enough
  - difficult to scale cleanly when business methods grow in ViewModel

### v0.2.x (Current Iteration - Split Intent Architecture)

- Positioning:
  - Production-oriented architecture upgrade on top of v0.1
- Added:
  - `MviUiIntent` + `MviMutation` two-lane intent model
  - `SplitIntent`, `MutationReducer`, `SplitIntentReducer`
  - `PulseSplitViewModel` + `UiIntentExecutor` + `UiIntentExecutionScope`
  - sample migration to `send(uiIntent) -> dispatchMutation(mutation)`
- Solved from v0.1:
  - separates "external trigger" from "pure reducer mutation"
  - keeps reducer focused on deterministic state transitions
  - side-effect orchestration moved to UI intent executor lane
  - improves readability and traceability in larger features
- Current limitations:
  - state decomposition toolkit is still missing
  - parent-child feature/store composition is not built yet
  - effect middle layer and unified effect pipeline are not finalized
  - concurrency policy toolkit (`drop/cancel/queue/latest`) is not standardized yet

## v0.1 -> v0.2 Update List

1. Intent model upgrade:
   - from single-lane intent to two-lane (`UiIntent` / `Mutation`)
2. Reducer responsibility tightening:
   - reducer now focuses on mutation-only state transitions in split mode
3. ViewModel execution model upgrade:
   - introduced `PulseSplitViewModel` for UI-triggered side effects and mutation writeback
4. Sample architecture migration:
   - network sample adapted to split lane flow for practical validation
5. Documentation upgrade:
   - README and module docs aligned with the new architecture semantics

## Priority Order

1. State decomposition toolkit
2. Feature/store composition
3. Effect execution middle layer
4. Concurrency and lifecycle policies
5. Debug tooling
6. Test DSL

## Milestones

### 1) State decomposition toolkit

- Goal:
  - Solve state-bloat in complex pages
- Deliverables:
  - sub-state composition model
  - `combineMutationReducer` utilities
  - localized state update helpers
- Acceptance:
  - one complex screen can be split into domain sub-states while keeping reducer tests readable and deterministic

### 2) Feature/store composition

- Goal:
  - Enable parent-child feature orchestration
- Deliverables:
  - parent store -> child `UiIntent`/`Mutation` routing
  - child state mounting into parent state
  - composition conventions for large pages
- Acceptance:
  - multi-feature page no longer requires a single giant reducer

### 3) Effect execution middle layer

- Goal:
  - decouple IO/navigation/analytics from ViewModel orchestration code
- Deliverables:
  - `EffectHandler` abstraction
  - pluggable `EffectPipeline`
  - real/mock execution strategies
- Acceptance:
  - business flow can switch effect execution backend without changing reducer/mutation logic

### 4) Concurrency and lifecycle policies

- Goal:
  - unify repeated-action and lifecycle behavior
- Deliverables:
  - in-flight strategy options (`drop`, `cancel`, `queue`, `latest`)
  - lifecycle-aware state/effect observation policies
- Acceptance:
  - repeated clicks, background/foreground transitions have predictable behavior across modules

### 5) Debug tooling

- Goal:
  - improve observability and troubleshooting speed
- Deliverables:
  - `UiIntent`/`Mutation` timeline
  - state diff inspector
  - performance tracing plugin hooks
- Acceptance:
  - can quickly answer: "which mutation caused this state change?"

### 6) Test DSL

- Goal:
  - reduce test boilerplate and increase readability
- Deliverables:
  - given-when-then style test DSL
  - deterministic timeline assertions
  - state/effect assertion helpers
- Acceptance:
  - business modules can add reducer/store tests with low setup cost

## Iteration Rule

For each milestone:

1. finalize contract/API
2. implement minimal runnable version
3. add module README updates
4. run checks and sample verification
5. stop for review before next milestone
