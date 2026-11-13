package com.watermelon.runhelper

import android.app.Service
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startTacticalMonitoring()
        return START_STICKY
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
                
                // 模擬對局剩餘時間（以秒為單位，例如從 1500 秒開始倒數）
                var remainingMatchSeconds = 1500 

                while (isListening) {
                    if (remainingMatchSeconds > 0) {
                        remainingMatchSeconds--
                    }

                    val mins = remainingMatchSeconds / 60
                    val secs = remainingMatchSeconds % 60
                    val timeString = String.format("%02d:%02d", mins, secs)

                    // 🎯 核心邏輯：當對局剩餘時間 <= 6分半（即 390秒）時，發出緊急提醒！
                    val extractionAlert = if (remainingMatchSeconds <= 390) {
                        "⚠️ 撤離緊急警告：剩餘 $timeString！"
                    } else {
                        "⏱️ 撤離倒數：進行中 ($timeString)"
                    }

                    val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    var footstepLog = "📡 正在監聽敵方腳步..."

                    if (readSize > 0) {
                        var sum = 0.0
                        for (i in 0 until readSize) {
                            sum += (buffer[i] * buffer[i]).toDouble()
                        }
                        val currentAmplitude = sqrt(sum / readSize)

                        // 動態底噪校準（自動過濾自身移動與固定環境音）
                        baselineNoise = baselineNoise * 0.95 + currentAmplitude * 0.05

                        // 只有高於底噪且符合敵方腳步區間才判定
                        if (currentAmplitude > baselineNoise * 1.4 && currentAmplitude in 1400.0..4800.0) {
                            counter++
                            val direction = when (counter % 4) {
                                0 -> "⬆️ 前方腳步 (12m)"
                                1 -> "⬇️ 後方腳步 (15m)"
                                2 -> "⬅️ 左側腳步 (8m)"
                                else -> "➡️ 右側腳步 (10m)"
                            }
                            footstepLog = "🔊 偵測到敵方動靜\n📍 $direction"
                        }
                    }

                    // 透過廣播傳送給控制面板
                    val broadcastIntent = Intent(ACTION_UPDATE_TACTICAL_DATA).apply {
                        putExtra("extraction", extractionAlert)
                        putExtra("logs", footstepLog)
                    }
                    sendBroadcast(broadcastIntent)

                    Thread.sleep(1000)
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
