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
import kotlin.math.abs

class CaptureService : Service() {

    private var windowManager: WindowManager? = null
    private var hudRootLayout: FrameLayout? = null

    private var viewLeft: TextView? = null
    private var viewRight: TextView? = null
    private var viewCenter: TextView? = null

    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isRunning = false

    companion object {
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "WatermelonLightRadar"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "西瓜聲納光條雷達",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍉 西瓜聲納光條雷達")
                .setContentText("無文字半透明戰術模式運行中...")
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

        // 建立半透明無文字的色塊指標
        val leftBox = TextView(context).apply {
            text = ""
            setBackgroundColor(Color.parseColor("#66141414"))
            layoutParams = FrameLayout.LayoutParams(35, 180).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                setMargins(30, 30, 30, 30)
            }
        }
        viewLeft = leftBox
        hudRootLayout?.addView(viewLeft)

        val rightBox = TextView(context).apply {
            text = ""
            setBackgroundColor(Color.parseColor("#66141414"))
            layoutParams = FrameLayout.LayoutParams(35, 180).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setMargins(30, 30, 30, 30)
            }
        }
        viewRight = rightBox
        hudRootLayout?.addView(viewRight)

        val centerBox = TextView(context).apply {
            text = ""
            setBackgroundColor(Color.parseColor("#66141414"))
            layoutParams = FrameLayout.LayoutParams(180, 35).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setMargins(30, 30, 30, 30)
            }
        }
        viewCenter = centerBox
        hudRootLayout?.addView(viewCenter)

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
                "WatermelonLightDisplay",
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
                        val isSelfAction = abs(lAvg - rAvg) < (lAvg * 0.25) && avgEnergy > dynamicThreshold + 50

                        handler.post {
                            when {
                                isSelfAction -> {
                                    viewLeft?.setBackgroundColor(Color.parseColor("#3300B0FF"))
                                    viewRight?.setBackgroundColor(Color.parseColor("#3300B0FF"))
                                    viewCenter?.setBackgroundColor(Color.parseColor("#3300B0FF"))
                                }
                                avgEnergy in (baselineNoise + 15)..(baselineNoise + 50) -> {
                                    viewLeft?.setBackgroundColor(Color.parseColor("#9900E676"))
                                    viewRight?.setBackgroundColor(Color.parseColor("#9900E676"))
                                    viewCenter?.setBackgroundColor(Color.parseColor("#9900E676"))
                                }
                                lAvg > rAvg * 1.5 && lAvg > dynamicThreshold -> {
                                    viewLeft?.setBackgroundColor(Color.parseColor("#CCFF5252"))
                                    viewRight?.setBackgroundColor(Color.parseColor("#33141414"))
                                    viewCenter?.setBackgroundColor(Color.parseColor("#33141414"))
                                }
                                rAvg > lAvg * 1.5 && rAvg > dynamicThreshold -> {
                                    viewLeft?.setBackgroundColor(Color.parseColor("#33141414"))
                                    viewRight?.setBackgroundColor(Color.parseColor("#CCFF5252"))
                                    viewCenter?.setBackgroundColor(Color.parseColor("#33141414"))
                                }
                                avgEnergy > dynamicThreshold + 40 -> {
                                    viewLeft?.setBackgroundColor(Color.parseColor("#33141414"))
                                    viewRight?.setBackgroundColor(Color.parseColor("#33141414"))
                                    viewCenter?.setBackgroundColor(Color.parseColor("#CCFFD700"))
                                }
                                else -> {
                                    viewLeft?.setBackgroundColor(Color.parseColor("#66141414"))
                                    viewRight?.setBackgroundColor(Color.parseColor("#66141414"))
                                    viewCenter?.setBackgroundColor(Color.parseColor("#66141414"))
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
