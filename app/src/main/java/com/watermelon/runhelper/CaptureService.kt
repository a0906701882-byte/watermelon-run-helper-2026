package com.watermelon.runhelper

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 負責接收音訊串流與畫面擷取權限，執行即時戰術分析與四向警報
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
