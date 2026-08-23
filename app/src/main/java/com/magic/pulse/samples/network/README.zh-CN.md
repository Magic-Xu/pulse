# 网络请求示例（分层）

这是 v0.4 的 Repository 分层示例，不是一套完整生产架构。它只展示 Pulse 的接入边界；网络、
重试、缓存和领域错误策略仍由应用负责。

## 目标

- 保持业务分层清晰（`Repository / DataSource / Service`）
- 保持双通道 intent 流程显式
- 用一个 `Latest` TaskKey 明确请求替换语义

## 特点

- 使用 `PulseSplitStoreViewModel` + `PulseUiIntentExecutor`
- 请求替换后由 Token 阻止迟到 Mutation
- 不依赖 reducer DSL
- `typealias` 仅用于减少泛型噪音，可选
- callback admission 和 task admission 都会被显式处理
- Exception 会先映射为领域错误枚举，不会把 message 传入 State 或 UiEffect

Repository 驱动、且新请求应替换旧请求的页面可使用这种组织方式。
`PulseIntentExecutionDecision.Completed` 只确认 executor 已处理 intent 且 task 已被接纳；task 的
完成由后续 Mutation 表达，不由这个 decision 表示。
