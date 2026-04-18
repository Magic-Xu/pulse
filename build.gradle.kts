// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
}

val publishableModules = setOf(
    "mvi-core-contract",
    "mvi-core-runtime",
    "mvi-platform-android",
    "mvi-platform-android-compose",
    "mvi-extensions",
)

val appDepModePropertyKey = "PULSE_APP_DEP_MODE"
val rootGradlePropertiesFile = rootProject.file("gradle.properties")

fun setRootGradleProperty(key: String, value: String) {
    val lines = if (rootGradlePropertiesFile.exists()) {
        rootGradlePropertiesFile.readLines().toMutableList()
    } else {
        mutableListOf()
    }
    val targetLine = "$key=$value"
    val index = lines.indexOfFirst { it.startsWith("$key=") }
    if (index >= 0) {
        lines[index] = targetLine
    } else {
        if (lines.isNotEmpty() && lines.last().isNotBlank()) {
            lines.add("")
        }
        lines.add(targetLine)
    }
    rootGradlePropertiesFile.writeText(lines.joinToString("\n") + "\n")
}

allprojects {
    group = providers.gradleProperty("POM_GROUP_ID").orElse("io.github.magic-xu").get()
    version = providers.gradleProperty("POM_VERSION_NAME").orElse("0.2.0-SNAPSHOT").get()
}

subprojects {
    if (name in publishableModules) {
        apply(plugin = "com.vanniktech.maven.publish")
    }
}

subprojects {
    plugins.withId("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            publishToMavenCentral()
            signAllPublications()
        }
    }
}

tasks.register("mviCoreCheck") {
    group = "verification"
    description = "Checks contract/runtime/extensions modules."
    dependsOn(
        ":mvi-core-contract:check",
        ":mvi-core-runtime:check",
        ":mvi-extensions:check",
    )
}

tasks.register("mviFrameworkCheck") {
    group = "verification"
    description = "Checks MVI framework wiring including Android module task graph."
    dependsOn(
        "mviCoreCheck",
        ":mvi-platform-android:tasks",
        ":mvi-platform-android-compose:tasks",
    )
}

tasks.register("verifyMavenCentralConfig") {
    group = "publishing"
    description = "Validates Maven Central metadata placeholders before publishing."
    doLast {
        val required = listOf(
            "POM_DEVELOPER_NAME",
            "POM_DEVELOPER_EMAIL",
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

tasks.register("printPulseDepMode") {
    group = "help"
    description = "Prints current app dependency mode (local or remote)."
    doLast {
        val modeFromFile = if (rootGradlePropertiesFile.exists()) {
            rootGradlePropertiesFile
                .readLines()
                .firstOrNull { it.startsWith("$appDepModePropertyKey=") }
                ?.substringAfter("=")
                ?.trim()
        } else {
            null
        }
        val mode = modeFromFile?.ifBlank { "local" } ?: "local"
        println("Current app dependency mode: $mode")
    }
}

tasks.register("useLocalPulseDeps") {
    group = "build setup"
    description = "Switches app to local project dependencies."
    doLast {
        setRootGradleProperty(appDepModePropertyKey, "local")
        println("Switched app dependencies to LOCAL mode.")
        println("Run Sync/Reload Gradle Project in IDE, then build again.")
    }
}

tasks.register("useRemotePulseDeps") {
    group = "build setup"
    description = "Switches app to Maven Central remote dependencies."
    doLast {
        setRootGradleProperty(appDepModePropertyKey, "remote")
        println("Switched app dependencies to REMOTE mode.")
        println("Run Sync/Reload Gradle Project in IDE, then build again.")
    }
}
