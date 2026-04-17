plugins {
    id("com.android.library")
}

description = "MVICore Android adapter: ViewModel-based Store ownership."

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

}

dependencies {
    api(project(":mvi-core-runtime"))
    api(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
}
