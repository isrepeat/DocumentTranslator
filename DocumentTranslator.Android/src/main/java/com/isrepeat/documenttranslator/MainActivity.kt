package com.isrepeat.documenttranslator

import com.isrepeat.androidcoresdk.androidcoresdk
import com.isrepeat.androidappkit.androidappkit

class MainActivity : androidx.activity.ComponentActivity() {
    private lateinit var driveUploader: androidappkit.drive.GoogleDriveUploader
    private lateinit var nativeRenderSurface: android.view.SurfaceView
    private lateinit var sessionLog: androidappkit.logging.NativeSessionLog
    private lateinit var updateController: androidappkit.update.GoogleDriveUpdateController

    private val authorizeGoogleDriveUpdate = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { updateController.completeAuthorization(it.data) }
    private val authorizeGoogleDriveUpload = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { driveUploader.completeAuthorization(it.data) }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        sessionLog = androidappkit.logging.NativeSessionLog(
            androidappkit.AppIdentity("DocumentTranslator", "DocumentTranslator/Logs"),
            androidappkit.NativeLogConfigurator { documenttranslator.native.NativeRenderer.configureLogFile(it) },
        )
        sessionLog.configure(this)
        documenttranslator.native.NativeRenderer.initialize(filesDir, assets)
        documenttranslator.native.NativeRenderer.log("MainActivity.onCreate: NativeRenderer initialized")
        com.isrepeat.documenttranslator.feature.logging.ApplicationDiagnostics.logInstalled(this)
        driveUploader = androidappkit.drive.GoogleDriveUploader(
            this,
            androidappkit.drive.GoogleDriveUploadConfiguration(listOf("Android", "DocumentTranslator", "Uploads")),
            authorizeGoogleDriveUpload::launch,
            ::handleDriveResult,
        )
        updateController = androidappkit.update.GoogleDriveUpdateController(
            this,
            androidappkit.update.GoogleDriveUpdateConfiguration(
                listOf("Android", "DocumentTranslator"),
                Regex("DocumentTranslator-(\\d+)-.+\\.apk", RegexOption.IGNORE_CASE),
                "com.isrepeat.apkupdater",
                "com.isrepeat.apkupdater.UpdaterActivity",
                "com.isrepeat.apkupdater.permission.INSTALL_UPDATE",
                "com.isrepeat.apkupdater.action.INSTALL_UPDATE",
            ),
            authorizeGoogleDriveUpdate::launch,
            ::showNativeStatus,
            androidappkit.update.UpdateLogger { documenttranslator.native.NativeRenderer.log(it) },
            ::confirmSameVersion,
        )
        documenttranslator.native.NativeRenderer.setCommandHandler(::handleNativeEvent)
        nativeRenderSurface = documenttranslator.native.NativeRenderSurfaceView(this)
        setContentView(nativeRenderSurface)
    }

    private fun handleNativeEvent(signal: Int, value: String, additionalValue: String) = runOnUiThread {
        when (documenttranslator.native.AppSessionSignal.fromValue(signal)) {
            documenttranslator.native.AppSessionSignal.UPDATE_APPLICATION -> updateController.start()
            documenttranslator.native.AppSessionSignal.UPLOAD_SCREENSHOT -> uploadScreenshot()
            documenttranslator.native.AppSessionSignal.EXPORT_LOGS -> uploadLogs()
            documenttranslator.native.AppSessionSignal.SET_STATUS -> showNativeStatus(value)
            else -> Unit
        }
    }

    private fun uploadScreenshot() = androidcoresdk.coroutines.LifecycleCoroutineRunner.launch(this) {
        runCatching { androidappkit.media.SurfaceScreenshotCapture("google-drive-screenshots", "DocumentTranslator").capture(nativeRenderSurface, cacheDir) }
            .onSuccess { driveUploader.upload(it, "image/png") }
            .onFailure { showNativeStatus("Failed to create screenshot: ${it.message}") }
    }

    private fun uploadLogs() {
        val uri = sessionLog.currentUri() ?: return showNativeStatus("Session log has not been created yet.")
        driveUploader.upload(uri, "text/plain", "DocumentTranslator-session.log")
    }

    private fun handleDriveResult(result: androidappkit.drive.GoogleDriveUploadResult) = when (result) {
        is androidappkit.drive.GoogleDriveUploadResult.Success -> showNativeStatus("${result.fileName} uploaded to Google Drive")
        is androidappkit.drive.GoogleDriveUploadResult.Failure -> showNativeStatus(result.message)
    }

    private fun confirmSameVersion(onConfirmed: () -> Unit, onCancelled: () -> Unit) {
        android.app.AlertDialog.Builder(this)
            .setTitle("Reinstall the same version?")
            .setMessage("The version on Google Drive matches the installed version. Continue installation?")
            .setNegativeButton("Cancel") { _, _ -> onCancelled() }
            .setOnCancelListener { onCancelled() }
            .setPositiveButton("Install") { _, _ -> onConfirmed() }
            .show()
    }

    private fun showNativeStatus(message: String) = runOnUiThread {
        documenttranslator.native.NativeRenderer.log("Status: $message")
        documenttranslator.native.NativeRenderer.dispatch(documenttranslator.native.AppSessionSignal.SET_STATUS, message)
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}