# Publishing Pulse to Maven Central

Chinese version: [PUBLISH_MAVEN_CENTRAL.zh-CN.md](./PUBLISH_MAVEN_CENTRAL.zh-CN.md)

The guarded candidate is **0.5.0**, tagged exactly `v0.5.0`. Publication is not complete until every signed artifact and both public consumers pass verification.

## One-time setup

1. Verify ownership of `io.github.magic-xu` in the
   [Sonatype Central Portal](https://central.sonatype.com/publishing).
2. Create a Central Portal publishing token.
3. Create an ASCII-armored GPG private key and passphrase for artifact signing.
4. Add these GitHub Actions secrets:
   - `MAVEN_CENTRAL_USERNAME`
   - `MAVEN_CENTRAL_PASSWORD`
   - `SIGNING_IN_MEMORY_KEY`
   - `SIGNING_IN_MEMORY_KEY_PASSWORD`

Never commit credentials or private keys. Local property names and the optional developer template
are documented in `gradle/maven-central-secrets.template.properties`.

Maven Central's coordinate, signature, source archive, and POM requirements are described in the
[publishing requirements](https://central.sonatype.org/publish/requirements/).

## Prepare a future stable release

Use JDK 21. For a target version `X.Y.Z`, before creating the release commit:

1. Set `POM_VERSION_NAME=X.Y.Z` in `gradle.properties`.
2. Confirm the group is `io.github.magic-xu` and all required POM metadata is final.
3. Review intentional API changes against the current checked-in baselines. Review a new artifact's
   first baseline explicitly; do not accept an `apiDump` diff automatically.
4. Confirm every intended module publishes the same version and every internal Pulse dependency
   uses that version.
5. Keep release notes and migration documents marked candidate until public verification succeeds.

Run the minimal complete local qualification:

```bash
./gradlew clean mviReleaseCheck --stacktrace
./gradlew mviAndroidDeviceCheck --stacktrace
```

`mviReleaseCheck` includes `mviFrameworkCheck`, seven-artifact staging and bundle verification,
the seven-artifact 0.4, six-artifact 0.3, and retained five-artifact 0.2 compatibility fixtures, both staged
artifact-only consumers, multi-seed stress, performance floors, version consistency, and
`verifyMavenCentralConfig`.
`mviAndroidDeviceCheck` runs the sample end-to-end instrumentation suite on the managed API 35
device.

## Publish a future stable tag

The official release path is `.github/workflows/publish-maven-central.yml`:

1. Configure the workflow to target only the exact stable tag `vX.Y.Z`.
2. Commit the reviewed stable `X.Y.Z` candidate.
3. Create the annotated tag `vX.Y.Z` on that exact commit.
4. Push the commit and tag without moving or reusing an existing release tag.
5. Observe the `Publish Maven Central` workflow through public-consumer completion.

The workflow must be triggered only by the configured exact stable tag and enforce this sequence:

| Job | Current command or responsibility |
|---|---|
| `device-check` | `./gradlew mviAndroidDeviceCheck --stacktrace -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect` |
| `release-check` | Verify the exact tag and matching `POM_VERSION_NAME`, then run `./gradlew verifyMavenCentralConfig mviReleaseCheck --stacktrace` |
| `publish` | Depend on both prior jobs, run `./gradlew publishAndReleaseToMavenCentral --stacktrace`, wait for every public bundle, then run the public consumers |

All jobs use the same workflow commit on JDK 21. The `publish` job cannot run unless both
qualification jobs pass.

Do not run `publishAndReleaseToMavenCentral` manually for an official stable release. Remote
publication belongs to the guarded workflow; Gradle publish tasks deliberately do not depend back
on `mviReleaseCheck`.

## Required publication bundle

The workflow publishes one version of each artifact:

- `mvi-core-contract`
- `mvi-core-runtime`
- `mvi-platform-android`
- `mvi-platform-android-compose`
- `mvi-platform-android-testing`
- `mvi-extensions`
- `mvi-testing`

Local staging verifies each binary—JAR or AAR—plus its sources and javadoc archives, POM, Gradle
module metadata, version, and internal Pulse dependency versions. The three Android artifacts
publish AAR binaries; the other four publish JAR binaries.

## Verify a future publication

The workflow must poll Maven Central for every artifact's POM, Gradle metadata, sources archive,
javadoc archive, binary, and their signatures, then build both isolated consumers from Maven
Central only. To reproduce the post-publication consumer gate for `X.Y.Z` locally:

```bash
./gradlew publicArtifactSamplesCheck --refresh-dependencies --stacktrace \
  -PpulsePublicVersion=X.Y.Z
```

Do not announce availability or remove candidate notices until every intended bundle is public and
`publicArtifactSamplesCheck` passes.

If `release-check` or `device-check` fails, no publish job runs. Fix the root cause and do not
weaken or bypass a gate. If a tag or deployment has become externally visible, do not move, reuse,
or overwrite it; choose a new version and deliberately update the guarded release target.
