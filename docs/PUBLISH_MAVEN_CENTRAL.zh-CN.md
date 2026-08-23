# 将 Pulse 0.4.0 发布到 Maven Central

英文版：[PUBLISH_MAVEN_CENTRAL.md](./PUBLISH_MAVEN_CENTRAL.md)

> 状态：**发布候选，尚未公开**。当前公开稳定版仍是 `0.3.0`。分支、Snapshot、RC、其他
> Tag 和手动 Gradle 发布都不是正式 `0.4.0` 发布路径。

## 一次性准备

1. 在 [Sonatype Central Portal](https://central.sonatype.com/publishing) 验证
   `io.github.magic-xu` Namespace 所有权。
2. 创建 Central Portal 发布 Token。
3. 创建 ASCII-armored GPG 私钥和制品签名密码。
4. 添加以下 GitHub Actions Secrets：
   - `MAVEN_CENTRAL_USERNAME`
   - `MAVEN_CENTRAL_PASSWORD`
   - `SIGNING_IN_MEMORY_KEY`
   - `SIGNING_IN_MEMORY_KEY_PASSWORD`

不要提交凭据或私钥。本地属性名和可选开发者模板记录在
`gradle/maven-central-secrets.template.properties`。

Maven Central 对坐标、签名、Source Archive 和 POM Metadata 的要求参见
[发布要求](https://central.sonatype.org/publish/requirements/)。

## 准备候选版本

使用 JDK 21。创建发布提交前：

1. 把 `gradle.properties` 中的 `POM_VERSION_NAME` 设为 `0.4.0`。
2. 确认 Group 为 `io.github.magic-xu`，且所有必要 POM Metadata 已定稿。
3. 对照 0.3 基线评审六个既有制品的有意 API 变化，并评审
   `mvi-platform-android-testing` 的首份基线。只能在该评审中生成基线，不能自动接受
   `apiDump` 差异。
4. 确认七个模块发布同一版本，且所有内部 Pulse 依赖都使用该版本。
5. 在公共验证成功前，Release Notes 与 Migration 文档必须保持候选状态。

运行最小完整本地准入：

```bash
./gradlew clean mviReleaseCheck --stacktrace
./gradlew mviAndroidDeviceCheck --stacktrace
```

`mviReleaseCheck` 包含 `mviFrameworkCheck`、七制品暂存与发布包验证、0.3 六制品与保留的
0.2 五制品兼容 Fixture、两个暂存纯制品消费者、多 Seed 压力、性能下限、版本一致性与
`verifyMavenCentralConfig`。`mviAndroidDeviceCheck` 会在托管 API 35 设备上运行示例端到端
Instrumentation。

## 发布稳定 Tag

唯一正式发布路径是 `.github/workflows/publish-maven-central.yml`：

1. 提交已评审的稳定 `0.4.0` 候选版本。
2. 在该准确提交上创建 Annotated Tag `v0.4.0`。
3. 推送提交和 Tag，不移动或复用已有发布 Tag。
4. 持续观察 `Publish Maven Central` Workflow，直到公共消费者完成。

Workflow 只由准确 Tag `v0.4.0` 触发，并强制以下顺序：

| Job | 当前命令或职责 |
|---|---|
| `device-check` | `./gradlew mviAndroidDeviceCheck --stacktrace -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect` |
| `release-check` | 验证准确 Tag 与 `POM_VERSION_NAME=0.4.0`，再运行 `./gradlew verifyMavenCentralConfig mviReleaseCheck --stacktrace` |
| `publish` | 同时依赖前两个 Job，运行 `./gradlew publishAndReleaseToMavenCentral --stacktrace`，等待七个公共发布包，再运行公共消费者 |

所有 Job 都在 JDK 21 上使用同一个 Workflow 提交；两个准入 Job 未同时通过时，`publish`
不能运行。

不要为正式 v0.4.0 手动运行 `publishAndReleaseToMavenCentral`。远程发布只属于受保护
Workflow；Gradle Publish Task 也不会反向依赖 `mviReleaseCheck`。

## 发布包

Workflow 会用同一个版本发布七个制品：

- `mvi-core-contract`
- `mvi-core-runtime`
- `mvi-platform-android`
- `mvi-platform-android-compose`
- `mvi-platform-android-testing`
- `mvi-extensions`
- `mvi-testing`

本地 Staging 会验证每个 Binary（JAR 或 AAR）、Source Archive、Javadoc Archive、POM、
Gradle Module Metadata、版本和内部 Pulse 依赖版本。三个 Android 制品发布 AAR，其余四个
发布 JAR。

## 发布后

Workflow 会轮询 Maven Central 中每个制品的 POM、Gradle Metadata、Source Archive、
Javadoc Archive、Binary 及其签名，再让两个隔离消费者只从 Maven Central 构建。需要本地
复现发布后消费者门禁时运行：

```bash
./gradlew publicArtifactSamplesCheck --refresh-dependencies --stacktrace \
  -PpulsePublicVersion=0.4.0
```

七个发布包全部公开且 `publicArtifactSamplesCheck` 通过前，不得宣布可用，也不能移除候选状态。

如果 `release-check` 或 `device-check` 失败，Publish Job 不会运行。应修复根因，不能削弱或
绕过门禁。如果 Tag 或 Deployment 已对外可见，不要移动、复用或覆盖；选择新版本，并明确更新
受保护的发布目标。
