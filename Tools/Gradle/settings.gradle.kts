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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

val repositoryRoot = file("../..")

rootProject.name = "MobileClock"
include(":DocumentTranslator.Android")

project(":DocumentTranslator.Android").projectDir = repositoryRoot.resolve("DocumentTranslator.Android")

gradle.beforeProject {
    layout.buildDirectory.set(repositoryRoot.resolve("Build/$name"))
}