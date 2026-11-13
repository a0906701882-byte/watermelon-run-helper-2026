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
    
    // 1. 控制面板視窗與元件
    private var panelLayout: LinearLayout? = null
    private lateinit var tvExtraction: TextView
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentScale = 1.0f

    // 2. 獨立的前後左右方位指示視窗與元件
    private var radarLayout: LinearLayout? = null
    private lateinit var tvFront: TextView
    private lateinit var tvBack: TextView
    private lateinit var tvLeft: TextView
    private lateinit var tvRight: TextView

    private val tacticalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CaptureService.ACTION_UPDATE_TACTICAL_DATA) {
                val extraction = intent.getStringExtra("extraction")
                val activeDir = intent.getIntExtra("direction", -1)

                // 更新面板上的撤離時間
                tvExtraction.text = extraction

                // 更新獨立雷達指示器的燈號
                val defaultBg = Color.parseColor("#99000000")
                val alertBg = Color.parseColor("#CCFF3333")
                val alertText = Color.parseColor("#FFFFFF")
                val defaultText = Color.parseColor("#888888")

                tvFront.setBackgroundColor(if (activeDir == 0) alertBg else defaultBg)
                tvFront.setTextColor(if (activeDir == 0) alertText else defaultText)

                tvBack.setBackgroundColor(if (activeDir == 1) alertBg else defaultBg)
                tvBack.setTextColor(if (activeDir == 1) alertText else defaultText)

                tvLeft.setBackgroundColor(if (activeDir == 2) alertBg else defaultBg)
                tvLeft.setTextColor(if (activeDir == 2) alertText else defaultText)

                tvRight.setBackgroundColor(if (activeDir == 3) alertBg else defaultBg)
                tvRight.setTextColor(if (activeDir == 3) alertText else defaultText)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val context = this

        val filter = IntentFilter(CaptureService.ACTION_UPDATE_TACTICAL_DATA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tacticalReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tacticalReceiver, filter)
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // ==================== A. 建立左側控制面板（純面板，支援等比縮放） ====================
        panelLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16f
                setColor(Color.parseColor("#D90A0A0A"))
                setStroke(2, Color.parseColor("#FFEA00"))
            }
            setPadding(16, 14, 16, 14)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitle = TextView(context).apply {
            text = "🍉 西瓜跑刀助手"
            setTextColor(Color.parseColor("#FFEA00"))
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 6)
        }
        panelLayout?.addView(tvTitle)

        tvExtraction = TextView(context).apply {
            text = "⏱️ 撤離倒數：進行中"
            setTextColor(Color.parseColor("#FF5555"))
            textSize = 10f
        }
        panelLayout?.addView(tvExtraction)

        val panelParams = WindowManager.LayoutParams(
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

        // 雙指等比例縮放控制面板
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = currentScale.coerceIn(0.6f, 2.2f)
                // 保持等比例縮放 (Pivot 設在左上角讓縮放基準穩定)
                panelLayout?.pivotX = 0f
                panelLayout?.pivotY = 0f
                panelLayout?.scaleX = currentScale
                panelLayout?.scaleY = currentScale
                return true
            }
        })

        // 單指拖曳面板
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        panelLayout?.setOnTouchListener { _, event ->
            scaleGestureDetector?.onTouchEvent(event)
            if (event.pointerCount > 1) return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = panelParams.x
                    initialY = panelParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    panelParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    panelParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(panelLayout, panelParams)
                    true
                }
                else -> false
            }
        }


        // ==================== B. 建立獨立的前後左右雷達指示器（不在面板內） ====================
        radarLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        tvFront = createDirectionLabel(context, "⬆️ 前方")
        radarLayout?.addView(tvFront)

        val middleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        tvLeft = createDirectionLabel(context, "⬅️ 左側")
        tvRight = createDirectionLabel(context, "➡️ 右側")
        
        val tvCenterDot = TextView(context).apply {
            text = " 🎯 "
            textSize = 10f
            setTextColor(Color.parseColor("#FFEA00"))
        }

        middleRow.addView(tvLeft)
        middleRow.addView(tvCenterDot)
        middleRow.addView(tvRight)
        radarLayout?.addView(middleRow)

        tvBack = createDirectionLabel(context, "⬇️ 後方")
        radarLayout?.addView(tvBack)

        val radarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // 不干擾觸控
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END // 放在畫另一側或適當位置（例如右上方或螢幕中央）
            x = 80
            y = 120
        }

        // 將兩個視窗分別加入 WindowManager
        try {
            windowManager?.addView(panelLayout, panelParams)
            windowManager?.addView(radarLayout, radarParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createDirectionLabel(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#888888"))
            textSize = 11f
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(10, 4, 10, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(2, 2, 2, 2)
            }
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
        if (radarLayout != null) {
            windowManager?.removeView(radarLayout)
            radarLayout = null
        }
    }
}
