# Publish To Maven Central

这份文档是“可直接替换信息后发布”的操作手册。

## 0. 先准备（一次性）

1. 在 Sonatype Central Portal 完成账号与 namespace 准备（建议使用 `io.github.<your-github-id>`）。
2. 在 Central Portal 创建发布 token（用户名 + 密码）。
3. 准备 GPG 私钥（ASCII armored）与密码，用于签名。

参考官方：

- Central Portal 发布入口与流程：[https://central.sonatype.com/publishing](https://central.sonatype.com/publishing)
- 发布要求（坐标、签名、Javadoc/Sources、POM 元数据）：[https://central.sonatype.org/publish/requirements/](https://central.sonatype.org/publish/requirements/)

## 1. 替换项目内 TODO 元数据

编辑 [gradle.properties](/Users/magic/Desktop/reborn/MVICore/gradle.properties) 中的 TODO：

- `POM_DEVELOPER_NAME`
- `POM_DEVELOPER_EMAIL`
- 以及你希望的 `POM_GROUP_ID`、`POM_VERSION_NAME`

## 2. 配置本地/CI 密钥（不要提交到仓库）

使用模板 [gradle/maven-central-secrets.template.properties](/Users/magic/Desktop/reborn/MVICore/gradle/maven-central-secrets.template.properties)。

推荐写到 `~/.gradle/gradle.properties`：

- `mavenCentralUsername`
- `mavenCentralPassword`
- `signingInMemoryKey`
- `signingInMemoryKeyPassword`

CI 中可使用同名环境变量或注入到 Gradle properties。

## 3. 发布前校验

```bash
./gradlew verifyMavenCentralConfig
./gradlew mviCoreCheck
./gradlew mviFrameworkCheck
```

## 4. 执行发布

```bash
./gradlew publishAndReleaseToMavenCentral
```

如果你只想先上传不释放（做人工确认），使用：

```bash
./gradlew publishToMavenCentral
```

## 5. 常见注意事项

- 不要在仓库中提交真实 token 和私钥。
- 对外发布版本不要用 `-SNAPSHOT`。
- 如果发布失败，优先检查：
  - POM 元数据是否完整
  - GPG 私钥是否可用
  - Central Portal token 是否有效
