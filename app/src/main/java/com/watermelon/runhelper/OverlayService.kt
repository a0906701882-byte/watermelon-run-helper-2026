package com.watermelon.runhelper

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var info: TextView? = null
    private var audio: TextView? = null
    private var map: TextView? = null
    private var classification: TextView? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            intent.getStringExtra(CaptureService.EXTRA_OCR)?.let { info?.text = "畫面文字：$it" }
            intent.getStringExtra(CaptureService.EXTRA_AUDIO)?.let { audio?.text = "聲音：$it" }
            intent.getStringExtra(CaptureService.EXTRA_STATE)?.let { info?.text = it }
            intent.getStringExtra(CaptureService.EXTRA_MAP)?.let { map?.text = "地圖：$it（自動辨識）" }
            intent.getStringExtra(CaptureService.EXTRA_CLASSIFICATION)?.let { classification?.text = "腳步判斷：$it" }
        }
    }

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter(CaptureService.ACTION_UPDATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        show()
    }

    private fun show() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.argb(225, 20, 20, 27))
        }
        box.addView(TextView(this).apply {
            text = "🍉 西瓜跑刀助手 3.0｜拖曳移動"
            textSize = 13f
            setTextColor(0xFFFFD166.toInt())
        })
        info = TextView(this).apply {
            text = "畫面：等待授權擷取"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 4)
        }
        audio = TextView(this).apply {
            text = "聲音：尚未分析"
            textSize = 12f
            setTextColor(0xFFB8E0D2.toInt())
        }
        map = TextView(this).apply {
            text = "地圖：等待畫面辨識"
            textSize = 12f
            setTextColor(0xFFB8E0D2.toInt())
            setPadding(0, 4, 0, 4)
        }
        classification = TextView(this).apply {
            text = "腳步判斷：不確定｜等待聲音資料"
            textSize = 12f
            setTextColor(0xFFFFD166.toInt())
            setPadding(0, 4, 0, 4)
        }
        box.addView(info)
        box.addView(map)
        box.addView(audio)
        box.addView(classification)
        box.addView(TextView(this).apply {
            text = "注意：腳步分類為節奏啟發式，尚未驗證；不能辨認特定動作音效"
            textSize = 10f
            setTextColor(0xFFFFC4A3.toInt())
            setPadding(0, 6, 0, 0)
        })

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 160 }

        var sx = 0; var sy = 0; var tx = 0f; var ty = 0f
        box.setOnTouchListener { _: View, event: MotionEvent ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { sx = params.x; sy = params.y; tx = event.rawX; ty = event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    params.x = sx + (event.rawX - tx).toInt()
                    params.y = sy + (event.rawY - ty).toInt()
                    wm.updateViewLayout(box, params); true
                }
                else -> false
            }
        }
        panel = box
        wm.addView(box, params)
    }

    override fun onDestroy() {
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        panel?.let { if (it.isAttachedToWindow) wm.removeView(it) }
        panel = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
