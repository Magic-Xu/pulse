# 网络请求示例（Standard）

这是推荐的生产级常用路径。

## 目标

- 保持业务分层清晰（`Repository / DataSource / Service`）
- 保持双通道 intent 流程显式
- 用一个 `Latest` TaskKey 明确请求替换语义

## 特点

- 使用 `PulseSplitStoreViewModel` + `PulseUiIntentExecutor`
- 请求替换后由 Token 阻止迟到 Mutation
- 不依赖 reducer DSL
- `typealias` 仅用于减少泛型噪音，可选

Repository 驱动、且新请求应替换旧请求的页面可使用这种组织方式。
