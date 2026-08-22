# Pulse 迭代路线图

[English](ITERATION_ROADMAP.md)

## 当前版本线

当前稳定版为 `0.3.0`。运行时有序性、Split Intent Task、Android/Compose 生命周期、State
Decomposition、测试、兼容与公共制品证据已作为一个整体发布。

## 0.3 范围

### 有序 Core

- `StateFlow` 是唯一状态真相源。
- 一个有界 FIFO Mailbox 串行化 Input 与生命周期命令。
- 一个 Processor 独占 read、reduce、commit、transition、effect 与 completion 顺序。
- 结果明确区分 `Changed`、`Unchanged` 与 `Ignored`。
- `close` 建立有序接纳截止点并排空已接纳任务。
- 受控异常转为 Typed Failure；取消与致命错误原样传播。
- v0.2 `DefaultStore` 委托同一个 Engine。

### 异步与 Effect

- Keyed Task 支持 Latest、DropWhileRunning、有界 Queue、有界 Parallel、Conflate，并显式返回
  过载与最终 outcome。
- 不透明代际 Token 会拒绝任务替换或取消后的迟到 Mutation。
- UI Effect replay=0、有界，且只允许一个活跃协调者。
- 未交付 Effect 与 Consumer 失败均可观测。

### Android 与 Compose

- Split Intent 对 UI 只公开返回执行结果的 `send(UI)` 与返回接纳结果的 `trySend(UI)`。
- Mutation 能力只存在于 `PulseIntentContext` 和 Task Context。
- Android 默认在 `Main.immediate` 运行 Reducer 与受控交付。
- ViewModel 获取必须显式提供 Owner 与稳定 key。
- SavedState 使用功能自定义 Adapter，不强迫 State Parcelable。
- Compose 收集感知 Lifecycle，并支持 Selector 级相等判断。

### Extensions 与 Testing

- State Decomposition 属于 `mvi-extensions`，不属于 contract 模块。
- Lens 子状态不绑定 Marker，并由三条 Lens Law 验证。
- Mutation 路由默认 fail-fast；ignore 必须显式；重复或重叠路由被拒绝。
- `mvi-testing` 发布虚拟时间、Probe、`TestPulseStore` 与 Store TCK。

### 发布证据

- 六个公开制品都有受控 API/ABI dump，包含两个 Android AAR。
- v0.2 Consumer 源码编译与二进制链接均对 0.3.0 制品执行。
- 发布物会先在隔离 Maven 仓库中校验，再执行公共消费验证。
- 两个独立示例只通过 Maven 坐标消费制品，不依赖 Project。
- 固定种子 PR 测试、多种子压力与性能回归分层执行。

## 发布边界

只有以下命令在干净检出中通过，`0.3.0` 才可发布：

```bash
./gradlew clean mviReleaseCheck
```

同时要求发布 tag 为 `v0.3.0`、配置版本为 `0.3.0`，且发布 Workflow 必须依赖本次成功门禁。
普通分支、RC 或 SNAPSHOT 不能直接发布到 Maven Central。

## 0.3 之后

后续能力独立评估，不能削弱 0.3 的顺序与兼容契约。候选方向包括 Multiplatform Runtime、
更丰富的诊断导出与额外的纯制品导航示例。
