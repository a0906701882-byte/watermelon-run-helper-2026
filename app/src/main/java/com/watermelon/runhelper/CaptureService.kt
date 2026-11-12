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

    private var tvLeftAlert: TextView? = null
    private var tvRightAlert: TextView? = null
    private var tvCenterAlert: TextView? = null
    private var tvStatusOverview: TextView? = null

    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isRunning = false

    companion object {
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "WatermelonFootstepChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "西瓜純腳步聲納雷達",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍉 西瓜純腳步聲納雷達")
                .setContentText("左右及正前方腳步與靜步監聽中...")
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

        fun createDirectionBox(gravity: Int, text: String): TextView {
            return TextView(context).apply {
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 13f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#B3141414"))
                setPadding(20, 15, 20, 15)
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    this.gravity = gravity
                    setMargins(35, 35, 35, 35)
                }
            }
        }

        // --- 1. 左側螢幕指標 ---
        tvLeftAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.START, "⬅️ 左側：無腳步")
        hudRootLayout?.addView(tvLeftAlert)

        // --- 2. 右側螢幕指標 ---
        tvRightAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.END, "右側：無腳步 ➡️")
        hudRootLayout?.addView(tvRightAlert)

        // --- 3. 正前方螢幕指標 (頂部中央) ---
        tvCenterAlert = createDirectionBox(Gravity.TOP or Gravity.CENTER_HORIZONTAL, "⬆️ 正前方：無腳步")
        hudRootLayout?.addView(tvCenterAlert)

        // --- 4. 底部綜合狀態總覽 ---
        tvStatusOverview = TextView(context).apply {
            text = "🍉 西瓜腳步雷達 | 專注於玩家與人機腳步/靜步偵測"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 10.5f
            setBackgroundColor(Color.parseColor("#CC141414"))
            setPadding(25, 10, 25, 10)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 30)
            }
        }
        hudRootLayout?.addView(tvStatusOverview)

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
                    initScreenCaptureDummy()
                    initGameAudioCapture(mediaProjection!!)
                }
            }
        }
        return START_STICKY
    }

    private fun initScreenCaptureDummy() {
        try {
            val width = 1280
            val height = 720
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "WatermelonFootstepDisplay",
                width, height, 300,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                image?.close()
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

            // 專注於腳步與靜步的即時偵測執行緒
            Thread {
                val buffer = ByteArray(minBufferSize)
                val handler = Handler(Looper.getMainLooper())
                while (isRunning) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        var leftSum = 0L
                        var rightSum = 0L
                        var totalEnergy = 0L
                        var i = 0
                        while (i < readSize - 4) {
                            val leftSample = (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8)).toShort()
                            val rightSample = (buffer[i + 2].toInt() or (buffer[i + 3].toInt() shl 8)).toShort()
                            val lVal = abs(leftSample.toInt())
                            val rVal = abs(rightSample.toInt())
                            leftSum += lVal
                            rightSum += rVal
                            totalEnergy += (lVal + rVal)
                            i += 4
                        }

                        val avgEnergy = totalEnergy / (readSize / 4)

                        handler.post {
                            when {
                                avgEnergy in 40..150 -> {
                                    // 輕微細微聲響：判定為靜步移動
                                    tvLeftAlert?.text = "⬅️ 左側：靜步移動中"
                                    tvLeftAlert?.setTextColor(Color.parseColor("#00E676"))
                                    tvRightAlert?.text = "右側：靜步移動中 ➡️"
                                    tvRightAlert?.setTextColor(Color.parseColor("#00E676"))
                                    tvCenterAlert?.text = "⬆️ 正前方：靜步潛行"
                                    tvCenterAlert?.setTextColor(Color.parseColor("#00E676"))
                                    tvStatusOverview?.text = "🍉 狀態：周遭有輕微靜步聲"
                                }
                                leftSum > rightSum * 1.3 -> {
                                    // 左側腳步
                                    tvLeftAlert?.text = "⚠️ ⬅️ 【左側】偵測到腳步聲！"
                                    tvLeftAlert?.setTextColor(Color.parseColor("#FF5252"))
                                    tvRightAlert?.text = "右側：無腳步 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⬆️ 正前方：無腳步"
                                    tvCenterAlert?.setTextColor(Color.WHITE)
                                    tvStatusOverview?.text = "🍉 提示：左側有腳步靠近"
                                }
                                rightSum > leftSum * 1.3 -> {
                                    // 右側腳步
                                    tvLeftAlert?.text = "⬅️ 左側：無腳步"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "【右側】偵測到腳步聲！ ➡️"
                                    tvRightAlert?.setTextColor(Color.parseColor("#FF5252"))
                                    tvCenterAlert?.text = "⬆️ 正前方：無腳步"
                                    tvCenterAlert?.setTextColor(Color.WHITE)
                                    tvStatusOverview?.text = "🍉 提示：右側有腳步靠近"
                                }
                                avgEnergy > 150 -> {
                                    // 左右平衡且音量較大：正前方腳步或奔跑
                                    tvLeftAlert?.text = "⬅️ 左側"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "右側 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⚠️ ⬆️ 【正前方】偵測到腳步聲！"
                                    tvCenterAlert?.setTextColor(Color.parseColor("#FFD700"))
                                    tvStatusOverview?.text = "🍉 提示：正前方有腳步靠近"
                                }
                                else -> {
                                    // 安靜無聲
                                    tvLeftAlert?.text = "⬅️ 左側：無腳步"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "右側：無腳步 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⬆️ 正前方：無腳步"
                                    tvCenterAlert?.setTextColor(Color.WHITE)
                                    tvStatusOverview?.text = "🍉 狀態：環境安靜 (監聽中)"
                                }
                            }
                        }
                    }
                    Thread.sleep(80)
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
