package com.watermelon.runhelper

/** Detects only the four supported map labels from OCR text. */
object MapAutoDetector {
    private val mapPatterns = listOf(
        "零號大壩" to Regex("零\\s*號\\s*大\\s*壩"),
        "長弓溪谷" to Regex("長\\s*弓\\s*溪\\s*谷"),
        "航天基地" to Regex("航\\s*天\\s*基\\s*地"),
        "巴克什（舊版）" to Regex("巴\\s*克\\s*什")
    )
    fun detect(text: String): String? {
        if (Regex("巴\\s*克\\s*什\\s*2\\s*[.]?\\s*0", RegexOption.IGNORE_CASE).containsMatchIn(text)) return null
        return mapPatterns.firstOrNull { (_, pattern) -> pattern.containsMatchIn(text) }?.first
    }
}

/** Requires two consecutive matching OCR reads, then locks for this capture session. */
class MapSessionTracker(private val confirmationsRequired: Int = 2) {
    private var candidate: String? = null
    private var confirmations = 0
    private var lockedMap: String? = null
    @Synchronized fun observe(ocrText: String): String? {
        if (lockedMap != null) return null
        val detected = MapAutoDetector.detect(ocrText) ?: run {
            candidate = null
            confirmations = 0
            return null
        }
        if (detected == candidate) confirmations++ else {
            candidate = detected
            confirmations = 1
        }
        if (confirmations >= confirmationsRequired) {
            lockedMap = detected
            return detected
        }
        return null
    }
    @Synchronized fun currentMap(): String? = lockedMap
}
