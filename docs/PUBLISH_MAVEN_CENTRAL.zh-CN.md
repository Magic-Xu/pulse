# 将 Pulse 0.3.0 发布到 Maven Central

英文版：[PUBLISH_MAVEN_CENTRAL.md](./PUBLISH_MAVEN_CENTRAL.md)

> `0.3.0-SNAPSHOT` 尚未发布。本文面向准备唯一稳定版 `v0.3.0` 的发布维护者。分支、snapshot、
> RC 和手动 workflow 都不能发布。

## 一次性准备

1. 在 [Sonatype Central Portal](https://central.sonatype.com/publishing) 验证
   `io.github.magic-xu` namespace 所有权。
2. 创建 Central Portal 发布 token。
3. 创建 ASCII-armored GPG 私钥和制品签名密码。
4. 添加以下 GitHub Actions secrets：
   - `MAVEN_CENTRAL_USERNAME`
   - `MAVEN_CENTRAL_PASSWORD`
   - `SIGNING_IN_MEMORY_KEY`
   - `SIGNING_IN_MEMORY_KEY_PASSWORD`

不要提交凭据或私钥。本地属性名和可选开发者模板记录在
`gradle/maven-central-secrets.template.properties`。

Maven Central 对坐标、签名、source archive 和 POM metadata 的要求参见
[发布要求](https://central.sonatype.org/publish/requirements/)。

## 准备候选版本

使用 JDK 21。创建发布提交前：

1. 把 `gradle.properties` 中的 `POM_VERSION_NAME` 设为 `0.3.0`。
2. 确认 group 为 `io.github.magic-xu`，并替换所有残留的开发者 metadata 占位值。
3. 评审有意修改的公开 API 及六份受控基线。只能在该评审中生成新基线，不能自动接受
   `apiDump` 差异。
4. 确认 release notes 和 migration 文档中的可用状态仍然准确。

运行与发布 workflow 相同的本地门禁：

```bash
./gradlew verifyMavenCentralConfig
./gradlew mviFrameworkCheck
./gradlew mviReleaseCheck
./gradlew mviAndroidDeviceCheck
```

`mviFrameworkCheck` 包含模块、Android、Compose、示例应用、API 基线、暂存制品、纯制品消费者、
五制品 0.2 兼容和版本检查。`mviReleaseCheck` 还会运行多 seed 压力检查和可移植性能下限 harness。
门禁通过不代表兼容或性能证据超出了这些 fixture 的实际覆盖范围。
`mviAndroidDeviceCheck` 会在托管 API 35 设备上运行示例端到端流程。

## 发布稳定 tag

官方发布路径是 `.github/workflows/publish-maven-central.yml`：

1. 提交已评审的 `0.3.0` 候选版本。
2. 在该提交上创建 annotated tag `v0.3.0`。
3. 推送提交和 tag，不移动或复用已有发布 tag。
4. 观察 `Publish Maven Central` workflow。

workflow 只由准确 tag `v0.3.0` 触发。`release-check` job 会验证 GitHub ref、
`POM_VERSION_NAME=0.3.0`、必要 metadata，并在 JDK 21 上运行 `mviReleaseCheck` 和托管设备测试。
`publish` job 显式依赖两个 job，并发布同一个 workflow commit。发布完成后，它会等待六个模块
的 POM、Gradle metadata、sources 和 binary 全部出现在 Maven Central，再用
`--refresh-dependencies` 运行 `publicArtifactSamplesCheck`；任一公共制品或纯制品消费者失败
都会让 workflow 失败。

不要为正式 v0.3.0 手动运行 `publishAndReleaseToMavenCentral`。远程发布只属于受保护的 workflow；
Gradle publish task 也不会反向依赖 `mviReleaseCheck`。

## 发布包

workflow 会用同一个版本发布六个制品：

- `mvi-core-contract`
- `mvi-core-runtime`
- `mvi-platform-android`
- `mvi-platform-android-compose`
- `mvi-extensions`
- `mvi-testing`

远程发布前，本地 staging 会检查每个 binary（`jar` 或 `aar`）、sources archive、POM、Gradle
module metadata、版本和内部 Pulse 依赖版本。

## 发布后

workflow 会等待 Central Portal 的完整公共制品传播，并只从 Maven Central 解析全部六个坐标，
不使用 local 或 staging repository。需要本地复现发布后门禁时运行：

```bash
./gradlew publicArtifactSamplesCheck --refresh-dependencies \
  -PpulsePublicVersion=0.3.0
```

正式宣布可用前，必须看到该 workflow step 和两个纯制品消费者都通过。

如果 `release-check` 失败，publish job 不会运行。应修复根因，不能削弱或绕过门禁。如果 tag 或
deployment 已对外可见，不要移动、复用或覆盖；选择新版本，并明确更新受保护的发布目标。
