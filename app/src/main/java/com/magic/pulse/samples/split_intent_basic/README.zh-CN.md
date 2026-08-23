# Split Intent 基础示例

这是 Pulse 推荐的第一个 v0.4 Split Intent 上手示例。

## 目标

- 展示最小可用路径
- 直接使用 `PulseSplitStoreViewModel`，不使用 `typealias`
- 直接实现 `PulseMutationReducer`，不使用路由 DSL
- 显式声明一个 `DropWhileRunning` TaskKey
- 通过 callback ingress 让 UI 显式处理 `Full` 和生命周期拒绝

## 你需要掌握

1. 定义 `State / UiIntent / Mutation / Effect`
2. 实现一个 `PulseMutationReducer`
3. 直接创建 `PulseSplitStoreViewModel`
4. 从 `PulseIntentContext` 启动任务，并只在 Task Context 中提交 Mutation
5. 穷举处理 `TaskLaunchResult`，并把应用失败映射为类型化错误

取消会原样抛出，不会映射成失败 Mutation。需要丢弃重叠请求时，可从本示例开始。

`PulseIntentExecutionDecision.Completed` 只表示串行 executor 已处理完 UI intent。对加载 intent，
它确认 task 已被接纳，但不表示后台 task 已完成。本示例通过 Mutation 表达任务进度和应用层终态；
如果调用方必须等待 `TaskOutcome`，应显式持有并等待 `Accepted` 返回的 task handle。
