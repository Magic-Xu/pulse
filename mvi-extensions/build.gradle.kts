plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
}

description = "Pulse state-decomposition tools and optional logging and transition plugins."

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(project(":mvi-core-runtime"))

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

val testSourceSet = sourceSets.named("test")

tasks.register<JavaExec>("extensionsSelfCheck") {
    group = "verification"
    description = "Runs extension invariants without external test frameworks."
    classpath = testSourceSet.get().runtimeClasspath
    mainClass.set("com.magic.mvicore.extensions.ExtensionsSelfCheckKt")
}

tasks.named("check") {
    dependsOn("extensionsSelfCheck")
}

tasks.withType<Test>().configureEach {
    useJUnit()
}
