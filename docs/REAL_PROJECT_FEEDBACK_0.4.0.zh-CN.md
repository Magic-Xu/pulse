# Pulse 0.4.0 真实项目反馈审计

英文版：[REAL_PROJECT_FEEDBACK_0.4.0.md](./REAL_PROJECT_FEEDBACK_0.4.0.md)

本文面向 Pulse 维护者与应用架构负责人，依据 OnePage 对产品边界的定义，审计五个应用接入
Pulse 0.3.0 后反馈的问题。

## 产品边界

输入进入 Pulse 边界后，进程内状态演进由 Pulse 负责：

- 有界、有序的接纳；
- State、Transition 与 replay-zero UiEffect 语义；
- keyed task 的取消、替换与过期结果拒绝；
- Android 生命周期所有权与清理；
- 类型化运行时诊断和可复用一致性测试。

Pulse 边界外的业务语义与编排仍由应用负责：

- 领域模型、Repository、导航、依赖注入与 UI 文案；
- 外部事件应该采样、重试、合并还是持久化；
- 持久操作 ID、恢复状态机、WorkManager 或 Service 策略；
- Feature Store 之间以及 Store 与外部 actor 之间的协调。

只有当问题在多个应用中重复出现、语义不依赖业务知识、不引入第二条运行路径，并能由测试或
兼容性证据验证时，API 才应进入 Pulse。

## 审计结论

| 反馈 | 归属判断 | 0.4.0 动作 |
|---|---|---|
| Split UI 接纳可能阻塞在 executor 队列，而 executor 又等待 mutation | 框架缺陷 | 用一个 admission 预算约束 UI 到 executor 的完整链路，增加类型化溢出与回归测试 |
| 普通 listener 无法调用挂起函数 | 框架适配缺口 | 增加必须处理拒绝结果的 callback ingress；`send` 仍是无丢失背压路径 |
| Split transition 无法用于诊断和测试 | 框架可观测性缺口 | 暴露只读 Split transition，不暴露 Store 或 mutation 权限 |
| 自定义 Android 运行参数可能意外把 Store 移出 Main | 框架配置陷阱 | 增加 Android config overlay，保留非 dispatcher 参数并强制 `Main.immediate` |
| Task 异常被记成 executor 异常，且丢失原始 UI 请求 | 框架诊断缺陷 | 增加独立 task failure，保留 task key、token、request ID 与 input type |
| 纯 JVM 测试工具无法创建真实 Split ViewModel | 框架测试缺口 | 新增独立 Android testing 制品，使用同一个虚拟时间调度器 |
| 新 transition 缺少安全、可复用的日志 | 框架扩展缺口 | 增加默认脱敏的 transition 与 failure 日志；不输出业务值、异常消息或堆栈 |
| 官方示例忽略 admission/task launch 结果并展示原始异常消息 | 框架自有示例缺陷 | 处理所有结果，并把异常映射为领域/UI 安全值 |
| 在 Store task 生命周期内绑定 callback 或 Flow | 合理且重复，但现有原语足够 | 发布 keyed task + `callbackFlow`/`awaitClose`/`conflate` 标准模式，并验证替换与关闭 |
| 从 callback 高频上报进度 | 合理，但策略依赖业务 | 使用 conflated Flow 与 token-bound mutation，不增加无界 progress sink |
| 进程死亡后恢复上传或下载 | 应用/持久运行时职责 | 持久化 operation ID 与领域状态，通过 WorkManager 或 Service 协调，再把观察结果送回 Pulse |
| 协调 root、feature 与 Service-owned Store | 应用架构职责 | 文档化所有权与 external actor 边界，不增加全局 Store bus |
| 完成 typed mutation 迁移 | 应用迁移职责 | 给出退出条件；框架继续提供显式 reducer 与 state decomposition |
| 增加 SourceRegistry、通用 scheduler/recovery API、Service 基类、全局总线、writer DSL/KSP、lint 套件或 dev panel | 0.4.0 不接受 | 这些方案在通用语义证据不足时会引入业务策略或第二条运行路径 |
| 有界非挂起 API 在任何负载下都不丢失、不拒绝 | 合同不可能成立 | 调用方必须选择挂起背压，或显式处理 `Full`/`Rejected` |

## 结论

Pulse 0.4.0 仍是一套单一的状态运行时，而不是应用框架。它强化 0.3.0 已承诺的边界，只在
Kotlin/Android 接口让正确使用变得困难时增加适配器。持久任务、业务重试与多 Feature 协调继续
显式存在于应用状态中，不由框架策略隐藏。
