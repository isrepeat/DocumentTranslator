package com.isrepeat.documenttranslator.feature.drive

import com.isrepeat.documenttranslator.documenttranslator
import com.isrepeat.androidcoresdk.androidcoresdk

//
// Прикладная точка отправки файлов в папку DocumentTranslator на Google Drive.
//
class GoogleDriveFileSender(
    private val activity: androidx.activity.ComponentActivity,
    private val onAuthorizationRequired: (androidx.activity.result.IntentSenderRequest) -> Unit,
    private val onCompleted: (Result) -> Unit,
) {
    sealed interface Result {
        data class Success(val fileName: String) : Result
        data class Failure(val message: String) : Result
    }

    private data class PendingFile(
        val name: String,
        val mimeType: String,
        val open: () -> java.io.InputStream?,
    )

    private var pendingFile: PendingFile? = null
    private var isRunning = false

    fun send(file: java.io.File, mimeType: String = "application/octet-stream") {
        if (!file.isFile) {
            onCompleted(Result.Failure("Файл ${file.name} не найден."))
            return
        }
        send(PendingFile(file.name, mimeType, file::inputStream))
    }

    fun send(uri: android.net.Uri, mimeType: String? = null, fileName: String? = null) {
        val name = fileName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
        val type = mimeType ?: activity.contentResolver.getType(uri) ?: "application/octet-stream"
        send(PendingFile(name, type) { activity.contentResolver.openInputStream(uri) })
    }

    fun completeAuthorization(intent: android.content.Intent?) {
        try {
            handle(
                com.google.android.gms.auth.api.identity.Identity
                    .getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(intent),
            )
        } catch (exception: Exception) {
            finish(Result.Failure("Доступ к Google Drive не предоставлен: ${exception.message}"))
        }
    }

    private fun send(file: PendingFile) {
        if (isRunning) {
            onCompleted(Result.Failure("Загрузка в Google Drive уже выполняется."))
            return
        }
        isRunning = true
        pendingFile = file
        val request = com.google.android.gms.auth.api.identity.AuthorizationRequest.builder()
            .setRequestedScopes(listOf(com.google.android.gms.common.api.Scope(DRIVE_SCOPE)))
            .build()
        com.google.android.gms.auth.api.identity.Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener(::handle)
            .addOnFailureListener { exception ->
                documenttranslator.native.NativeRenderer.log("Google Drive upload authorization failed: ${exception.message}")
                finish(Result.Failure("Доступ к Google Drive не предоставлен: ${exception.message}"))
            }
    }

    private fun handle(result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        if (result.hasResolution()) {
            val pendingIntent = result.pendingIntent
                ?: return finish(Result.Failure("Google не вернул экран авторизации."))
            onAuthorizationRequired(
                androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
            )
            return
        }
        val token = result.accessToken
            ?: return finish(Result.Failure("Google не вернул токен доступа к Drive."))
        val file = pendingFile ?: return finish(Result.Failure("Не выбран файл для загрузки."))
        androidcoresdk.coroutines.LifecycleCoroutineRunner.launch(activity) {
            val uploadResult = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    androidcoresdk.drive.GoogleDriveClient(token).run {
                        upload(ensureFolderPath(FOLDER_PATH), file.name, file.mimeType, file.open)
                    }
                }
            }.fold(
                onSuccess = { Result.Success(file.name) },
                onFailure = { Result.Failure("Не удалось загрузить ${file.name}: ${it.message}") },
            )
            finish(uploadResult)
        }
    }

    private fun finish(result: Result) {
        pendingFile = null
        isRunning = false
        onCompleted(result)
    }

    private companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"
        val FOLDER_PATH = listOf("Android", "DocumentTranslator", "Uploads")
    }
}