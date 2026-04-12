plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(11)
}

dependencies {
    implementation(project(":mvi-core-contract"))
}

val testSourceSet = sourceSets.named("test")

tasks.register<JavaExec>("runtimeSelfCheck") {
    group = "verification"
    description = "Runs runtime invariants without external test frameworks."
    classpath = testSourceSet.get().runtimeClasspath
    mainClass.set("com.magic.mvicore.runtime.RuntimeSelfCheckKt")
}

tasks.named("check") {
    dependsOn("runtimeSelfCheck")
}

tasks.named<Test>("test") {
    failOnNoDiscoveredTests = false
}
