# mvi-core-runtime

`mvi-core-runtime` 是第 2 步：在 `mvi-core-contract` 之上提供一个可直接使用的极简 Store 实现。

## 目标

- 提供一个真正可运行的 `Store`：`dispatch(intent)` 后能推进状态并分发 effect
- 保持平台无关（无 Android 依赖）
- 在不增加复杂度的前提下，预留扩展点（插件机制）

## 目录结构

- `src/main/kotlin/com/magic/mvicore/runtime/DefaultStore.kt`
  - `DefaultStore`：最小 runtime 实现
- `src/main/kotlin/com/magic/mvicore/runtime/StorePlugin.kt`
  - `StorePlugin`：扩展接口（日志、埋点、调试都可通过插件实现）
- `src/test/kotlin/com/magic/mvicore/runtime/RuntimeSelfCheck.kt`
  - 自检入口：验证生命周期、状态/effect 分发、插件回调

## 实验原理（为什么这样实现）

1. 串行 dispatch，保证状态演进可预测
   - `DefaultStore` 用互斥锁串行化 `dispatch`，不会出现并发 intent 打乱状态顺序的问题。

2. 状态与事件的分发语义分离
   - `State`：`observeState` 订阅后会先收到当前快照，再收到后续变更。
   - `Effect`：`observeEffect` 只接收后续一次性事件，不回放历史事件。

3. 生命周期最小闭环
   - `start/stop/close` 三态控制 dispatch：
   - `stop` 后拒绝 intent（`StoreNotStarted`）
   - `close` 后彻底拒绝并清理订阅（`StoreClosed`）

4. 扩展能力外置到插件
   - runtime 核心只负责调度与分发。
   - 日志、监控、调试、trace 通过 `StorePlugin` 接入，避免核心逻辑膨胀。

## 当前可扩展接口

- `StorePlugin` 提供关键钩子：
  - `onIntent`、`onState`、`onEffect`
  - `onStart`、`onStop`、`onClose`
  - `onRejected`、`onError`

后续你可以在不改 `Store` 核心接口的前提下扩展：

- 日志插件
- 性能/埋点插件
- DevTools 时间旅行记录插件（先记录 intent+state，不必改调度器）

## 当前可运行验证

运行命令：

```bash
./gradlew :mvi-core-runtime:check
```

`RuntimeSelfCheck` 验证点：

- 生命周期对 dispatch 的约束（start/stop/close）
- 状态流转和 effect 单次分发行为
- 插件回调顺序与核心事件一致
