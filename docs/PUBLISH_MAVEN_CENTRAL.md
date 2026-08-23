# Publishing Pulse 0.4.0 to Maven Central

Chinese version: [PUBLISH_MAVEN_CENTRAL.zh-CN.md](./PUBLISH_MAVEN_CENTRAL.zh-CN.md)

> Status: **release candidate — not public yet**. `0.3.0` remains the public stable release.
> Branches, snapshots, RCs, different tags, and manual Gradle publication are not the official
> `0.4.0` release path.

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

## Prepare the candidate

Use JDK 21. Before creating the release commit:

1. Set `POM_VERSION_NAME=0.4.0` in `gradle.properties`.
2. Confirm the group is `io.github.magic-xu` and all required POM metadata is final.
3. Review intentional API changes for the six existing artifacts against their 0.3 baselines and
   review the first baseline of `mvi-platform-android-testing`. Generate baselines only as part of
   that review; do not accept an `apiDump` diff automatically.
4. Confirm all seven modules publish the same version and all internal Pulse dependencies use that
   version.
5. Keep release notes and migration documents marked candidate until public verification succeeds.

Run the minimal complete local qualification:

```bash
./gradlew clean mviReleaseCheck --stacktrace
./gradlew mviAndroidDeviceCheck --stacktrace
```

`mviReleaseCheck` includes `mviFrameworkCheck`, seven-artifact staging and bundle verification,
the six-artifact 0.3 and retained five-artifact 0.2 compatibility fixtures, both staged
artifact-only consumers, multi-seed stress, performance floors, version consistency, and
`verifyMavenCentralConfig`.
`mviAndroidDeviceCheck` runs the sample end-to-end instrumentation suite on the managed API 35
device.

## Publish the stable tag

The only official release path is `.github/workflows/publish-maven-central.yml`:

1. Commit the reviewed stable `0.4.0` candidate.
2. Create the annotated tag `v0.4.0` on that exact commit.
3. Push the commit and tag without moving or reusing an existing release tag.
4. Observe the `Publish Maven Central` workflow through public-consumer completion.

The workflow is triggered only by the exact tag `v0.4.0` and enforces this sequence:

| Job | Current command or responsibility |
|---|---|
| `device-check` | `./gradlew mviAndroidDeviceCheck --stacktrace -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect` |
| `release-check` | Verify exact tag and `POM_VERSION_NAME=0.4.0`, then run `./gradlew verifyMavenCentralConfig mviReleaseCheck --stacktrace` |
| `publish` | Depend on both prior jobs, run `./gradlew publishAndReleaseToMavenCentral --stacktrace`, wait for seven public bundles, then run the public consumers |

All jobs use the same workflow commit on JDK 21. The `publish` job cannot run unless both
qualification jobs pass.

Do not run `publishAndReleaseToMavenCentral` manually for the official v0.4.0 release. Remote
publication belongs to the guarded workflow; Gradle publish tasks deliberately do not depend back
on `mviReleaseCheck`.

## Published bundle

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

## After publication

The workflow polls Maven Central for every artifact's POM, Gradle metadata, sources archive,
javadoc archive, binary, and their signatures, then builds both isolated consumers from Maven
Central only. To reproduce the post-publication consumer gate locally:

```bash
./gradlew publicArtifactSamplesCheck --refresh-dependencies --stacktrace \
  -PpulsePublicVersion=0.4.0
```

Do not announce availability or remove candidate notices until all seven bundles are public and
`publicArtifactSamplesCheck` passes.

If `release-check` or `device-check` fails, no publish job runs. Fix the root cause and do not
weaken or bypass a gate. If a tag or deployment has become externally visible, do not move, reuse,
or overwrite it; choose a new version and deliberately update the guarded release target.
