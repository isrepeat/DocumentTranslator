package com.example.mobileclock.feature.crash

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Публичный журнал создаётся в начале процесса, чтобы пережить native-падение.
// В него пишутся Kotlin-события вокруг JNI-вызовов; C++-логи требуют отдельного
// безопасного моста и не могут напрямую писать в MediaStore по файловому пути.
internal object PublicDiagnostics {
    private var applicationContext: Context? = null
    private var mediaStoreUri: Uri? = null
    private var legacyFile: File? = null

    @Synchronized
    fun initialize(context: Context) {
        if (applicationContext != null) {
            return
        }

        applicationContext = context.applicationContext
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        val fileName = "DocumentTranslator-session-$timestamp.log"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createMediaStoreLog(context, fileName)
        } else {
            createLegacyLog(context, fileName)
        }
        write("Application process created")
    }

    @Synchronized
    fun write(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "$timestamp  $message\n"
        try {
            when {
                mediaStoreUri != null -> applicationContext?.contentResolver
                    ?.openOutputStream(mediaStoreUri!!, "wa")
                    ?.bufferedWriter()
                    ?.use { writer -> writer.write(line) }
                legacyFile != null -> legacyFile?.appendText(line)
            }
        } catch (_: Exception) {
            // Диагностика не должна влиять на запуск приложения.
        }
    }

    private fun createMediaStoreLog(context: Context, fileName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DocumentTranslator/Logs")
        }
        mediaStoreUri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    private fun createLegacyLog(context: Context, fileName: String) {
        // На Android до 10 публичная папка требует runtime-разрешения.
        // Поэтому используем доступную внешнюю папку приложения как fallback.
        val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Logs")
        directory.mkdirs()
        legacyFile = File(directory, fileName)
    }
}