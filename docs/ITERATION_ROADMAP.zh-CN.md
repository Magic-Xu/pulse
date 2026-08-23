# Pulse 迭代路线图

[English](ITERATION_ROADMAP.md)

## 当前版本线

`0.3.0` 是当前已公开的稳定版。 `0.4.0` 仍是发布候选，**尚未公开**。在受保护的
`v0.4.0` 发布和公共消费者验证完成前，应用应继续解析 `0.3.0`。

## 0.3 基础

Pulse 0.3 确立了 0.4 继续遵守的产品边界：

- 一个有界 FIFO Processor 统一拥有状态归约、Transition 发布、Effect 交付、Completion 与
  Close 的顺序；
- Keyed Task 提供显式过载策略，并用不透明 Token 拒绝迟到 Mutation；
- Android 与 Compose 适配器负责生命周期安全的收集和清理，不改变运行时模型；
- State Decomposition、可复用 Store 测试、受控 API 基线与保留的 0.2 兼容证据分别属于独立
  模块或验证门禁，不形成第二条运行时路径。

## 0.4 候选范围

### 端到端 Split 接纳

- 一个有界在途预算覆盖从 UI 接纳到串行 Executor 决策的完整路径。
- 挂起式 `send(UI)` 仍是背压路径；非挂起式 `trySend(UI)` 会报告 `Full` 或生命周期拒绝，
  不承诺不可能同时满足的无损与非阻塞交付。
- `callbackIngress(onRejected)` 为 Listener API 提供显式的过载和生命周期边界。
- 开启报告策略后，Split 过载会成为可观测的 Typed Admission Failure。

### 诊断与 Android 安全

- Split ViewModel 公开只读 Transition Frame，不暴露 Store 或 Mutation 权限。
- Keyed Task Failure 携带任务身份与原始 UI 请求上下文。
- `androidPulseRuntimeConfig(base)` 保留非 Dispatcher 配置，同时强制 Android Main
  Dispatcher。
- 现代 Store 与 Transition Flow 日志能力可复用，且默认脱敏。

### 集成与测试

- 新增 `mvi-platform-android-testing` 制品：用同一个虚拟时间 Scheduler 驱动真实 Split
  ViewModel，并提供确定性 Probe 与清理。
- 官方示例显式处理接纳结果和 Task Launch Result，不把原始异常详情放入 State 或 Effect。
- 维护者指南覆盖外部 Flow/Callback 绑定、进度、持久任务、Store Owner 与 Typed Mutation
  迁移，但不新增第二条运行时路径。

### 明确边界

Pulse 仍是进程内有序状态运行时。领域模型、持久操作恢复、WorkManager 或 Service 策略、
多 Store 编排，以及 Source 的采样或重试策略仍由应用负责。SourceRegistry、通用 Scheduler、
Service 基类、全局 Bus、Writer DSL/KSP、Lint 套件和开发者面板不进入 0.4 候选。

## 候选证据

- 七个公共制品必须具有受控 API/ABI 基线，其中包括三个 Android AAR。
- `compatibility03Check` 会针对暂存的 0.4 制品编译并链接冻结的 0.3 六制品表面，包括可执行
  JVM 替换与 Archive 比较。
- `apiCheck` 控制七个候选基线，其中包括新 Android Testing 制品经评审的首份基线。
- 保留的 0.2 Fixture 会编译冻结的五制品 Kotlin 表面、比较对应 Archive，并针对暂存的 0.4
  制品执行 Core Runtime 链接消费者。
- 两个隔离示例只从暂存 Maven 制品构建；发布后再只从 Maven Central 构建一次。
- 固定 Seed PR 检查、多 Seed 压力、性能下限与托管设备 Instrumentation 仍是相互独立的证据。

## 发布边界

只有以下命令在 JDK 21 的干净 Checkout 中通过，候选才可发布：

```bash
./gradlew clean mviReleaseCheck --stacktrace
./gradlew mviAndroidDeviceCheck --stacktrace
```

远程发布还要求 `POM_VERSION_NAME=0.4.0`、准确的 Annotated Tag `v0.4.0`，以及受保护的
`.github/workflows/publish-maven-central.yml`。Publish Job 必须等待 `release-check` 与
`device-check`，发布同一提交，验证七个公共制品，再运行公共纯制品消费者。普通分支、Snapshot、
RC 或其他 Tag 都不能发布 `0.4.0`。

## 0.4 之后

后续能力必须保持单一有序运行时，并通过跨应用重复证据、与领域无关的语义，以及可执行的兼容或
一致性检查证明其框架归属。
