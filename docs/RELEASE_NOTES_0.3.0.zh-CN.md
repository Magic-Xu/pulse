# Pulse 0.3.0 发布说明

英文版：[RELEASE_NOTES_0.3.0.md](./RELEASE_NOTES_0.3.0.md)

> 状态：**尚未发布**。仓库当前候选版本为 `0.3.0-SNAPSHOT`。只有正式发布公告出现后，稳定版
> `0.3.0` 制品才可使用。

Pulse 0.3 为新的协程 API 和保留的 0.2 兼容 API 提供同一套有序运行时。本版本重点解决确定的
输入顺序、显式 reducer outcome、可诊断的 UI-effect 交付、生命周期所有权和可复用的一致性测试。

## 重点变化

### 有序 Store 运行时

- 新增 `PulseStore` 和 `DefaultPulseStore`，公开 `StateFlow` 状态、transition frame、replay-zero
  UI effect、按键任务、`send`、`trySend` 和有序关闭屏障。
- 一个有界 FIFO mailbox 串行处理已接收输入和生命周期控制。并发与重入 send 不会递归进入
  reducer。
- 每个已处理输入都会产生不可变 `TransitionFrame`，包含 request、sequence、revision、状态、
  outcome、effect、耗时和失败关联信息。
- `ReduceOutcome.Changed`、`Unchanged` 和 `Ignored` 让无状态变化和不适用输入也可观察；相等的
  候选状态会归一化为 `Unchanged`。

### 失败与交付契约

- `PulseFailure` 通过脱敏上下文报告 reducer、consumer、plugin、executor、overflow、未交付
  effect、过期 mutation，以及状态 restore/save 等类型化失败。
- 一个受控 consumer 或 plugin 失败不会阻断其他 consumer 或 mailbox processor。
- 取消和 JVM 致命错误不会转换成 Pulse failure。
- `UiEffect` 只表示一次性前台指令。交付 replay 为 0，同一时刻只有一个活动协调者；未交付的
  envelope 会被报告，而不是静默保留。

### 按键异步工作

- 运行时管理的 task 支持 `Latest`、`DropWhileRunning`、`Queue`、`Parallel` 和 `Conflate`。
- task token 会阻止已替换、已取消或已关闭的工作继续发送过期 mutation。
- task 只存在于当前进程。持久操作仍需要持久状态、操作 ID，以及持久化或外部调度器。

### Android 与 Compose

- `PulseSplitStoreViewModel` 只向 UI 暴露 UI 输入，mutation 权限保留在 executor 和绑定 token 的
  task context 内。
- ViewModel 获取支持显式 owner、稳定 key、`CreationExtras` 和可选 `SavedStateHandle` adapter。
- `PulseStateHost` 只暴露只读 state 和 UI-effect 接口。
- Compose 新增生命周期感知的整状态/selector 收集和 `ObserveUiEffects`；owner 必须显式传入，
  默认在 `STARTED` 时收集。
- ViewModel 关闭是 final 且幂等的。saved-state 集成只恢复已提交状态，不恢复 task、effect 或待处理
  运行时对象。

### Extensions 与 Testing

- `mvi-extensions` 新增不要求 marker 的 `StateLens` 组合与显式 outcome 的
  `pulseMutationReducer` DSL，同时保留旧辅助能力。
- 新模块 `mvi-testing` 提供虚拟时间运行时配置、带 probe 的 Store、state/transition/effect/failure
  probe、并发 send 辅助能力和可复用 `PulseStoreTck`。

## 发布制品

稳定版会在 `io.github.magic-xu` group 下发布六个制品：

- `mvi-core-contract`
- `mvi-core-runtime`
- `mvi-platform-android`
- `mvi-platform-android-compose`
- `mvi-extensions`
- `mvi-testing`

前五个坐标从 0.2 保留，`mvi-testing` 是 0.3 新增制品。不要在这些模块之间混用版本。

## 兼容性

0.2 的公开类型和制品坐标会作为兼容面继续保留。旧 `DefaultStore`、回调 API、ViewModel 适配器
和 Compose Store 绑定运行在有序引擎之上，可以按功能逐步迁移。

依赖回调时机的行为需要重新测试。0.3 明确定义 FIFO 重入、回调隔离、取消处理和有序 close
截止点；这些更严格的规则可能改变时机，但不会改变已接受的 API 调用。

详情参见[兼容性政策](./COMPATIBILITY.zh-CN.md)和
[从 Pulse 0.2 迁移到 0.3](./MIGRATION_0.2_TO_0.3.zh-CN.md)。

## 平台基线

- Java 字节码目标：11
- Android `minSdk`：23
- Android 编译 API：36.1
- 发布与 CI JDK：21

传递依赖的实际版本记录在各制品的 Gradle metadata 和 POM 中。

## 发布准入

稳定版 `0.3.0` 只会在候选版本通过以下检查后发布：

- 六模块公开 API 检查与五制品 0.2 源码/二进制兼容 fixture；
- core、Android、Compose 和示例应用的测试、lint 与构建；
- 只消费暂存 Maven 制品的隔离消费者构建；
- 多 seed 的 10,000 输入压力检查；
- 可移植的吞吐、延迟、内存和有界 mailbox 性能下限；
- 发布包、metadata、版本、tag 和 Maven Central 配置检查。

准确门禁参见[发布规划](./RELEASE_PLAN.zh-CN.md)。在这些门禁通过并发布稳定 tag 前，本文描述的是
计划发布内容，不代表制品已经可用。
