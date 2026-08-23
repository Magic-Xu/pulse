# Pulse 0.4 接入模式

[English](INTEGRATION_PATTERNS.md)

本文面向需要把 Pulse 接入 callback API、外部数据流、持久任务和多状态 Owner 的应用架构师。
Pulse 负责进程内 State 演进的有序性；数据源投递策略、持久调度、领域持久化和跨 Owner 协调仍由
应用负责。

## 把外部 Flow 绑定为 keyed task

在 Data Source 边界把 callback API 转成 `Flow`。注册与注销必须通过 `callbackFlow` 和
`awaitClose` 成对出现；`trySend` 失败必须进入应用自己的丢失处理或重新同步策略，不能静默消失。

```kotlin
fun DeviceSource.events(
    onUndelivered: (DeviceEvent) -> Unit,
): Flow<DeviceEvent> = callbackFlow {
    val listener = DeviceListener { event ->
        if (trySend(event).isFailure) onUndelivered(event)
    }
    addListener(listener)
    awaitClose { removeListener(listener) }
}
```

应从 `PulseIntentContext.launchTask` 启动收集，不要在 ViewModel 中另存应用自建的 Scope。稳定的
`TaskKey` 让替换与关闭只有一个 Owner；task 内的 mutation 会自动绑定 task token。

```kotlin
private val OBSERVE_DEVICE = TaskKey("screen.observe-device")

val executor = PulseUiIntentExecutor<ScreenState, ScreenUiIntent, ScreenMutation> {
        intent, context ->
    when (intent) {
        ScreenUiIntent.StartObserving -> {
            val admission = context.launchTask(OBSERVE_DEVICE, TaskPolicy.Latest) {
                deviceSource.events(onUndelivered = sourceLossHandler)
                    .conflate()
                    .collect { event ->
                        mutate(ScreenMutation.DeviceChanged(event))
                    }
            }
            when (admission) {
                is TaskLaunchResult.Accepted -> PulseIntentExecutionDecision.Completed
                else -> PulseIntentExecutionDecision.Ignored("device-observer-not-admitted")
            }
        }
    }
}
```

只有当事件是可替换的快照、业务只关心最新值时才使用 `conflate()`。如果每个事件都是命令或账本
记录，不要合并：在 Source Adapter 中明确选择有界缓冲、溢出动作和权威数据重同步策略。Pulse
不会把无法挂起的 callback 自动变成无损数据源。

再次发送 `StartObserving` 时，`TaskPolicy.Latest` 会取消上一代任务；取消会到达 `awaitClose` 并
注销旧 listener。关闭 `PulseSplitStoreViewModel` 也会通过同一路径取消 task。

## 把进度建模成绑定 token 的快照

进度通常是可替换 State，因此应把 callback 转为 Flow，合并中间值，并从 keyed task 提交
mutation：

```kotlin
private val UPLOAD = TaskKey("upload.observe")

context.launchTask(UPLOAD, TaskPolicy.Latest) {
    uploadSource.progress(operationId, onUndelivered = progressLossHandler)
        .conflate()
        .collect { percent ->
            mutate(UploadMutation.Progress(operationId, percent))
        }
}
```

`PulseTaskContext.mutate` 会自动携带当前 task token，因此被替换或取消的一代不能提交迟到更新。
当结果可能跨进程、Service 或持久操作边界时，mutation 仍应携带 `operationId`。完成和失败应是明确
的领域状态；不要从最后一个进度百分比推断完成或失败，也不要把异常消息直接放进 UI State。

## 把持久任务留在 Pulse Runtime 之外

Keyed task 与 task token 只在当前进程有效。需要跨进程死亡继续执行的任务必须使用应用自己的持久
协议：

1. 先分配 `operationId`，并持久化领域命令与状态，再开始执行。
2. 由应用拥有的 WorkManager Worker 或 Service 幂等执行，并把状态写回同一条持久记录。
3. 启动时扫描未终止记录，再按应用业务策略调度、恢复或判定失败。
4. Pulse 通过 keyed-Flow 模式观察持久记录，并映射为展示层的 typed mutation。

应用命令层可以暴露调度端口供 executor 调用，但 Pulse 本身不负责 enqueue Worker、启动 Service，也
不定义重试与恢复策略。数据库或其他持久模型才是操作真相源；Pulse State 是它的进程内投影。不要把
`Job`、`TaskToken`、待处理 `UiEffect` 或 task handle 的结果当作恢复状态持久化。

## 让每个 Store 只有一个生命周期 Owner

| Owner | 适合拥有的状态 | 生命周期与边界 |
| --- | --- | --- |
| Root/App Owner | 会话与全局协调状态 | 由 Application 或根导航作用域拥有；Feature 通过领域端口或 Repository 观察通信，不获得 mutation 权限 |
| Feature Owner | 页面或导航 Feature 状态 | 优先使用 `PulseSplitStoreViewModel`，并绑定到 Feature 明确的 `ViewModelStoreOwner` |
| External Actor | Service 内部或连接器状态机 | Service 可以拥有 `DefaultPulseStore`，通过自己的 API 接收命令，并在自身清理阶段调用 `close()`、观察 `awaitClosed()` |

不要增加全局 Store Bus。Feature 不应发送另一个 Store 的 mutation，Root Store 也不应吞并子页面的
瞬时 UI State。共享的持久事实通过 Repository 传播，协调动作使用 typed domain command。
`StateFlow`、task handle 与 Store 引用都只在进程内有效；跨进程 Actor 必须通过持久化或显式 IPC
通信。

## 明确选择 callback 入口

协程中的默认入口是 `PulseSplitStoreViewModel.send(intent)`：它提供有界背压，并返回 executor
决策。无法挂起的 listener 必须使用 `trySend` 或 `callbackIngress`：

```kotlin
val ingress = viewModel.callbackIngress { intent, result ->
    callbackRejectionPolicy.handle(intent, result)
}

listener.onEvent { event ->
    ingress.submit(ScreenUiIntent.ExternalEvent(event))
}
```

`trySend` 与 `PulseCallbackIngress.submit` 都返回 `EnqueueResult.Enqueued`、`Full` 或 `Rejected`。
callback ingress 的拒绝处理器是必填项，并会接收每个未入队结果。应用策略可以请求权威快照、合并
可替换事件、停止已经注销的数据源，或暴露过载；对于 Pulse 未接纳的输入，不能伪装成成功。有界且
非挂起的入口不可能在过载时保证全部接纳。

## 把 `Completed` 理解为 executor 决策

`PulseIntentExecutionResult.Completed` 表示 serial executor 已为该 UI Intent 返回
`PulseIntentExecutionDecision.Completed`。如果 executor 接纳了 keyed task，`send` 返回时 task
仍可能运行。`trySend` 返回 `Enqueued` 的语义更窄：它只表示接纳，不代表 executor 或 task 完成。

只有调用方确实需要进程内 task 结果时，才使用
`TaskLaunchResult.Accepted.handle.awaitOutcome()`。用户可见或需要持久化的业务完成状态应放在 typed
State 或以 `operationId` 为键的持久记录中；不要为了让 `send` 代表后台操作而长期占住 executor
通道。

## Typed Mutation 迁移完成标准

只有同时满足以下条件，Feature 才算完成 Split Intent 迁移：

- UI 与外部调用方只能提交 `MviUiIntent`，无法取得 mutation sender，也没有 UI/mutation 混合输入
  通道。
- 异步与 callback 结果统一通过 `PulseIntentContext` 或 `PulseTaskContext.mutate` 进入，使任务替换、
  关闭和迟到结果拒绝使用同一套语义。
- Mutation Reducer 是穷尽的，每个分支都有意返回 `Changed`、`Unchanged` 或 `Ignored`；不存在静默
  丢弃未知 mutation 的 catch-all。
- 业务失败与状态使用 typed domain value；原始异常消息、`Job`、callback 和可变集合不进入 State。
- 测试覆盖公开 UI 输入路径、task 替换或取消、迟到 mutation 拒绝、callback 过载策略和 Reducer
  行为。
- 所有调用方迁移完成后，删除旧的混合输入 Adapter 或公开 mutation writer；同时保留两条可写路径
  不算迁移完成。
