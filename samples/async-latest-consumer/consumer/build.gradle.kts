plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.magic.pulse.samples.asynclatest"
    compileSdk {
        version = release(36) { minorApiLevel = 1 }
    }
    defaultConfig { minSdk = 23 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { compose = true }
}

val pulseVersion = providers.gradleProperty("pulseVersion").get()

dependencies {
    implementation("io.github.magic-xu:mvi-platform-android-compose:$pulseVersion")
    implementation("io.github.magic-xu:mvi-extensions:$pulseVersion")
    testImplementation("io.github.magic-xu:mvi-testing:$pulseVersion")
    testImplementation("junit:junit:4.13.2")
}
