// watermelon-run-helper
package com.watermelon.runhelper

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class CaptureService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null

    // UI 元件
    private var tvScreenStatus: TextView? = null
    private var tvMapStatus: TextView? = null
    private var tvAudioStatus: TextView? = null
    private var tvFootstepStatus: TextView? = null
    private var tvPlayerPos: TextView? = null
    private var tvMode: TextView? = null
    private var tvNearbyInfo: TextView? = null

    // 內部遊戲音訊收音物件
    private var audioRecord: AudioRecord? = null
    private var isCapturingAudio = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val context = this
        floatingView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC141414")) // 黑紅戰術風半透明背景
            setPadding(30, 30, 30, 30)
        }

        fun addTextView(text: String, color: Int, size: Float, bold: Boolean = false): TextView {
            return TextView(context).apply {
                this.text = text
                setTextColor(color)
                textSize = size
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 8) }
                floatingView?.addView(this)
            }
        }

        // 1. 頂部標題
        addTextView("🍉 西瓜跑刀助手 3.0 | 戰術面板", Color.parseColor("#FF5252"), 12f, true)

        // 2. 戰術導航資訊
        tvPlayerPos = addTextView("玩家位置：零號大壩 (X: 724, Y: 436)", Color.WHITE, 10f)
        tvMode = addTextView("跑刀模式：高收益跑刀 📈", Color.parseColor("#FFD700"), 10f)
        tvNearbyInfo = addTextView("附近資訊：物資點 52m | 撤離點 126m (🟢)", Color.parseColor("#AAAAAA"), 9f)

        // 分隔線
        val divider = android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 4, 0, 8) }
            setBackgroundColor(Color.parseColor("#444444"))
        }
        floatingView?.addView(divider)

        // 3. 系統狀態與辨識控制（包含專屬遊戲內部收音狀態）
        tvScreenStatus = addTextView("畫面：等待授權擷取 (🟢)", Color.parseColor("#00E676"), 9f)
        tvMapStatus = addTextView("地圖：等待畫面辨識", Color.WHITE, 9f)
        tvAudioStatus = addTextView("聲音：內部收音就緒 (僅限遊戲音效)", Color.parseColor("#00E676"), 9f)
        tvFootstepStatus = addTextView("腳步判斷：監聽中 | 槍聲/腳步過濾", Color.parseColor("#FF9800"), 9f)

        // 設定懸浮窗屬性
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            750, // 寬度
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

    /**
     * 初始化專門抓取遊戲內聲音的方法（需搭配 Android 10+ 與 MediaProjection）
     */
    fun initGameAudioCapture(mediaProjection: MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            // 透過 MediaProjection 擷取僅限遊戲與媒體的內部聲音，完美過濾麥克風雜音
            val playbackCaptureConfiguration = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 2)
                .setAudioPlaybackCaptureConfig(playbackCaptureConfiguration)
                .build()

            audioRecord?.startRecording()
            isCapturingAudio = true
            tvAudioStatus?.text = "聲音：內部遊戲收音中 (已過濾外部雜音)"
        } catch (e: Exception) {
            e.printStackTrace()
            tvAudioStatus?.text = "聲音：內部收音初始化失敗"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
    }
}
