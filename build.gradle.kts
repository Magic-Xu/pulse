// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
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
    )
}
