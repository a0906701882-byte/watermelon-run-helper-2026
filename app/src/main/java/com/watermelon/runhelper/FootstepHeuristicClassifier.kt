package com.watermelon.runhelper

import kotlin.math.sqrt

/**
 * Experimental cadence-only heuristic. RMS pulses are NOT semantic recognition of
 * footsteps, jumps, vaults, reloads, or weapon handling. Those require a validated
 * audio-event model and labelled samples. The output must remain tentative.
 */
class FootstepHeuristicClassifier {
    private val pulseTimes = mutableListOf<Long>()
    private var aboveThreshold = false
    private var lastPulseAt = 0L
    private var noiseFloor = 90.0

    data class Result(val label: String, val detail: String, val confidence: String)

    @Synchronized
    fun addRms(rms: Int, now: Long = System.currentTimeMillis()): Result {
        val value = rms.toDouble()
        noiseFloor = if (value < noiseFloor * 2.0) noiseFloor * 0.94 + value * 0.06 else noiseFloor * 0.995 + value * 0.005
        val threshold = maxOf(260.0, noiseFloor * 2.8)
        val loud = value > threshold
        if (loud && !aboveThreshold && now - lastPulseAt > 220) {
            pulseTimes.add(now)
            lastPulseAt = now
        }
        aboveThreshold = loud
        while (pulseTimes.isNotEmpty() && now - pulseTimes.first() > 9000) pulseTimes.removeAt(0)
        if (pulseTimes.size < 5) return Result("不確定", "聲音事件不足，尚不能判斷腳步節奏", "資料不足")
        val times = pulseTimes.toList()
        val intervals = times.zipWithNext { a, b -> (b - a).toDouble() }
        if (intervals.size < 4) return Result("不確定", "正在累積聲音節奏", "資料不足")
        val mean = intervals.average()
        val variance = intervals.map { (it - mean) * (it - mean) }.average()
        val cv = if (mean <= 0) 1.0 else sqrt(variance) / mean
        val repeatedStops = intervals.count { it > 1100 } >= 2
        val recentEnough = now - times.last() < 1800
        if (!recentEnough) return Result("不確定", "目前沒有連續聲音事件", "資料不足")
        return when {
            cv < 0.16 && !repeatedStops -> Result("AI 傾向", "聲音事件間隔較規律；符合你提出的 AI 線索之一", "低")
            cv > 0.34 -> Result("真人傾向", "聲音事件間隔變化較大；符合你提出的真人線索之一", "低")
            repeatedStops -> Result("AI 傾向", "觀察到多次較長停頓；需更多聲音確認", "低")
            else -> Result("不確定", "節奏特徵不明顯，暫不分類", "低")
        }
    }

    @Synchronized
    fun reset() {
        pulseTimes.clear()
        aboveThreshold = false
        lastPulseAt = 0L
        noiseFloor = 90.0
    }
}
