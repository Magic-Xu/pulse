pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

val pulseGroup = "io.github.magic-xu"
val pulseVersion = providers.gradleProperty("pulseVersion").get()
val pulseModules = listOf(
    "mvi-core-contract",
    "mvi-core-runtime",
    "mvi-platform-android",
    "mvi-platform-android-compose",
    "mvi-extensions",
    "mvi-testing",
)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "PulseCandidateStaging"
                    url = uri(providers.gradleProperty("pulseRepository").get())
                }
            }
            filter {
                pulseModules.forEach { moduleName ->
                    includeVersion(pulseGroup, moduleName, pulseVersion)
                }
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "pulse-async-latest-consumer"
include(":consumer")
