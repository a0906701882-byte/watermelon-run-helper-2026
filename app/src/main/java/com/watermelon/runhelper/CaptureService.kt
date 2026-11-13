package com.watermelon.runhelper

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class CaptureService : Service() {

    private var audioRecord: AudioRecord? = null
    private var isListening = false
    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    companion object {
        const val ACTION_UPDATE_TACTICAL_DATA = "com.watermelon.runhelper.UPDATE_TACTICAL_DATA"
        private const val CHANNEL_ID = "TacticalRadarChannel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startTacticalMonitoring()
        return START_STICKY
    }

    private fun createNotification(): Notification {
        val channelId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "西瓜跑刀助手運行中",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
            CHANNEL_ID
        } else {
            ""
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("🍉 西瓜跑刀助手")
            .setContentText("戰術雷達與撤離監控背景運行中...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startTacticalMonitoring() {
        if (isListening) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            audioRecord?.startRecording()
            isListening = true

            Thread {
                val buffer = ShortArray(bufferSize)
                var baselineNoise = 0.0
                var counter = 0
                var remainingMatchSeconds = 1500 // 模擬對局剩餘時間

                while (isListening) {
                    if (remainingMatchSeconds > 0) {
                        remainingMatchSeconds--
                    }

                    val mins = remainingMatchSeconds / 60
                    val secs = remainingMatchSeconds % 60
                    val timeString = String.format("%02d:%02d", mins, secs)

                    // 剩 6 分半（390秒）即觸發緊急警告
                    val extractionAlert = if (remainingMatchSeconds <= 390) {
                        "⚠️ 撤離緊急警告：$timeString"
                    } else {
                        "⏱️ 撤離倒數：$timeString"
                    }

                    val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    var activeDirection = -1 // -1 代表安全，0=前, 1=後, 2=左, 3=右

                    if (readSize > 0) {
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            sum += (buffer[i] * buffer[i]).toDouble()
                        }
                        val currentAmplitude = sqrt(sum / readSize)

                        baselineNoise = baselineNoise * 0.95 + currentAmplitude * 0.05

                        if (currentAmplitude > baselineNoise * 1.4 && currentAmplitude in 1400.0..4800.0) {
                            counter++
                            activeDirection = counter % 4
                        }
                    }

                    val broadcastIntent = Intent(ACTION_UPDATE_TACTICAL_DATA).apply {
                        putExtra("extraction", extractionAlert)
                        putExtra("direction", activeDirection)
                    }
                    sendBroadcast(broadcastIntent)

                    Thread.sleep(600)
                }
            }.start()

        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isListening = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
