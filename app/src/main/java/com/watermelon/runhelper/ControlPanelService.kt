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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class ControlPanelService : Service() {

    private var windowManager: WindowManager? = null
    
    // 1. 放大版的控制面板
    private var panelLayout: LinearLayout? = null
    private lateinit var tvExtraction: TextView
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var currentScale = 1.15f // 預設稍微放大

    // 2. 鋪滿全螢幕四周的前後左右雷達指示器
    private var screenRadarLayout: FrameLayout? = null
    private lateinit var tvFront: TextView
    private lateinit var tvBack: TextView
    private lateinit var tvLeft: TextView
    private lateinit var tvRight: TextView

    private val tacticalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CaptureService.ACTION_UPDATE_TACTICAL_DATA) {
                val extraction = intent.getStringExtra("extraction")
                val activeDir = intent.getIntExtra("direction", -1)

                tvExtraction.text = extraction

                // 更新全螢幕四周的方位燈號
                val defaultBg = Color.parseColor("#88000000")
                val alertBg = Color.parseColor("#E6FF3333") // 亮紅警報
                val alertText = Color.parseColor("#FFFFFF")
                val defaultText = Color.parseColor("#AAAAAA")

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

        // ==================== A. 放大版的左側控制面板 ====================
        panelLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20f
                setColor(Color.parseColor("#E60A0A0A"))
                setStroke(3, Color.parseColor("#FFEA00"))
            }
            setPadding(24, 20, 24, 20)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scaleX = currentScale
            scaleY = currentScale
            pivotX = 0f
            pivotY = 0f
        }

        val tvTitle = TextView(context).apply {
            text = "🍉 西瓜跑刀助手"
            setTextColor(Color.parseColor("#FFEA00"))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        panelLayout?.addView(tvTitle)

        tvExtraction = TextView(context).apply {
            text = "⏱️ 撤離倒數：進行中"
            setTextColor(Color.parseColor("#FF5555"))
            textSize = 13f
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

        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                currentScale *= detector.scaleFactor
                currentScale = currentScale.coerceIn(0.8f, 2.5f)
                panelLayout?.pivotX = 0f
                panelLayout?.pivotY = 0f
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


        // ==================== B. 鋪滿全螢幕四周的前後左右雷達指示器 ====================
        screenRadarLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 1. 前方（置頂中央）
        tvFront = createFullScreenDirectionLabel(context, "⬆️ 前方敵方動靜").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 30
            }
        }
        screenRadarLayout?.addView(tvFront)

        // 2. 後方（置底中央）
        tvBack = createFullScreenDirectionLabel(context, "⬇️ 後方敵方動靜").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 30
            }
        }
        screenRadarLayout?.addView(tvBack)

        // 3. 左側（置左垂直置中）
        tvLeft = createFullScreenDirectionLabel(context, "⬅️ 左側").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                leftMargin = 20
            }
        }
        screenRadarLayout?.addView(tvLeft)

        // 4. 右側（置右垂直置中）
        tvRight = createFullScreenDirectionLabel(context, "➡️ 右側").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightMargin = 20
            }
        }
        screenRadarLayout?.addView(tvRight)

        val screenRadarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // 全螢幕穿透觸控，不擋遊戲
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(panelLayout, panelParams)
            windowManager?.addView(screenRadarLayout, screenRadarParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createFullScreenDirectionLabel(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#88000000"))
            setPadding(18, 10, 18, 10)
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
        if (screenRadarLayout != null) {
            windowManager?.removeView(screenRadarLayout)
            screenRadarLayout = null
        }
    }
}
