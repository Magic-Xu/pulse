plugins {
    id("org.jetbrains.kotlin.jvm")
    id("java-library")
    alias(libs.plugins.vanniktech.maven.publish)
}

description = "Pulse testing APIs, probes, coroutine helpers, and Store TCK."

kotlin {
    jvmToolchain(11)
}

dependencies {
    api(project(":mvi-core-runtime"))
    api(libs.kotlinx.coroutines.test)
    implementation(libs.kotlin.test)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    useJUnit()
}

tasks.register<Test>("multiSeedStressCheck") {
    group = "verification"
    description = "Runs the 10,000-input concurrent stress suite across deterministic seeds."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnit()
    include("**/MultiSeedStressTest.class")
    systemProperty(
        "pulse.test.seeds",
        providers.gradleProperty("pulse.test.seeds")
            .orElse("20260819,20260820,20260821,20260822,20260823")
            .get(),
    )
    outputs.upToDateWhen { false }
}
