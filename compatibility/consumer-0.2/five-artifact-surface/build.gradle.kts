import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.attributes.Attribute
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.magic.pulse.compat.surface"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    flavorDimensions += "pulseVersion"
    productFlavors {
        create("baseline") {
            dimension = "pulseVersion"
        }
        create("candidate") {
            dimension = "pulseVersion"
        }
    }

    buildFeatures {
        compose = true
    }
}

// Capture the public variant API provider only after AGP has created a variant. Earlier access sees
// unfinished Java compatibility values; accessing the legacy DSL object from a task action loses
// AGP's service-backed instantiation context under AGP 9.
val androidBootClasspath = objects.fileCollection()
androidComponents.onVariants(androidComponents.selector().all()) {
    androidBootClasspath.from(androidComponents.sdkComponents.bootClasspath)
}

val pulseGroup = "io.github.magic-xu"
val baselineVersion = "0.2.0"
val candidateVersion = providers.gradleProperty("pulseVersion").get()
val comparedModules = listOf(
    ComparedModule("mvi-core-contract", "jar"),
    ComparedModule("mvi-core-runtime", "jar"),
    ComparedModule("mvi-platform-android", "aar"),
    ComparedModule("mvi-platform-android-compose", "aar"),
    ComparedModule("mvi-extensions", "jar"),
)

dependencies {
    comparedModules.forEach { module ->
        add("baselineImplementation", "$pulseGroup:${module.name}:$baselineVersion")
        add("candidateImplementation", "$pulseGroup:${module.name}:$candidateVersion")
    }
}

val japicmpClasspath = configurations.create("japicmpClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies.add(japicmpClasspath.name, "com.github.siom79.japicmp:japicmp:0.26.1")

fun directArchiveConfiguration(
    label: String,
    module: ComparedModule,
    version: String,
) = configurations.create("${label}${module.taskName}Archive") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    dependencies.add(
        project.dependencies.create("$pulseGroup:${module.name}:$version@${module.extension}")
    )
}

fun preparedArchive(
    label: String,
    module: ComparedModule,
    version: String,
): PreparedArchive {
    val configuration = directArchiveConfiguration(label, module, version)
    if (module.extension == "jar") {
        return PreparedArchive(files = files(configuration), preparation = null)
    }

    val outputFile =
        layout.buildDirectory.file("compatibility-archives/$label/${module.name}/classes.jar")
    val extraction =
        tasks.register<ExtractClassesJar>(
            "extract${label.replaceFirstChar(Char::uppercase)}${module.taskName}Classes"
        ) {
            aar.from(configuration)
            classesJar.set(outputFile)
        }
    return PreparedArchive(
        files = files(extraction.flatMap { task -> task.classesJar }),
        preparation = extraction,
    )
}

abstract class ExtractClassesJar : DefaultTask() {
    @get:Classpath
    abstract val aar: ConfigurableFileCollection

    @get:OutputFile
    abstract val classesJar: RegularFileProperty

    @TaskAction
    fun extract() {
        val input = aar.singleFile
        val output = classesJar.get().asFile
        output.parentFile.mkdirs()
        ZipFile(input).use { archive ->
            val entry = requireNotNull(archive.getEntry("classes.jar")) {
                "AAR does not contain classes.jar: $input"
            }
            archive.getInputStream(entry).use { source ->
                output.outputStream().use { destination -> source.copyTo(destination) }
            }
        }
    }
}

val baselineArchives = comparedModules.associateWith { module ->
    preparedArchive("baseline", module, baselineVersion)
}
val candidateArchives = comparedModules.associateWith { module ->
    preparedArchive("candidate", module, candidateVersion)
}

val sourceCompatibilityCheck = tasks.register("sourceCompatibilityCheck") {
    group = "verification"
    description = "Compiles the tag-v0.2.0 five-artifact Kotlin surface against baseline and candidate."
    dependsOn("compileBaselineDebugKotlin", "compileCandidateDebugKotlin")
}

val binaryChecks = comparedModules.map { module ->
    val baseline = baselineArchives.getValue(module)
    val candidate = candidateArchives.getValue(module)
    tasks.register<JavaExec>("${module.taskName.replaceFirstChar(Char::lowercase)}BinaryLinkageCheck") {
        group = "verification"
        description = "Checks ${module.name} 0.2 bytecode linkage against the staged candidate."
        baseline.preparation?.let { preparation -> dependsOn(preparation) }
        candidate.preparation?.let { preparation -> dependsOn(preparation) }
        classpath = japicmpClasspath
        mainClass.set("japicmp.JApiCmp")

        doFirst {
            val artifactType = Attribute.of("artifactType", String::class.java)
            fun classesClasspath(configurationName: String): Set<File> {
                return configurations.getByName(configurationName).incoming.artifactView {
                    attributes.attribute(artifactType, "android-classes-jar")
                }.files.files
            }
            val baselineCompileClasspath =
                classesClasspath("baselineDebugCompileClasspath") +
                    androidBootClasspath.files
            val candidateCompileClasspath =
                classesClasspath("candidateDebugCompileClasspath") +
                    androidBootClasspath.files
            args(
                "--old", baseline.files.singleFile.absolutePath,
                "--new", candidate.files.singleFile.absolutePath,
                "--old-classpath", baselineCompileClasspath.joinToString(File.pathSeparator),
                "--new-classpath", candidateCompileClasspath.joinToString(File.pathSeparator),
                "--only-modified",
                "--only-incompatible",
                "--include-synthetic",
                "--error-on-binary-incompatibility",
                "--error-on-source-incompatibility",
            )
            if (module.name == "mvi-platform-android") {
                // This top-level function is `internal` in tag v0.2.0. Kotlin exposes it as JVM-public
                // bytecode, but it was never callable by an external Kotlin consumer or part of Pulse's
                // supported public API. Keep this narrow exclusion instead of suppressing missing classes
                // or any public/protected declaration.
                args(
                    "--exclude",
                    "com.magic.mvicore.android.ViewModelCoroutineScopeKt#createPulseCoroutineScope()",
                )
            }
            if (module.name == "mvi-core-runtime") {
                // These four nested types are declared `private` in tag v0.2.0's DefaultStore.
                // Kotlin emitted JVM-public class files for that implementation detail, so compare
                // the supported Kotlin/Java surface without treating compiler leakage as API.
                listOf(
                    "com.magic.mvicore.runtime.DefaultStore\$DispatchFrame",
                    "com.magic.mvicore.runtime.DefaultStore\$DispatchOutcome",
                    "com.magic.mvicore.runtime.DefaultStore\$DispatchOutcome\$Accepted",
                    "com.magic.mvicore.runtime.DefaultStore\$DispatchOutcome\$Rejected",
                ).forEach { privateImplementationType ->
                    args("--exclude", privateImplementationType)
                }
            }
            if (module.name == "mvi-platform-android-compose") {
                // Compose 0.2 emitted public JVM holder classes for two inline `onDispose`
                // lambdas. They have no source-level name and are not callable API; the actual
                // collectStateAsState/observeEffects functions remain in the frozen source check.
                listOf(
                    "com.magic.mvicore.android.compose.StoreComposeKt\$collectStateAsState\$lambda\$4\$lambda\$3\$\$inlined\$onDispose\$1",
                    "com.magic.mvicore.android.compose.StoreComposeKt\$observeEffects\$lambda\$8\$lambda\$7\$\$inlined\$onDispose\$1",
                ).forEach { compilerGeneratedType ->
                    args("--exclude", compilerGeneratedType)
                }
            }
        }
    }
}

val binaryLinkageCheck = tasks.register("binaryLinkageCheck") {
    group = "verification"
    description = "Checks binary and Java-source compatibility for all five 0.2 artifacts."
    dependsOn(binaryChecks)
}

tasks.register("compatibilityCheck") {
    group = "verification"
    dependsOn(sourceCompatibilityCheck, binaryLinkageCheck)
}

data class ComparedModule(
    val name: String,
    val extension: String,
) {
    val taskName: String = name
        .split('-')
        .joinToString("") { segment -> segment.replaceFirstChar(Char::uppercase) }
}

data class PreparedArchive(
    val files: FileCollection,
    val preparation: TaskProvider<out Task>?,
)
