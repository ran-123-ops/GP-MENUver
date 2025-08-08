package com.example.voiceapp

import android.app.Application
import com.google.android.material.color.DynamicColors

class VoiceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Android 12+ など Dynamic Color 対応端末で自動適用
        try {
            DynamicColors.applyToActivitiesIfAvailable(this)
        } catch (e: Throwable) {
            // 失敗しても致命的ではない
        }
    }
}
