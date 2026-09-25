package com.isrepeat.documenttranslator.feature.screenshot

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import com.isrepeat.documenttranslator.native.NativeRenderSurfaceView
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Считывает OpenGL Surface через PixelCopy и сохраняет PNG во временный cache.
class ScreenshotCapture {
    suspend fun capture(surface: NativeRenderSurfaceView, cacheDir: File): File =
        save(captureBitmap(surface), cacheDir)

    private suspend fun captureBitmap(surface: NativeRenderSurfaceView): Bitmap = suspendCancellableCoroutine { continuation ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            continuation.resumeWithException(UnsupportedOperationException("PixelCopy требует Android 8.0 или новее."))
            return@suspendCancellableCoroutine
        }
        if (!surface.holder.surface.isValid || surface.width <= 0 || surface.height <= 0) {
            continuation.resumeWithException(IllegalStateException("OpenGL surface недоступен."))
            return@suspendCancellableCoroutine
        }
        val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
        PixelCopy.request(surface.holder.surface, bitmap, { result ->
            if (result == PixelCopy.SUCCESS) {
                continuation.resume(bitmap)
            } else {
                bitmap.recycle()
                continuation.resumeWithException(IllegalStateException("Ошибка PixelCopy: $result"))
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun save(bitmap: Bitmap, cacheDir: File): File {
        val directory = File(cacheDir, "google-drive-screenshots").apply { mkdirs() }
        val name = "DocumentTranslator-${SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())}.png"
        return File(directory, name).also { file ->
            try {
                FileOutputStream(file).use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Не удалось сохранить PNG." }
                }
            } finally {
                bitmap.recycle()
            }
        }
    }
}