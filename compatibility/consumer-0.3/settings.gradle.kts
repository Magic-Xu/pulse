pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

val pulseGroup = "io.github.magic-xu"
val candidateVersion = providers.gradleProperty("pulseVersion").get()
val publishedModules = listOf(
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
                publishedModules.forEach { moduleName ->
                    includeVersion(pulseGroup, moduleName, candidateVersion)
                }
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "pulse-v03-compatibility-consumer"
include(":jvm-surface")
include(":android-surface")
