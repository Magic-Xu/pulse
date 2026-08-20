# Publishing Pulse 0.3.0 to Maven Central

Chinese version: [PUBLISH_MAVEN_CENTRAL.zh-CN.md](./PUBLISH_MAVEN_CENTRAL.zh-CN.md)

> `0.3.0-SNAPSHOT` is not published. This guide is for the release maintainer preparing the one
> stable `v0.3.0` publication. Branches, snapshots, RCs, and manual workflow runs cannot publish.

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

Never commit credentials or private keys. The local property names and optional developer template
are documented in `gradle/maven-central-secrets.template.properties`.

Maven Central's required coordinates, signatures, source archives, and POM metadata are described in
the [publishing requirements](https://central.sonatype.org/publish/requirements/).

## Prepare the candidate

Use JDK 21. Before creating the release commit:

1. Set `POM_VERSION_NAME=0.3.0` in `gradle.properties`.
2. Confirm the group is `io.github.magic-xu` and replace any remaining placeholder developer
   metadata.
3. Review intentional public API changes and their six controlled baselines. Generate new baselines
   only as part of that review; do not accept an `apiDump` diff automatically.
4. Confirm release notes and migration documents still state the correct availability.

Run the same local gates used by the release workflow:

```bash
./gradlew verifyMavenCentralConfig
./gradlew mviFrameworkCheck
./gradlew mviReleaseCheck
./gradlew mviAndroidDeviceCheck
```

`mviFrameworkCheck` includes module, Android, Compose, sample app, API-baseline, staged-artifact,
artifact-consumer, five-artifact 0.2 compatibility, and version checks. `mviReleaseCheck` adds multi-seed stress and
the portable performance-floor harness. Passing these gates does not broaden the compatibility or
performance evidence beyond the fixtures they execute.
`mviAndroidDeviceCheck` runs the end-to-end sample flow on a managed API 35 device.

## Publish the stable tag

The official release path is `.github/workflows/publish-maven-central.yml`:

1. Commit the reviewed `0.3.0` candidate.
2. Create the annotated tag `v0.3.0` on that commit.
3. Push the commit and tag without moving or reusing an existing release tag.
4. Observe the `Publish Maven Central` workflow.

The workflow is triggered only by the exact tag `v0.3.0`. Its `release-check` job verifies that the
GitHub ref is that tag, `POM_VERSION_NAME` is exactly `0.3.0`, required metadata is present, and
`mviReleaseCheck` and the managed-device instrumentation job pass on JDK 21. The `publish` job has
explicit dependencies on both jobs and publishes the same workflow commit.

Do not run `publishAndReleaseToMavenCentral` manually for the official v0.3.0 release. Remote
publication belongs to the guarded workflow; Gradle publish tasks deliberately do not depend back on
`mviReleaseCheck`.

## Published bundle

The workflow publishes one version of each artifact:

- `mvi-core-contract`
- `mvi-core-runtime`
- `mvi-platform-android`
- `mvi-platform-android-compose`
- `mvi-extensions`
- `mvi-testing`

Local staging verifies each binary (`jar` or `aar`), sources archive, POM, Gradle module metadata,
version, and internal Pulse dependency version before remote publication.

## After publication

Wait until Central Portal reports the deployment as released, then resolve all six coordinates from
Maven Central without a local or staging repository. Re-run the two artifact-only consumers against
the public coordinates before announcing availability.

If `release-check` fails, no publish job runs. Fix the root cause and do not weaken or bypass a gate.
If a tag or deployment has already become externally visible, do not move, reuse, or overwrite it;
choose a new version and update the guarded release target deliberately.
