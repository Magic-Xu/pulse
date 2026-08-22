plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
}

description = "Pulse ordered Store runtime, keyed tasks, UI-effect coordination, and legacy adapters."

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(project(":mvi-core-contract"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
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

tasks.withType<Test>().configureEach {
    useJUnit()
}
