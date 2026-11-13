package com.watermelon.runhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
    private var tvBrandHeader: TextView? = null

    private var audioRecord: AudioRecord? = null
    private var mediaProjection: MediaProjection? = null
    private var isRunning = false

    companion object {
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "WatermelonEsportsCompact"
        private const val NOTIFICATION_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "西瓜電競精巧雷達",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🍉 西瓜電競戰術雷達")
                .setContentText("精巧霓虹監聽中...")
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

        // 建立精巧版戰術方塊（字體與內距縮小，邊距更靠近螢幕邊緣）
        fun createDirectionBox(gravity: Int, defaultText: String): TextView {
            return TextView(context).apply {
                text = defaultText
                setTextColor(Color.parseColor("#00F0FF"))
                textSize = 16f // 縮小至 16 號精巧字體
                setTypeface(null, android.graphics.Typeface.BOLD)
                
                val neonDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10f
                    setColor(Color.parseColor("#B30A0A0A"))
                    setStroke(2, Color.parseColor("#6600F0FF"))
                }
                background = neonDrawable
                setShadowLayer(8f, 0f, 0f, Color.parseColor("#FF00F0FF"))
                
                setPadding(18, 10, 18, 10) // 縮小內距，外觀更緊湊
                visibility = View.VISIBLE
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    this.gravity = gravity
                    setMargins(25, 25, 25, 25) // 縮小螢幕邊距，更靠邊不擋視野
                }
            }
        }

        tvLeftAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.START, "⬅️ 左:待命")
        hudRootLayout?.addView(tvLeftAlert)

        tvRightAlert = createDirectionBox(Gravity.CENTER_VERTICAL or Gravity.END, "右:待命 ➡️")
        hudRootLayout?.addView(tvRightAlert)

        tvCenterAlert = createDirectionBox(Gravity.TOP or Gravity.CENTER_HORIZONTAL, "⬆️ 前:待命")
        hudRootLayout?.addView(tvCenterAlert)

        tvBackAlert = createDirectionBox(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, "⬇️ 背:待命")
        hudRootLayout?.addView(tvBackAlert)

        // 🍉【西瓜電競】頂部精巧品牌標誌
        tvBrandHeader = TextView(context).apply {
            text = "🍉 西瓜電競"
            setTextColor(Color.parseColor("#FFEA00"))
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val brandDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f
                setColor(Color.parseColor("#99000000"))
                setStroke(1, Color.parseColor("#66FFEA00"))
            }
            background = brandDrawable
            setShadowLayer(6f, 0f, 0f, Color.parseColor("#FFFFEA00"))
            setPadding(12, 4, 12, 4)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setMargins(0, 10, 0, 0)
            }
        }
        hudRootLayout?.addView(tvBrandHeader)

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
                            // 重設為待命狀態（精巧字體 16f）
                            val resetView = { view: TextView?, text: String ->
                                view?.apply {
                                    this.text = text
                                    setTextColor(Color.parseColor("#00F0FF"))
                                    textSize = 16f
                                    setShadowLayer(6f, 0f, 0f, Color.parseColor("#FF00F0FF"))
                                }
                            }
                            resetView(tvLeftAlert, "⬅️ 左:待命")
                            resetView(tvRightAlert, "右:待命 ➡️")
                            resetView(tvCenterAlert, "⬆️ 前:待命")
                            resetView(tvBackAlert, "⬇️ 背:待命")

                            when {
                                isSelfAction -> {
                                    // 自身動作中暫不顯示
                                }
                                hasFootstep -> {
                                    when {
                                        lAvg > rAvg * 1.3 -> {
                                            if (isNear) {
                                                tvLeftAlert?.text = "🔥 ⬅️ 【左】貼臉！"
                                                tvLeftAlert?.setTextColor(Color.parseColor("#FFEA00"))
                                                tvLeftAlert?.textSize = 18f // 貼臉時稍微大一點點以示區別
                                                tvLeftAlert?.setShadowLayer(10f, 0f, 0f, Color.parseColor("#FFFFEA00"))
                                            } else {
                                                tvLeftAlert?.text = "⚠️ ⬅️ 【左】腳步"
                                                tvLeftAlert?.setTextColor(Color.parseColor("#39FF14"))
                                                tvLeftAlert?.textSize = 16f
                                                tvLeftAlert?.setShadowLayer(8f, 0f, 0f, Color.parseColor("#FF39FF14"))
                                            }
                                        }
                                        rAvg > lAvg * 1.3 -> {
                                            if (isNear) {
                                                tvRightAlert?.text = "【右】貼臉！ ➡️"
                                                tvRightAlert?.setTextColor(Color.parseColor("#FFEA00"))
                                                tvRightAlert?.textSize = 18f
                                                tvRightAlert?.setShadowLayer(10f, 0f, 0f, Color.parseColor("#FFFFEA00"))
                                            } else {
                                                tvRightAlert?.text = "【右】腳步 ➡️"
                                                tvRightAlert?.setTextColor(Color.parseColor("#39FF14"))
                                                tvRightAlert?.textSize = 16f
                                                tvRightAlert?.setShadowLayer(8f, 0f, 0f, Color.parseColor("#FF39FF14"))
                                            }
                                        }
                                        phaseCross > phaseHistory * 1.12 -> {
                                            if (isNear) {
                                                tvCenterAlert?.text = "🔥 ⬆️ 【前】貼臉！"
                                                tvCenterAlert?.setTextColor(Color.parseColor("#FFEA00"))
                                                tvCenterAlert?.textSize = 18f
                                                tvCenterAlert?.setShadowLayer(10f, 0f, 0f, Color.parseColor("#FFFFEA00"))
                                            } else {
                                                tvCenterAlert?.text = "⚠️ ⬆️ 【前】腳步"
                                                tvCenterAlert?.setTextColor(Color.parseColor("#39FF14"))
                                                tvCenterAlert?.textSize = 16f
                                                tvCenterAlert?.setShadowLayer(8f, 0f, 0f, Color.parseColor("#FF39FF14"))
                                            }
                                        }
                                        else -> {
                                            if (isNear) {
                                                tvBackAlert?.text = "🚨 ⬇️ 【背】貼臉危機！"
                                                tvBackAlert?.setTextColor(Color.parseColor("#FF0055"))
                                                tvBackAlert?.textSize = 18f
                                                tvBackAlert?.setShadowLayer(10f, 0f, 0f, Color.parseColor("#FFFF0055"))
                                            } else {
                                                tvBackAlert?.text = "⚠️ ⬇️ 【背】腳步"
                                                tvBackAlert?.setTextColor(Color.parseColor("#FF77AA"))
                                                tvBackAlert?.textSize = 16f
                                                tvBackAlert?.setShadowLayer(8f, 0f, 0f, Color.parseColor("#FFFF77AA"))
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
