package com.watermelon.runhelper

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class ControlPanelService : Service() {

    private var windowManager: WindowManager? = null
    private var panelLayout: LinearLayout? = null
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentScale = 1.0f

    private lateinit var tvExtraction: TextView
    private lateinit var tvLogs: TextView

    private val tacticalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CaptureService.ACTION_UPDATE_TACTICAL_DATA) {
                val extraction = intent.getStringExtra("extraction")
                val logs = intent.getStringExtra("logs")

                tvExtraction.text = extraction
                tvLogs.text = logs
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val context = this

        // ✅ 正確引用 CaptureService 的常數
        val filter = IntentFilter(CaptureService.ACTION_UPDATE_TACTICAL_DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tacticalReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tacticalReceiver, filter)
        }

        // 左側戰術面板容器
        panelLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor("#D90A0A0A"))
                setStroke(2, Color.parseColor("#FFEA00"))
            }
            setPadding(18, 16, 18, 16)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. 頂部品牌標題
        val tvTitle = TextView(context).apply {
            text = "🍉 西瓜跑刀助手\n     · DELTA FORCE"
            setTextColor(Color.parseColor("#FFEA00"))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setShadowLayer(4f, 0f, 0f, Color.parseColor("#FFFFEA00"))
            setPadding(0, 0, 0, 8)
        }
        panelLayout?.addView(tvTitle)

        // 2. 撤離倒數計時與 6 分半提醒
        tvExtraction = TextView(context).apply {
            text = "⏱️ 撤離倒數：進行中"
            setTextColor(Color.parseColor("#FF5555"))
            textSize = 11f
            setLineSpacing(3f, 1f)
            setPadding(0, 0, 0, 8)
        }
        panelLayout?.addView(tvExtraction)

        // 3. 戰術腳步方位日誌
        tvLogs = TextView(context).apply {
            text = "🔊 戰術雷達已就緒\n📡 正在監聽敵方腳步..."
            setTextColor(Color.parseColor("#00FF66"))
            textSize = 10f
            setLineSpacing(3f, 1f)
        }
        panelLayout?.addView(tvLogs)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 100
        }

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = currentScale.coerceIn(0.6f, 2.2f)
                panelLayout?.scaleX = currentScale
                panelLayout?.scaleY = currentScale
                return true
            }
        })

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        panelLayout?.setOnTouchListener { _, event ->
            scaleGestureDetector?.onTouchEvent(event)
            if (event.pointerCount > 1) {
                return@setOnTouchListener true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(panelLayout, params)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(panelLayout, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(tacticalReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (panelLayout != null) {
            windowManager?.removeView(panelLayout)
            panelLayout = null
        }
    }
}
