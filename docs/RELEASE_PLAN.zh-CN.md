# Pulse 0.5.0 发布规划

英文版：[RELEASE_PLAN.md](./RELEASE_PLAN.md)

> 状态：**已发布**，来源为准确的 Annotated Tag `v0.5.0`。[发布验证](https://github.com/Magic-Xu/pulse/actions/runs/34136328471)通过。

## 范围

本次新增精确 Mutation 结果并保留 Boolean 调用，将 Split Executor 耗时和 Mutation 关联到
原始 Core UI 请求。诊断保持有界、尽力交付；Android 测试宿主通过自身管理的 Probe 暴露
同一诊断。参见[发布说明](./RELEASE_NOTES_0.5.0.zh-CN.md)和[迁移指南](./MIGRATION_0.4_TO_0.5.zh-CN.md)。

Platform/Factory 负责生成应用的约定；消费者负责导航、输入反馈及 Feature 任务策略。
Pulse 继续独立于应用脚手架。

公开 API 或制品变更后，最终候选必须完成 API 评审、Framework、兼容检查、暂存消费者、
压力/性能及托管设备验证。每次发布验证自身的最终提交，历史发布证据不能替代本次验证。

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
- `compatibility04Check`：0.4 七制品冻结源码与 Archive 检查，包含 Android Split 测试宿主和
  JVM 二进制替换；
- `compatibility03Check`：六个 0.3 制品的冻结源码编译与 Archive 比较，以及 Baseline/Candidate
  JVM 运行、冻结的 0.3 Bytecode 在候选 Runtime 上运行和旧 `PulseTasks` 实现桥接；
- 保留的 `compatibilityCheck`：0.2 五制品源码/Archive Fixture 与可执行 Core Runtime 链接
  消费者；
- 两个隔离的暂存纯制品消费者；
- 候选版本一致性。

三个兼容聚合都消费暂存候选制品。有意修改 API 时，还必须对照当前已检入表面评审差异后才能更新
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
