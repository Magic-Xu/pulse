plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(11)
}

val baseline by sourceSets.creating {
    kotlin.srcDir("src/main/kotlin")
}
val candidate by sourceSets.creating {
    kotlin.srcDir("src/main/kotlin")
}
val legacyImplementer by sourceSets.creating
val candidateBridge by sourceSets.creating

val pulseGroup = "io.github.magic-xu"
val baselineVersion = "0.3.0"
val candidateVersion = providers.gradleProperty("pulseVersion").get()
val comparedModules = listOf(
    "mvi-core-contract",
    "mvi-core-runtime",
    "mvi-extensions",
    "mvi-testing",
)

dependencies {
    comparedModules.forEach { moduleName ->
        add(baseline.implementationConfigurationName, "$pulseGroup:$moduleName:$baselineVersion")
        add(candidate.implementationConfigurationName, "$pulseGroup:$moduleName:$candidateVersion")
    }
    add(baseline.implementationConfigurationName, kotlin("stdlib"))
    add(candidate.implementationConfigurationName, kotlin("stdlib"))

    add(
        legacyImplementer.implementationConfigurationName,
        "$pulseGroup:mvi-core-runtime:$baselineVersion",
    )
    add(legacyImplementer.implementationConfigurationName, kotlin("stdlib"))
    add(
        candidateBridge.implementationConfigurationName,
        "$pulseGroup:mvi-core-runtime:$candidateVersion",
    )
    add(candidateBridge.implementationConfigurationName, legacyImplementer.output)
    add(candidateBridge.implementationConfigurationName, kotlin("stdlib"))
}

val japicmpClasspath = configurations.create("japicmpClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies.add(japicmpClasspath.name, "com.github.siom79.japicmp:japicmp:0.26.1")

fun directArchiveConfiguration(
    label: String,
    moduleName: String,
    version: String,
) = configurations.create("${label}${moduleName.toTaskName()}Archive") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    dependencies.add(
        project.dependencies.create("$pulseGroup:$moduleName:$version@jar")
    )
}

val baselineArchives = comparedModules.associateWith { moduleName ->
    directArchiveConfiguration("baseline", moduleName, baselineVersion)
}
val candidateArchives = comparedModules.associateWith { moduleName ->
    directArchiveConfiguration("candidate", moduleName, candidateVersion)
}

val sourceCompatibilityCheck = tasks.register("sourceCompatibilityCheck") {
    group = "verification"
    description = "Compiles the frozen 0.3 Kotlin surface against baseline and candidate JVM artifacts."
    dependsOn(tasks.named(baseline.classesTaskName), tasks.named(candidate.classesTaskName))
}

val baselineRuntimeCheck = tasks.register<JavaExec>("baselineRuntimeCheck") {
    group = "verification"
    description = "Runs the frozen JVM consumer on the public 0.3 artifacts."
    dependsOn(tasks.named(baseline.classesTaskName))
    classpath = baseline.runtimeClasspath
    mainClass.set("com.magic.pulse.compat.v03.LegacyJvmSurfaceKt")
}

val candidateRuntimeCheck = tasks.register<JavaExec>("candidateRuntimeCheck") {
    group = "verification"
    description = "Runs the source-compatible JVM consumer on the staged candidate artifacts."
    dependsOn(tasks.named(candidate.classesTaskName))
    classpath = candidate.runtimeClasspath
    mainClass.set("com.magic.pulse.compat.v03.LegacyJvmSurfaceKt")
}

val frozenBinaryRuntimeCheck = tasks.register<JavaExec>("frozenBinaryRuntimeCheck") {
    group = "verification"
    description = "Runs JVM consumer bytecode compiled against 0.3 on the staged candidate runtime."
    dependsOn(tasks.named(baseline.classesTaskName), tasks.named(candidate.classesTaskName))
    val candidateOutput = candidate.output.files.map { it.canonicalFile }.toSet()
    classpath = baseline.output + candidate.runtimeClasspath.filter { file ->
        file.canonicalFile !in candidateOutput
    }
    mainClass.set("com.magic.pulse.compat.v03.LegacyJvmSurfaceKt")
}

val pulseTasksDefaultBridgeCheck = tasks.register<JavaExec>("pulseTasksDefaultBridgeCheck") {
    group = "verification"
    description =
        "Invokes the candidate PulseTasks overload on an implementation compiled only against 0.3."
    dependsOn(
        tasks.named(legacyImplementer.classesTaskName),
        tasks.named(candidateBridge.classesTaskName),
    )
    classpath = candidateBridge.runtimeClasspath
    mainClass.set("com.magic.pulse.compat.v03.CandidatePulseTasksBridgeKt")
}

val archiveBinaryChecks = comparedModules.map { moduleName ->
    tasks.register<JavaExec>("${moduleName.toTaskName().replaceFirstChar(Char::lowercase)}BinaryCompatibilityCheck") {
        group = "verification"
        description = "Checks $moduleName 0.3 bytecode linkage against the staged candidate."
        classpath = japicmpClasspath
        mainClass.set("japicmp.JApiCmp")

        doFirst {
            args(
                "--old", baselineArchives.getValue(moduleName).singleFile.absolutePath,
                "--new", candidateArchives.getValue(moduleName).singleFile.absolutePath,
                "--old-classpath", baseline.compileClasspath.files.joinToString(File.pathSeparator),
                "--new-classpath", candidate.compileClasspath.files.joinToString(File.pathSeparator),
                "--only-modified",
                "--only-incompatible",
                "--include-synthetic",
                "--error-on-binary-incompatibility",
                "--error-on-source-incompatibility",
            )
        }
    }
}

tasks.register("binaryCompatibilityCheck") {
    group = "verification"
    description = "Checks binary and Java-source compatibility for all four pure JVM artifacts."
    dependsOn(archiveBinaryChecks)
}

tasks.register("compatibilityCheck") {
    group = "verification"
    dependsOn(
        sourceCompatibilityCheck,
        baselineRuntimeCheck,
        candidateRuntimeCheck,
        frozenBinaryRuntimeCheck,
        pulseTasksDefaultBridgeCheck,
        "binaryCompatibilityCheck",
    )
}

fun String.toTaskName(): String =
    split('-').joinToString("") { segment -> segment.replaceFirstChar(Char::uppercase) }
