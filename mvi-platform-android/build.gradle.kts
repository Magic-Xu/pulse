plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.magic.mvicore.android"
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
    api(project(":mvi-core-runtime"))
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
}
