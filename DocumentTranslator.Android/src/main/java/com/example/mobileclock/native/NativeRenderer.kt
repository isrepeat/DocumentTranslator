package com.example.mobileclock.native

import android.content.res.AssetManager
import android.view.Surface

object NativeRenderer {
    init {
        // Загружает libmobileclock.so из APK. После этого ART может вызвать
        // экспортированные JNI-функции из DocumentTranslator.AndroidHost/main.cpp.
        System.loadLibrary("mobileclock")
    }

    fun initialize(assetManager: AssetManager) {
        // Kotlin подготавливает Android-зависимые объекты до первого GL-кадра.
        nativeSetAssetManager(assetManager)
        nativeSetCommandDispatcher(NativeBridgeCommandDispatcher)
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
    private external fun nativeSurfaceDestroyed()
    private external fun nativeTouch(action: Int, x: Float, y: Float)
    private external fun nativeRender()
}

object NativeBridgeCommandDispatcher {
    // Зарезервирован для будущих запросов C++ к Android-платформе.
    fun dispatch(signal: Int, value: String, additionalValue: String) {
        Unit
    }
}