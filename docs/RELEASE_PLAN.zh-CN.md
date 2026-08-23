# Pulse 0.4.0 发布规划

英文版：[RELEASE_PLAN.md](./RELEASE_PLAN.md)

> 发布状态：**稳定版，已于 2026-08-24 发布**，来源为准确的 Annotated Tag `v0.4.0`。
> [Workflow Run 32659106344](https://github.com/Magic-Xu/pulse/actions/runs/32659106344) 已通过；
> 七个签名 Maven Central 发布包均已公开，两个隔离纯制品消费者均已通过。

## 发布结果

Pulse 0.4 已发布为一条内部一致的版本线，在 0.3 有序运行时基础上解决真实 Android 集成问题。
它修复了端到端 Split 接纳，完善了 Task 与 Transition 诊断，为真实 Split ViewModel 提供
确定性测试，并修正框架自有示例；同时不吸收应用的领域、持久任务或多 Store 编排策略。

## 已完成准入

| 阶段 | 已完成结果 |
|---|---|
| 0. 基线 | 修改公开行为前，识别已发布的 0.3 API 表面与保留的 0.2 兼容 Fixture |
| 1. 接纳 | 一个有界预算覆盖 Split UI 接纳到串行 Executor 决策；挂起与非挂起契约明确 |
| 2. 诊断 | Split Transition 只读、Task Failure 保留请求关联，Android 配置不会静默把生产工作移出 Main |
| 3. 测试与扩展 | Android Split Test Host 与默认脱敏的现代日志只建立在有序运行时之上 |
| 4. 示例与指南 | 官方示例处理接纳和 Task Launch Result；集成指南保持框架与应用职责边界 |
| 5. API 与兼容 | 七份 API 基线、可执行的 0.3 六制品检查，以及保留的 0.2 五制品检查通过 |
| 6. 发布制品 | 七个暂存发布物和两个纯制品消费者全部通过 |
| 7. 发布准入 | Framework、Publication、Stress、Performance、托管设备与稳定身份门禁在同一提交通过 |

后续阶段不能为了通过自身检查而削弱更早阶段的契约。准入后如再修改公开 API 或制品，必须重新执行
API 评审、Framework、Compatibility、暂存消费者、Stress、Performance 与托管设备验证。

## 七个发布制品

所有制品使用 group `io.github.magic-xu`，且版本必须完全一致。

| 制品 | 职责 |
|---|---|
| `mvi-core-contract` | 平台无关的 Store、Transition、Task、Effect 与 Typed Failure 契约 |
| `mvi-core-runtime` | 有序 Store Engine、Effect 协调者、Keyed Task、配置与旧适配器 |
| `mvi-platform-android` | Split ViewModel、显式 Owner、Android Main 配置、Saved State 与 Callback Ingress |
| `mvi-platform-android-compose` | 生命周期感知的 State Selector 与 UI Effect 协调 |
| `mvi-platform-android-testing` | 真实 Split ViewModel Host、共享虚拟时间 Scheduler、Probe 与确定性清理 |
| `mvi-extensions` | 可选的脱敏日志、`StateLens` 与 Reducer 组合 |
| `mvi-testing` | 平台无关的虚拟时间辅助能力、Probe 与 Store TCK |

示例应用、兼容 Fixture、隔离消费者和 Benchmark 都是验证输入，不是发布制品。

## 必须通过的门禁

### PR Framework 门禁

```bash
./gradlew mviFrameworkCheck --stacktrace
```

`.github/workflows/ci.yml` 在 JDK 21 上运行该任务。聚合门禁覆盖：

- Contract、Runtime、Extensions 与纯 JVM Testing 模块检查；
- 三个 Android Library 制品的单元测试与 `lintDebug`；
- 示例应用单元测试、Debug Assembly 与 Lint；
- 七个发布制品的 `apiCheck`；
- `compatibility03Check`：六个 0.3 制品的冻结源码编译与 Archive 比较，以及 Baseline/Candidate
  JVM 运行、冻结的 0.3 Bytecode 在候选 Runtime 上运行和旧 `PulseTasks` 实现桥接；
- 保留的 `compatibilityCheck`：0.2 五制品源码/Archive Fixture 与可执行 Core Runtime 链接
  消费者；
- 两个隔离的暂存纯制品消费者；
- 候选版本一致性。

两个兼容聚合都消费暂存候选制品。有意修改 API 时，还必须对照当前已检入表面评审差异后才能更新
基线；新制品的首份基线也需要同样的显式评审。

### 发布聚合门禁

```bash
./gradlew mviReleaseCheck --stacktrace
```

发布聚合包含完整 Framework 门禁，并额外要求：

- `verifyPublicationBundle`：验证七个 Binary、Source Archive、Javadoc Archive、POM、
  Gradle Metadata、版本与内部依赖版本；
- `:mvi-testing:multiSeedStressCheck`；
- `:mvi-benchmarks:performanceRegressionCheck`；
- `verifyMavenCentralConfig`。

定时 `.github/workflows/stress.yml` 会独立执行 Stress 与 Performance 任务。Performance Harness
是可移植的灾难性回归下限，不是设备渲染 Benchmark。

### 托管设备门禁

```bash
./gradlew mviAndroidDeviceCheck --stacktrace
```

该任务会在托管 API 35 设备上运行示例端到端 Instrumentation。`.github/workflows/android-device.yml`
会在 PR 与 Push 上执行；稳定发布 Workflow 在独立 `device-check` Job 中运行同一任务，且只有
它与 `release-check` 同时通过才允许发布。

缺失 Task、缺少 API 基线、跳过纯制品消费者或发布包不完整都属于发布失败；不接受空门禁或
尽力而为式门禁。

## 准入命令

在 JDK 21 的干净 Checkout 中、不提供发布凭据，运行最小完整本地准入：

```bash
./gradlew clean mviReleaseCheck --stacktrace
./gradlew mviAndroidDeviceCheck --stacktrace
```

`mviReleaseCheck` 已包含 `mviFrameworkCheck`、`verifyPublicationBundle`、Stress、
Performance 与 Maven Metadata 验证。

只有在有意评审公开 API 时才更新基线：

```bash
./gradlew apiDump
git diff -- */api/*.api
./gradlew apiCheck
```

`apiDump` 差异不能自动批准自己。接受前必须评审删除项、签名变化、泛型边界、可见性、穷举式
Sealed 表面，以及三个 Release AAR 的公开 API。

## 后续稳定版发布规则

后续稳定版本 `X.Y.Z` 只有同时满足以下条件，才允许远程发布：

1. 受保护 Workflow 的目标与 GitHub Ref 都是准确的 Annotated Tag `vX.Y.Z`。
2. `POM_VERSION_NAME` 准确等于 `X.Y.Z`，并与 Tag 一致。
3. 版本不包含 `SNAPSHOT`、`RC` 或其他预发布后缀。
4. `release-check` 与 `device-check` 在 JDK 21 上针对同一提交通过。
5. `verifyMavenCentralConfig` 已验证必要 Metadata。
6. `publish` Job 同时依赖两个 Job，并发布该 Workflow 提交。

Publish Task 不能反向依赖 `mviReleaseCheck`；远程发布屏障由 Workflow Job 顺序负责，以避免
Gradle 依赖环。

## 后续发布验证

Maven Central 显示部署已发布后：

1. 只从 Maven Central 解析全部预期的 `io.github.magic-xu:*:X.Y.Z` 坐标，不使用 Local
   或 Staging Repository。
2. 验证每个 POM、Gradle Module Metadata、Source Archive、Javadoc Archive、Binary 及其签名。
3. 使用 `--refresh-dependencies` 构建并测试两个纯制品消费者。
4. 确认每个内部 Pulse 依赖都准确等于 `X.Y.Z`。
5. 只有此后才能标记为公开发布并宣布可用。

受保护 Workflow 会执行公共制品轮询并运行 `publicArtifactSamplesCheck`；本地 Staging 结果
不能替代该证据。

失败或不完整的候选不能重新打同一个 Tag，也不能覆盖。修复根因、选择新版本，并重新运行完整准入
序列。
