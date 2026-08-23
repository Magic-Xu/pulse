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
    namespace = "com.magic.pulse.compat.v03.android"
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

val androidBootClasspath = objects.fileCollection()
androidComponents.onVariants(androidComponents.selector().all()) {
    androidBootClasspath.from(androidComponents.sdkComponents.bootClasspath)
}

val pulseGroup = "io.github.magic-xu"
val baselineVersion = "0.3.0"
val candidateVersion = providers.gradleProperty("pulseVersion").get()
val comparedModules = listOf(
    ComparedModule("mvi-platform-android"),
    ComparedModule("mvi-platform-android-compose"),
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
        project.dependencies.create("$pulseGroup:${module.name}:$version@aar")
    )
}

fun preparedArchive(
    label: String,
    module: ComparedModule,
    version: String,
): PreparedArchive {
    val configuration = directArchiveConfiguration(label, module, version)
    val outputFile =
        layout.buildDirectory.file("compatibility-archives/$label/${module.name}/classes.jar")
    val extraction = tasks.register<ExtractClassesJar>(
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
    description = "Compiles the frozen 0.3 Android and Compose surface against baseline and candidate."
    dependsOn("compileBaselineDebugKotlin", "compileCandidateDebugKotlin")
}

val binaryChecks = comparedModules.map { module ->
    val baseline = baselineArchives.getValue(module)
    val candidate = candidateArchives.getValue(module)
    tasks.register<JavaExec>("${module.taskName.replaceFirstChar(Char::lowercase)}BinaryCompatibilityCheck") {
        group = "verification"
        description = "Checks ${module.name} 0.3 bytecode linkage against the staged candidate."
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
                classesClasspath("baselineDebugCompileClasspath") + androidBootClasspath.files
            val candidateCompileClasspath =
                classesClasspath("candidateDebugCompileClasspath") + androidBootClasspath.files
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
                // These types are declared `private` in tag v0.3.0's Split ViewModel source.
                // Kotlin emitted JVM-public class files for the implementation pipeline, but none
                // is callable from an external Kotlin consumer or present in the controlled API.
                listOf(
                    "com.magic.mvicore.android.SplitStoreInput",
                    "com.magic.mvicore.android.SplitStoreInput\$Ui",
                    "com.magic.mvicore.android.SplitStoreInput\$Mutation",
                    "com.magic.mvicore.android.ExecutorInput",
                ).forEach { privateImplementationType ->
                    args("--exclude", privateImplementationType)
                }
            }
        }
    }
}

tasks.register("binaryCompatibilityCheck") {
    group = "verification"
    description = "Checks binary and Java-source compatibility for both Android artifacts."
    dependsOn(binaryChecks)
}

tasks.register("compatibilityCheck") {
    group = "verification"
    dependsOn(sourceCompatibilityCheck, "binaryCompatibilityCheck")
}

data class ComparedModule(
    val name: String,
) {
    val taskName: String = name
        .split('-')
        .joinToString("") { segment -> segment.replaceFirstChar(Char::uppercase) }
}

data class PreparedArchive(
    val files: FileCollection,
    val preparation: TaskProvider<out Task>,
)
