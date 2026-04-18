# 迭代路线图

英文版：[ITERATION_ROADMAP.md](https://github.com/Magic-Xu/pulse/blob/master/docs/ITERATION_ROADMAP.md)

Pulse 当前架构基线：

- `PulseViewModel`：可继承的基础 VM
- `PulseSplitViewModel`：双通道意图模型（`UiIntent` + `Mutation`）
- reducer 聚焦纯状态转移

本路线图定义了后续面向生产规模的迭代方向。

## 版本演进列表

### v0.1.0（第一版极简可用版本）

- 定位：
  - 极简可运行 MVI 基线
- 设计：
  - 单通道 intent（`MviIntent`）
  - reducer 消费所有 intent
  - Android 层使用 ViewModel + Store 组合
- 取得效果：
  - 形成跨平台优先模块化（`contract/runtime/platform`）
  - 能快速跑通示例，学习成本低
  - 基础能力可端到端运行
- 缺点：
  - 触发型 intent 与纯状态 mutation 混在一起
  - 复杂业务下 reducer 容易膨胀
  - 副作用编排和状态变更边界不够清晰
  - VM 公共方法增长后扩展性变差

### v0.2.x（当前迭代：双通道 Intent 架构）

- 定位：
  - 在 v0.1 基础上的生产级可扩展升级
- 新增能力：
  - `MviUiIntent` + `MviMutation` 双通道模型
  - `SplitIntent`、`MutationReducer`、`SplitIntentReducer`
  - `PulseSplitViewModel` + `UiIntentExecutor` + `UiIntentExecutionScope`
  - 示例迁移为 `send(uiIntent) -> dispatchMutation(mutation)` 流程
- 解决的问题：
  - 外部触发与纯状态变更语义分离
  - reducer 更聚焦于确定性状态演进
  - 副作用编排迁移到 UI intent 执行层
  - 大型业务场景下可读性、可追踪性提升
- 当前仍存在的问题：
  - State 拆分工具集尚未落地
  - 父子 Feature/Store 组合能力尚未实现
  - Effect 中间层与统一执行管线未完成
  - 并发策略工具（`drop/cancel/queue/latest`）尚未标准化

## v0.1 -> v0.2 更新清单

1. Intent 模型升级：
   - 从单通道升级为双通道（`UiIntent` / `Mutation`）
2. Reducer 职责收敛：
   - 双通道模式下 reducer 仅处理 mutation
3. ViewModel 执行模型升级：
   - 引入 `PulseSplitViewModel`，支持 UI 触发副作用与 mutation 回写
4. 示例架构迁移：
   - 网络示例已迁移为双通道流程并完成验证
5. 文档体系升级：
   - 根 README 与模块 README 已与新语义对齐

## 优先级顺序

1. State 拆分工具集
2. Feature/Store 组合能力
3. Effect 执行中间层
4. 并发与生命周期策略
5. 调试工具
6. 测试 DSL

## 里程碑

### 1) State 拆分工具集

- 目标：
  - 解决复杂页面状态臃肿问题
- 交付物：
  - 子状态组合模型
  - `combineMutationReducer` 组合工具
  - 局部状态更新辅助工具
- 验收标准：
  - 一个复杂页面可拆成多个领域子状态，且 reducer 测试仍清晰可预测

### 2) Feature/Store 组合能力

- 目标：
  - 支持父子 Feature 编排
- 交付物：
  - 父 Store 路由子 `UiIntent` / `Mutation`
  - 子状态挂载到父状态
  - 复杂页面的组合约定
- 验收标准：
  - 多功能页面不再依赖单个超大 reducer

### 3) Effect 执行中间层

- 目标：
  - 让 IO/导航/埋点与 VM 编排进一步解耦
- 交付物：
  - `EffectHandler` 抽象
  - 可插拔 `EffectPipeline`
  - 真实/Mock 执行策略
- 验收标准：
  - 不改 reducer/mutation 逻辑即可切换 effect 执行后端

### 4) 并发与生命周期策略

- 目标：
  - 统一重复触发和生命周期行为
- 交付物：
  - in-flight 策略（`drop` / `cancel` / `queue` / `latest`）
  - 生命周期感知的状态/事件观察策略
- 验收标准：
  - 重复点击、前后台切换行为在各模块中保持可预测

### 5) 调试工具

- 目标：
  - 提升可观测性和排障效率
- 交付物：
  - `UiIntent` / `Mutation` 时间线
  - state diff 检视能力
  - 性能追踪插件挂点
- 验收标准：
  - 能快速回答“哪个 mutation 导致了这次状态变化”

### 6) 测试 DSL

- 目标：
  - 降低测试样板代码，提高可读性
- 交付物：
  - given-when-then 风格 DSL
  - 可预测的时序断言
  - state/effect 断言助手
- 验收标准：
  - 业务模块能以低成本补齐 reducer/store 测试

## 迭代规则

每个里程碑遵循：

1. 先定 contract/API
2. 实现最小可运行版本
3. 更新对应模块 README
4. 跑检查与示例验证
5. 停下评审，确认后再进入下一里程碑
