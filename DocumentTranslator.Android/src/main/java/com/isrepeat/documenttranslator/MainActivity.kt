package com.isrepeat.documenttranslator

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.isrepeat.documenttranslator.feature.drive.GoogleDriveFileSender
import com.isrepeat.documenttranslator.feature.logging.NativeLogFile
import com.isrepeat.documenttranslator.feature.screenshot.ScreenshotCapture
import com.isrepeat.documenttranslator.feature.update.DocumentUpdateController
import com.isrepeat.documenttranslator.native.NativeRenderSurfaceView
import com.isrepeat.documenttranslator.native.NativeRenderer
import com.isrepeat.androidcoresdk.HelloWorld
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
        NativeLogFile.configure(this)
        NativeRenderer.initialize(filesDir, assets)
        NativeRenderer.log("MainActivity.onCreate: NativeRenderer initialized")
        com.isrepeat.documenttranslator.feature.logging.ApplicationDiagnostics.logInstalled(this)
        handleUpdaterResult(intent)
        NativeRenderer.log(HelloWorld.message())
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
        NativeRenderer.log("MainActivity.onCreate: NativeRenderSurfaceView attached")
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdaterResult(intent)
    }

    override fun onResume() {
        super.onResume()
        NativeRenderer.log("MainActivity.onResume: task=$taskId")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        NativeRenderer.log("MainActivity.windowFocus=$hasFocus, task=$taskId")
    }

    private fun handleUpdaterResult(intent: android.content.Intent) {
        NativeRenderer.log("MainActivity intent: action=${intent.action}, session=${intent.getIntExtra("install_session_id", -1)}")
        intent.getStringExtra("updater_trace")?.takeIf { it.isNotBlank() }?.let {
            NativeRenderer.log("External updater trace:\n$it")
        }
        intent.getStringExtra("update_error")?.let {
            NativeRenderer.log("External updater failure: $it")
        }
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
        val logUri = NativeLogFile.currentUri()
        if (logUri == null) {
            showNativeStatus("Session-лог ещё не создан.")
            return
        }
        driveFileSender.send(logUri, "text/plain", "DocumentTranslator-session.log")
    }

    private fun handleDriveResult(result: GoogleDriveFileSender.Result) {
        when (result) {
            is GoogleDriveFileSender.Result.Success -> showNativeStatus("${result.fileName} загружен в Google Drive")
            is GoogleDriveFileSender.Result.Failure -> showNativeStatus(result.message)
        }
    }

    private fun showNativeStatus(message: String) {
        runOnUiThread {
            NativeRenderer.log("Статус: $message")
            NativeRenderer.dispatch(NativeRenderer.AppSessionSignal.SET_STATUS, message)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}