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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class ControlPanelService : Service() {

    private var windowManager: WindowManager? = null
    
    private var screenOverlayLayout: FrameLayout? = null
    private lateinit var tvExtractionTopRight: TextView
    private lateinit var tvFront: TextView
    private lateinit var tvBack: TextView
    private lateinit var tvLeft: TextView
    private lateinit var tvRight: TextView

    private val tacticalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CaptureService.ACTION_UPDATE_TACTICAL_DATA) {
                val extraction = intent.getStringExtra("extraction")
                val activeDir = intent.getIntExtra("direction", -1)

                tvExtractionTopRight.text = extraction

                val defaultBg = Color.parseColor("#99000000")
                val alertBg = Color.parseColor("#E6FF3333") // 亮紅警報
                val alertText = Color.parseColor("#FFFFFF")
                val defaultText = Color.parseColor("#CCCCCC")

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

        screenOverlayLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 右上角撤離倒數
        tvExtractionTopRight = TextView(context).apply {
            text = "⏱️ 撤離倒數：計算中..."
            setTextColor(Color.parseColor("#FF5555"))
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#B3000000"))
            setPadding(20, 12, 20, 12)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = 40
                rightMargin = 40
            }
        }
        screenOverlayLayout?.addView(tvExtractionTopRight)

        // 上方超大前方
        tvFront = createHugeDirectionLabel(context, "⬆️ 前方敵方動靜").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = 20
            }
        }
        screenOverlayLayout?.addView(tvFront)

        // 下方超大後方
        tvBack = createHugeDirectionLabel(context, "⬇️ 後方敵方動靜").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = 20
            }
        }
        screenOverlayLayout?.addView(tvBack)

        // 左側超大左方
        tvLeft = createHugeDirectionLabel(context, "⬅️ 左側").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                leftMargin = 10
            }
        }
        screenOverlayLayout?.addView(tvLeft)

        // 右側超大右方
        tvRight = createHugeDirectionLabel(context, "➡️ 右側").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                rightMargin = 10
            }
        }
        screenOverlayLayout?.addView(tvRight)

        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // 穿透觸控不擋遊戲
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(screenOverlayLayout, overlayParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createHugeDirectionLabel(context: Context, text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(28, 16, 28, 16)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(tacticalReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (screenOverlayLayout != null) {
            windowManager?.removeView(screenOverlayLayout)
            screenOverlayLayout = null
        }
    }
}
