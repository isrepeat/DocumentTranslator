package com.example.mobileclock

import android.app.Activity
import android.os.Bundle
import java.io.File

import com.example.mobileclock.feature.crash.PublicDiagnostics
import com.example.mobileclock.native.NativeRenderSurfaceView
import com.example.mobileclock.native.NativeRenderer

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PublicDiagnostics.write("MainActivity.onCreate started")
        NativeRenderer.initialize(filesDir, assets)
        PublicDiagnostics.write("NativeRenderer.initialize completed")
        setContentView(NativeRenderSurfaceView(this))
        PublicDiagnostics.write("NativeRenderSurfaceView attached")
    }
}