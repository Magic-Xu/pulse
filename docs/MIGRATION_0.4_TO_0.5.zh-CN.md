# 从 Pulse 0.4 迁移到 0.5

英文版：[MIGRATION_0.4_TO_0.5.md](./MIGRATION_0.4_TO_0.5.md)

> 状态：**候选版**。生产依赖继续使用已经完成公共验证的版本。

所有 Pulse 模块保持同一版本。通过 Magic Android Platform 接入时，升级已验证 Pulse 0.5 的
Platform 版本，不单独覆盖传递依赖。直接接入者在公共验证完成后使用
`mvi-platform-android-compose:0.5.0`，Android 测试使用 `mvi-platform-android-testing:0.5.0`。

现有 Boolean Mutation 调用无需修改。需要区分业务忽略、应用成功或任务失效时使用
`mutateResult`。不要自动重试 `StaleTask`：被替换的任务已经失去修改状态的资格。
精确结果语义见[发布说明](./RELEASE_NOTES_0.5.0.zh-CN.md)。

在有生命周期归属的协程中、发送输入之前收集 `viewModel.diagnostics`。关联时同时使用
`storeId` 和 `originIntentId`，因为 Core ID 只在单个 Store 内有效。序号缺口表示诊断可能丢失。
需要持久统计时在外部保存有界摘要；不要让导航和业务完成依赖此流。现有 `transitions`
及其公开帧、输入类型保持不变。

真实 ViewModel 测试可通过 `host.diagnosticProbe.forIntent(id)` 或 `awaitCount(count)` 检查
诊断。Probe 只能观察实际交付的事件，不能恢复丢失记录。
