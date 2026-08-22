# Split Intent 基础示例

这是 Pulse 推荐的第一个 v0.3 Split Intent 上手示例。

## 目标

- 展示最小可用路径
- 直接使用 `PulseSplitStoreViewModel`，不使用 `typealias`
- 直接实现 `PulseMutationReducer`，不使用路由 DSL
- 显式声明一个 `DropWhileRunning` TaskKey

## 你需要掌握

1. 定义 `State / UiIntent / Mutation / Effect`
2. 实现一个 `PulseMutationReducer`
3. 直接创建 `PulseSplitStoreViewModel`
4. 从 `PulseIntentContext` 启动任务，并只在 Task Context 中提交 Mutation

取消会原样抛出，不会映射成失败 Mutation。需要丢弃重叠请求时，可从本示例开始。
