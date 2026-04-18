# Release Plan (v0.2 Split-Intent Baseline)

Chinese version: [RELEASE_PLAN.zh-CN.md](https://github.com/Magic-Xu/pulse/blob/master/docs/RELEASE_PLAN.zh-CN.md)

Current positioning: **split-intent architecture baseline + Android adapter + foundational extensions**.

## Module Scope

1. `mvi-core-contract`
   - Contract definitions only
2. `mvi-core-runtime`
   - Default Store runtime
3. `mvi-platform-android`
   - Android ViewModel adapter layer (no Compose dependency)
4. `mvi-platform-android-compose`
   - Compose binding layer
5. `mvi-extensions`
   - Logging and state transition tracking plugins

## Versioning Strategy

- Use `0.x.y` in early stage
- `x` (minor): feature additions
- `y` (patch): bug fixes and documentation updates

## Pre-release Checklist

```bash
./gradlew verifyMavenCentralConfig
./gradlew mviCoreCheck
./gradlew mviFrameworkCheck
```

If Android repositories are reachable, also run:

```bash
./gradlew :app:assembleDebug
```

## Compatibility Guidelines

- Treat `mvi-core-contract` changes cautiously (highest blast radius)
- Prefer new modules/plugins for new capabilities; avoid bloating `DefaultStore`
- Keep Android-specific features in `mvi-platform-android`, not in core modules
