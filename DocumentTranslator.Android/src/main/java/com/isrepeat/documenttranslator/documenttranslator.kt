package com.isrepeat.documenttranslator

// Псевдопространство имён для краткого обращения к native-части приложения.
internal object documenttranslator {
    object native {
        val NativeRenderer = com.isrepeat.documenttranslator.native.NativeRenderer
        object AppSessionSignal {
            val UPDATE_APPLICATION = com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.UPDATE_APPLICATION
            val UPLOAD_SCREENSHOT = com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.UPLOAD_SCREENSHOT
            val EXPORT_LOGS = com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.EXPORT_LOGS
            val SET_STATUS = com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.SET_STATUS
            fun fromValue(value: Int) = com.isrepeat.documenttranslator.native.NativeRenderer.AppSessionSignal.fromValue(value)
        }
        fun NativeRenderSurfaceView(context: android.content.Context) =
            com.isrepeat.documenttranslator.native.NativeRenderSurfaceView(context)
    }
}