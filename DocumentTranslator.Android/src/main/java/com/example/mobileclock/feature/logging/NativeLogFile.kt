package com.example.mobileclock.feature.logging

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.mobileclock.native.NativeRenderer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Создаёт публичный MediaStore-файл и передаёт его descriptor единому native-логгеру.
internal object NativeLogFile {
    private var uri: Uri? = null

    @Synchronized
    fun configure(context: Context): Uri {
        uri?.let { return it }
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Публичный session-лог через MediaStore требует Android 10 или новее."
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "DocumentTranslator-session-$timestamp.log")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/DocumentTranslator/Logs")
        }
        val logUri = checkNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)) {
            "Не удалось создать session-лог в Downloads."
        }
        val descriptor = checkNotNull(context.contentResolver.openFileDescriptor(logUri, "rw")) {
            "Не удалось открыть session-лог."
        }
        val fileDescriptor = descriptor.detachFd()
        NativeRenderer.configureLogFile("/proc/self/fd/$fileDescriptor")
        uri = logUri
        return logUri
    }

    @Synchronized
    fun currentUri(): Uri? = uri
}