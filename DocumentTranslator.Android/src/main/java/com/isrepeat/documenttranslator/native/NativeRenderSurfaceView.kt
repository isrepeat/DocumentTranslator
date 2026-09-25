package com.isrepeat.documenttranslator.native

import android.content.Context
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.Choreographer

class NativeRenderSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Choreographer.FrameCallback {
    private var isRendering = false
    private var hasRenderedFirstFrame = false
    init {
        // Этот View — единственный Android-адаптер для Surface и touch-событий.
        holder.addCallback(this)
        setOnTouchListener { _, event ->
            NativeRenderer.onTouch(event.actionMasked, event.x, event.y)
            true
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        NativeRenderer.log("SurfaceView.surfaceCreated")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Surface из Android передаётся через Kotlin JNI-фасаду, затем в C++
        // преобразуется в ANativeWindow* для создания EGLSurface.
        NativeRenderer.log("SurfaceView.surfaceChanged started: ${width}x$height")
        NativeRenderer.onSurfaceChanged(holder.surface, width, height)
        NativeRenderer.log("SurfaceView.surfaceChanged completed")
        isRendering = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isRendering = false
        NativeRenderer.log("SurfaceView.surfaceDestroyed started")
        NativeRenderer.onSurfaceDestroyed()
        NativeRenderer.log("SurfaceView.surfaceDestroyed completed")
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRendering) return
        if (!hasRenderedFirstFrame) {
            NativeRenderer.log("SurfaceView.firstFrame started")
        }
        NativeRenderer.render()
        if (!hasRenderedFirstFrame) {
            hasRenderedFirstFrame = true
            NativeRenderer.log("SurfaceView.firstFrame completed")
        }
        Choreographer.getInstance().postFrameCallback(this)
    }
}