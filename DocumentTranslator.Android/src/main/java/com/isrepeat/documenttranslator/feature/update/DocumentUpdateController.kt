package com.isrepeat.documenttranslator.feature.update

import com.isrepeat.documenttranslator.documenttranslator
import com.isrepeat.androidcoresdk.androidcoresdk

//
// Ищет обновление приложения на Drive и передаёт проверенный APK ApkUpdater-у.
//
class DocumentUpdateController(
    private val activity: androidx.activity.ComponentActivity,
    private val onAuthorizationRequired: (androidx.activity.result.IntentSenderRequest) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private var isRunning = false

    fun start() {
        if (isRunning) {
            onStatus("Обновление уже выполняется.")
            return
        }
        isRunning = true
        onStatus("Проверяем доступ к Google Drive…")
        documenttranslator.native.NativeRenderer.log(
            "Google Drive update authorization requested: package=${activity.packageName}, scope=$DRIVE_SCOPE",
        )
        val request = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
            .setRequestedScopes(listOf(com.google.android.gms.common.api.Scope(DRIVE_SCOPE)))
            .build()
        com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener(::handleAuthorizationResult)
            .addOnFailureListener { exception ->
                finish(authorizationFailure("Google Drive update authorization request failed", exception))
            }
    }

    fun completeAuthorization(intent: android.content.Intent?) {
        try {
            handleAuthorizationResult(
                com.google.android.gms.auth.api.identity.Identity
                    .getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(intent),
            )
        } catch (exception: Exception) {
            finish(authorizationFailure("Google Drive update authorization result failed", exception))
        }
    }

    private fun handleAuthorizationResult(
        result: com.google.android.gms.auth.api.identity.AuthorizationResult,
    ) {
        documenttranslator.native.NativeRenderer.log(
            "Google Drive update authorization result received: " +
                "hasResolution=${result.hasResolution()}, hasToken=${result.accessToken != null}",
        )
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent ?: run {
                finish("Google не вернул экран авторизации.")
                return
            }
            onAuthorizationRequired(
                androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
            )
            return
        }
        val token = result.accessToken ?: run {
            finish("Google не вернул токен доступа к Drive.")
            return
        }
        androidcoresdk.coroutines.LifecycleCoroutineRunner.launch(activity) {
            runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { downloadUpdate(token) }
            }
                .onSuccess { apk ->
                    if (apk == null) {
                        finish("Новой версии DocumentTranslator на Google Drive нет.")
                    } else {
                        confirmSameVersionOrLaunch(apk)
                    }
                }
                .onFailure { finish("Не удалось обновить приложение: ${it.message}") }
        }
    }

    private fun downloadUpdate(token: String): java.io.File? {
        val driveFile = findLatestApk(token) ?: return null
        onStatus("Найдена ${driveFile.name}. Скачивание…")
        val directory = java.io.File(activity.cacheDir, "self-updates").apply { mkdirs() }
        val apk = java.io.File(directory, "update.apk")
        try {
            androidcoresdk.drive.GoogleDriveClient(token).download(driveFile.id, apk)
            validateApk(apk)
            return apk
        } catch (exception: Exception) {
            apk.delete()
            throw exception
        }
    }

    private fun findLatestApk(token: String): DriveFile? {
        val client = androidcoresdk.drive.GoogleDriveClient(token)
        return client.listFiles(client.ensureFolderPath(FOLDER_PATH)).mapNotNull { file ->
            VERSIONED_APK.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()?.let { versionCode ->
                DriveFile(file.id, file.name, versionCode)
            }
        }.maxByOrNull { it.versionCode }
    }

    private fun validateApk(apk: java.io.File) {
        val archive = activity.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: error("Google Drive вернул некорректный APK.")
        check(archive.packageName == activity.packageName) {
            "APK предназначен для ${archive.packageName}, а не для ${activity.packageName}."
        }
        check(versionCode(archive) >= installedVersionCode()) { "На Google Drive есть только более старая версия." }
    }

    private fun launchUpdater(apk: java.io.File) {
        @Suppress("DEPRECATION")
        val updater = activity.packageManager.getPackageInfo(UPDATER_PACKAGE, 0)
        documenttranslator.native.NativeRenderer.log(
            "Updater request: installedVersion=${updater.versionName}, code=${versionCode(updater)}, " +
                "updatedAt=${updater.lastUpdateTime}, component=$UPDATER_ACTIVITY, " +
                "callerTask=${activity.taskId}, apkBytes=${apk.length()}",
        )
        val uri = androidx.core.content.FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        try {
            activity.startActivity(
                android.content.Intent(ACTION_INSTALL_UPDATE)
                    .setClassName(UPDATER_PACKAGE, UPDATER_ACTIVITY)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    .putExtra(EXTRA_TARGET_PACKAGE, activity.packageName),
            )
            documenttranslator.native.NativeRenderer.log("Updater startActivity returned; awaiting installation result")
        } catch (exception: android.content.ActivityNotFoundException) {
            throw IllegalStateException("ApkUpdater не установлен.", exception)
        } catch (exception: Exception) {
            documenttranslator.native.NativeRenderer.log("Updater launch failed: ${android.util.Log.getStackTraceString(exception)}")
            throw exception
        }
    }

    private fun confirmSameVersionOrLaunch(apk: java.io.File) {
        val candidateVersionCode = activity.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?.let(::versionCode)
            ?: error("Не удалось прочитать версию скачанного APK.")
        val installedVersionCode = installedVersionCode()
        if (candidateVersionCode != installedVersionCode) {
            launchUpdaterAndFinish(apk)
            return
        }

        android.app.AlertDialog.Builder(activity)
            .setTitle("Переустановить ту же версию?")
            .setMessage(
                "На Google Drive находится версия $candidateVersionCode — такая же, как установленная. " +
                    "Продолжить установку?",
            )
            .setNegativeButton("Отмена") { _, _ -> finish("Переустановка отменена.") }
            .setOnCancelListener { finish("Переустановка отменена.") }
            .setPositiveButton("Установить") { _, _ -> launchUpdaterAndFinish(apk) }
            .show()
    }

    private fun launchUpdaterAndFinish(apk: java.io.File) {
        try {
            com.isrepeat.documenttranslator.feature.logging.ApplicationDiagnostics.logInstalled(activity)
            com.isrepeat.documenttranslator.feature.logging.ApplicationDiagnostics.logArchive(activity, apk)
            val signaturesMatch = activity.packageManager.checkSignatures(activity.packageName, UPDATER_PACKAGE)
            val permission = activity.packageManager.checkPermission(
                "com.isrepeat.apkupdater.permission.INSTALL_UPDATE",
                activity.packageName,
            )
            documenttranslator.native.NativeRenderer.log("Updater access: signaturesMatch=$signaturesMatch, permission=$permission")
            check(signaturesMatch == android.content.pm.PackageManager.SIGNATURE_MATCH) {
                "DocumentTranslator и ApkUpdater подписаны разными ключами. Нужны сборки с одинаковой подписью."
            }
            check(permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                "Android не предоставил разрешение вызова ApkUpdater. " +
                    "Обновите DocumentTranslator после установки ApkUpdater."
            }
            launchUpdater(apk)
            finish("Обновление передано ApkUpdater. Ожидаем установку…")
        } catch (exception: Exception) {
            // Обработчик кнопки диалога выполняется вне coroutine исходного запроса.
            documenttranslator.native.NativeRenderer.log("Updater handoff failed: ${android.util.Log.getStackTraceString(exception)}")
            finish("Не удалось запустить ApkUpdater: ${exception.message}")
        }
    }

    private fun finish(message: String) {
        isRunning = false
        onStatus(message)
    }

    private fun authorizationFailure(operation: String, exception: Exception): String {
        val statusCode = (exception as? com.google.android.gms.common.api.ApiException)?.statusCode
        val diagnostic = "$operation: type=${exception::class.java.name}, statusCode=$statusCode, " +
            "message=${exception.message}, cause=${exception.cause?.javaClass?.name}:${exception.cause?.message}"
        documenttranslator.native.NativeRenderer.log(diagnostic)
        return "Доступ к Google Drive не предоставлен: ${exception.message}"
    }

    @Suppress("DEPRECATION")
    private fun versionCode(packageInfo: android.content.pm.PackageInfo): Long =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Long =
        versionCode(activity.packageManager.getPackageInfo(activity.packageName, 0))

    private data class DriveFile(val id: String, val name: String, val versionCode: Long)

    private companion object {
        const val ACTION_INSTALL_UPDATE = "com.isrepeat.apkupdater.action.INSTALL_UPDATE"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val UPDATER_ACTIVITY = "com.isrepeat.apkupdater.UpdaterActivity"
        const val UPDATER_PACKAGE = "com.isrepeat.apkupdater"
        val FOLDER_PATH = listOf("Android", "DocumentTranslator")
        val VERSIONED_APK = Regex("DocumentTranslator-(\\d+)-.+\\.apk", RegexOption.IGNORE_CASE)
    }
}