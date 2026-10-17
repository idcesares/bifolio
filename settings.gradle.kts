pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupByRegex("com.android.*")
                includeGroupByRegex("com.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "bifolio"

include(
    ":app",
    ":core:model",
    ":core:parse",
    ":core:layout",
    ":core:text",
    ":core:highlight",
    ":core:math",
    ":core:designsystem",
    ":core:data",
    ":feature:library",
    ":feature:reader",
    ":feature:settings",
    ":benchmark",
    ":baselineprofile",
)
