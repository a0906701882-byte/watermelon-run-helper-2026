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
        private const val CHANNEL_ID = "WatermelonTieredRadar"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "西瓜分級常駐雷達",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍉 西瓜分級雷達")
                .setContentText("常駐分級監聽中...")
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

        fun createDirectionBox(gravity: Int, defaultText: String): TextView {
            return TextView(context).apply {
                text = defaultText
                setTextColor(Color.parseColor("#B0B0B0")) // 待命時為低調灰色
                textSize = 20f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setBackgroundColor(Color.parseColor("#99141414")) // 半透明戰術深黑底
                setPadding(25, 15, 25, 15)
                visibility = View.VISIBLE
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    this.gravity = gravity
                    setMargins(45, 45, 45, 45)
                }
            }
        }

        tvLeftAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.START, "⬅️ 左側:待命")
        hudRootLayout?.addView(tvLeftAlert)

        tvRightAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.END, "右側:待命 ➡️")
        hudRootLayout?.addView(tvRightAlert)

        tvCenterAlert = createDirectionBox(Gravity.TOP or Gravity.CENTER_HORIZONTAL, "⬆️ 前方:待命")
        hudRootLayout?.addView(tvCenterAlert)

        tvBackAlert = createDirectionBox(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, "⬇️ 背後:待命")
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

                            if (sampleEnergy > baselineNoise + 30L) {
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
                        val footstepThreshold = 18L
                        val hasFootstep = avgEnergy > baselineNoise + footstepThreshold

                        val isNear = avgEnergy > baselineNoise + 65L

                        handler.post {
                            // 重設四向預設待命狀態
                            tvLeftAlert?.text = "⬅️ 左側:待命"
                            tvLeftAlert?.setTextColor(Color.parseColor("#B0B0B0"))
                            tvLeftAlert?.textSize = 20f

                            tvRightAlert?.text = "右側:待命 ➡️"
                            tvRightAlert?.setTextColor(Color.parseColor("#B0B0B0"))
                            tvRightAlert?.textSize = 20f

                            tvCenterAlert?.text = "⬆️ 前方:待命"
                            tvCenterAlert?.setTextColor(Color.parseColor("#B0B0B0"))
                            tvCenterAlert?.textSize = 20f

                            tvBackAlert?.text = "⬇️ 背後:待命"
                            tvBackAlert?.setTextColor(Color.parseColor("#B0B0B0"))
                            tvBackAlert?.textSize = 20f

                            when {
                                isSelfAction -> {
                                    // 自身動作中不動作
                                }
                                hasFootstep -> {
                                    when {
                                        lAvg > rAvg * 1.3 -> {
                                            if (isNear) {
                                                tvLeftAlert?.text = "🔥 ⬅️ 【左側】極近貼臉！"
                                                tvLeftAlert?.setTextColor(Color.parseColor("#FFD700"))
                                                tvLeftAlert?.textSize = 21f // 調整為 21 號
                                            } else {
                                                tvLeftAlert?.text = "⚠️ ⬅️ 【左側】遠處腳步"
                                                tvLeftAlert?.setTextColor(Color.parseColor("#00E676"))
                                                tvLeftAlert?.textSize = 20f
                                            }
                                        }
                                        rAvg > lAvg * 1.3 -> {
                                            if (isNear) {
                                                tvRightAlert?.text = "【右側】極近貼臉！ ➡️"
                                                tvRightAlert?.setTextColor(Color.parseColor("#FFD700"))
                                                tvRightAlert?.textSize = 21f // 調整為 21 號
                                            } else {
                                                tvRightAlert?.text = "【右側】遠處腳步 ➡️"
                                                tvRightAlert?.setTextColor(Color.parseColor("#00E676"))
                                                tvRightAlert?.textSize = 20f
                                            }
                                        }
                                        phaseCross > phaseHistory * 1.12 -> {
                                            if (isNear) {
                                                tvCenterAlert?.text = "🔥 ⬆️ 【正前方】極近貼臉！"
                                                tvCenterAlert?.setTextColor(Color.parseColor("#FFD700"))
                                                tvCenterAlert?.textSize = 21f // 調整為 21 號
                                            } else {
                                                tvCenterAlert?.text = "⚠️ ⬆️ 【正前方】遠處腳步"
                                                tvCenterAlert?.setTextColor(Color.parseColor("#00E676"))
                                                tvCenterAlert?.textSize = 20f
                                            }
                                        }
                                        else -> {
                                            if (isNear) {
                                                tvBackAlert?.text = "🚨 ⬇️ 【背後】極近貼臉危機！"
                                                tvBackAlert?.setTextColor(Color.parseColor("#FF5252"))
                                                tvBackAlert?.textSize = 21f // 調整為 21 號
                                            } else {
                                                tvBackAlert?.text = "⚠️ ⬇️ 【背後】遠處腳步"
                                                tvBackAlert?.setTextColor(Color.parseColor("#FF8A80"))
                                                tvBackAlert?.textSize = 20f
                                            }
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
