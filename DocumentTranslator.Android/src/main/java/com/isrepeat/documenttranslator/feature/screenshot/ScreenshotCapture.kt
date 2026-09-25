package com.isrepeat.documenttranslator.feature.screenshot

import com.isrepeat.androidcoresdk.androidcoresdk

//
// Считывает OpenGL Surface через PixelCopy и сохраняет PNG во временный cache.
//
class ScreenshotCapture {
    suspend fun capture(
        surface: android.view.SurfaceView,
        cacheDir: java.io.File,
    ): java.io.File =
        androidcoresdk.media.ScreenshotCapture.capture(
            surface,
            cacheDir,
            "google-drive-screenshots",
            "DocumentTranslator",
        )
}