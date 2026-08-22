plugins {
    base
    kotlin("jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.android.library") version "9.1.0" apply false
}

tasks.register("compatibilityCheck") {
    group = "verification"
    description = "Checks source and binary compatibility for all five Pulse 0.2 artifacts."
    dependsOn(
        ":core-linkage:compatibilityCheck",
        ":five-artifact-surface:compatibilityCheck",
    )
}
