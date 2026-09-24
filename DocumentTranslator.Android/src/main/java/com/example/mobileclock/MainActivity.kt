package com.example.mobileclock

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.mobileclock.feature.crash.PublicDiagnostics
import com.example.mobileclock.feature.drive.GoogleDriveFileSender
import com.example.mobileclock.feature.screenshot.ScreenshotCapture
import com.example.mobileclock.feature.update.DocumentUpdateController
import com.example.mobileclock.native.NativeRenderSurfaceView
import com.example.mobileclock.native.NativeRenderer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var driveFileSender: GoogleDriveFileSender
    private lateinit var nativeRenderSurface: NativeRenderSurfaceView
    private lateinit var updateController: DocumentUpdateController

    private val authorizeGoogleDriveUpdate = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> updateController.completeAuthorization(result.data) }

    private val authorizeGoogleDriveUpload = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> driveFileSender.completeAuthorization(result.data) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PublicDiagnostics.write("MainActivity.onCreate started")
        NativeRenderer.initialize(filesDir, assets)
        PublicDiagnostics.write("NativeRenderer.initialize completed")
        driveFileSender = GoogleDriveFileSender(
            activity = this,
            onAuthorizationRequired = authorizeGoogleDriveUpload::launch,
            onCompleted = ::handleDriveResult,
        )
        updateController = DocumentUpdateController(
            activity = this,
            onAuthorizationRequired = authorizeGoogleDriveUpdate::launch,
            onStatus = ::showNativeStatus,
        )
        NativeRenderer.setCommandHandler(::handleNativeEvent)
        nativeRenderSurface = NativeRenderSurfaceView(this)
        setContentView(nativeRenderSurface)
        PublicDiagnostics.write("NativeRenderSurfaceView attached")
    }

    private fun handleNativeEvent(signal: Int, value: String, additionalValue: String) {
        runOnUiThread {
            when (NativeRenderer.AppSessionSignal.fromValue(signal)) {
                NativeRenderer.AppSessionSignal.UPDATE_APPLICATION -> updateController.start()
                NativeRenderer.AppSessionSignal.UPLOAD_SCREENSHOT -> uploadScreenshot()
                NativeRenderer.AppSessionSignal.EXPORT_LOGS -> uploadLogs()
                NativeRenderer.AppSessionSignal.SET_STATUS -> showNativeStatus(value)
                null -> Unit
            }
        }
    }

    private fun uploadScreenshot() {
        lifecycleScope.launch {
            runCatching { ScreenshotCapture().capture(nativeRenderSurface, cacheDir) }
                .onSuccess { file -> driveFileSender.send(file, "image/png") }
                .onFailure { showNativeStatus("Не удалось создать скриншот: ${it.message}") }
        }
    }

    private fun uploadLogs() {
        val snapshot = PublicDiagnostics.createCacheSnapshot(this)
        if (snapshot == null) {
            showNativeStatus("Не удалось подготовить session-лог.")
            return
        }
        driveFileSender.send(snapshot, "text/plain")
    }

    private fun handleDriveResult(result: GoogleDriveFileSender.Result) {
        when (result) {
            is GoogleDriveFileSender.Result.Success -> showNativeStatus("${result.fileName} загружен в Google Drive")
            is GoogleDriveFileSender.Result.Failure -> showNativeStatus(result.message)
        }
    }

    private fun showNativeStatus(message: String) {
        NativeRenderer.dispatch(NativeRenderer.AppSessionSignal.SET_STATUS, message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}