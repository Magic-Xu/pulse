# Pulse

Pulse 表示 MVI 中的每一次状态变化，是系统对输入的清晰响应信号。

极简、跨平台优先的 MVI 框架。

[![GitHub Repo](https://img.shields.io/badge/GitHub-Magic--Xu%2Fpulse-181717?logo=github)](https://github.com/Magic-Xu/pulse)
[![GitHub Stars](https://img.shields.io/github/stars/magic-xu/pulse?style=flat)](https://github.com/Magic-Xu/pulse/stargazers)
[![License](https://img.shields.io/github/license/magic-xu/pulse)](https://github.com/Magic-Xu/pulse/blob/master/LICENSE)
[![Maven Central Core](https://img.shields.io/maven-central/v/io.github.magic-xu/mvi-core-runtime?label=Maven%20Central%20(core))](https://central.sonatype.com/artifact/io.github.magic-xu/mvi-core-runtime)
[![Maven Central Android](https://img.shields.io/maven-central/v/io.github.magic-xu/mvi-platform-android?label=Maven%20Central%20(android))](https://central.sonatype.com/artifact/io.github.magic-xu/mvi-platform-android)
[![Maven Central Compose](https://img.shields.io/maven-central/v/io.github.magic-xu/mvi-platform-android-compose?label=Maven%20Central%20(compose))](https://central.sonatype.com/artifact/io.github.magic-xu/mvi-platform-android-compose)

当前版本：`0.2.0`

英文 README： [README.md](https://github.com/Magic-Xu/pulse/blob/master/README.md)

## 简介

- 单向数据流：`Intent -> Reducer -> State (+ Effect)`
- `State` 与 `Effect` 分离（状态可重放，事件一次性）
- 核心模块不依赖 Android
- Android/Compose 通过子模块按需接入

## 模块

- `mvi-core-contract`：跨平台契约（`MviState` / `MviIntent` / `MviEffect` / `Reducer` / `Store`）
- `mvi-core-runtime`：跨平台运行时（`DefaultStore` + `StorePlugin`）
- `mvi-platform-android`：Android `ViewModel` 适配
- `mvi-platform-android-compose`：Compose 绑定（`collectStateAsState` / `observeEffects`）
- `mvi-extensions`：可选扩展（日志插件、状态迁移插件）

## 双通道 Intent 模型

Pulse 现已支持复杂业务的 Intent 拆分：

- `MviUiIntent`：来自 UI/用户动作的外部输入
- `MviMutation`：仅用于 reducer 的内部状态变更消息
- `PulseSplitViewModel`：`send(uiIntent)` -> 副作用执行 -> `dispatchMutation(mutation)` -> reducer

这样 reducer 只负责纯状态变更，IO/业务编排不会混入 mutation 逻辑。

## Maven Central 依赖方式

确保 `settings.gradle.kts` 包含 `mavenCentral()`：

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

Compose Android 项目（推荐）：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-platform-android-compose:0.2.0")
}
```

非 Compose Android 项目：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-platform-android:0.2.0")
}
```

可选扩展：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-extensions:0.2.0")
}
```

纯核心（跨平台）：

```kotlin
dependencies {
    implementation("io.github.magic-xu:mvi-core-runtime:0.2.0")
}
```

## App 依赖模式（本地/远端）

`app` 模块支持两种模式：

- `local`：依赖工程内 `project(:xxx)`（默认）
- `remote`：依赖 Maven Central 已发布构件

一键切换命令：

```bash
./gradlew useLocalPulseDeps
./gradlew useRemotePulseDeps
./gradlew printPulseDepMode
```

单次构建临时覆盖：

```bash
./gradlew :app:assembleDebug -PPULSE_APP_DEP_MODE=remote
./gradlew :app:assembleDebug -PPULSE_APP_DEP_MODE=local
```

## 最小使用示例（Android + Compose）

```kotlin
import com.magic.mvicore.android.PulseViewModel
import com.magic.mvicore.android.compose.collectStateAsState
import com.magic.mvicore.android.compose.observeEffects
import com.magic.mvicore.contract.MviEffect
import com.magic.mvicore.contract.MviIntent
import com.magic.mvicore.contract.MviState
import com.magic.mvicore.contract.Next
import com.magic.mvicore.contract.Reducer

data class CounterState(val count: Int = 0) : MviState

sealed interface CounterIntent : MviIntent {
    data object Increase : CounterIntent
    data object Decrease : CounterIntent
}

sealed interface CounterEffect : MviEffect {
    data object ReachTen : CounterEffect
}

object CounterReducer : Reducer<CounterState, CounterIntent, CounterEffect> {
    override fun reduce(previous: CounterState, intent: CounterIntent): Next<CounterState, CounterEffect> {
        return when (intent) {
            CounterIntent.Increase -> {
                val next = previous.copy(count = previous.count + 1)
                if (next.count == 10) Next.withEffect(next, CounterEffect.ReachTen) else Next.just(next)
            }
            CounterIntent.Decrease -> Next.just(previous.copy(count = previous.count - 1))
        }
    }
}

class CounterViewModel : PulseViewModel<CounterState, CounterIntent, CounterEffect>(
    initialState = CounterState(),
    reducer = CounterReducer
) {
    fun increase() = dispatch(CounterIntent.Increase)
}
```

```kotlin
@Composable
fun CounterScreen(viewModel: CounterViewModel) {
    val state by viewModel.collectStateAsState()

    viewModel.observeEffects { effect ->
        when (effect) {
            CounterEffect.ReachTen -> {
                // show toast / navigate
            }
        }
    }

    Button(onClick = { viewModel.increase() }) {
        Text("Count = ${state.count}")
    }
}
```

## 链接

- GitHub: [https://github.com/Magic-Xu/pulse](https://github.com/Magic-Xu/pulse)
- Releases: [https://github.com/Magic-Xu/pulse/releases](https://github.com/Magic-Xu/pulse/releases)
- Issues: [https://github.com/Magic-Xu/pulse/issues](https://github.com/Magic-Xu/pulse/issues)
- API（Contract 源码）: [https://github.com/Magic-Xu/pulse/tree/master/mvi-core-contract/src/main/kotlin/com/magic/mvicore/contract](https://github.com/Magic-Xu/pulse/tree/master/mvi-core-contract/src/main/kotlin/com/magic/mvicore/contract)
- API（Runtime 源码）: [https://github.com/Magic-Xu/pulse/tree/master/mvi-core-runtime/src/main/kotlin/com/magic/mvicore/runtime](https://github.com/Magic-Xu/pulse/tree/master/mvi-core-runtime/src/main/kotlin/com/magic/mvicore/runtime)
- API（Android 源码）: [https://github.com/Magic-Xu/pulse/tree/master/mvi-platform-android/src/main/java/com/magic/mvicore/android](https://github.com/Magic-Xu/pulse/tree/master/mvi-platform-android/src/main/java/com/magic/mvicore/android)

## 文档

- 使用方接入指南（中文）：[docs/CONSUMER_GUIDE.zh-CN.md](https://github.com/Magic-Xu/pulse/blob/master/docs/CONSUMER_GUIDE.zh-CN.md)
- 使用方接入指南（EN）：[docs/CONSUMER_GUIDE.md](https://github.com/Magic-Xu/pulse/blob/master/docs/CONSUMER_GUIDE.md)
- 迭代路线图（中文）：[docs/ITERATION_ROADMAP.zh-CN.md](https://github.com/Magic-Xu/pulse/blob/master/docs/ITERATION_ROADMAP.zh-CN.md)
- 迭代路线图（EN）：[docs/ITERATION_ROADMAP.md](https://github.com/Magic-Xu/pulse/blob/master/docs/ITERATION_ROADMAP.md)
- 发布规划（中文）：[docs/RELEASE_PLAN.zh-CN.md](https://github.com/Magic-Xu/pulse/blob/master/docs/RELEASE_PLAN.zh-CN.md)
- 发布规划（EN）：[docs/RELEASE_PLAN.md](https://github.com/Magic-Xu/pulse/blob/master/docs/RELEASE_PLAN.md)
- Maven Central 发布手册（中文）：[docs/PUBLISH_MAVEN_CENTRAL.zh-CN.md](https://github.com/Magic-Xu/pulse/blob/master/docs/PUBLISH_MAVEN_CENTRAL.zh-CN.md)
- Maven Central 发布手册（EN）：[docs/PUBLISH_MAVEN_CENTRAL.md](https://github.com/Magic-Xu/pulse/blob/master/docs/PUBLISH_MAVEN_CENTRAL.md)

## 迭代路线图

Pulse v0.2 面向生产规模的迭代清单：

1. Reducer 入口不变量。
   状态：已完成
   结果：`PulseViewModel` + `IntentExecutionScope`

2. Intent 双通道拆分（UI intent / mutation）。
   状态：已完成
   结果：`MviUiIntent` + `MviMutation` + `PulseSplitViewModel` + `UiIntentExecutor`

3. State 拆分工具集。
   状态：计划中
   目标：子状态 reducer + 分域状态组合

4. Feature/Store 组合能力。
   状态：计划中
   目标：复杂页面父子 Store 编排

5. Effect 执行中间层。
   状态：计划中
   目标：把 IO/导航/埋点从 reducer 中分离

6. 调试工具。
   状态：计划中
   目标：intent 轨迹 + state diff 时间线

7. 测试 DSL。
   状态：计划中
   目标：低样板代码的 reducer/store 可预测断言
