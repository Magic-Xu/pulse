# mvi-testing

Pulse v0.3 Store 的虚拟时间测试工具、探针和可复用行为契约。

> 当前版本为 `0.3.0-SNAPSHOT`，仍在开发中，尚未发布到 Maven Central。发布前请使用仓库源码依赖，不要声明不存在的 `0.3.0` 制品。

## 依赖

```kotlin
dependencies {
    testImplementation(project(":mvi-testing"))
}
```

本模块公开传递 `mvi-core-runtime` 和 `kotlinx-coroutines-test`。

## 测试 Store

```kotlin
@Test
fun increment() = runPulseTest {
    val store = testStore(
        initialState = CounterState(0),
        reducer = PulseReducer<CounterState, CounterIntent, CounterEffect> { state, intent ->
            ReduceOutcome.Changed(state.copy(value = state.value + intent.amount))
        },
    )

    store.send(CounterIntent(amount = 2))
    runCurrent()

    store.stateProbe.assertLatest(CounterState(2))
    store.transitionProbe.assertSequence(1L)
    store.failureProbe.assertEmpty()
}
```

`runPulseTest` 为一个测试建立统一的虚拟时间 scheduler，并在结束时关闭所有通过 `testStore` 创建的 Store。

## 公开测试能力

- `TestPulseStore`：包装任意 `PulseStore`，同时保留生产接口。
- `StateProbe`：初始快照、值序列和等待条件。
- `TransitionProbe`：frame、sequence 和 outcome。
- `EffectProbe`：带关联信息的 `EffectEnvelope`。
- `FailureProbe`：类型化 `PulseFailure` 和 phase。
- `TestRuntimeConfig`：统一测试 dispatcher、单调时钟、容量、失败探针和脱敏器。Probe 断言默认只
  输出脱敏后的 State/Effect 类型，并携带最新 sequence。
- `PulseTestScope.sendConcurrently`、`runCurrent`、`advanceTimeBy`、`advanceUntilIdle`：并发与虚拟时间控制。

## 验证自定义 Store

实现 `PulseStoreTckFactory`，并把 `PulseStoreTck` 与 `PulseTaskTck` 的公开用例暴露为测试框架可识别的方法：

```kotlin
private val tck = PulseStoreTck(MyStoreFactory)
private val taskTck = PulseTaskTck(MyStoreFactory)

@Test fun sequentialOrdering() = tck.sequentialOrdering()
@Test fun concurrentTotalOrder() = tck.concurrentTotalOrder()
@Test fun closeCutoff() = tck.closeEstablishesCutoffAndDrains()
@Test fun latestTask() = taskTck.latestReplacesActiveRequestBeforeStartingTheNextGeneration()
@Test fun conflatedTask() = taskTck.conflateKeepsTheActiveAndOnlyTheNewestPendingRequest()
```

Store TCK 覆盖顺序与并发全序、原子状态快照、相等状态归一化、显式忽略、失败隔离、effect 基数与顺序、溢出、关闭截止、取消、重入、插件隔离和 10,000 输入压力测试。Task TCK 覆盖五种策略、可观察替换终态、token 失效、late mutation 诊断，以及任务失败与取消隔离。

## 验证

```bash
./gradlew :mvi-testing:check
```
