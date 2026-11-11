package com.watermelon.runhelper

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class CaptureService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    private var tvScreenStatus: TextView? = null
    private var tvMapStatus: TextView? = null
    private var tvAudioStatus: TextView? = null
    private var tvFootstepStatus: TextView? = null
    private var tvPlayerPos: TextView? = null
    private var tvMode: TextView? = null
    private var tvNearbyInfo: TextView? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        tvScreenStatus = floatingView?.findViewById(R.id.tvScreenStatus)
        tvMapStatus = floatingView?.findViewById(R.id.tvMapStatus)
        tvAudioStatus = floatingView?.findViewById(R.id.tvAudioStatus)
        tvFootstepStatus = floatingView?.findViewById(R.id.tvFootstepStatus)
        tvPlayerPos = floatingView?.findViewById(R.id.tvPlayerPos)
        tvMode = floatingView?.findViewById(R.id.tvMode)
        tvNearbyInfo = floatingView?.findViewById(R.id.tvNearbyInfo)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
    }
}
