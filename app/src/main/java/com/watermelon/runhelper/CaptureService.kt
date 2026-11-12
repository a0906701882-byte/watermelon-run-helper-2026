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
        private const val CHANNEL_ID = "WatermelonAntiSelfRadar"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "西瓜防自我干擾聲納雷達",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍉 西瓜防自我干擾雷達")
                .setContentText("已啟用過濾自身動作・專注敵方腳步中...")
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

        fun createLargeDirectionBox(gravity: Int, text: String): TextView {
            return TextView(context).apply {
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#CC141414"))
                setPadding(30, 22, 30, 22)
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    this.gravity = gravity
                    setMargins(45, 45, 45, 45)
                }
            }
        }

        tvLeftAlert = createLargeDirectionBox(Gravity.CENTER_VERTICAL or Gravity.START, "⬅️ 左側：無腳步")
        hudRootLayout?.addView(tvLeftAlert)

        tvRightAlert = createLargeDirectionBox(Gravity.CENTER_VERTICAL or Gravity.END, "右側：無腳步 ➡️")
        hudRootLayout?.addView(tvRightAlert)

        tvCenterAlert = createLargeDirectionBox(Gravity.TOP or Gravity.CENTER_HORIZONTAL, "⬆️ 正前方：無腳步")
        hudRootLayout?.addView(tvCenterAlert)

        tvStatusOverview = TextView(context).apply {
            text = "🍉 防自我干擾模式 | 已自動忽略自身移動音訊"
            setTextColor(Color.parseColor("#00E676"))
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#CC141414"))
            setPadding(35, 15, 35, 15)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 40)
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
                "WatermelonAntiSelfDisplay",
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

            Thread {
                val buffer = ByteArray(minBufferSize)
                val handler = Handler(Looper.getMainLooper())
                var baselineNoise = 100L

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

                        val sampleCount = (readSize / 4).coerceAtLeast(1)
                        val avgEnergy = totalEnergy / sampleCount
                        val lAvg = leftSum / sampleCount
                        val rAvg = rightSum / sampleCount

                        baselineNoise = (baselineNoise * 0.95 + avgEnergy * 0.05).toLong()
                        val dynamicThreshold = baselineNoise + 40L

                        // 判斷是否為「自己動作」（當左右聲道能量同時暴增且比例非常接近時，代表聲音來自正中央玩家視角或自身操作）
                        val isSelfAction = abs(lAvg - rAvg) < (lAvg * 0.25) && avgEnergy > dynamicThreshold + 50

                        handler.post {
                            when {
                                isSelfAction -> {
                                    // 偵測到自身動作，直接過濾顯示安全，不發出敵方警報
                                    tvLeftAlert?.text = "⬅️ 左側：無敵方腳步"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "右側：無敵方腳步 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⬆️ 正前方：自身動作過濾中"
                                    tvCenterAlert?.setTextColor(Color.parseColor("#00B0FF"))
                                    tvStatusOverview?.text = "🍉 狀態：正在移動/操作 (已過濾自身聲音)"
                                }
                                avgEnergy in (baselineNoise + 15)..(baselineNoise + 50) -> {
                                    // 外部靜步
                                    tvLeftAlert?.text = "⬅️ 左側：靜步潛行中"
                                    tvLeftAlert?.setTextColor(Color.parseColor("#00E676"))
                                    tvRightAlert?.text = "右側：靜步潛行中 ➡️"
                                    tvRightAlert?.setTextColor(Color.parseColor("#00E676"))
                                    tvCenterAlert?.text = "⬆️ 正前方：靜步潛行"
                                    tvCenterAlert?.setTextColor(Color.parseColor("#00E676"))
                                    tvStatusOverview?.text = "🍉 狀態：捕捉到周遭敵方靜步"
                                }
                                lAvg > rAvg * 1.5 && lAvg > dynamicThreshold -> {
                                    // 嚴格過濾後的左側敵方腳步
                                    tvLeftAlert?.text = "⚠️ ⬅️ 【左側】敵方腳步接近！"
                                    tvLeftAlert?.setTextColor(Color.parseColor("#FF5252"))
                                    tvRightAlert?.text = "右側：安全 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⬆️ 正前方：安全"
                                    tvCenterAlert?.setTextColor(Color.WHITE)
                                    tvStatusOverview?.text = "🍉 鎖定：左側有敵人接近"
                                }
                                rAvg > lAvg * 1.5 && rAvg > dynamicThreshold -> {
                                    // 嚴格過濾後的右側敵方腳步
                                    tvLeftAlert?.text = "⬅️ 左側：安全"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "【右側】敵方腳步接近！ ➡️"
                                    tvRightAlert?.setTextColor(Color.parseColor("#FF5252"))
                                    tvCenterAlert?.text = "⬆️ 正前方：安全"
                                    tvCenterAlert?.setTextColor(Color.WHITE)
                                    tvStatusOverview?.text = "🍉 鎖定：右側有敵人接近"
                                }
                                else -> {
                                    tvLeftAlert?.text = "⬅️ 左側：無腳步"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "右側：無腳步 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⬆️ 正前方：無腳步"
                                    tvCenterAlert?.setTextColor(Color.WHITE)
                                    tvStatusOverview?.text = "🍉 狀態：雷達監聽中 (防自我干擾啟用)"
                                }
                            }
                        }
                    }
                    Thread.sleep(60)
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
