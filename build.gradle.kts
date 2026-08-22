import groovy.json.JsonSlurper
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.Sync
import org.gradle.plugins.signing.SigningExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.dokka) apply false
}

val publishableModules = setOf(
    "mvi-core-contract",
    "mvi-core-runtime",
    "mvi-platform-android",
    "mvi-platform-android-compose",
    "mvi-extensions",
    "mvi-testing",
)
val androidPublishedModules = setOf(
    "mvi-platform-android",
    "mvi-platform-android-compose",
)
val nativeBcvModules = publishableModules - androidPublishedModules
val pulseGroup = providers.gradleProperty("POM_GROUP_ID").orElse("io.github.magic-xu")
val pulseVersion = providers.gradleProperty("POM_VERSION_NAME").orElse("0.3.0-SNAPSHOT")
val pulseStagingRepository = layout.buildDirectory.dir("staging-repo")
val publicPulseRepository = providers.gradleProperty("pulsePublicRepository")
    .orElse("https://repo.maven.apache.org/maven2")
val publicPulseVersion = providers.gradleProperty("pulsePublicVersion").orElse(pulseVersion)

apiValidation {
    ignoredProjects.addAll(
        subprojects
            .map { it.name }
            .filterNot { it in publishableModules }
    )
}

allprojects {
    group = providers.gradleProperty("POM_GROUP_ID").orElse("io.github.magic-xu").get()
    version = pulseVersion.get()
}

subprojects {
    if (name in publishableModules) {
        apply(plugin = "com.vanniktech.maven.publish")
    }
}

// BCV 0.18.1 does not discover AGP 9 built-in Kotlin compilations. These explicit bridge tasks
// feed each release AAR's classes.jar into the same BCV build/compare engine used by JVM modules.
val kotlinMetadataVersion = libs.versions.kotlin.get()
val androidApiTasks = androidPublishedModules.associateWith { moduleName ->
    project(":$moduleName").run {
        dependencies.add(
            "bcv-rt-jvm-cp",
            "org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinMetadataVersion",
        )

        val releaseAar = layout.buildDirectory.file("outputs/aar/$moduleName-release.aar")
        val extractedClassesDirectory =
            layout.buildDirectory.dir("intermediates/release-aar-api")
        val extractedClassesJar = extractedClassesDirectory.map { it.file("classes.jar") }

        val extractReleaseAarClasses = tasks.register<Sync>("extractReleaseAarClassesForApi") {
            description = "Extracts classes.jar from the release AAR for explicit BCV validation."
            dependsOn("bundleReleaseAar")
            from(releaseAar.map { zipTree(it.asFile) }) {
                include("classes.jar")
            }
            into(extractedClassesDirectory)
        }

        val releaseAarApiBuild = tasks.register<KotlinApiBuildTask>("releaseAarApiBuild") {
            description = "Builds a deterministic BCV dump from the AGP 9 release AAR."
            dependsOn(extractReleaseAarClasses)
            inputJar.set(extractedClassesJar)
            outputApiFile.set(layout.buildDirectory.file("api/$moduleName.api"))
            runtimeClasspath.from(configurations.named("bcv-rt-jvm-cp-resolver"))
        }

        val releaseAarApiDump = tasks.register<Copy>("releaseAarApiDump") {
            group = "other"
            description = "Updates the controlled API baseline from the release AAR BCV dump."
            from(releaseAarApiBuild.flatMap { it.outputApiFile })
            into(layout.projectDirectory.dir("api"))
            includeEmptyDirs = false
        }

        val releaseAarApiCheck = tasks.register<KotlinApiCompareTask>("releaseAarApiCheck") {
            group = "verification"
            description = "Checks the release AAR public API against its controlled baseline."
            projectApiFile.set(layout.projectDirectory.file("api/$moduleName.api"))
            generatedApiFile.set(releaseAarApiBuild.flatMap { it.outputApiFile })
        }

        releaseAarApiDump to releaseAarApiCheck
    }
}

tasks.register("apiDump") {
    group = "other"
    description = "Updates API baselines for all six published Pulse modules."
    dependsOn(nativeBcvModules.map { ":$it:apiDump" })
    dependsOn(androidApiTasks.values.map { it.first })
}

tasks.register("apiCheck") {
    group = "verification"
    description = "Checks API baselines for all six published Pulse modules."
    dependsOn(nativeBcvModules.map { ":$it:apiCheck" })
    dependsOn(androidApiTasks.values.map { it.second })
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral()
            signAllPublications()
        }
        configure<SigningExtension> {
            // Vanniktech requires every stable publication to be signed by default. The local
            // PulseStaging repository is an isolated verification input, not a remote release.
            // Requiring a signatory only when a Maven Central task is actually selected keeps
            // ordinary CI secret-free while a stable Central publication still fails without keys.
            setRequired({
                !project.version.toString().endsWith("-SNAPSHOT") &&
                    gradle.taskGraph.allTasks.any { task ->
                        task.name.contains("MavenCentral", ignoreCase = true)
                    }
            })
        }
    }
    plugins.withId("maven-publish") {
        configure<PublishingExtension> {
            repositories.maven {
                name = "PulseStaging"
                url = pulseStagingRepository.get().asFile.toURI()
            }
        }
    }
}

val cleanPulseStagingRepository = tasks.register<Delete>("cleanPulseStagingRepository") {
    delete(pulseStagingRepository)
}

val stagingPublicationTasks = publishableModules.map { moduleName ->
    project(":$moduleName").tasks.named("publishAllPublicationsToPulseStagingRepository").also {
        it.configure { mustRunAfter(cleanPulseStagingRepository) }
    }
}

tasks.register("stagePulsePublications") {
    group = "publishing"
    description = "Publishes all six candidate modules into an isolated local Maven repository."
    dependsOn(cleanPulseStagingRepository)
    dependsOn(stagingPublicationTasks)
}

tasks.register("verifyPublicationBundle") {
    group = "verification"
    description = "Verifies candidate artifacts, sources, POMs, metadata, versions, and dependencies."
    dependsOn("stagePulsePublications")
    doLast {
        val repository = pulseStagingRepository.get().asFile
        val version = pulseVersion.get()
        val groupPath = pulseGroup.get().replace('.', '/')
        publishableModules.forEach { moduleName ->
            val moduleDirectory = repository.resolve("$groupPath/$moduleName/$version")
            val binaryExtension = if (moduleName in androidPublishedModules) "aar" else "jar"

            fun publicationFile(
                description: String,
                matches: (String) -> Boolean,
            ): File {
                val candidates = moduleDirectory.listFiles()
                    .orEmpty()
                    .filter { file -> file.isFile && file.name.startsWith("$moduleName-") }
                    .filter { file -> matches(file.name) }
                check(candidates.size == 1) {
                    "Candidate publication $moduleName must contain exactly one $description; " +
                        "found ${candidates.map(File::getName).sorted()}."
                }
                return candidates.single()
            }

            publicationFile("$binaryExtension binary") { name ->
                name.endsWith(".$binaryExtension") &&
                    (binaryExtension != "jar" ||
                        (!name.endsWith("-sources.jar") && !name.endsWith("-javadoc.jar")))
            }
            publicationFile("sources JAR") { name -> name.endsWith("-sources.jar") }
            val pomFile = publicationFile("POM") { name -> name.endsWith(".pom") }
            val metadataFile = publicationFile("Gradle module metadata") { name ->
                name.endsWith(".module")
            }

            val pom = pomFile.readText()
            check("<groupId>${pulseGroup.get()}</groupId>" in pom) {
                "$moduleName POM has an unexpected groupId."
            }
            check("<artifactId>$moduleName</artifactId>" in pom) {
                "$moduleName POM has an unexpected artifactId."
            }
            check("<version>$version</version>" in pom) {
                "$moduleName POM has an unexpected version."
            }
            Regex("<dependency>(.*?)</dependency>", RegexOption.DOT_MATCHES_ALL)
                .findAll(pom)
                .map { it.groupValues[1] }
                .filter { "<groupId>${pulseGroup.get()}</groupId>" in it }
                .forEach { dependency ->
                    check("<version>$version</version>" in dependency) {
                        "$moduleName POM contains an internal dependency with the wrong version."
                    }
                }

            val metadata = JsonSlurper().parse(metadataFile) as? Map<*, *>
                ?: error("$moduleName Gradle metadata root must be a JSON object.")
            val component = metadata["component"] as? Map<*, *>
                ?: error("$moduleName Gradle metadata is missing its component object.")
            check(component["group"] == pulseGroup.get()) {
                "$moduleName Gradle metadata has an unexpected group."
            }
            check(component["module"] == moduleName) {
                "$moduleName Gradle metadata has an unexpected module name."
            }
            check(component["version"] == version) {
                "$moduleName Gradle metadata has an unexpected version."
            }

            val variants = metadata["variants"] as? List<*>
                ?: error("$moduleName Gradle metadata is missing its variants array.")
            variants.forEach { variantValue ->
                val variant = variantValue as? Map<*, *>
                    ?: error("$moduleName Gradle metadata contains a non-object variant.")
                val variantName = variant["name"] as? String ?: "<unnamed>"
                listOf("dependencies", "dependencyConstraints").forEach { dependencyKey ->
                    val dependencies = variant[dependencyKey] as? List<*> ?: emptyList<Any>()
                    dependencies.forEach dependencyLoop@{ dependencyValue ->
                        val dependency = dependencyValue as? Map<*, *>
                            ?: error(
                                "$moduleName $variantName contains a non-object $dependencyKey entry."
                            )
                        if (dependency["group"] != pulseGroup.get()) return@dependencyLoop

                        val dependencyModule = dependency["module"] as? String
                            ?: error(
                                "$moduleName $variantName contains an internal dependency " +
                                    "without a module name."
                            )
                        check(dependencyModule in publishableModules) {
                            "$moduleName $variantName references unpublished internal module " +
                                "$dependencyModule."
                        }
                        val versionConstraint = dependency["version"] as? Map<*, *>
                            ?: error(
                                "$moduleName $variantName internal dependency $dependencyModule " +
                                    "has no structured version constraint."
                            )
                        val declaredVersions = listOf("requires", "strictly", "prefers")
                            .mapNotNull { key ->
                                (versionConstraint[key] as? String)?.let { value -> key to value }
                            }
                        check(declaredVersions.isNotEmpty()) {
                            "$moduleName $variantName internal dependency $dependencyModule " +
                                "does not declare a candidate version."
                        }
                        val mismatchedVersions = declaredVersions.filter { (_, value) ->
                            value != version
                        }
                        check(mismatchedVersions.isEmpty()) {
                            "$moduleName $variantName internal dependency $dependencyModule " +
                                "has versions $mismatchedVersions; expected $version."
                        }
                    }
                }
            }
        }
    }
}

fun GradleBuild.configureCandidateConsumer(
    buildDirectoryPath: String,
    requestedTasks: List<String>,
) {
    dir = file(buildDirectoryPath)
    tasks = requestedTasks
    startParameter.projectProperties = startParameter.projectProperties + mapOf(
        "pulseRepository" to pulseStagingRepository.get().asFile.absolutePath,
        "pulseVersion" to pulseVersion.get(),
    )
}

val simpleSyncArtifactSampleCheck = tasks.register<GradleBuild>("simpleSyncArtifactSampleCheck") {
    group = "verification"
    description = "Builds the isolated synchronous consumer from staged Maven artifacts only."
    dependsOn("verifyPublicationBundle")
    configureCandidateConsumer(
        buildDirectoryPath = "samples/simple-sync-consumer",
        requestedTasks = listOf(":consumer:assembleDebug"),
    )
}

val asyncLatestArtifactSampleCheck = tasks.register<GradleBuild>("asyncLatestArtifactSampleCheck") {
    group = "verification"
    description = "Builds and tests the isolated async Latest consumer from staged artifacts only."
    dependsOn("verifyPublicationBundle")
    configureCandidateConsumer(
        buildDirectoryPath = "samples/async-latest-consumer",
        requestedTasks = listOf(":consumer:assembleDebug", ":consumer:testDebugUnitTest"),
    )
}

tasks.register("artifactSamplesCheck") {
    group = "verification"
    description = "Verifies both artifact-only consumer builds."
    dependsOn(simpleSyncArtifactSampleCheck, asyncLatestArtifactSampleCheck)
}

fun GradleBuild.configurePublicConsumer(
    buildDirectoryPath: String,
    requestedTasks: List<String>,
) {
    dir = file(buildDirectoryPath)
    tasks = requestedTasks
    startParameter.projectProperties = startParameter.projectProperties + mapOf(
        "pulseRepository" to publicPulseRepository.get(),
        "pulseVersion" to publicPulseVersion.get(),
    )
    doFirst {
        val version = publicPulseVersion.get()
        require(!version.endsWith("-SNAPSHOT")) {
            "publicArtifactSamplesCheck requires a stable -PpulsePublicVersion."
        }
    }
}

val publicSimpleSyncArtifactSampleCheck =
    tasks.register<GradleBuild>("publicSimpleSyncArtifactSampleCheck") {
        group = "verification"
        description = "Builds the synchronous consumer from the public Pulse repository only."
        configurePublicConsumer(
            buildDirectoryPath = "samples/simple-sync-consumer",
            requestedTasks = listOf(":consumer:assembleDebug"),
        )
    }

val publicAsyncLatestArtifactSampleCheck =
    tasks.register<GradleBuild>("publicAsyncLatestArtifactSampleCheck") {
        group = "verification"
        description = "Builds and tests the async consumer from the public Pulse repository only."
        configurePublicConsumer(
            buildDirectoryPath = "samples/async-latest-consumer",
            requestedTasks = listOf(":consumer:assembleDebug", ":consumer:testDebugUnitTest"),
        )
    }

tasks.register("publicArtifactSamplesCheck") {
    group = "verification"
    description = "Verifies both consumers against a stable public Pulse release."
    dependsOn(publicSimpleSyncArtifactSampleCheck, publicAsyncLatestArtifactSampleCheck)
}

tasks.register<GradleBuild>("compatibilityCheck") {
    group = "verification"
    description = "Checks all five v0.2 artifact surfaces against the staged candidate."
    dependsOn("verifyPublicationBundle")
    configureCandidateConsumer(
        buildDirectoryPath = "compatibility/consumer-0.2",
        requestedTasks = listOf("compatibilityCheck"),
    )
}

tasks.register("verifyVersionConsistency") {
    group = "verification"
    description = "Checks candidate module versions and an optional stable release tag."
    doLast {
        val expected = pulseVersion.get()
        check(Regex("\\d+\\.\\d+\\.\\d+(?:-SNAPSHOT)?").matches(expected)) {
            "POM_VERSION_NAME must be a semantic release or SNAPSHOT version: $expected"
        }
        val mismatched = publishableModules.filter { project(":$it").version.toString() != expected }
        check(mismatched.isEmpty()) {
            "Published module versions differ from POM_VERSION_NAME: ${mismatched.joinToString()}"
        }
        val tag = providers.environmentVariable("GITHUB_REF_NAME").orNull
        if (tag?.startsWith("v") == true) {
            check(!expected.endsWith("-SNAPSHOT")) { "A stable tag cannot publish a SNAPSHOT version." }
            check(tag == "v$expected") { "Release tag $tag does not match v$expected." }
        }
    }
}

tasks.register("mviCoreCheck") {
    group = "verification"
    description = "Checks non-Android Pulse library modules."
    dependsOn(
        ":mvi-core-contract:check",
        ":mvi-core-runtime:check",
        ":mvi-extensions:check",
        ":mvi-testing:check",
    )
}

tasks.register("mviFrameworkCheck") {
    group = "verification"
    description = "Runs the complete pull-request gate for Pulse and its sample app."
    dependsOn(
        "mviCoreCheck",
        ":mvi-platform-android:testDebugUnitTest",
        ":mvi-platform-android:lintDebug",
        ":mvi-platform-android-compose:testDebugUnitTest",
        ":mvi-platform-android-compose:lintDebug",
        ":app:testDebugUnitTest",
        ":app:assembleDebug",
        ":app:lintDebug",
        "apiCheck",
        "compatibilityCheck",
        "artifactSamplesCheck",
        "verifyVersionConsistency",
    )
}

tasks.register("mviAndroidDeviceCheck") {
    group = "verification"
    description = "Runs the app end-to-end instrumentation suite on the managed API 35 device."
    dependsOn(":app:pulseApi35DebugAndroidTest")
}

tasks.register("mviReleaseCheck") {
    group = "verification"
    description = "Runs every framework, publication, stress, and performance release gate."
    dependsOn(
        tasks.named("mviFrameworkCheck"),
        tasks.named("verifyPublicationBundle"),
        project(":mvi-testing").tasks.named("multiSeedStressCheck"),
        project(":mvi-benchmarks").tasks.named("performanceRegressionCheck"),
        "verifyMavenCentralConfig",
    )
}

tasks.register("verifyMavenCentralConfig") {
    group = "publishing"
    description = "Validates Maven Central metadata placeholders before publishing."
    doLast {
        val required = listOf(
            "POM_NAME",
            "POM_DESCRIPTION",
            "POM_URL",
            "POM_INCEPTION_YEAR",
            "POM_LICENSE_NAME",
            "POM_LICENSE_URL",
            "POM_DEVELOPER_ID",
            "POM_DEVELOPER_NAME",
            "POM_DEVELOPER_EMAIL",
            "POM_SCM_URL",
            "POM_SCM_CONNECTION",
            "POM_SCM_DEV_CONNECTION",
            "POM_GROUP_ID",
            "POM_VERSION_NAME",
        )
        val invalid = required.filter { key ->
            val value = providers.gradleProperty(key).orNull.orEmpty()
            value.isBlank() || value.contains("TODO_REPLACE")
        }
        if (invalid.isNotEmpty()) {
            error(
                "Maven Central publishing metadata is incomplete. " +
                    "Please replace TODO values for: ${invalid.joinToString(", ")}"
            )
        }
    }
}
