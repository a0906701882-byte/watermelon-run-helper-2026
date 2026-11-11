package com.watermelon.runhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.Service
import android.content.pm.ServiceInfo
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Process
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class CaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_UPDATE = "com.watermelon.runhelper.UPDATE"
        const val EXTRA_OCR = "ocr"
        const val EXTRA_AUDIO = "audio"
        const val EXTRA_STATE = "state"
        const val EXTRA_MAP = "map"
        const val EXTRA_CLASSIFICATION = "classification"
        const val ACTION_STOP = "stop"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var audioThread: Thread? = null
    private val processing = AtomicBoolean(false)
    private val runningAudio = AtomicBoolean(false)
    private val footstepClassifier = FootstepHeuristicClassifier()
    private val mapSessionTracker = MapSessionTracker(confirmationsRequired = 2)
    private val recognizer by lazy { TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()) }
    private val channelId = "watermelon_capture"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (Build.VERSION.SDK_INT >= 29) startForeground(2001, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(2001, notification())
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (data == null || projection != null) {
            broadcast(state = "擷取未啟動：缺少授權資料或服務已在執行")
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, Handler(mainLooper))
            startScreenCapture()
            startAudioCaptureIfPossible()
            broadcast(state = "畫面分析已啟動；音訊擷取視遊戲政策而定")
        } catch (e: Exception) {
            broadcast(state = "擷取啟動失敗：" + (e.message ?: "未知原因"))
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "西瓜跑刀助手分析", android.app.NotificationManager.IMPORTANCE_LOW)
            getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("西瓜跑刀助手")
            .setContentText("正在依照你的授權分析螢幕")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    private fun startScreenCapture() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        worker = HandlerThread("screen-ocr", Process.THREAD_PRIORITY_BACKGROUND).also { it.start() }
        reader?.setOnImageAvailableListener({ source ->
            val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
            if (!processing.compareAndSet(false, true)) {
                image.close()
                return@setOnImageAvailableListener
            }
            try {
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val paddedWidth = rowStride / pixelStride
                val buffer: ByteBuffer = plane.buffer
                val full = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(buffer)
                val screen = Bitmap.createBitmap(full, 0, 0, width.coerceAtMost(full.width), height.coerceAtMost(full.height))
                full.recycle()
                image.close()
                val cropLeft = (screen.width * 0.54f).toInt().coerceIn(0, screen.width - 1)
                val cropTop = (screen.height * 0.66f).toInt().coerceIn(0, screen.height - 1)
                val mapCrop = Bitmap.createBitmap(screen, cropLeft, cropTop, screen.width - cropLeft, screen.height - cropTop)
                screen.recycle()
                val input = InputImage.fromBitmap(mapCrop, 0)
                recognizer.process(input)
                    .addOnSuccessListener { result ->
                        val text = result.text.lines().filter { it.isNotBlank() }.take(8).joinToString("｜")
                        broadcast(ocr = if (text.isBlank()) "右下角地圖名稱尚未辨識到" else "右下角：$text")
                        mapSessionTracker.observe(result.text)?.let { confirmedMap -> broadcast(map = confirmedMap) }
                    }
                    .addOnFailureListener { broadcast(ocr = "右下角地圖 OCR 分析失敗") }
                    .addOnCompleteListener {
                        mapCrop.recycle()
                        processing.set(false)
                    }
            } catch (e: Exception) {
                image.close()
                processing.set(false)
                broadcast(ocr = "畫面讀取失敗")
            }
        }, Handler(worker!!.looper))
        virtualDisplay = projection?.createVirtualDisplay(
            "WatermelonScreenCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, Handler(worker!!.looper)
        )
    }

    private fun startAudioCaptureIfPossible() {
        if (Build.VERSION.SDK_INT < 29 || projection == null) {
            broadcast(audio = "此 Android 版本不支援遊戲音訊擷取")
            return
        }
        audioThread = Thread {
            var recorder: AudioRecord? = null
            try {
                val config = android.media.AudioPlaybackCaptureConfiguration.Builder(projection!!)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(16000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build()
                val minSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                recorder = AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes((minSize.coerceAtLeast(4096)) * 2)
                    .setAudioPlaybackCaptureConfig(config)
                    .build()
                if (recorder.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("AudioRecord 無法初始化")
                recorder.startRecording()
                runningAudio.set(true)
                val samples = ShortArray(2048)
                while (runningAudio.get()) {
                    val count = recorder.read(samples, 0, samples.size)
                    if (count > 0) {
                        var sum = 0.0
                        for (i in 0 until count) {
                            val value = samples[i].toDouble()
                            sum += value * value
                        }
                        val rms = sqrt(sum / count).toInt()
                        val result = footstepClassifier.addRms(rms)
                        val activity = if (rms > 900) "有聲音活動" else "聲音較弱"
                        broadcast(
                            audio = "$activity；音量指標 $rms",
                            classification = "${result.label}｜${result.detail}｜可信度：${result.confidence}"
                        )
                    }
                }
            } catch (e: Exception) {
                broadcast(audio = "遊戲音訊無法擷取：遊戲可能禁止擷取或系統限制")
            } finally {
                runningAudio.set(false)
                try { recorder?.stop() } catch (_: Exception) {}
                recorder?.release()
            }
        }.apply { name = "playback-audio-analysis"; start() }
    }

    private fun broadcast(ocr: String? = null, audio: String? = null, state: String? = null, map: String? = null, classification: String? = null) {
        val intent = Intent(ACTION_UPDATE).setPackage(packageName)
        if (ocr != null) intent.putExtra(EXTRA_OCR, ocr)
        if (audio != null) intent.putExtra(EXTRA_AUDIO, audio)
        if (state != null) intent.putExtra(EXTRA_STATE, state)
        if (map != null) intent.putExtra(EXTRA_MAP, map)
        if (classification != null) intent.putExtra(EXTRA_CLASSIFICATION, classification)
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        runningAudio.set(false)
        footstepClassifier.reset()
        try { audioThread?.join(300) } catch (_: InterruptedException) {}
        virtualDisplay?.release()
        virtualDisplay = null
        reader?.close()
        reader = null
        worker?.quitSafely()
        worker = null
        projection?.stop()
        projection = null
        try { recognizer.close() } catch (_: Exception) {}
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
