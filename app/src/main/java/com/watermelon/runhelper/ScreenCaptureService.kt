package com.example.watermelonhelper

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.regex.Pattern

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START_CAPTURE"
        const val ACTION_STOP = "ACTION_STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"

        const val ACTION_COUNTDOWN_UPDATE = "com.example.watermelonhelper.COUNTDOWN_UPDATE"
        const val EXTRA_TIME_TEXT = "time_text"
        const val EXTRA_TOTAL_SECONDS = "total_seconds"
        const val EXTRA_IS_EMERGENCY = "is_emergency"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0
    private var isRunning = false

    private val timePattern = Pattern.compile("\\b([0-5]?\\d):([0-5]\\d)\\b")

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            processLatestFrame()
            handler.postDelayed(this, 1000L) // 每秒抓取辨識一次
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startForegroundNotification()
                    initMediaProjection(resultCode, resultData)
                }
            }
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val channelId = "watermelon_ocr_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "西瓜跑刀助手 - 撤離倒數監控",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("🍉 西瓜戰術視覺監控中")
            .setContentText("正在自動偵測對局撤離倒數時間...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()

        startForeground(2026, notification)
    }

    private fun initMediaProjection(resultCode: Int, data: Intent) {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "WatermelonScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            handler
        )

        isRunning = true
        handler.post(captureRunnable)
    }

    private fun processLatestFrame() {
        val image = imageReader?.acquireLatestImage() ?: return
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val fullBitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            fullBitmap.copyPixelsFromBuffer(buffer)

            // 僅裁切上方 22% 區域（倒數通常在畫面頂端）
            val cropHeight = (screenHeight * 0.22).toInt().coerceAtLeast(100)
            val croppedBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, screenWidth, cropHeight)
            fullBitmap.recycle()

            val inputImage = InputImage.fromBitmap(croppedBitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    extractCountdownTime(visionText.text)
                    croppedBitmap.recycle()
                }
                .addOnFailureListener {
                    croppedBitmap.recycle()
                }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    private fun extractCountdownTime(recognizedText: String) {
        val matcher = timePattern.matcher(recognizedText)
        if (matcher.find()) {
            val timeStr = matcher.group(0) ?: return
            val minutes = matcher.group(1)?.toIntOrNull() ?: 0
            val seconds = matcher.group(2)?.toIntOrNull() ?: 0
            val totalSeconds = minutes * 60 + seconds

            val isEmergency = totalSeconds in 1..90

            val broadcastIntent = Intent(ACTION_COUNTDOWN_UPDATE).apply {
                setPackage(packageName)
                putExtra(EXTRA_TIME_TEXT, timeStr)
                putExtra(EXTRA_TOTAL_SECONDS, totalSeconds)
                putExtra(EXTRA_IS_EMERGENCY, isEmergency)
            }
            sendBroadcast(broadcastIntent)
        }
    }

    private fun stopCapture() {
        isRunning = false
        handler.removeCallbacks(captureRunnable)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
