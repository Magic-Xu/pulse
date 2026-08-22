# Pulse

[English](README.md)

Pulse 是一个面向 Kotlin 与 Android 的有序、协程优先 MVI 运行时。每个 Store 只拥有一个有界
输入邮箱和一个处理器，通过 `StateFlow` 发布唯一状态，把每次输入记录成可关联的 Transition
Frame，并由单一协调者交付 replay=0 的 UI Effect。

仓库当前正在验证稳定版 **0.3.0** 候选；在受保护发布 workflow 通过前，Maven Central 最新
稳定版仍是 0.2.0。

## 模块

| 制品 | 职责 |
| --- | --- |
| `mvi-core-contract` | State、Input、Reducer、Transition、结果与失败契约 |
| `mvi-core-runtime` | 有序 Store 引擎、Effect、Plugin、Keyed Task 与 v0.2 适配器 |
| `mvi-platform-android` | Split Intent ViewModel、显式 Owner、SavedState 与 Android 默认运行环境 |
| `mvi-platform-android-compose` | 生命周期感知的 State、Selector、Effect 与 ViewModel 绑定 |
| `mvi-extensions` | 可选日志、Transition 辅助与 State Decomposition DSL |
| `mvi-testing` | 虚拟时间、Probe、`TestPulseStore` 与可复用 Store TCK |

所有发布制品的 group 均为 `io.github.magic-xu`。

## 运行时契约

每个被接纳的输入只执行一个有序 Frame：

1. 读取当前 State；
2. 归约为 `Changed`、`Unchanged` 或 `Ignored`；
3. 提交变化后的 State；
4. 发布 Transition Frame；
5. 交付零到多个带关联信息的 UI Effect；
6. 完成 `send`。

`trySend` 只表示是否进入邮箱。`close` 建立有序截止点：截止点前已接纳的输入会排空，之后的
输入被拒绝。取消和 JVM 致命错误不会被转成业务失败；框架受控边界中的普通异常统一报告为
具名 `PulseFailure`。

## 最小 Store

```kotlin
data class CounterState(val value: Int) : MviState

sealed interface CounterInput : MviIntent {
    data object Increment : CounterInput
}

sealed interface CounterEffect : UiEffect

val store = DefaultPulseStore<CounterState, CounterInput, CounterEffect>(
    initialState = CounterState(0),
    reducer = PulseReducer { state, input ->
        when (input) {
            CounterInput.Increment -> ReduceOutcome.Changed(
                state.copy(value = state.value + 1)
            )
        }
    },
)

scope.launch {
    store.send(CounterInput.Increment)
}
```

Android 功能优先使用 `PulseSplitStoreViewModel`：UI 侧只能看到返回 executor 结果的挂起式
`send(UI)`，以及只返回 mailbox 接纳结果的非阻塞 `trySend(UI)`；Mutation 和 Keyed Task 只存在于
`PulseIntentContext` 内。

## 依赖配置

0.3 正式发布前不会出现在公共仓库。本地候选制品的使用方式：

```kotlin
repositories {
    maven { url = uri("<checkout>/build/staging-repo") }
    google()
    mavenCentral()
}

val pulseVersion = "0.3.0"
dependencies {
    implementation("io.github.magic-xu:mvi-core-runtime:$pulseVersion")
    implementation("io.github.magic-xu:mvi-platform-android-compose:$pulseVersion")
    implementation("io.github.magic-xu:mvi-extensions:$pulseVersion")
    testImplementation("io.github.magic-xu:mvi-testing:$pulseVersion")
}
```

只添加功能真正需要的模块。Android Compose 模块会传递引入 Android、runtime 与 contract 层。

## 示例

- `app/.../split_intent_basic`：不使用便捷 DSL 的显式 Split Intent 接线。
- `app/.../network`：带 Repository 的标准功能。
- `app/.../state_decomposition`：一个 Root Store 拆成 image/video 两个子状态 Reducer。
- `samples/simple-sync-consumer`：只消费 Maven 候选制品的同步示例。
- `samples/async-latest-consumer`：只消费 Maven 候选制品，覆盖 Latest、SavedState、Selector。

## 验证

```bash
# 确定性的 PR 门禁
./gradlew mviFrameworkCheck

# 完整发布候选门禁
./gradlew clean mviReleaseCheck
```

发布门禁包含标准测试、Store TCK、六模块 API/ABI dump、五制品 v0.2 源码/二进制兼容
fixture、Android/Compose 检查、候选制品校验、纯制品示例、多种子压力与可移植性能下限 harness。

继续阅读：[接入指南](docs/CONSUMER_GUIDE.zh-CN.md)、
[0.2 到 0.3 迁移](docs/MIGRATION_0.2_TO_0.3.zh-CN.md) 与
[架构决策](docs/decisions/)。

## License

Apache License 2.0。
