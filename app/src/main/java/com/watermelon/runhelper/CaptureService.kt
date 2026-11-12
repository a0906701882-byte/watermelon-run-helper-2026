package com.watermelon.runhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class CaptureService : Service() {

    private var windowManager: WindowManager? = null
    private var hudRootLayout: FrameLayout? = null

    private var tvPlayerPos: TextView? = null
    private var tvNearbyInfo: TextView? = null
    private var tvAudioStatus: TextView? = null
    private var tvMission: TextView? = null
    private var tvScreenDetect: TextView? = null

    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isRunning = false

    companion object {
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "WatermelonRunHelperChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

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
                .setContentText("全螢幕戰術 HUD 與即時畫面偵測中...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val context = this

        hudRootLayout = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        fun addLabel(parent: LinearLayout, text: String, color: Int, size: Float, bold: Boolean = false): TextView {
            return TextView(context).apply {
                this.text = text
                setTextColor(color)
                textSize = size
                if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 4)
                }
                parent.addView(this)
            }
        }

        // --- 左上角戰術面板 ---
        val topLeftPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#99141414"))
            setPadding(20, 20, 20, 20)
            layoutParams = FrameLayout.LayoutParams(450, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(30, 30, 0, 0)
            }
        }
        
        addLabel(topLeftPanel, "🍉 西瓜跑刀助手 | DELTA FORCE", Color.parseColor("#FF5252"), 11f, true)
        tvPlayerPos = addLabel(topLeftPanel, "玩家位置：零號大壩 (掃描中...)", Color.WHITE, 9.5f)
        addLabel(topLeftPanel, "跑刀模式：高收益跑刀 📈", Color.parseColor("#FFD700"), 9.5f)
        tvNearbyInfo = addLabel(topLeftPanel, "物資點追蹤中...", Color.parseColor("#00E676"), 9f)
        tvScreenDetect = addLabel(topLeftPanel, "畫面偵測：等待串流影格", Color.parseColor("#FF9800"), 8.5f)
        tvAudioStatus = addLabel(topLeftPanel, "內部收音：監聽中", Color.parseColor("#00B0FF"), 8.5f)
        hudRootLayout?.addView(topLeftPanel)

        // --- 頂部戰術橫幅 ---
        val topCenterBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#B3D32F2F"))
            setPadding(25, 12, 25, 12)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(600, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setMargins(0, 35, 0, 0)
            }
        }
        addLabel(topCenterBanner, "⚠️ 戰術監控中：準備進入對局", Color.WHITE, 10.5f, true)
        hudRootLayout?.addView(topCenterBanner)

        // --- 底部目標導引列 ---
        val bottomMissionPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#99141414"))
            setPadding(30, 15, 30, 15)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(650, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 40)
            }
        }
        tvMission = addLabel(bottomMissionPanel, "🎯 目標：等待遊戲對局開始...", Color.parseColor("#00E676"), 11f, true)
        hudRootLayout?.addView(bottomMissionPanel)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            windowManager?.addView(hudRootLayout, params)
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
                    isRunning = true
                    initScreenCaptureAndAnalysis()
                    initGameAudioCapture(mediaProjection!!)
                }
            }
        }
        return START_STICKY
    }

    private fun initScreenCaptureAndAnalysis() {
        try {
            val width = 1280
            val height = 720
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "WatermelonScreenCapture",
                width, height, 300,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            val handler = Handler(Looper.getMainLooper())

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        handler.post {
                            tvScreenDetect?.text = "畫面偵測：已連線擷取 (1280x720)"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        image.close()
                    }
                }
            }, null)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initGameAudioCapture(projection: MediaProjection) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

            val playbackCaptureConfiguration = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(audioFormat)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build())
                .setBufferSizeInBytes(minBufferSize)
                .setAudioPlaybackCaptureConfig(playbackCaptureConfiguration)
                .build()

            audioRecord?.startRecording()

            Thread {
                val buffer = ByteArray(minBufferSize)
                val handler = Handler(Looper.getMainLooper())
                while (isRunning) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        var leftSum = 0L
                        var rightSum = 0L
                        var i = 0
                        while (i < readSize - 4) {
                            val leftSample = (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8)).toShort()
                            val rightSample = (buffer[i + 2].toInt() or (buffer[i + 3].toInt() shl 8)).toShort()
                            leftSum += abs(leftSample.toInt())
                            rightSum += abs(rightSample.toInt())
                            i += 4
                        }

                        handler.post {
                            if (leftSum > rightSum * 1.4) {
                                tvAudioStatus?.text = "內部收音：⚠️ 左側偵測腳步/槍聲"
                            } else if (rightSum > leftSum * 1.4) {
                                tvAudioStatus?.text = "內部收音：⚠️ 右側偵測腳步/槍聲"
                            } else {
                                tvAudioStatus?.text = "內部收音：遊戲音效監聽中 (🟢)"
                            }
                        }
                    }
                    Thread.sleep(150)
                }
            }.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (hudRootLayout != null) {
            windowManager?.removeView(hudRootLayout)
            hudRootLayout = null
        }
    }
}
