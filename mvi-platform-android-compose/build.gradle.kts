plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
}

description = "MVICore Android Compose adapter: Store <-> Compose bindings."

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
}

dependencies {
    api(project(":mvi-platform-android"))
    api(libs.androidx.compose.runtime)
}
