plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
}

description = "Pulse lifecycle-aware Compose state, selector, effect, and ViewModel bindings."

android {
    namespace = "com.magic.mvicore.android.compose"
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

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(project(":mvi-platform-android"))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.lifecycle.runtime.testing)
    testImplementation(libs.robolectric)
}

tasks.withType<Test>().configureEach {
    systemProperty(
        "robolectric.dependency.repo.url",
        "https://repo.maven.apache.org/maven2",
    )
}
