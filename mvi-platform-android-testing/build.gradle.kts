plugins {
    id("com.android.library")
    alias(libs.plugins.dokka)
}

description = "Pulse deterministic test host for the Android Split Intent runtime."

android {
    namespace = "com.magic.mvicore.android.testing"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    api(project(":mvi-platform-android"))
    api(project(":mvi-testing"))

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}
