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
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.nio.ByteBuffer
import kotlin.math.abs

class CaptureService : Service() {

    private var windowManager: WindowManager? = null
    private var hudRootLayout: FrameLayout? = null

    private var tvLeftAlert: TextView? = null
    private var tvRightAlert: TextView? = null
    private var tvCenterAlert: TextView? = null

    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isRunning = false

    private var previousPixelSample: ByteArray? = null
    @Volatile private var isScreenMoving = false

    companion object {
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "WatermelonTextRadar"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "西瓜視訊聯防文字雷達",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍉 西瓜視訊聯防文字雷達")
                .setContentText("動態過濾與精準方位提示運行中...")
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
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#99141414"))
                setPadding(30, 22, 30, 22)
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    this.gravity = gravity
                    setMargins(45, 45, 45, 45)
                }
            }
        }

        tvLeftAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.START, "⬅️ 左側：雷達就緒")
        hudRootLayout?.addView(tvLeftAlert)

        tvRightAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.END, "右側：雷達就緒 ➡️")
        hudRootLayout?.addView(tvRightAlert)

        tvCenterAlert = createDirectionBox(Gravity.TOP or Gravity.CENTER_HORIZONTAL, "⬆️ 正前方：雷達就緒")
        hudRootLayout?.addView(tvCenterAlert)

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
                    initScreenMotionAnalysis()
                    initGameAudioCapture(mediaProjection!!)
                }
            }
        }
        return START_STICKY
    }

    private fun initScreenMotionAnalysis() {
        try {
            val width = 640
            val height = 360
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "WatermelonMotionDisplay",
                width, height, 200,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )

            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer: ByteBuffer = planes[0].buffer
                        val currentBytes = ByteArray(buffer.remaining())
                        buffer.get(currentBytes)

                        val prev = previousPixelSample
                        if (prev != null && prev.size == currentBytes.size) {
                            var diffSum = 0L
                            var idx = 0
                            while (idx < currentBytes.size) {
                                diffSum += abs(currentBytes[idx] - prev[idx]).toLong()
                                idx += 16
                            }
                            isScreenMoving = diffSum > 150000L
                        }
                        previousPixelSample = currentBytes
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
                var baselineNoise = 300L

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

                        baselineNoise = (baselineNoise * 0.92 + avgEnergy * 0.08).toLong()
                        val dynamicThreshold = baselineNoise + 90L
                        val isSelfMoving = isScreenMoving

                        handler.post {
                            when {
                                isSelfMoving -> {
                                    tvLeftAlert?.text = "⬅️ 左側：安全"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "右側：安全 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⬆️ 正前方：移動中 (自身過濾)"
                                    tvCenterAlert?.setTextColor(Color.parseColor("#00B0FF"))
                                }
                                avgEnergy in (baselineNoise + 30)..(baselineNoise + 120) -> {
                                    tvLeftAlert?.text = "⬅️ 左側：靜步潛行中"
                                    tvLeftAlert?.setTextColor(Color.parseColor("#00E676"))
                                    tvRightAlert?.text = "右側：靜步潛行中 ➡️"
                                    tvRightAlert?.setTextColor(Color.parseColor("#00E676"))
                                    tvCenterAlert?.text = "⬆️ 正前方：靜步潛行"
                                    tvCenterAlert?.setTextColor(Color.parseColor("#00E676"))
                                }
                                lAvg > rAvg * 1.6 && lAvg > dynamicThreshold -> {
                                    tvLeftAlert?.text = "⚠️ ⬅️ 【左側】敵方腳步！"
                                    tvLeftAlert?.setTextColor(Color.parseColor("#FF5252"))
                                    tvRightAlert?.text = "右側：安全 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⬆️ 正前方：安全"
                                    tvCenterAlert?.setTextColor(Color.WHITE)
                                }
                                rAvg > lAvg * 1.6 && rAvg > dynamicThreshold -> {
                                    tvLeftAlert?.text = "⬅️ 左側：安全"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "【右側】敵方腳步！ ➡️"
                                    tvRightAlert?.setTextColor(Color.parseColor("#FF5252"))
                                    tvCenterAlert?.text = "⬆️ 正前方：安全"
                                    tvCenterAlert?.setTextColor(Color.WHITE)
                                }
                                avgEnergy > dynamicThreshold + 90 -> {
                                    tvLeftAlert?.text = "⬅️ 左側"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "右側 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⚠️ ⬆️ 【正前方】敵方腳步！"
                                    tvCenterAlert?.setTextColor(Color.parseColor("#FFD700"))
                                }
                                else -> {
                                    tvLeftAlert?.text = "⬅️ 左側：無腳步"
                                    tvLeftAlert?.setTextColor(Color.WHITE)
                                    tvRightAlert?.text = "右側：無腳步 ➡️"
                                    tvRightAlert?.setTextColor(Color.WHITE)
                                    tvCenterAlert?.text = "⬆️ 正前方：無腳步"
                                    tvCenterAlert?.setTextColor(Color.WHITE)
                                }
                            }
                        }
                    }
                    Thread.sleep(50)
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
