package com.example.mobileclock.native

import android.content.res.AssetManager
import android.view.Surface
import com.example.mobileclock.feature.crash.PublicDiagnostics
import java.io.File

object NativeRenderer {
    private var isApplicationInitialized = false
    private var isLogFileConfigured = false

    init {
        // Загружает libmobileclock.so из APK. После этого ART может вызвать
        // экспортированные JNI-функции из DocumentTranslator.AndroidHost/main.cpp.
        System.loadLibrary("mobileclock")
    }

    fun initialize(filesDirectory: File, assetManager: AssetManager) {
        // Kotlin подготавливает Android-зависимые объекты до первого GL-кадра.
        configureLogFile(filesDirectory)
        initializeApplication(filesDirectory)
        PublicDiagnostics.write("JNI nativeSetAssetManager started")
        nativeSetAssetManager(assetManager)
        PublicDiagnostics.write("JNI nativeSetAssetManager completed")
        PublicDiagnostics.write("JNI nativeSetCommandDispatcher started")
        nativeSetCommandDispatcher(NativeBridgeCommandDispatcher)
        PublicDiagnostics.write("JNI nativeSetCommandDispatcher completed")
    }

    @Synchronized
    private fun initializeApplication(filesDirectory: File) {
        if (isApplicationInitialized) {
            return
        }
        // Состояние приложения передаётся явно. Настройка логов не должна
        // неявно создавать репозитории или AppSessionController.
        val storageFile = File(filesDirectory, "mobileclock-state.json")
        PublicDiagnostics.write("JNI nativeInitializeApplication started")
        nativeInitializeApplication(storageFile.absolutePath)
        PublicDiagnostics.write("JNI nativeInitializeApplication completed")
        isApplicationInitialized = true
    }

    @Synchronized
    private fun configureLogFile(filesDirectory: File) {
        if (isLogFileConfigured) {
            return
        }
        val logFile = File(filesDirectory, "logs/documenttranslator.log")
        logFile.parentFile?.mkdirs()
        PublicDiagnostics.write("JNI nativeSetLogFile started")
        nativeSetLogFile(logFile.absolutePath)
        PublicDiagnostics.write("JNI nativeSetLogFile completed")
        isLogFileConfigured = true
    }

    fun onSurfaceChanged(surface: Surface, width: Int, height: Int) {
        nativeSurfaceChanged(surface, width, height)
    }

    fun onSurfaceDestroyed() {
        nativeSurfaceDestroyed()
    }

    fun onTouch(action: Int, x: Float, y: Float) {
        nativeTouch(action, x, y)
    }

    fun render() {
        nativeRender()
    }

    // У external-методов нет Kotlin-тела: вызов переходит в JNI. ART ищет
    // C++-символ Java_com_example_mobileclock_native_NativeRenderer_<имя метода>
    // в libmobileclock.so. Этот символ определён в DocumentTranslator.AndroidHost/main.cpp.
    //
    // Примеры преобразования аргументов: Surface/AssetManager -> jobject,
    // Int -> jint, Float -> jfloat, String -> jstring.
    private external fun nativeSurfaceChanged(surface: Surface, width: Int, height: Int)
    private external fun nativeSetAssetManager(assetManager: AssetManager)
    private external fun nativeSetCommandDispatcher(dispatcher: NativeBridgeCommandDispatcher)
    private external fun nativeInitializeApplication(storagePath: String)
    private external fun nativeSetLogFile(path: String)
    private external fun nativeSurfaceDestroyed()
    private external fun nativeTouch(action: Int, x: Float, y: Float)
    private external fun nativeRender()
}

object NativeBridgeCommandDispatcher {
    // Зарезервирован для будущих запросов C++ к Android-платформе.
    fun dispatch(signal: Int, value: String, additionalValue: String) {
        Unit
    }

    // Нативные маркеры запуска направляются в публичный session-log.
    fun log(message: String) {
        PublicDiagnostics.write("Native: $message")
    }
}