plugins {
    id("com.android.library")
}

android {
    namespace = "com.magic.pulse.samples.syncconsumer"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val pulseVersion = providers.gradleProperty("pulseVersion").get()

dependencies {
    implementation("io.github.magic-xu:mvi-core-runtime:$pulseVersion")
    implementation("io.github.magic-xu:mvi-extensions:$pulseVersion")
}
