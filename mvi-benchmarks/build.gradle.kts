plugins {
    id("org.jetbrains.kotlin.jvm")
}

description = "Non-published Pulse performance and bounded-growth verification harness."

kotlin { jvmToolchain(11) }

dependencies {
    implementation(project(":mvi-core-runtime"))
    implementation(project(":mvi-extensions"))
    implementation(libs.kotlinx.coroutines.core)
}

tasks.register<JavaExec>("performanceRegressionCheck") {
    group = "verification"
    description = "Compares the v0.2 reference workload with v0.3 and writes a JSON report."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.magic.mvicore.benchmarks.PerformanceHarnessKt")
    systemProperty("pulse.performance.report", rootProject.layout.buildDirectory
        .file("reports/performance/pulse-performance.json").get().asFile.absolutePath)
    // Relative throughput is meaningful only when this process is not competing with functional,
    // lint, publication, or stress work in the same Gradle graph.
    mustRunAfter(":mviFrameworkCheck", ":mvi-testing:multiSeedStressCheck")
}
