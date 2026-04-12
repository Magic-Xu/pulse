# mvi-extensions

`mvi-extensions` 是第 4 步：在不改核心 API 的前提下，把常见能力做成可插拔插件。

## 目标

- 验证 `StorePlugin` 扩展机制可以承载通用能力
- 提供可直接复用的极简插件实现
- 保持跨平台，不引入 Android 依赖

## 目录结构

- `LoggingPlugin.kt`
  - `LoggingPlugin`：记录生命周期、intent、state、effect、错误
  - `LogSink`：日志输出抽象（可接控制台、文件、埋点系统）
- `StateTransitionPlugin.kt`
  - `StateTransitionPlugin`：统一输出 `previous + intent + next` 三元组
  - `StateTransition`：状态变化记录模型
- `ExtensionsSelfCheck.kt`
  - 自检入口：验证两个插件的关键行为

## 实验原理（为什么这么实现）

1. 核心保持最小闭环
   - `DefaultStore` 只做调度和分发，不内建日志/追踪逻辑。

2. 扩展能力插件化
   - `LoggingPlugin` 通过 `LogSink` 抽象输出端，做到“同一插件，多种落地”。
   - `StateTransitionPlugin` 在 `onIntent/onState` 两个阶段拼出完整状态迁移数据，便于调试与分析。

3. 对使用方依赖最小化
   - 本模块依赖 `mvi-core-runtime`，并通过 `api` 透出，使用方只需按需引入本模块即可。

## 用法示例

```kotlin
val store = DefaultStore(
    initialState = state,
    reducer = reducer,
    plugins = listOf(
        LoggingPlugin(),
        StateTransitionPlugin { transition ->
            // transition.previous / transition.intent / transition.next
        }
    )
)
```

## 当前可运行验证

```bash
./gradlew :mvi-extensions:check
```

验证点：

- 日志插件回调顺序与实际调度事件一致
- 状态变化插件能输出完整三元组
