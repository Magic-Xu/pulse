# Consumer Guide

Chinese version: [CONSUMER_GUIDE.zh-CN.md](https://github.com/Magic-Xu/pulse/blob/master/docs/CONSUMER_GUIDE.zh-CN.md)

Integration guide for projects consuming Pulse.

## Minimal Dependencies (Android)

### Non-Compose projects

```kotlin
dependencies {
    implementation(project(":mvi-platform-android"))
}
```

### Compose projects (recommended)

```kotlin
dependencies {
    implementation(project(":mvi-platform-android-compose"))
}
```

Notes:

- `mvi-platform-android` re-exports `mvi-core-runtime` and `mvi-core-contract`
- `mvi-platform-android-compose` re-exports `mvi-platform-android`
- Business code can use core types directly (`MviState` / `MviIntent` / `MviEffect`)
- No extra core dependency declarations are required in feature modules

## Optional Extensions

If you need logging or state transition tracking plugins:

```kotlin
dependencies {
    implementation(project(":mvi-extensions"))
}
```

## Recommended Adoption Flow

1. Define `State` / `Intent` / `Effect`
2. Implement `Reducer` (or `MutationReducer` in split mode)
3. Use `PulseViewModel` as base
4. For complex features, prefer `PulseSplitViewModel` (`UiIntent` -> side effects -> `Mutation`)
5. In Compose, use `collectStateAsState()` and `observeEffects()`
6. Add `mvi-extensions` only when needed
