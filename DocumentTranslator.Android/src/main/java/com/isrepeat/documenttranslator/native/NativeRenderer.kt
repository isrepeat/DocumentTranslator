package com.isrepeat.documenttranslator.native

import com.isrepeat.androidcoresdk.nativeui.NativeMessageDispatcher
import com.isrepeat.androidcoresdk.nativeui.NativeRenderHost

object NativeRenderer : NativeRenderHost {
    enum class AppSessionSignal(val value: Int) {
        UPDATE_APPLICATION(3),
        UPLOAD_SCREENSHOT(4),
        EXPORT_LOGS(6),
        SET_STATUS(9),
        ;

        companion object {
            fun fromValue(value: Int): AppSessionSignal? = entries.firstOrNull { it.value == value }
        }
    }
    private var isApplicationInitialized = false
    private var isLogFileConfigured = false

    init {
        // Загружает libmobileclock.so из APK. После этого ART может вызвать
        // экспортированные JNI-функции из DocumentTranslator.AndroidHost/main.cpp.
        System.loadLibrary("mobileclock")
    }

    fun initialize(filesDirectory: java.io.File, assetManager: android.content.res.AssetManager) {
        // Kotlin подготавливает Android-зависимые объекты до первого GL-кадра.
        initializeApplication(filesDirectory)
        nativeSetAssetManager(assetManager)
        nativeSetCommandDispatcher(NativeBridgeCommandDispatcher)
    }

    @Synchronized
    private fun initializeApplication(filesDirectory: java.io.File) {
        if (isApplicationInitialized) {
            return
        }
        // Состояние приложения передаётся явно. Настройка логов не должна
        // неявно создавать репозитории или AppSessionController.
        val storageFile = java.io.File(filesDirectory, "documenttranslator-state.json")
        nativeInitializeApplication(storageFile.absolutePath)
        isApplicationInitialized = true
    }

    @Synchronized
    fun configureLogFile(path: String) {
        if (isLogFileConfigured) {
            return
        }
        nativeSetLogFile(path)
        isLogFileConfigured = true
    }

    override fun log(message: String) {
        nativeLog("Android", message)
    }

    override fun onSurfaceChanged(surface: android.view.Surface, width: Int, height: Int) {
        nativeSurfaceChanged(surface, width, height)
    }

    override fun onSurfaceDestroyed() {
        nativeSurfaceDestroyed()
    }

    override fun onTouch(action: Int, x: Float, y: Float) {
        nativeTouch(action, x, y)
    }

    override fun render() {
        nativeRender()
    }

    fun setCommandHandler(handler: (Int, String, String) -> Unit) {
        NativeBridgeCommandDispatcher.handler = handler
    }

    fun dispatch(signal: AppSessionSignal, value: String = "", additionalValue: String = "") {
        nativeDispatchSessionSignal(signal.value, value, additionalValue)
    }

    // У external-методов нет Kotlin-тела: вызов переходит в JNI. ART ищет
    // C++-символ Java_com_isrepeat_documenttranslator_native_NativeRenderer_<имя метода>
    // в libmobileclock.so. Этот символ определён в DocumentTranslator.AndroidHost/main.cpp.
    //
    // Примеры преобразования аргументов: Surface/AssetManager -> jobject,
    // Int -> jint, Float -> jfloat, String -> jstring.
    private external fun nativeSurfaceChanged(surface: android.view.Surface, width: Int, height: Int)
    private external fun nativeSetAssetManager(assetManager: android.content.res.AssetManager)
    private external fun nativeSetCommandDispatcher(dispatcher: NativeBridgeCommandDispatcher)
    private external fun nativeDispatchSessionSignal(signal: Int, value: String, additionalValue: String)
    private external fun nativeLog(category: String, message: String)
    private external fun nativeInitializeApplication(storagePath: String)
    private external fun nativeSetLogFile(path: String)
    private external fun nativeSurfaceDestroyed()
    private external fun nativeTouch(action: Int, x: Float, y: Float)
    private external fun nativeRender()
}

object NativeBridgeCommandDispatcher {
    private val dispatcher = NativeMessageDispatcher()

    var handler: ((Int, String, String) -> Unit)?
        get() = null
        set(value) {
            dispatcher.handler = value?.let { callback ->
                { message -> callback(message.signal, message.value, message.additionalValue) }
            }
        }

    // Вызывается C++ после обработки native-кнопки.
    fun dispatch(signal: Int, value: String, additionalValue: String) {
        dispatcher.dispatch(signal, value, additionalValue)
    }
}