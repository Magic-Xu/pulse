# mvi-core-contract

`mvi-core-contract` 是框架的第 1 步：只定义 MVI 的核心契约，不做运行时实现。

## 目标

- 只保留 MVI 的本质抽象：`Intent -> Reducer -> State (+ Effect)`
- 不依赖 Android
- 为后续 runtime / Android 适配层预留扩展边界

## 目录结构

- `src/main/kotlin/com/magic/mvicore/contract/MviContract.kt`
  - `MviIntent` / `MviState` / `MviEffect`
  - `Next<S, E>`：一次 reduce 的产物（新状态 + 一次性事件）
- `src/main/kotlin/com/magic/mvicore/contract/SplitIntent.kt`
  - `MviUiIntent` / `MviMutation`：Intent 双通道标记
  - `SplitIntent<Ui, Mutation>`：内部统一消息封装
  - `MutationReducer` / `SplitIntentReducer`：只对 mutation 做纯状态变更
- `src/main/kotlin/com/magic/mvicore/contract/Reducer.kt`
  - `Reducer<S, I, E>`：纯状态转移接口
- `src/main/kotlin/com/magic/mvicore/contract/Store.kt`
  - `Store<S, I, E>`：平台无关 Store 边界
  - `StoreLifecycle`：生命周期（start/stop/close）
  - `DispatchResult` / `StoreError`：最小错误模型
  - `Subscription`：观察者取消句柄

## 设计原理（为什么这么做）

1. 先立协议，再写实现
   - 先固定接口能避免 runtime 先入为主，把框架锁死在某个平台或某套并发模型上。

2. `Reducer` 强制纯转换语义
   - 输入是 `previous + intent`，输出是 `Next(state, effects)`，这样状态演进可预测、可测试。

3. `Effect` 与 `State` 分离
   - `State` 代表可重放的数据快照；`Effect` 代表一次性事件（如导航、Toast）。
   - 这能避免“事件塞进状态导致重复消费”。

4. Store 只定义边界，不定义实现细节
   - 合同层不绑定 Flow/Rx/Android Lifecycle，后续 runtime 可自由选型（如协程 + Flow）。

5. 双通道意图模型
   - `UiIntent` 表达“外部输入”，`Mutation` 表达“可 reducer 的状态变化”。
   - reducer 只消费 mutation，副作用触发从状态变更中拆离，避免业务逻辑和纯状态变换混杂。

## 扩展接口预留点

- `StoreLifecycle`：后续可在 runtime 中扩展自动启动、懒启动、热插拔策略
- `StoreError`：后续可增加插件错误、并发冲突错误等
- `Subscription`：后续可适配到 Flow / Compose / Swift 等观察模型

## 当前可运行验证

- 自检入口：`ContractSelfCheck`
  - 验证状态演进的确定性
  - 验证 `Next.withEffects` 会拷贝输入，避免外部可变集合污染

运行命令：

```bash
./gradlew :mvi-core-contract:check
```
