# Pulse 0.3.0 发布规划

英文版：[RELEASE_PLAN.md](./RELEASE_PLAN.md)

> 发布状态：稳定版 `0.3.0`，**已于 2026-08-23 从准确的 `v0.3.0` tag 发布**。分支、snapshot、
> RC 和其他预发布 tag 都不能触发发布。

## 发布目标

发布一个内部一致的 v0.3：有序运行时、公开契约、Android 生命周期绑定、扩展、测试工具、兼容
检查和文档必须相互匹配。便利 DSL 不能早于其依赖的运行时契约单独发布。

## 候选阶段

| 阶段 | 必须达到的结果 |
|---|---|
| 0. 基线 | 行为变化前已有冻结的 0.2 源码/二进制消费者和失败契约用例 |
| 1. 公开 API | runtime、effect、task、lifecycle 和 failure 决策已接受并落实到契约 |
| 2. Core runtime | 单 FIFO processor 统一拥有 reduce、commit、transition、effect、completion 和 close 顺序 |
| 3. Split、task、effect | UI 输入边界、按键任务策略、token 校验和 replay-zero effect 完整 |
| 4. Android 与 Compose | Main 线程、owner、lifecycle、saved state 和清理契约均已验证 |
| 5. Testing 与 extensions | 公开 probe/TCK 和状态拆分辅助能力构建在稳定运行时表面之上 |
| 6. 文档 | 迁移、兼容、消费方和发布文档与候选制品一致 |
| 7. 发布候选 | framework、compatibility、artifact、stress、performance 和 publication 门禁全部通过 |

后续阶段不能为了通过自身检查而削弱更早阶段的契约。候选准入后如再修改公开 API，必须重新运行
API、兼容、纯制品消费者、压力和性能验证。

## 六个发布制品

所有制品使用 group `io.github.magic-xu`，且版本必须完全一致。

| 制品 | 职责 |
|---|---|
| `mvi-core-contract` | 平台无关的旧契约与 0.3 契约 |
| `mvi-core-runtime` | 有序 Store、effect 协调者、task、配置与旧适配器 |
| `mvi-platform-android` | ViewModel、显式 owner、Main dispatcher 与 saved-state 集成 |
| `mvi-platform-android-compose` | 生命周期感知的 state selector 与 UI-effect 协调 |
| `mvi-extensions` | 可选 plugin、`StateLens` 与 reducer 组合 |
| `mvi-testing` | 虚拟时间辅助能力、probe 与 Store TCK |

示例应用、兼容性 fixture、隔离消费者和 benchmark 都是验证输入，不是发布制品。

## 必须通过的门禁

### PR framework 门禁

```bash
./gradlew mviFrameworkCheck
```

此门禁必须覆盖：

- contract、runtime、extensions 和 testing 模块的 `check`；
- Android 与 Compose 单元测试和 `lintDebug`；
- 示例应用单元测试、`assembleDebug` 和 `lintDebug`；
- 六个发布制品的受控 API 基线；
- 对五个既有制品运行冻结的 0.2 Kotlin 源码面与 archive 级兼容比较，并执行 core-runtime
  二进制链接消费者；
- 只消费暂存 Maven 制品的两个隔离示例消费者；
- 发布版本一致性。

### 发布专用门禁

```bash
./gradlew mviReleaseCheck
```

发布聚合必须包含完整 framework 门禁，并额外覆盖：

- `verifyPublicationBundle`：校验六个 binary、source archive、POM、Gradle metadata、版本及内部
  依赖版本；
- `:mvi-testing:multiSeedStressCheck`：多组确定 seed 的 10,000 输入压力检查；
- `:mvi-benchmarks:performanceRegressionCheck`：吞吐、frame/collector p95 延迟、内存残留、
  有界 mailbox 下限，以及真实的平台无关 selector 流水线。

五制品兼容 fixture 会在源码与 archive 层验证 Android、Compose 和 extensions；运行时替换由
core-runtime 消费者执行。性能 harness 用于发现灾难性回归，不是设备渲染 benchmark；Compose
生命周期与重组行为由对应测试覆盖。

真实 Android instrumentation 需要模拟器或连接设备，因此使用独立设备门禁：

```bash
./gradlew mviAndroidDeviceCheck
```

PR 由 `.github/workflows/android-device.yml` 执行；稳定版发布 workflow 运行同一个托管设备任务，
且只有 device job 与 `release-check` 同时通过才允许发布。

远程发布前还必须运行 `verifyMavenCentralConfig`。缺失 task、跳过制品消费者或缺少 API 基线都
属于发布失败；不接受空任务或尽力而为式门禁。

## 候选验证命令

在干净 checkout 中使用 JDK 21 运行，且此阶段不提供发布凭据：

```bash
./gradlew clean
./gradlew mviFrameworkCheck
./gradlew mviReleaseCheck
./gradlew mviAndroidDeviceCheck
```

只有在有意评审公开 API 时才更新基线：

```bash
./gradlew apiDump
git diff -- */api/*.api
./gradlew apiCheck
```

`apiDump` 差异不能自动批准自己。接受基线前必须评审删除项、签名变化、泛型边界和 Android
release AAR 的公开表面。

## 稳定版发布规则

只有同时满足以下条件，才允许远程发布：

1. GitHub 正在处理准确 tag `v0.3.0`。
2. `POM_VERSION_NAME` 准确等于 `0.3.0`，并与 tag 一致。
3. 版本不包含 `SNAPSHOT`、`RC` 或其他预发布后缀。
4. release-check 与托管设备 instrumentation job 已在 JDK 21 上通过。
5. `verifyMavenCentralConfig` 已验证必要 metadata。
6. publish job 同时依赖两个 job，并使用同一个 commit。

publish task 不能反向依赖 `mviReleaseCheck`；远程发布屏障由 job 顺序负责，以避免 Gradle 任务
依赖环。

## 发布后验证

Maven Central 显示部署已发布后：

1. 只从 Maven Central 解析全部六个 `io.github.magic-xu:*:0.3.0` 坐标，不使用本地或 staging
   仓库。
2. 针对这些坐标重新构建同步和异步纯制品消费者。
3. 确认 POM 和 Gradle metadata 中的内部依赖全部为 `0.3.0`。
4. 只有解析成功后，才发布 release notes 和 migration 链接。

受保护的发布 workflow 会等待六个模块的 POM、Gradle metadata、sources 和 binary 公开，并使用 `--refresh-dependencies` 执行
`publicArtifactSamplesCheck`。该 step 是完成定义的一部分，不能用本地 staging 结果替代。

失败或不完整的候选版本不能重新打同一个 tag，也不能覆盖。修复根因、选择新版本，并重新运行完整
准入流程。
