package com.isrepeat.documenttranslator

//
// Псевдопространства имён для краткого обращения к публичным объектам приложения.
//
internal object documenttranslator {
    object native {
        val NativeRenderer = com.isrepeat.documenttranslator.native.NativeRenderer
        object AppSessionSignal {
            val UPDATE_APPLICATION =
                com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.UPDATE_APPLICATION
            val UPLOAD_SCREENSHOT =
                com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.UPLOAD_SCREENSHOT
            val EXPORT_LOGS =
                com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.EXPORT_LOGS
            val SET_STATUS =
                com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.SET_STATUS

            fun fromValue(value: Int) =
                com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.fromValue(value)
        }
        fun NativeRenderSurfaceView(context: android.content.Context) =
            com.isrepeat.documenttranslator.native.NativeRenderSurfaceView(context)
    }
    object feature {
        object drive {
            class GoogleDriveFileSender(
                activity: androidx.activity.ComponentActivity,
                onAuthorizationRequired: (androidx.activity.result.IntentSenderRequest) -> Unit,
                onCompleted: (Result) -> Unit,
            ) {
                sealed interface Result {
                    data class Success(val fileName: String) : Result
                    data class Failure(val message: String) : Result
                }

                private val sender = com.isrepeat.documenttranslator.feature.drive.GoogleDriveFileSender(
                    activity = activity,
                    onAuthorizationRequired = onAuthorizationRequired,
                    onCompleted = { result ->
                        onCompleted(
                            when (result) {
                                is com.isrepeat.documenttranslator.feature.drive.GoogleDriveFileSender.Result.Success ->
                                    Result.Success(result.fileName)
                                is com.isrepeat.documenttranslator.feature.drive.GoogleDriveFileSender.Result.Failure ->
                                    Result.Failure(result.message)
                            },
                        )
                    },
                )

                fun send(file: java.io.File, mimeType: String = "application/octet-stream") {
                    sender.send(file, mimeType)
                }

                fun send(uri: android.net.Uri, mimeType: String? = null, fileName: String? = null) {
                    sender.send(uri, mimeType, fileName)
                }

                fun completeAuthorization(intent: android.content.Intent?) {
                    sender.completeAuthorization(intent)
                }
            }
        }
        object logging {
            val NativeLogFile = com.isrepeat.documenttranslator.feature.logging.NativeLogFile
        }
        object screenshot {
            fun ScreenshotCapture() = com.isrepeat.documenttranslator.feature.screenshot.ScreenshotCapture()
        }
        object update {
            class DocumentUpdateController(
                activity: androidx.activity.ComponentActivity,
                onAuthorizationRequired: (androidx.activity.result.IntentSenderRequest) -> Unit,
                onStatus: (String) -> Unit,
            ) {
                private val controller = com.isrepeat.documenttranslator.feature.update.DocumentUpdateController(
                    activity = activity,
                    onAuthorizationRequired = onAuthorizationRequired,
                    onStatus = onStatus,
                )

                fun start() {
                    controller.start()
                }

                fun completeAuthorization(intent: android.content.Intent?) {
                    controller.completeAuthorization(intent)
                }
            }
        }
    }
}