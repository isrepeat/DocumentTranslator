package com.example.mobileclock.feature.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Ищет обновление приложения на Drive и передаёт проверенный APK ApkUpdater-у.
class DocumentUpdateController(
    private val activity: ComponentActivity,
    private val onAuthorizationRequired: (IntentSenderRequest) -> Unit,
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
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener(::handleAuthorizationResult)
            .addOnFailureListener { finish("Не удалось открыть Google Drive: ${it.message}") }
    }

    fun completeAuthorization(intent: Intent?) {
        try {
            handleAuthorizationResult(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intent))
        } catch (exception: Exception) {
            finish("Доступ к Google Drive не предоставлен: ${exception.message}")
        }
    }

    private fun handleAuthorizationResult(result: AuthorizationResult) {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent ?: run {
                finish("Google не вернул экран авторизации.")
                return
            }
            onAuthorizationRequired(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            return
        }
        val token = result.accessToken ?: run {
            finish("Google не вернул токен доступа к Drive.")
            return
        }
        activity.lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { downloadUpdate(token) } }
                .onSuccess { apk ->
                    if (apk == null) {
                        finish("Новой версии DocumentTranslator на Google Drive нет.")
                    } else {
                        launchUpdater(apk)
                        onStatus("Обновление передано ApkUpdater. Ожидаем установку…")
                    }
                }
                .onFailure { finish("Не удалось обновить приложение: ${it.message}") }
        }
    }

    private fun downloadUpdate(token: String): File? {
        val driveFile = findLatestApk(token) ?: return null
        onStatus("Найдена ${driveFile.name}. Скачивание…")
        val directory = File(activity.cacheDir, "self-updates").apply { mkdirs() }
        val apk = File(directory, "update.apk")
        try {
            val connection = openConnection(URL("$FILES_URL/${driveFile.id}?alt=media"), token)
            connection.inputStream.use { input -> apk.outputStream().use { input.copyTo(it) } }
            validateApk(apk)
            return apk
        } catch (exception: Exception) {
            apk.delete()
            throw exception
        }
    }

    private fun findLatestApk(token: String): DriveFile? {
        val folderId = ensureFolderPath(token)
        val query = "'$folderId' in parents and trashed = false"
        val url = URL("$FILES_URL?q=${encode(query)}&pageSize=100&fields=${encode("files(id,name)")}")
        val response = openConnection(url, token).inputStream.bufferedReader().use { it.readText() }
        val files = JSONObject(response).getJSONArray("files")
        return (0 until files.length()).mapNotNull { index ->
            files.getJSONObject(index).let { file ->
                val name = file.getString("name")
                VERSIONED_APK.matchEntire(name)?.groupValues?.get(1)?.toLongOrNull()?.let { DriveFile(file.getString("id"), name, it) }
            }
        }.maxByOrNull { it.versionCode }
    }

    private fun ensureFolderPath(token: String): String {
        var parentId = "root"
        FOLDER_PATH.forEach { name -> parentId = findFolder(token, name, parentId) ?: createFolder(token, name, parentId) }
        return parentId
    }

    private fun findFolder(token: String, name: String, parentId: String): String? {
        val query = "name = '$name' and mimeType = '$FOLDER_MIME_TYPE' and '$parentId' in parents and trashed = false"
        val response = openConnection(URL("$FILES_URL?q=${encode(query)}&pageSize=1&fields=${encode("files(id)")}"), token)
            .inputStream.bufferedReader().use { it.readText() }
        return JSONObject(response).getJSONArray("files").optJSONObject(0)?.optString("id")
    }

    private fun createFolder(token: String, name: String, parentId: String): String {
        val connection = openConnection(URL(FILES_URL), token, "POST").apply {
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        }
        connection.outputStream.bufferedWriter().use { writer ->
            writer.write(JSONObject().apply {
                put("name", name)
                put("mimeType", FOLDER_MIME_TYPE)
                put("parents", listOf(parentId))
            }.toString())
        }
        requireSuccess(connection)
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getString("id")
    }

    private fun validateApk(apk: File) {
        val archive = activity.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: error("Google Drive вернул некорректный APK.")
        check(archive.packageName == activity.packageName) { "APK предназначен для ${archive.packageName}, а не для ${activity.packageName}." }
        check(versionCode(archive) > installedVersionCode()) { "На Google Drive есть только установленная или более старая версия." }
    }

    private fun launchUpdater(apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        try {
            activity.startActivity(
                Intent(ACTION_INSTALL_UPDATE)
                    .setClassName(UPDATER_PACKAGE, UPDATER_ACTIVITY)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(EXTRA_TARGET_PACKAGE, activity.packageName),
            )
        } catch (exception: ActivityNotFoundException) {
            throw IllegalStateException("ApkUpdater не установлен.", exception)
        }
    }

    private fun finish(message: String) {
        isRunning = false
        onStatus(message)
    }

    private fun openConnection(url: URL, token: String, method: String = "GET"): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $token")
            if (method == "GET") {
                connect()
                requireSuccess(this)
            }
        }

    private fun requireSuccess(connection: HttpURLConnection) {
        check(connection.responseCode in 200..299) { "Google Drive вернул HTTP ${connection.responseCode}" }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(packageInfo: android.content.pm.PackageInfo): Long =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Long =
        versionCode(activity.packageManager.getPackageInfo(activity.packageName, 0))

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private data class DriveFile(val id: String, val name: String, val versionCode: Long)

    private companion object {
        const val ACTION_INSTALL_UPDATE = "com.isrepeat.apkupdater.action.INSTALL_UPDATE"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        const val EXTRA_TARGET_PACKAGE = "target_package"
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val UPDATER_ACTIVITY = "com.isrepeat.apkupdater.UpdaterActivity"
        const val UPDATER_PACKAGE = "com.isrepeat.apkupdater"
        val FOLDER_PATH = listOf("Android", "DocumentTranslator")
        val VERSIONED_APK = Regex("DocumentTranslator-(\\d+)-.+\\.apk", RegexOption.IGNORE_CASE)
    }
}