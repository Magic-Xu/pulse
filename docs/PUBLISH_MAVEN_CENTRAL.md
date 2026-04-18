# Publish To Maven Central

Chinese version: [PUBLISH_MAVEN_CENTRAL.zh-CN.md](https://github.com/Magic-Xu/pulse/blob/master/docs/PUBLISH_MAVEN_CENTRAL.zh-CN.md)

This is the operational guide for publishing after replacing project-specific metadata.

## 0. One-time prerequisites

1. Prepare account and namespace in Sonatype Central Portal (`io.github.<your-github-id>` is recommended).
2. Create publishing token in Central Portal (username + password).
3. Prepare GPG private key (ASCII armored) and passphrase for signing.

Official references:

- Central publishing flow: [https://central.sonatype.com/publishing](https://central.sonatype.com/publishing)
- Publishing requirements (coordinates, signing, Javadoc/Sources, POM metadata): [https://central.sonatype.org/publish/requirements/](https://central.sonatype.org/publish/requirements/)

## 1. Replace TODO metadata in project

Edit TODO entries in [gradle.properties](https://github.com/Magic-Xu/pulse/blob/master/gradle.properties):

- `POM_DEVELOPER_NAME`
- `POM_DEVELOPER_EMAIL`
- and your intended `POM_GROUP_ID` / `POM_VERSION_NAME`

## 2. Configure local/CI secrets (do not commit)

Use template: [gradle/maven-central-secrets.template.properties](https://github.com/Magic-Xu/pulse/blob/master/gradle/maven-central-secrets.template.properties)

Recommended location: `~/.gradle/gradle.properties`

- `mavenCentralUsername`
- `mavenCentralPassword`
- `signingInMemoryKey`
- `signingInMemoryKeyPassword`

In CI, use environment variables with same keys or inject Gradle properties securely.

## 3. Pre-publish verification

```bash
./gradlew verifyMavenCentralConfig
./gradlew mviCoreCheck
./gradlew mviFrameworkCheck
```

## 4. Publish

```bash
./gradlew publishAndReleaseToMavenCentral
```

If you want to upload first and release later (manual confirmation):

```bash
./gradlew publishToMavenCentral
```

## 5. Common notes

- Never commit real tokens or private keys.
- Avoid `-SNAPSHOT` for public releases.
- If publish fails, check first:
  - POM metadata completeness
  - GPG key validity
  - Central Portal token validity
