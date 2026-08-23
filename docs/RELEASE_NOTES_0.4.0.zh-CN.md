# Pulse 0.4.0 发布说明

English version: [RELEASE_NOTES_0.4.0.md](./RELEASE_NOTES_0.4.0.md)

> 状态：**已于 2026-08-24 发布**，来源为准确的 Annotated Tag `v0.4.0`。
> 七个签名 Maven Central 发布包均已公开，两个隔离纯制品消费者均已通过。

Pulse 0.4 针对真实 Android 项目加固了 0.3 runtime。它继续坚持单一、有序、进程内的状态
runtime，重点完善 Split 端到端接纳、可行动的诊断、确定性的 Android 测试，以及不易误用的
接入路径。

## 重点变化

### Split 端到端接纳

- `PulseSplitStoreViewModel` 现在用同一个有界在途预算覆盖从 UI 接纳到串行 executor 决策的
  完整路径。executor 等待 mutation 时，不再会与另一个已满的 executor 队列形成背压环路。
- 挂起式 `send(UI)` 仍是进程内无损路径：它先等待容量，再等待串行 executor 的决策。
- 非挂起 `trySend(UI)` 会在整个 Split 流水线达到容量时返回 `EnqueueResult.Full`，即使 core
  mailbox 本身尚未满。使用 `REJECT_AND_REPORT` 时，runtime 还会报告
  `PulseFailure.SplitAdmissionOverflow`。
- `callbackIngress(onRejected)` 用于接入 listener 风格 API。拒绝处理器是必填项，会收到每个
  `Full` 或生命周期 `Rejected` 结果；已接纳的回调仍按接纳顺序处理。

有界且非挂起的 API 不可能同时保证“不阻塞”和“绝不拒绝”。应用必须在挂起背压与明确的回调
过载策略之间作出选择。

### 只读诊断与更安全的 Android 配置

- Split ViewModel 通过 `Flow<TransitionFrame<S, PulseSplitInput<UI, M>, E>>` 暴露
  `transitions`。消费方可以查看 `Ui.value` 与 `Mutation.value`；这个只读表面不提供再次提交
  这些 input、取得 backing Store 或获得 mutation 权限的能力。
- `androidPulseRuntimeConfig(base)` 保留 mailbox、overflow、effect buffer、clock、failure、
  strict mode、redaction 与 Store identity 配置，同时将 Store 和 consumer 工作切换到
  `Dispatchers.Main.immediate`。无参数 Android 默认配置保持不变。
- keyed task 抛出的异常现在报告为 `PulseFailure.TaskFailure` 和 `FailurePhase.TASK`，并包含
  task key、token；从 `PulseIntentContext` 启动时，还会保留原始 UI intent 的 request ID 与
  input type。
- 如果底层 Store 独立终止——例如终止性的诊断 handler 失败——Split adapter 现在会汇合到同一个
  ViewModel 清理屏障，不再遗留 executor 或 transition job。

### 真实 Split ViewModel 测试

新增的 `mvi-platform-android-testing` 与纯 JVM 的 `mvi-testing` 相互补充。它用同一个虚拟时间
scheduler 驱动 Android Main、Pulse runtime 和显式 execution owner，并运行真实的
`PulseSplitStoreViewModel`。

`runPulseSplitTest` 与 `splitHost` 提供 state、transition、UI effect 和 typed failure probe，
并负责确定性清理。`sendAndDrain` 等待目标 intent 的 executor 决策，以及该 executor 直接
等待的 mutation；它有意不等待 keyed task、ticker 或无限 source。由于 Android Main 是进程
全局状态，调用会串行执行；嵌套 `runPulseSplitTest` 会在替换外层 Main dispatcher 前被拒绝。

### 安全日志与修正后的示例

- `mvi-extensions` 新增现代 Store 的 transition/failure 日志插件 `PulseLoggingPlugin`，以及
  适用于 Split ViewModel 等只读 facade 的
  `Flow<TransitionFrame<...>>.logPulseTransitions`。
- 日志默认使用 `TypeOnlyPulseRedactor`。默认不会暴露应用值、诊断上下文字符串、异常 message
  或 stack trace。
- Android 示例现在会显式处理 callback 接纳结果与 task launch 结果，准确表达 `Completed`
  语义，并将异常映射为 typed domain/UI 值，而不是把原始异常 message 放入 State 或 effect。

## 执行结果语义

`PulseIntentExecutionResult.Completed` 表示串行 executor 返回了
`PulseIntentExecutionDecision.Completed`。如果 executor 接纳了 keyed task，那么 `send`
返回时 task 仍可能在运行。只有进程内调用方确实需要 task 终态时，才观察
`TaskLaunchResult.Accepted.handle.awaitOutcome()`；用户可见或可恢复的业务完成状态应建模到
typed State 或持久化 operation 状态中。

`EnqueueResult.Enqueued` 的含义更窄：它只确认已接纳，不表示 reduce、executor 或后台 task
已经完成。

## 兼容性

Pulse 0.4 是对 0.3 公共 API 的加法演进。现有 0.3 Store、Split ViewModel、Compose、扩展与
测试接入不需要全应用重写；保留的 0.2 兼容层继续可用。

需要重新测试假设 `trySend` 只反映 core mailbox 的代码。现在 Split 接纳覆盖完整的
UI-to-executor 路径，因此可能更早、更准确地报告过载。对 `PulseFailure` 或 `FailurePhase`
做穷举处理的代码也需要加入 `TaskFailure`、`TASK`、`SplitAdmissionOverflow` 与 `ADMISSION`。

参见[从 Pulse 0.3 迁移到 0.4](./MIGRATION_0.3_TO_0.4.zh-CN.md)。

## 已发布 Artifact 集合

本次发布包含 `io.github.magic-xu` 下的七个 Artifact：

- `mvi-core-contract`
- `mvi-core-runtime`
- `mvi-platform-android`
- `mvi-platform-android-compose`
- `mvi-platform-android-testing` — 0.4 新增
- `mvi-extensions`
- `mvi-testing`

同一项目中的所有 Pulse 模块应保持为 `0.4.0`。

## 明确边界

Pulse keyed task 仍然只在进程内有效。必须跨进程死亡继续的工作应由应用自己的 durable
protocol 负责：持久化 operation ID 与 domain 状态，通过 WorkManager 或 Service 执行，启动时
reconcile，再通过 typed mutation 将持久化记录投影到 Pulse。不要持久化 `Job`、`TaskToken`、
待发送的 `UiEffect` 或 task handle outcome。

Pulse 0.4 不新增 SourceRegistry、通用 scheduler/recovery API、无界 progress sink、Service
基类、全局 Store bus、writer DSL/KSP、lint 套件或开发者面板。source delivery 策略、持久化
重试/恢复、domain model 与 Store owner 之间的协调仍由应用负责。参见
[Pulse 0.4 接入模式](./INTEGRATION_PATTERNS.zh-CN.md)。
