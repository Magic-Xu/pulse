# 从 Pulse 0.3 迁移到 0.4

English version: [MIGRATION_0.3_TO_0.4.md](./MIGRATION_0.3_TO_0.4.md)

> 状态：**发布候选版，尚未公开发布**。下文中的 `0.4.0` 坐标只有在发布验证完成并更新本状态后
> 才可使用。

Pulse 0.4 是对 0.3 的加法演进。现有 feature 可以在不改变 reducer 或 ViewModel 架构的前提下
升级，再按具体接入问题采用新的诊断、callback adapter 与 Android 测试宿主。

## 1. 公开发布后对齐依赖

升级应用已经使用的最高层 Pulse 入口，只添加确实需要的可选能力：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-platform-android-compose:0.4.0")

    // 可选生产扩展。
    implementation("io.github.magic-xu:mvi-extensions:0.4.0")

    // 可选：在 Android test module 中测试真实 PulseSplitStoreViewModel。
    testImplementation("io.github.magic-xu:mvi-platform-android-testing:0.4.0")
}
```

不需要 Compose 时使用 `mvi-platform-android`；纯 Kotlin/JVM 使用 `mvi-core-runtime`。
`mvi-platform-android-testing` 会传递暴露 `mvi-testing`，纯 JVM Store 测试仍可直接依赖
`mvi-testing`。同一项目中的所有 Pulse 模块应保持在 `0.4.0`。

## 2. 为 Split input 选择正确入口

在 coroutine 中优先使用挂起式 `send(UI)`。它提供有界背压，并在串行 executor 作出决策后
返回：

```kotlin
when (val result = viewModel.send(ScreenUiIntent.Refresh)) {
    PulseIntentExecutionResult.Completed -> Unit
    is PulseIntentExecutionResult.Ignored -> handleIgnored(result.reason)
    is PulseIntentExecutionResult.Failed -> handleExecutorFailure(result.cause)
    PulseIntentExecutionResult.Cancelled -> Unit
    is PulseIntentExecutionResult.Rejected -> handleClosed(result.reason)
}
```

调用方在等待容量时被取消，UI intent 不会被接纳。接纳后的取消在 frame 转交 executor 前仍可
抑制执行；转交完成后，已接纳工作由 runtime 持有，并在 executor 清理阶段释放容量。

无法挂起的 listener 应创建一个 callback ingress，并提供过载/生命周期策略：

```kotlin
val ingress = viewModel.callbackIngress { intent, result ->
    callbackRejectionPolicy.handle(intent, result)
}

listener.onEvent { event ->
    ingress.submit(ScreenUiIntent.ExternalEvent(event))
}
```

`PulseCallbackIngress.submit` 返回即时 `EnqueueResult`，并对每个非 `Enqueued` 结果调用必填的
handler。仍可直接使用 `trySend`，但调用方必须自行处理 `Full` 与 `Rejected`。

0.4 的 Split capacity 覆盖从已接纳 UI 到 executor 的完整路径。原先假设“core mailbox offer
成功就表示 downstream executor 有容量”的代码必须改为使用返回结果。不要添加无界队列来隐藏
`Full`：需要背压时使用 `send`；否则由应用明确决定 event conflate、重新同步或丢失报告策略。

## 3. 在不暴露 mutation 权限的前提下观察 Split transition

`PulseSplitStoreViewModel.transitions` 现在是公开只读 Flow：

```kotlin
viewModel.transitions.collect { frame ->
    when (val input = frame.input) {
        is PulseSplitInput.Ui -> observeUiInput(frame.requestId, input.value)
        is PulseSplitInput.Mutation -> observeMutation(frame.requestId, input.value)
    }
}
```

公开的 `PulseSplitInput` 观察值只包含 UI 或 Mutation 值；live runtime input 及其 completion、
token 与 admission metadata 仍为 internal。这个 Flow 只用于诊断和测试，不能成为第二条
command 路径。它不会暴露 Store 或 mutation sender；collector 也不应根据 transition 发布来
推断 executor 或 task 已完成。

## 4. 将 Android 默认 dispatcher 覆盖到自定义 runtime 选项

生产环境从自定义 `PulseRuntimeConfig` 构造 Split ViewModel 时，应应用 Android overlay：

```kotlin
val baseConfig = PulseRuntimeConfig(
    mailboxCapacity = 128,
    errorHandler = applicationFailureHandler,
    storeId = "checkout",
)

val viewModel = PulseSplitStoreViewModel(
    initialState = initialState,
    mutationReducer = reducer,
    uiIntentExecutor = executor,
    runtimeConfig = androidPulseRuntimeConfig(baseConfig),
)
```

overlay 会保留所有非 dispatcher 选项，并为 Store 与 consumer 工作应用
`Dispatchers.Main.immediate`。没有自定义选项时继续使用无参数
`androidPulseRuntimeConfig()`。有意控制 dispatcher 的测试应使用 testing factory，而不是应用
生产 overlay。

## 5. 处理带关联信息的 task failure

keyed task 异常不再归类为 executor failure。需要为新的 phase 与 failure 类型更新穷举处理：

```kotlin
val applicationFailureHandler = PulseErrorHandler { storeId, failure, redactor ->
    if (failure is PulseFailure.TaskFailure) {
        reportTaskFailure(
            storeId = storeId,
            taskKey = failure.taskKey,
            token = failure.token,
            requestId = failure.context.requestId,
            inputType = failure.context.inputType,
            causeType = failure.cause::class.qualifiedName,
        )
    } else {
        reportPulseFailure(storeId, failure, redactor)
    }
}
```

同时把 `FailurePhase.TASK`、`FailurePhase.ADMISSION`、
`PulseFailure.SplitAdmissionOverflow` 与 `PulseFailure.TaskFailure` 加入穷举映射。取消仍然是
取消，不会变成 `TaskFailure`；fatal JVM error 仍会继续抛出。

不要把原始异常 message 写入 State 或 `UiEffect`。将预期失败映射为 typed domain status 或 UI
resource ID，把诊断细节保留在配置好的脱敏 failure boundary 后面。

## 6. 测试真实 Split ViewModel

当测试跨越 UI 接纳、串行 executor、mutation、effect 或 Android ownership 时，使用
`mvi-platform-android-testing`：

```kotlin
@Test
fun increment() = runPulseSplitTest {
    val host = splitHost(
        initialState = CounterState(),
        mutationReducer = CounterReducer,
        uiIntentExecutor = CounterExecutor,
    )

    assertEquals(
        PulseIntentExecutionResult.Completed,
        host.sendAndDrain(CounterUiIntent.Increment),
    )
    assertEquals(CounterState(count = 1), host.stateProbe.latest())
    assertTrue(host.transitionProbe.snapshot().isNotEmpty())
    host.failureProbe.assertEmpty()
}
```

host 还会暴露 `effectProbe` 与 `viewModel`；需要测试应用自己的 ViewModel 子类时，也可以传入
应用 factory。Android Main、runtime 与 execution owner 共用一个
`TestCoroutineScheduler`；`runPulseSplitTest` 结束时会自动关闭所有 host。

`sendAndDrain` 只等待 executor 决策与 executor 直接等待的 mutation，不等待 keyed task、
ticker 或无限 Flow。应通过 task handle、probe 或显式推进虚拟时间来观察这些工作。需要测试
close boundary 时使用 `closeAndDrain`。

## 7. 使用安全的现代日志

对 `DefaultPulseStore` 安装新插件：

```kotlin
val logging = PulseLoggingPlugin<AppState, AppIntent, AppEffect>(
    tag = "Checkout",
    sink = LogSink(logger::debug),
)

val store = DefaultPulseStore(
    initialState = initialState,
    reducer = reducer,
    plugins = listOf(logging),
)
```

对包括 Split ViewModel 在内的只读 transition Flow：

```kotlin
viewModel.transitions
    .logPulseTransitions(tag = "Checkout", sink = LogSink(logger::debug))
    .collect { frame -> consume(frame) }
```

默认的 `TypeOnlyPulseRedactor` 只暴露类型，不暴露应用值；这些 helper 永远不会渲染异常
message 与 stack trace。只有在应用已经定义日志目标的数据策略后，才传入自定义
`PulseRedactor`。Flow operator 是惰性的，不改变 frame 值；它遵循普通 `onEach` 的失败语义，
因此除非确实要终止诊断收集，否则 `LogSink` 不应抛异常。

## 8. 区分接纳、executor 决策与 task 完成

在 executor 内处理 `TaskLaunchResult`，不要假设每个 launch request 都已接纳：

```kotlin
val launch = context.launchTask(TaskKey("refresh"), TaskPolicy.Latest) {
    mutate(ScreenMutation.Loaded(repository.load()))
}

when (launch) {
    is TaskLaunchResult.Accepted -> PulseIntentExecutionDecision.Completed
    TaskLaunchResult.DroppedWhileRunning ->
        PulseIntentExecutionDecision.Ignored("refresh-already-running")
    is TaskLaunchResult.QueueFull ->
        PulseIntentExecutionDecision.Ignored("refresh-queue-full")
    is TaskLaunchResult.ParallelLimitReached ->
        PulseIntentExecutionDecision.Ignored("refresh-parallel-limit")
    TaskLaunchResult.Closed ->
        PulseIntentExecutionDecision.Ignored("task-registry-closed")
}
```

`PulseIntentExecutionResult.Completed` 只映射 executor 的 `Completed` 决策，不表示已接纳的
keyed task 完成。`EnqueueResult.Enqueued` 更只表示 UI input 已接纳。只有进程内协调确实需要时
才等待 accepted task handle；业务完成应表达在 typed State 或 durable operation record 中。

## 9. 把 durable 与多 owner 工作留在 Pulse 之外

用 keyed task 承担进程内 callback/Flow collection。用 `callbackFlow` 与 `awaitClose` 配对注册
和注销；只对可替换 snapshot 使用 `conflate`；通过 token-bound `mutate` 提交结果。这样，替换
与 ViewModel close 会取消同一条 owned path，过期 task generation 也无法提交 mutation。

必须跨进程死亡继续的工作需要应用自己的 `operationId`、持久化 command/status、WorkManager
或 Service 执行，以及启动时 reconcile。Pulse State 只是持久化记录的进程内投影。root Store、
feature Store 与 Service-owned Store 都应各有一个明确 lifecycle owner，并通过 domain port、
repository、持久化或显式 IPC 协调，而不是全局 mutation bus。

Pulse 0.4 有意不新增 SourceRegistry、通用 scheduler/recovery API、progress sink、Service
基类、全局 Store bus、writer DSL/KSP、lint 套件或开发者面板。参考
[Pulse 0.4 接入模式](./INTEGRATION_PATTERNS.zh-CN.md)。

## 迁移检查清单

- [ ] 所有 Pulse 模块保持同一版本；只在需要真实 Split host 时添加 Android testing。
- [ ] 需要挂起背压时使用 `send`；处理每个非挂起接纳结果。
- [ ] 为 callback ingress 提供明确的 `Full`/`Rejected` 策略。
- [ ] 为 task 与 Split admission failure 更新穷举处理。
- [ ] 对生产环境的 Android 自定义 runtime 选项应用 `androidPulseRuntimeConfig(base)`。
- [ ] 只把 Split transition 用作只读诊断，不把它变成 mutation 路径。
- [ ] 处理每个 `TaskLaunchResult`；不要把 executor `Completed` 当成 task 完成。
- [ ] 不把异常 message 写入 State、effect 或默认日志。
- [ ] 按 feature 风险测试真实 Split ordering、effect、failure、overload、task replacement 与
      close。
- [ ] 将 durable scheduling、recovery 与 owner 间协调保留在应用层。
