import java.util.Properties

val repositoryRoot = rootProject.projectDir.parentFile.parentFile
val versionProperties = Properties().apply {
    repositoryRoot.resolve("version.properties").inputStream().use(this::load)
}
val appVersionCode = providers.gradleProperty("appVersionCode")
    .orElse(versionProperties.getProperty("VERSION_CODE_BASE"))
    .get().toInt()
val appVersionName = providers.gradleProperty("appVersionName")
    .orElse(versionProperties.getProperty("VERSION_NAME_BASE"))
    .get()
// Пароли и ключ лежат вне Git. Этот файл создаётся при настройке рабочей
// машины и используется всеми вашими Android-приложениями для release-сборок.
val releaseSigningPropertiesFile = file("C:/WORK/Secrets/isrepeat-android-release.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(this::load)
    }
}

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.isrepeat.documenttranslator"
    compileSdk {
        version = release(36)
    }
    defaultConfig {
        applicationId = "com.isrepeat.documenttranslator"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            // В APK для физических устройств включаем только ARM64-библиотеки.
            abiFilters += "arm64-v8a"
        }

    }
    signingConfigs {
        create("release") {
            // Когда файла нет, debug-сборка остаётся доступной. Release без
            // ключа намеренно не настраивается и не должен распространяться.
            if (releaseSigningPropertiesFile.isFile) {
                storeFile = file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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

dependencies {
    implementation("com.isrepeat:androidappkit:+")
    implementation("com.isrepeat:androidcoresdk:+")
    implementation(libs.androidx.activity)
    implementation(libs.androidx.lifecycle)
    implementation(libs.play.services.auth)
}

configurations.configureEach {
    // Локальный Maven feed должен отдаваться без кэша динамической версии SDK.
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}