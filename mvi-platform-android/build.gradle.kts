plugins {
    id("com.android.library")
    alias(libs.plugins.dokka)
}

description = "Pulse Android ViewModel, Split Intent, explicit owner, and saved-state adapters."

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
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.lifecycle.viewmodel.savedstate)
    api(libs.androidx.savedstate.ktx)
    api(libs.kotlinx.coroutines.android)

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
