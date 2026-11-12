package com.watermelon.runhelper

import android.app.Service
import android.content.Intent
import android.os.IBinder

class OverlayService : Service() {

    companion object {
        const val ACTION_UPDATE = "com.watermelon.runhelper.ACTION_UPDATE"
        const val EXTRA_OCR = "EXTRA_OCR"
        const val EXTRA_AUDIO = "EXTRA_AUDIO"
        const val EXTRA_STATE = "EXTRA_STATE"
        const val EXTRA_MAP = "EXTRA_MAP"
        const val EXTRA_CLASSIFICATION = "EXTRA_CLASSIFICATION"
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
    }
}
