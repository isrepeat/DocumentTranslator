package com.isrepeat.documenttranslator.feature.drive

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.lifecycleScope
import com.isrepeat.documenttranslator.native.NativeRenderer
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Единая точка отправки файлов приложения в Android/DocumentTranslator/Uploads.
class GoogleDriveFileSender(
    private val activity: ComponentActivity,
    private val onAuthorizationRequired: (IntentSenderRequest) -> Unit,
    private val onCompleted: (Result) -> Unit,
) {
    sealed interface Result {
        data class Success(val fileName: String) : Result
        data class Failure(val message: String) : Result
    }

    private data class PendingFile(val name: String, val mimeType: String, val openInputStream: () -> java.io.InputStream?)

    private var pendingFile: PendingFile? = null
    private var isRunning = false

    fun send(file: File, mimeType: String = "application/octet-stream") {
        if (!file.isFile) {
            onCompleted(Result.Failure("Файл ${file.name} не найден."))
            return
        }
        send(PendingFile(file.name, mimeType) { file.inputStream() })
    }

    fun send(uri: Uri, mimeType: String? = null, fileName: String? = null) {
        val name = fileName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
        val type = mimeType ?: activity.contentResolver.getType(uri) ?: "application/octet-stream"
        send(PendingFile(name, type) { activity.contentResolver.openInputStream(uri) })
    }

    fun completeAuthorization(intent: Intent?) {
        try {
            handleAuthorizationResult(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(intent))
        } catch (exception: Exception) {
            finish(Result.Failure(authorizationFailure("Google Drive upload authorization result failed", exception)))
        }
    }

    private fun send(file: PendingFile) {
        if (isRunning) {
            onCompleted(Result.Failure("Загрузка в Google Drive уже выполняется."))
            return
        }
        isRunning = true
        pendingFile = file
        NativeRenderer.log("Google Drive upload authorization requested: package=${activity.packageName}, scope=$DRIVE_SCOPE, file=${file.name}")
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE))).build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener(::handleAuthorizationResult)
            .addOnFailureListener { exception ->
                finish(Result.Failure(authorizationFailure("Google Drive upload authorization request failed", exception)))
            }
    }

    private fun handleAuthorizationResult(result: AuthorizationResult) {
        NativeRenderer.log("Google Drive upload authorization result received: hasResolution=${result.hasResolution()}, hasToken=${result.accessToken != null}")
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent ?: run {
                finish(Result.Failure("Google не вернул экран авторизации."))
                return
            }
            onAuthorizationRequired(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            return
        }
        val token = result.accessToken ?: run {
            finish(Result.Failure("Google не вернул токен доступа к Drive."))
            return
        }
        val file = pendingFile ?: run {
            finish(Result.Failure("Не выбран файл для загрузки."))
            return
        }
        activity.lifecycleScope.launch {
            val uploadResult = runCatching { withContext(Dispatchers.IO) { uploadFile(file, token) } }.fold(
                onSuccess = { Result.Success(file.name) },
                onFailure = { Result.Failure("Не удалось загрузить ${file.name}: ${it.message}") },
            )
            finish(uploadResult)
        }
    }

    private fun uploadFile(file: PendingFile, token: String) {
        val parentId = ensureFolderPath(token)
        val boundary = "DocumentTranslator${System.currentTimeMillis()}"
        val connection = openConnection(URL(UPLOAD_URL), token, "POST").apply {
            doOutput = true
            setChunkedStreamingMode(0)
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }
        connection.outputStream.buffered().use { output ->
            output.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            output.write(JSONObject().apply {
                put("name", file.name)
                put("mimeType", file.mimeType)
                put("parents", listOf(parentId))
            }.toString().toByteArray(Charsets.UTF_8))
            output.write("\r\n--$boundary\r\nContent-Type: ${file.mimeType}\r\n\r\n".toByteArray())
            file.openInputStream()?.use { input -> input.copyTo(output) } ?: error("Не удалось открыть исходный файл.")
            output.write("\r\n--$boundary--\r\n".toByteArray())
        }
        requireSuccess(connection)
    }

    private fun ensureFolderPath(token: String): String {
        var parentId = "root"
        FOLDER_PATH.forEach { name -> parentId = findFolder(token, name, parentId) ?: createFolder(token, name, parentId) }
        return parentId
    }

    private fun findFolder(token: String, name: String, parentId: String): String? {
        val safeName = name.replace("'", "\\'")
        val query = "name = '$safeName' and mimeType = '$FOLDER_MIME_TYPE' and '$parentId' in parents and trashed = false"
        val connection = openConnection(URL("$FILES_URL?q=${encode(query)}&pageSize=1&fields=${encode("files(id)")}"), token, "GET")
        val response = connection.inputStream.bufferedReader().use { it.readText() }
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

    private fun openConnection(url: URL, token: String, method: String): HttpURLConnection =
        (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECTION_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun requireSuccess(connection: HttpURLConnection) {
        check(connection.responseCode in 200..299) {
            "Google Drive вернул HTTP ${connection.responseCode}: ${connection.errorStream?.bufferedReader()?.use { it.readText() }}"
        }
    }

    private fun finish(result: Result) {
        pendingFile = null
        isRunning = false
        onCompleted(result)
    }

    private fun authorizationFailure(operation: String, exception: Exception): String {
        val statusCode = (exception as? ApiException)?.statusCode
        val diagnostic = "$operation: type=${exception::class.java.name}, statusCode=$statusCode, message=${exception.message}, cause=${exception.cause?.javaClass?.name}:${exception.cause?.message}"
        NativeRenderer.log(diagnostic)
        return "Доступ к Google Drive не предоставлен: ${exception.message}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private companion object {
        const val CONNECTION_TIMEOUT_MILLIS = 15_000
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val READ_TIMEOUT_MILLIS = 120_000
        const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        val FOLDER_PATH = listOf("Android", "DocumentTranslator", "Uploads")
    }
}