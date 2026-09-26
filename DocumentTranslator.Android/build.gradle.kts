plugins {
    id("com.isrepeat.android.application")
}

android {
    namespace = "com.isrepeat.documenttranslator"
    defaultConfig.applicationId = "com.isrepeat.documenttranslator"
    sourceSets {
        getByName("main").assets.directories += "../DocumentTranslator.Application/Resources"
        getByName("main").jniLibs.directories += "../Build/DocumentTranslator.AndroidHost/android/jniLibs"
    }
    androidResources.ignoreAssetsPatterns += "Effects"
}

dependencies {
    implementation("com.isrepeat:androidappkit:+")
    implementation("com.isrepeat:androidcoresdk:+")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
}