package com.example.mobileclock

import android.app.Activity
import android.os.Bundle

import com.example.mobileclock.native.NativeRenderSurfaceView
import com.example.mobileclock.native.NativeRenderer

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NativeRenderer.initialize(assets)
        setContentView(NativeRenderSurfaceView(this))
    }
}