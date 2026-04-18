plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
}

description = "Pulse optional plugins: logging and state transition observers."

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(project(":mvi-core-runtime"))
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

tasks.named<Test>("test") {
    failOnNoDiscoveredTests = false
}
