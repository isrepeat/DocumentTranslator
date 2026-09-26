pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    val restore = providers.exec {
        commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", file("../../build.ps1").absolutePath, "restore")
    }
    val packageRoot = restore.standardOutput.asText.get().trim().lineSequence().last()
    includeBuild("$packageRoot/gradle")
}
plugins {
    id("com.isrepeat.android.settings")
}

rootProject.name = "DocumentTranslator"
include(":DocumentTranslator.Android")
project(":DocumentTranslator.Android").projectDir = file("../../DocumentTranslator.Android")