plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(11)
}

val baseline by sourceSets.creating {
    kotlin.srcDir("../src/consumer/kotlin")
}
val candidate by sourceSets.creating {
    kotlin.srcDir("../src/consumer/kotlin")
}

val pulseVersion = providers.gradleProperty("pulseVersion").get()

dependencies {
    add(baseline.implementationConfigurationName, "io.github.magic-xu:mvi-core-runtime:0.2.0")
    add(baseline.implementationConfigurationName, kotlin("stdlib"))
    add(candidate.implementationConfigurationName, "io.github.magic-xu:mvi-core-runtime:$pulseVersion")
    add(candidate.implementationConfigurationName, kotlin("stdlib"))
}

tasks.register<JavaExec>("sourceCompatibilityCheck") {
    group = "verification"
    description = "Compiles and runs the frozen 0.2 core consumer against staged candidate artifacts."
    dependsOn(tasks.named(candidate.classesTaskName))
    classpath = candidate.runtimeClasspath
    mainClass.set("com.magic.pulse.compat.LegacyConsumerKt")
}

tasks.register<JavaExec>("binaryLinkageCheck") {
    group = "verification"
    description = "Runs consumer bytecode compiled against 0.2 on the staged candidate runtime."
    dependsOn(tasks.named(baseline.classesTaskName), tasks.named(candidate.classesTaskName))
    val candidateOutput = candidate.output.files.map { it.canonicalFile }.toSet()
    classpath = baseline.output + candidate.runtimeClasspath.filter { file ->
        file.canonicalFile !in candidateOutput
    }
    mainClass.set("com.magic.pulse.compat.LegacyConsumerKt")
}

tasks.register("compatibilityCheck") {
    group = "verification"
    dependsOn("sourceCompatibilityCheck", "binaryLinkageCheck")
}
