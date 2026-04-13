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

allprojects {
    group = providers.gradleProperty("POM_GROUP_ID").orElse("io.github.magic-xu").get()
    version = providers.gradleProperty("POM_VERSION_NAME").orElse("0.1.0-SNAPSHOT").get()
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
