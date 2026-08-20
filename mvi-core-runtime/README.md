# mvi-core-runtime

Pulse 的平台无关协程运行时，提供有序 Store、UI effect 协调、任务策略和类型化失败报告。

> 当前版本为 `0.3.0-SNAPSHOT`，仍在开发中，尚未发布到 Maven Central。

## 依赖

```kotlin
dependencies {
    implementation(project(":mvi-core-runtime"))
}
```

本模块通过 `api` 公开传递 `mvi-core-contract` 和协程核心类型。

## v0.3 Store

`DefaultPulseStore<S, I, E>` 实现 `PulseStore<S, I, E>`，主要语义如下：

- 所有已接纳输入按单一全序规约：`requestId` 标识请求，`sequenceId` 逐 frame 递增，`stateRevision` 只在状态实际变化时递增。
- `state` 是包含原子初始快照的 `StateFlow`；相等的新状态会归一化为 `Unchanged`。
- `transitions` 发布完成后的 `TransitionFrame`。
- `effects` 是 replay-zero 的 `UiEffectStream`，同一时刻只允许一个活跃协调者；没有协调者时不会缓存等待重放。
- `send` 等待规约、提交、frame 和 effect 发布完成，但不等待外部 Flow 消费者完成。
- `trySend` 不挂起；邮箱满时返回 `EnqueueResult.Full`。`MailboxOverflowPolicy.REJECT_AND_REPORT`
  额外发布合并后的类型化诊断，`REJECT` 仅依赖调用方可见结果。
- `close()` 建立新的输入截止线，`awaitClosed()` 等待截止线前已接纳输入处理完毕。
- `tasks` 提供 `Latest`、`DropWhileRunning`、`Queue(capacity)`、
  `Parallel(maxConcurrency)` 和 `Conflate` 五种 keyed task 策略，以及 `cancel`/`cancelAll`。

```kotlin
val store = DefaultPulseStore(
    initialState = CounterState(0),
    reducer = counterReducer,
    config = PulseRuntimeConfig(storeId = "counter"),
)

val result = store.send(CounterIntent.Increment)
store.close()
store.awaitClosed()
```

## 失败与诊断

`PulseRuntimeConfig` 控制邮箱容量、溢出策略、effect 缓冲、dispatcher、时钟、脱敏器和
`PulseErrorHandler`。受控边界内的普通异常会上报为 `PulseFailure`，并携带请求、序列、阶段、
输入类型、线程和 Store 关联信息；它不会阻断后续输入或其他消费者。
`CancellationException` 保持取消语义。

`PulseStorePlugin` 通过 `onTransition` 和 `onFailure` 观察完成 frame 与失败。插件自身的普通异常会被隔离并上报。

## v0.2 兼容

`DefaultStore`、callback observer 和 `StorePlugin` 继续保留。它们是同一 v0.3 `PulseEngine` 上的兼容外观，不维护第二套 reducer 顺序或生命周期实现。

## 验证

```bash
./gradlew :mvi-core-runtime:check
```
