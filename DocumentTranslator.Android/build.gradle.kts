import java.util.Properties

val repositoryRoot = rootProject.projectDir.parentFile.parentFile
val versionProperties = Properties().apply {
    repositoryRoot.resolve("version.properties").inputStream().use(this::load)
}
val appVersionCode = versionProperties.getProperty("VERSION_CODE").toInt()
val appVersionName = versionProperties.getProperty("VERSION_NAME")

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.mobileclock"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.mobileclock"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            // В APK для физических устройств включаем только ARM64-библиотеки.
            abiFilters += "arm64-v8a"
        }

    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    sourceSets {
        getByName("main").assets.directories += "../DocumentTranslator.Application/Resources"
        getByName("main").jniLibs.srcDirs("../Build/DocumentTranslator.AndroidHost/android/jniLibs")
    }
    androidResources {
        // Исходники модуля хоста компилируются в native-библиотеку и не нужны в assets.
        ignoreAssetsPatterns += "Effects"
    }
}