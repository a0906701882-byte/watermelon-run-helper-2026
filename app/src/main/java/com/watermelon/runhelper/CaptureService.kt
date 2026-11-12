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
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class CaptureService : Service() {

    private var windowManager: WindowManager? = null
    private var hudRootLayout: FrameLayout? = null

    private var tvLeftAlert: TextView? = null
    private var tvRightAlert: TextView? = null
    private var tvCenterAlert: TextView? = null
    private var tvBackAlert: TextView? = null

    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var isRunning = false

    companion object {
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "WatermelonLargeRadar"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "西瓜大字四向雷達",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍉 西瓜大字四向雷達")
                .setContentText("大字警報高能監聽中...")
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

        fun createDirectionBox(gravity: Int): TextView {
            return TextView(context).apply {
                text = ""
                setTextColor(Color.WHITE)
                textSize = 24f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#B3141414"))
                setPadding(28, 18, 28, 18)
                visibility = View.INVISIBLE
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    this.gravity = gravity
                    setMargins(45, 45, 45, 45)
                }
            }
        }

        tvLeftAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.START)
        hudRootLayout?.addView(tvLeftAlert)

        tvRightAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.END)
        hudRootLayout?.addView(tvRightAlert)

        tvCenterAlert = createDirectionBox(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        hudRootLayout?.addView(tvCenterAlert)

        tvBackAlert = createDirectionBox(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        hudRootLayout?.addView(tvBackAlert)

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
                    initGameAudioCapture(mediaProjection!!)
                }
            }
        }
        return START_STICKY
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
                var baselineNoise = 250L
                var phaseHistory = 0L

                while (isRunning) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        var leftSum = 0L
                        var rightSum = 0L
                        var totalEnergy = 0L
                        var peakCount = 0
                        var phaseCross = 0L
                        var i = 0
                        
                        while (i < readSize - 4) {
                            val leftSample = (buffer[i].toInt() or (buffer[i + 1].toInt() shl 8)).toShort()
                            val rightSample = (buffer[i + 2].toInt() or (buffer[i + 3].toInt() shl 8)).toShort()
                            val lVal = abs(leftSample.toInt())
                            val rVal = abs(rightSample.toInt())
                            
                            leftSum += lVal
                            rightSum += rVal
                            val sampleEnergy = lVal + rVal
                            totalEnergy += sampleEnergy

                            if (sampleEnergy > baselineNoise + 40L) {
                                peakCount++
                            }

                            phaseCross += abs(leftSample - rightSample)
                            i += 4
                        }

                        val sampleCount = (readSize / 4).coerceAtLeast(1)
                        val avgEnergy = totalEnergy / sampleCount
                        val lAvg = leftSum / sampleCount
                        val rAvg = rightSum / sampleCount

                        baselineNoise = (baselineNoise * 0.90 + avgEnergy * 0.10).toLong()
                        phaseHistory = (phaseHistory * 0.8 + phaseCross * 0.2).toLong()

                        val isSelfAction = abs(lAvg - rAvg) < (lAvg * 0.3) && avgEnergy > baselineNoise + 100L

                        val stealthMin = baselineNoise + 15L
                        val stealthMax = baselineNoise + 110L

                        val isPlayerStealth = (avgEnergy in stealthMin..stealthMax) && (peakCount in 8..120)

                        handler.post {
                            when {
                                isSelfAction || !isPlayerStealth -> {
                                    tvLeftAlert?.visibility = View.INVISIBLE
                                    tvRightAlert?.visibility = View.INVISIBLE
                                    tvCenterAlert?.visibility = View.INVISIBLE
                                    tvBackAlert?.visibility = View.INVISIBLE
                                }
                                else -> {
                                    when {
                                        lAvg > rAvg * 1.35 -> {
                                            tvLeftAlert?.text = "⚠️ ⬅️ 【左側】靜步"
                                            tvLeftAlert?.setTextColor(Color.parseColor("#00E676"))
                                            tvLeftAlert?.visibility = View.VISIBLE

                                            tvRightAlert?.visibility = View.INVISIBLE
                                            tvCenterAlert?.visibility = View.INVISIBLE
                                            tvBackAlert?.visibility = View.INVISIBLE
                                        }
                                        rAvg > lAvg * 1.35 -> {
                                            tvLeftAlert?.visibility = View.INVISIBLE

                                            tvRightAlert?.text = "【右側】靜步 ➡️"
                                            tvRightAlert?.setTextColor(Color.parseColor("#00E676"))
                                            tvRightAlert?.visibility = View.VISIBLE

                                            tvCenterAlert?.visibility = View.INVISIBLE
                                            tvBackAlert?.visibility = View.INVISIBLE
                                        }
                                        phaseCross > phaseHistory * 1.15 -> {
                                            tvLeftAlert?.visibility = View.INVISIBLE
                                            tvRightAlert?.visibility = View.INVISIBLE
                                            tvBackAlert?.visibility = View.INVISIBLE

                                            tvCenterAlert?.text = "⚠️ ⬆️ 【正前方】靜步"
                                            tvCenterAlert?.setTextColor(Color.parseColor("#00E676"))
                                            tvCenterAlert?.visibility = View.VISIBLE
                                        }
                                        else -> {
                                            tvLeftAlert?.visibility = View.INVISIBLE
                                            tvRightAlert?.visibility = View.INVISIBLE
                                            tvCenterAlert?.visibility = View.INVISIBLE

                                            tvBackAlert?.text = "⚠️ ⬇️ 【背後】靜步摸進！"
                                            tvBackAlert?.setTextColor(Color.parseColor("#FF5252"))
                                            tvBackAlert?.visibility = View.VISIBLE
                                        }
                                    }
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
