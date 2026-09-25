package com.isrepeat.documenttranslator.feature.logging

import com.isrepeat.documenttranslator.documenttranslator
import com.isrepeat.androidcoresdk.androidcoresdk

//
// Создаёт публичный MediaStore-файл и передаёт его descriptor единому native-логгеру.
//
internal object NativeLogFile {
    private var uri: android.net.Uri? = null

    @Synchronized
    fun configure(context: android.content.Context): android.net.Uri {
        uri?.let { return it }
        val sessionLog = androidcoresdk.logging.MediaStoreSessionLog.create(
            context,
            "DocumentTranslator/Logs",
            "DocumentTranslator",
        )
        documenttranslator.native.NativeRenderer.configureLogFile(sessionLog.nativePath)
        uri = sessionLog.uri
        return sessionLog.uri
    }

    @Synchronized
    fun currentUri(): android.net.Uri? = uri
}