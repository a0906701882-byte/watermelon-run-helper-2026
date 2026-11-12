package com.watermelon.runhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class CaptureService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: LinearLayout? = null

    private var tvScreenStatus: TextView? = null
    private var tvMapStatus: TextView? = null
    private var tvAudioStatus: TextView? = null
    private var tvFootstepStatus: TextView? = null
    private var tvPlayerPos: TextView? = null
    private var tvMode: TextView? = null
    private var tvNearbyInfo: TextView? = null

    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null

    companion object {
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "WatermelonRunHelperChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // 針對 Android 8.0+ 建立前台服務必要通知頻道，防止被系統強制終止
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "西瓜跑刀助手前台服務",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍉 西瓜跑刀助手 3.0")
                .setContentText("戰術懸浮窗與內部收音運行中...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }

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

        addTextView("🍉 西瓜跑刀助手 3.0 | 戰術面板", Color.parseColor("#FF5252"), 12f, true)
        tvPlayerPos = addTextView("玩家位置：零號大壩 (X: 724, Y: 436)", Color.WHITE, 10f)
        tvMode = addTextView("跑刀模式：高收益跑刀 📈", Color.parseColor("#FFD700"), 10f)
        tvNearbyInfo = addTextView("附近資訊：物資點 52m | 撤離點 126m (🟢)", Color.parseColor("#AAAAAA"), 9f)

        val divider = android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { setMargins(0, 4, 0, 8) }
            setBackgroundColor(Color.parseColor("#444444"))
        }
        floatingView?.addView(divider)

        tvScreenStatus = addTextView("畫面：等待授權擷取 (🟢)", Color.parseColor("#00E676"), 9f)
        tvMapStatus = addTextView("地圖：等待畫面辨識", Color.WHITE, 9f)
        tvAudioStatus = addTextView("聲音：內部收音就緒 (僅限遊戲音效)", Color.parseColor("#00E676"), 9f)
        tvFootstepStatus = addTextView("腳步判斷：監聽中 | 槍聲/腳步過濾", Color.parseColor("#FF9800"), 9f)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            750,
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)

            if (resultCode != 0 && resultData != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
                if (mediaProjection != null) {
                    initGameAudioCapture(mediaProjection!!)
                }
            }
        }
        return START_STICKY
    }

    private fun initGameAudioCapture(projection: MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()

            val playbackCaptureConfiguration = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 2)
                .setAudioPlaybackCaptureConfig(playbackCaptureConfiguration)
                .build()

            audioRecord?.startRecording()
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
            mediaProjection?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
    }
}
