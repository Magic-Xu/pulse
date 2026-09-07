# Pulse 0.5.0 发布说明

英文版：[RELEASE_NOTES_0.5.0.md](./RELEASE_NOTES_0.5.0.md)

> 状态：**已发布**。七个签名制品与两个公共消费者均通过[发布验证](https://github.com/Magic-Xu/pulse/actions/runs/34136328471)。

## Mutation 结果

`PulseIntentContext.mutateResult` 和 `PulseTaskContext.mutateResult` 区分状态改变 `Changed`、
状态不变 `Unchanged`、业务忽略 `Ignored`、Reducer 失败 `Failed`、生命周期拒绝 `Rejected` 和
过期任务 `StaleTask`。前两者包含 Reducer UI Effect 的提交完成；取消和致命错误继续向上传播。
原有 `mutate(): Boolean` 保留：改变、不变和业务忽略返回 true；失败、拒绝和过期任务返回 false。
业务忽略原因即使恰好为 `late-task-mutation`，也不会再被误判为任务 Token 失效。

## Split 关联与耗时

`PulseSplitStoreViewModel.diagnostics` 通过 `originIntentId` 关联 Executor 完成记录和直接或任务
产生的 Mutation；该 ID 对应 Core UI 输入帧的 Request ID。Mutation 存在 Core 帧时同时提供其
Request ID。`PulseTaskContext.originIntentId` 在任务挂起和替换后仍保留原始关联。
Executor 记录提供准入等待、Executor 等待、执行时长和总耗时。排队阶段被取消的 Executor
没有开始时间，执行时长为零。

该流不重放历史，只用于尽力诊断。最多缓冲 `mailboxCapacity` 条记录，观察者落后时丢弃最旧
记录，绝不等待诊断消费者；序号缺口可用于识别丢失。关闭可能使已准入的 Core 帧尚未进入
Executor 通道就停止被观察，因此可靠完成判断仍使用 `send` 及其结果。诊断只记录类型和
标识，不复制输入内容、任务 Key、忽略原因或异常消息。

`TestPulseSplitHost.diagnosticProbe` 支持快照、等待指定数量和按原始 Intent 筛选，与测试宿主
共享调度器及清理生命周期。

Selector 基准检查会先确认当前分组已被观察，再生成下一组，并限制等待时间；原有命中数和性能阈值保留，不再假设 StateFlow 必须交付每个中间状态。

## 升级与门禁

参见 [0.4 → 0.5 迁移](./MIGRATION_0.4_TO_0.5.zh-CN.md)。七个制品的坐标保持一致。
发布验证已通过 API 检查（基线经过审查）、0.4 七制品冻结源码和 Archive 兼容检查、保留的 0.3/0.2
检查、纯制品消费者、压力与性能检查，以及托管设备测试。全部签名制品和两个公共消费者
均已通过受控发布工作流验证。
