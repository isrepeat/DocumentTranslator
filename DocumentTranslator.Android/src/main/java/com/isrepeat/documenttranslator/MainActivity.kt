package com.isrepeat.documenttranslator

import com.isrepeat.androidcoresdk.androidcoresdk

class MainActivity : androidx.activity.ComponentActivity() {
    private lateinit var driveFileSender: documenttranslator.feature.drive.GoogleDriveFileSender
    private lateinit var nativeRenderSurface: android.view.SurfaceView
    private lateinit var updateController: documenttranslator.feature.update.DocumentUpdateController

    private val authorizeGoogleDriveUpdate = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> updateController.completeAuthorization(result.data) }

    private val authorizeGoogleDriveUpload = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> driveFileSender.completeAuthorization(result.data) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        documenttranslator.feature.logging.NativeLogFile.configure(this)
        documenttranslator.native.NativeRenderer.initialize(filesDir, assets)
        documenttranslator.native.NativeRenderer.log("MainActivity.onCreate: NativeRenderer initialized")
        com.isrepeat.documenttranslator.feature.logging.ApplicationDiagnostics.logInstalled(this)
        handleUpdaterResult(intent)
        driveFileSender = documenttranslator.feature.drive.GoogleDriveFileSender(
            activity = this,
            onAuthorizationRequired = authorizeGoogleDriveUpload::launch,
            onCompleted = ::handleDriveResult,
        )
        updateController = documenttranslator.feature.update.DocumentUpdateController(
            activity = this,
            onAuthorizationRequired = authorizeGoogleDriveUpdate::launch,
            onStatus = ::showNativeStatus,
        )
        documenttranslator.native.NativeRenderer.setCommandHandler(::handleNativeEvent)
        nativeRenderSurface = documenttranslator.native.NativeRenderSurfaceView(this)
        setContentView(nativeRenderSurface)
        documenttranslator.native.NativeRenderer.log("MainActivity.onCreate: NativeRenderSurfaceView attached")
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUpdaterResult(intent)
    }

    override fun onResume() {
        super.onResume()
        documenttranslator.native.NativeRenderer.log("MainActivity.onResume: task=$taskId")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        documenttranslator.native.NativeRenderer.log("MainActivity.windowFocus=$hasFocus, task=$taskId")
    }

    private fun handleUpdaterResult(intent: android.content.Intent) {
        documenttranslator.native.NativeRenderer.log(
            "MainActivity intent: action=${intent.action}, " +
                "session=${intent.getIntExtra("install_session_id", -1)}",
        )
        intent.getStringExtra("updater_trace")?.takeIf { it.isNotBlank() }?.let {
            documenttranslator.native.NativeRenderer.log("External updater trace:\n$it")
        }
        intent.getStringExtra("update_error")?.let {
            documenttranslator.native.NativeRenderer.log("External updater failure: $it")
        }
    }

    private fun handleNativeEvent(signal: Int, value: String, additionalValue: String) {
        runOnUiThread {
            when (documenttranslator.native.AppSessionSignal.fromValue(signal)) {
                documenttranslator.native.AppSessionSignal.UPDATE_APPLICATION -> updateController.start()
                documenttranslator.native.AppSessionSignal.UPLOAD_SCREENSHOT -> uploadScreenshot()
                documenttranslator.native.AppSessionSignal.EXPORT_LOGS -> uploadLogs()
                documenttranslator.native.AppSessionSignal.SET_STATUS -> showNativeStatus(value)
                null -> Unit
                else -> Unit
            }
        }
    }

    private fun uploadScreenshot() {
        androidcoresdk.coroutines.LifecycleCoroutineRunner.launch(this) {
            runCatching {
                documenttranslator.feature.screenshot.ScreenshotCapture()
                    .capture(nativeRenderSurface, cacheDir)
            }
                .onSuccess { file -> driveFileSender.send(file, "image/png") }
                .onFailure { showNativeStatus("Не удалось создать скриншот: ${it.message}") }
        }
    }

    private fun uploadLogs() {
        val logUri = documenttranslator.feature.logging.NativeLogFile.currentUri()
        if (logUri == null) {
            showNativeStatus("Session-лог ещё не создан.")
            return
        }
        driveFileSender.send(logUri, "text/plain", "DocumentTranslator-session.log")
    }

    private fun handleDriveResult(result: documenttranslator.feature.drive.GoogleDriveFileSender.Result) {
        when (result) {
            is documenttranslator.feature.drive.GoogleDriveFileSender.Result.Success -> {
                showNativeStatus("${result.fileName} загружен в Google Drive")
            }
            is documenttranslator.feature.drive.GoogleDriveFileSender.Result.Failure -> {
                showNativeStatus(result.message)
            }
        }
    }

    private fun showNativeStatus(message: String) {
        runOnUiThread {
            documenttranslator.native.NativeRenderer.log("Статус: $message")
            documenttranslator.native.NativeRenderer.dispatch(
                documenttranslator.native.AppSessionSignal.SET_STATUS,
                message,
            )
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}