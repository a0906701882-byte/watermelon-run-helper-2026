package com.watermelon.runhelper

import android.app.Service
import android.content.Intent
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val context = this

        panelLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f
                setColor(Color.parseColor("#B30A0A0A"))
                setStroke(2, Color.parseColor("#FFEA00"))
            }
            setPadding(18, 14, 18, 14)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val tvTitle = TextView(context).apply {
            text = "🍉 西瓜電競 · 戰術中控台"
            setTextColor(Color.parseColor("#FFEA00"))
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setShadowLayer(3f, 0f, 0f, Color.parseColor("#FFFFEA00"))
            setPadding(0, 0, 0, 8)
        }
        panelLayout?.addView(tvTitle)

        val tvStatusInfo = TextView(context).apply {
            text = "⬆️ 前方：待命\n⬇️ 背後：待命\n⬅️ 左側：待命\n➡️ 右側：待命"
            setTextColor(Color.parseColor("#00F0FF"))
            textSize = 12f
            setLineSpacing(4f, 1f)
            setShadowLayer(3f, 0f, 0f, Color.parseColor("#FF00F0FF"))
        }
        panelLayout?.addView(tvStatusInfo)

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
            x = 100
            y = 200
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
        if (panelLayout != null) {
            windowManager?.removeView(panelLayout)
            panelLayout = null
        }
    }
}
