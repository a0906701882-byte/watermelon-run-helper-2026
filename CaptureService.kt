package com.watermelon.runhelper

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 這裡負責處理 MediaProjection 與語音解析的核心邏輯
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
