# mvi-core-contract

Pulse 的平台无关契约模块，不包含协程、Android 或 Compose 实现。

> 当前源码版本为稳定版 `0.3.0` 候选；公共制品验证成功前仍未发布到 Maven Central。

## 依赖

仓库内源码工程使用：

```kotlin
dependencies {
    implementation(project(":mvi-core-contract"))
}
```

通常无需直接依赖本模块；`mvi-core-runtime` 会公开传递这些契约。

## v0.3 契约

- `MviIntent`、`MviState`、`UiEffect`：输入、持久状态和一次性 UI 指令的标记接口。
- `PulseReducer`：纯函数 `previous + input -> ReduceOutcome`。
- `ReduceOutcome.Changed`、`Unchanged`、`Ignored`：显式描述一次规约结果；忽略输入必须给出原因。
- `TransitionFrame`、`TransitionOutcome`、`EffectEnvelope`：一次已完成处理的有序记录及其关联 effect。
- `TransitionResult`、`EnqueueResult`、`RejectionReason`：挂起提交与非挂起入队的结果。
- `PulseFailure`：reducer、消费者、插件、执行器、溢出、未投递 effect、过期 mutation，以及状态恢复/保存失败的类型化诊断。
- `MviUiIntent`、`MviMutation`、`PulseMutationReducer`、`TaskLaunchResult`、`TaskHandle` 与 `TaskOutcome`：Split Intent 所需契约。

普通异常会进入类型化失败边界；协程取消和致命 JVM 错误不属于 `PulseFailure`。

## Reducer 示例

```kotlin
data class CounterState(val count: Int) : MviState

sealed interface CounterIntent : MviIntent {
    data object Increment : CounterIntent
    data object Save : CounterIntent
}

sealed interface CounterEffect : UiEffect {
    data object Saved : CounterEffect
}

val counterReducer = PulseReducer<CounterState, CounterIntent, CounterEffect> { state, input ->
    when (input) {
        CounterIntent.Increment -> ReduceOutcome.Changed(state.copy(count = state.count + 1))
        CounterIntent.Save -> ReduceOutcome.Unchanged(listOf(CounterEffect.Saved))
    }
}
```

## v0.2 兼容契约

`MviEffect`、`Next`、`Reducer`、`Store`、`SplitIntent` 和 `MutationReducer` 继续保留，供现有源码迁移。新代码优先使用 v0.3 的 `UiEffect`、`PulseReducer` 和显式结果类型。

状态拆分不属于契约层。`StateLens`、`stateLens`、`pulseMutationReducer`、`onSub` 和显式 `ignore` 位于 `mvi-extensions`。

## 验证

```bash
./gradlew :mvi-core-contract:check
```
