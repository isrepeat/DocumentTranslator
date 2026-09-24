package com.example.mobileclock

import android.app.Application
import com.example.mobileclock.feature.crash.PublicDiagnostics

class DocumentTranslatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Открывает session-log до создания первой Activity.
        PublicDiagnostics.initialize(this)
    }
}