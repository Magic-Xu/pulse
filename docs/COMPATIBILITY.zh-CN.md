# 兼容性政策

英文版：[COMPATIBILITY.md](./COMPATIBILITY.md)

> `0.4.0` 是 Maven Central 当前稳定版；`0.3.0` 是直接兼容基线，0.2.0 的五制品表面继续作为
> 长期兼容承诺保留。

本文会区分 API 兼容和运行时行为。程序可以保持源码与二进制兼容，同时获得更严格的顺序和失败语义。

## 版本线

| 版本线 | 状态 | 适用场景 |
|---|---|---|
| `0.2.0` | 长期兼容面 | 通过旧版适配器继续支持现有五制品接入 |
| `0.3.0` | 直接兼容基线 | 用于验证 0.4 的六制品有序运行时表面 |
| `0.4.0` | 当前稳定版 | 加固 Android 接入与诊断、增加安全日志和第七个测试制品 |

Pulse 使用 `0.x.y` 版本。1.0 之前，minor 版本可以新增或修订公开 API；patch 版本不得有意删除公开
API，也不得以不兼容方式改变已记录行为。

## 0.3 到 0.4 的直接兼容目标

| 维度 | 0.4 目标 |
|---|---|
| 制品坐标 | 保留 0.3 的六个坐标，新增 `mvi-platform-android-testing` |
| 源码兼容 | 既有 0.3 公开调用可针对对应的六个 0.4.0 制品编译 |
| 二进制兼容 | 针对 0.3 编译的消费者可链接并运行对应的 0.4.0 制品 |
| 新 API | 以新增为主；对扩展后的 enum 与 sealed failure 做穷举处理的代码必须更新 |
| 行为兼容 | 保持有序运行时的功能意图，并记录更完整的 Split 接纳边界 |
| 持久化兼容 | 应用自己负责其 schema 兼容性 |

直接基线是完整的 0.3 六制品版本，而不只是 core runtime。新增的接纳、失败、Android 配置和测试
表面参见[从 Pulse 0.3 迁移到 0.4](./MIGRATION_0.3_TO_0.4.zh-CN.md)。

## 0.4 兼容证据

0.4 发布门禁要求：

- 七个发布制品均有受控 API/ABI 基线；
- `compatibility03Check`：用冻结的 0.3 六制品源码面分别针对基线与 0.4 编译，对每个原有 archive
  执行二进制与 Java 源码兼容比较，运行 baseline/candidate JVM 消费者，让针对 0.3 编译的
  bytecode 在 0.4 runtime 上运行，并验证仅针对 0.3 编译的 `PulseTasks` 实现可通过默认桥接调用；
- 保留的 `compatibilityCheck`：对 0.2 五个坐标编译冻结源码面，对全部五个 archive 执行 linkage
  与 Java 源码兼容比较，并针对 0.4 执行 core-runtime 的源码与二进制 linkage。

这些 fixture 消费暂存的 0.4 发布物，而不是 project dependency，因此验证的是 Maven 消费者实际
看到的制品边界。

## 0.2 到 0.3 的历史兼容目标

| 维度 | 0.3 目标 |
|---|---|
| 制品坐标 | 保留 0.2 的五个坐标，新增 `mvi-testing` |
| 源码兼容 | 现有 0.2 公开调用可以针对 0.3.0 编译 |
| 二进制兼容 | 针对 0.2 编译的消费者可以链接并运行 0.3.0 制品 |
| 新 API | 以新增为主，可按功能逐步迁移 |
| 行为兼容 | 保持功能意图，但应用已记录的顺序和生命周期变化 |
| 持久化兼容 | 应用自己负责其 schema 兼容性 |

v0.3 发布门禁曾用同一份冻结的 0.2 Kotlin 调用面分别编译五个既有制品的基线与 0.3.0 形态，并对
每个坐标执行 archive 级二进制与 Java 源码兼容比较；core-runtime 二进制链接消费者也针对暂存的
0.3.0 制品实际运行。六个发布模块均有受控公开 API 基线。v0.3.0 发布前已通过这些检查，0.4 继续
按上文所述保留 0.2 五制品表面。

## 发布制品

所有坐标使用 group `io.github.magic-xu`。

| 制品 | 兼容职责 |
|---|---|
| `mvi-core-contract` | 保留 0.2 marker、reducer、`Next`、`Store` 和结果类型；新增 0.3 契约 |
| `mvi-core-runtime` | 保留 `DefaultStore` 和旧插件；新增有序 `PulseStore` 运行时 |
| `mvi-platform-android` | 保留 0.2 ViewModel 适配器；新增显式 owner、saved state 和新 Split API |
| `mvi-platform-android-compose` | 保留旧 Store 绑定；新增生命周期感知的 host 绑定 |
| `mvi-platform-android-testing` | 0.4 新增；为真实 Split ViewModel 提供确定性测试 |
| `mvi-extensions` | 保留旧插件和 reducer 辅助能力；新增 0.3 状态拆分 |
| `mvi-testing` | 0.3 新增；公开的虚拟时间 probe 与 Store TCK |

通常只需依赖实际使用的最高层适配器，它会传递引入下层 Pulse 依赖。同一个依赖图中不要混用不同
版本的 Pulse 模块。

## 有意调整的运行时行为

保留的 0.2 与 0.3 API 均由 0.4 有序引擎承载。对于 0.2 消费者，下列 0.3 变化仍是有意行为；如果
应用依赖的是时机而不是文档中的结果，需要重点测试：

- 已接收输入、生命周期控制和重入 send 共用一个 FIFO 顺序边界；
- 旧 `dispatch` 会等待自身 frame，回调交付则与 reducer 调用栈隔离；
- state、effect 和 plugin 消费者失败会被隔离和报告，不会阻断其他消费者；
- 取消和 JVM 致命错误不会转换成领域失败或 Pulse failure；
- close 会建立接收截止点，并排空截止点之前已接收的工作；
- 新 `StateFlow` API 不会重复发送相等的候选状态；
- 新 `UiEffect` 交付 replay 为 0，并且只有一个活动协调者。

相对 0.3，0.4 有以下可观测的有意变化：

- Split `trySend` 的接纳范围覆盖完整 UI-to-executor 路径，即使 core mailbox 尚未满，也可能报告
  `Full`；
- keyed task 异常会报告为带关联信息的 `TaskFailure`；启用对应策略时，Split 过载通过 admission
  failure phase 报告；
- Split transition frame 可供只读观察，但不会暴露 Store 或 mutation 能力；
- Android runtime config overlay 会保留非 dispatcher 选项，同时应用 Android Main dispatcher。

这些保证不等于分布式顺序、持久任务执行或 UI effect 恰好一次交付。任务和 UI effect 都只存在于
当前进程。

## 平台基线

Pulse 0.4.0 继续以 Java 11 字节码为目标。Android 制品保持 `minSdk 23`，发布工程针对 Android
API 36.1 编译；发布和 CI 门禁运行在 JDK 21。公开依赖集合以发布的 Gradle metadata 和 POM
为准，消费者仍需在自己的平台与约束下验证依赖解析。

## 兼容边界

以下内容不在兼容承诺内：

- 反射访问 private、internal、synthetic 或生成声明；
- 依赖回调线程交错或未记录的异常消息；
- 序列化框架运行时对象、task token、effect envelope 或 subscription；
- 在不同模块间混用不同 Pulse 版本；
- 应用自己的 saved-state key、schema 版本、数据迁移或持久操作规则；
- 把 snapshot、未发布提交或本地源码替换当作稳定版本。

## 报告兼容性问题

请提供准确的新旧 Pulse 版本、受影响坐标、Kotlin/Gradle/AGP 版本、相关 Android API 级别，说明问题
属于源码、二进制还是行为兼容，并附最小复现。行为问题还应提供期望与实际的 transition 或生命周期
顺序。
