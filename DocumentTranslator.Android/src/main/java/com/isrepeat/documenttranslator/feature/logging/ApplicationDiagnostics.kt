package com.isrepeat.documenttranslator.feature.logging

import com.isrepeat.documenttranslator.documenttranslator

//
// Пишет в существующий native-логгер; не собирает аккаунты, токены и ID устройства.
//
internal object ApplicationDiagnostics {
    private const val UPDATER = "com.isrepeat.apkupdater"
    private const val PERMISSION = "$UPDATER.permission.INSTALL_UPDATE"

    fun logInstalled(context: android.content.Context) {
        section("environment") {
            log(
                "Android release=${android.os.Build.VERSION.RELEASE}, " +
                    "sdk=${android.os.Build.VERSION.SDK_INT}, " +
                    "securityPatch=${android.os.Build.VERSION.SECURITY_PATCH}, " +
                    "manufacturer=${android.os.Build.MANUFACTURER}, model=${android.os.Build.MODEL}, " +
                    "build=${android.os.Build.DISPLAY}, " +
                    "abis=${android.os.Build.SUPPORTED_ABIS.joinToString()}, " +
                    "pid=${android.os.Process.myPid()}, uid=${android.os.Process.myUid()}",
            )
        }
        for (name in listOf(context.packageName, UPDATER)) {
            section("installed $name") {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageInfo(name, flags())
                logPackage(info, "installed")
                @Suppress("DEPRECATION")
                val installer = if (android.os.Build.VERSION.SDK_INT >= 30) {
                    context.packageManager.getInstallSourceInfo(name).let {
                        "installing=${it.installingPackageName}, initiating=${it.initiatingPackageName}, originating=${it.originatingPackageName}"
                    }
                } else {
                    context.packageManager.getInstallerPackageName(name)
                }
                log(
                    "$name installer=$installer, " +
                        "enabledSetting=${context.packageManager.getApplicationEnabledSetting(name)}, " +
                        "launcher=${context.packageManager.getLaunchIntentForPackage(name)?.component}",
                )
            }
        }
        section("updater access") {
            val pm = context.packageManager
            log("signatureCheck=${pm.checkSignatures(context.packageName, UPDATER)}, permissionCheck=${pm.checkPermission(PERMISSION, context.packageName)}")
            @Suppress("DEPRECATION")
            val permission = pm.getPermissionInfo(PERMISSION, 0)
            log("permissionOwner=${permission.packageName}, protectionLevel=${permission.protectionLevel}")
        }
    }

    fun logArchive(context: android.content.Context, file: java.io.File) {
        section("downloaded APK") {
            log("file=${file.name}, bytes=${file.length()}")
            @Suppress("DEPRECATION")
            val archive = context.packageManager.getPackageArchiveInfo(file.path, flags())
            if (archive == null) {
                log("APK metadata unavailable")
            } else {
                logPackage(archive, "archive")
            }
        }
    }

    private fun flags(): Int =
        android.content.pm.PackageManager.GET_PERMISSIONS or android.content.pm.PackageManager.GET_ACTIVITIES or
            (if (android.os.Build.VERSION.SDK_INT >= 28) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else legacySignatureFlag())

    @Suppress("DEPRECATION")
    private fun legacySignatureFlag(): Int = android.content.pm.PackageManager.GET_SIGNATURES

    @Suppress("DEPRECATION")
    private fun logPackage(info: android.content.pm.PackageInfo, source: String) {
        val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        log(
            "$source package=${info.packageName}, version=${info.versionName}, code=$code, " +
                "firstInstall=${java.util.Date(info.firstInstallTime)}, " +
                "lastUpdate=${java.util.Date(info.lastUpdateTime)}",
        )
        info.applicationInfo?.let {
            log(
                "application uid=${it.uid}, enabled=${it.enabled}, minSdk=${it.minSdkVersion}, " +
                    "targetSdk=${it.targetSdkVersion}, flags=0x${it.flags.toString(16)}, " +
                    "debuggable=${it.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0}, " +
                    "sourceDir=${it.sourceDir}, splits=${it.splitSourceDirs?.joinToString()}",
            )
        }
        info.requestedPermissions?.forEachIndexed { index, name ->
            val granted = (info.requestedPermissionsFlags?.getOrNull(index) ?: 0) and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            log("requestedPermission=$name, granted=$granted")
        }
        info.activities?.forEach {
            log(
                "activity=${it.name}, enabled=${it.enabled}, exported=${it.exported}, " +
                    "permission=${it.permission}, launchMode=${it.launchMode}, " +
                    "affinity=${it.taskAffinity}, flags=0x${it.flags.toString(16)}",
            )
        }
        val current = if (android.os.Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        current?.forEachIndexed { index, signature -> logCertificate("current[$index]", signature.toByteArray()) }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.let { signing ->
                log("multipleSigners=${signing.hasMultipleSigners()}, hasSigningHistory=${signing.hasPastSigningCertificates()}")
                signing.signingCertificateHistory?.forEachIndexed { index, signature ->
                    logCertificate("history[$index]", signature.toByteArray())
                }
            }
        }
        if (current.isNullOrEmpty()) {
            log("Signing certificates unavailable")
        }
    }

    private fun logCertificate(label: String, bytes: ByteArray) {
        section(label) {
            for (algorithm in listOf("SHA-256", "SHA-1")) {
                val fingerprint = java.security.MessageDigest.getInstance(algorithm).digest(bytes)
                    .joinToString(":") { "%02X".format(it.toInt() and 255) }
                log("$label $algorithm=$fingerprint")
            }
            val cert = java.security.cert.CertificateFactory.getInstance("X.509")
                .generateCertificate(bytes.inputStream()) as java.security.cert.X509Certificate
            log(
                "$label subject=${cert.subjectX500Principal}, issuer=${cert.issuerX500Principal}, " +
                    "validFrom=${cert.notBefore}, validUntil=${cert.notAfter}, " +
                    "signatureAlgorithm=${cert.sigAlgName}",
            )
        }
    }

    private inline fun section(name: String, action: () -> Unit) {
        try {
            action()
        } catch (exception: Exception) {
            log("$name unavailable: ${exception.javaClass.name}: ${exception.message}")
        }
    }

    private fun log(message: String) = documenttranslator.native.NativeRenderer.log("AppDiagnostics: $message")
}