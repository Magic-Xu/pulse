# Release Plan (v0 Minimal)

当前版本定位：**极简可用内核 + Android 适配 + 基础插件扩展**。

## 模块清单

1. `mvi-core-contract`
   - 仅契约定义
2. `mvi-core-runtime`
   - 默认 Store 运行时
3. `mvi-platform-android`
   - Android ViewModel + Compose 绑定
4. `mvi-extensions`
   - 日志插件、状态迁移插件

## 版本策略建议

- 早期阶段建议使用 `0.x.y`
- `x`（minor）用于功能新增
- `y`（patch）用于 bugfix 或文档修订

## 发布前检查

```bash
./gradlew verifyMavenCentralConfig
./gradlew mviCoreCheck
./gradlew mviFrameworkCheck
```

如果环境可访问 Android 仓库，补充：

```bash
./gradlew :app:assembleDebug
```

## 向后兼容建议

- `mvi-core-contract` 的接口变更优先谨慎（影响最大）
- 新能力优先走插件或新模块，不要直接膨胀 `DefaultStore`
- Android 特性始终放在 `mvi-platform-android`，避免侵入 core
