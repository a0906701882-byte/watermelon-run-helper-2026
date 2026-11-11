package com.watermelon.runhelper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapAutoDetectorTest {
    @Test fun detectsSupportedMaps() {
        assertEquals("零號大壩", MapAutoDetector.detect("零號大壩作戰區域"))
        assertEquals("長弓溪谷", MapAutoDetector.detect("長弓溪谷"))
        assertEquals("航天基地", MapAutoDetector.detect("航天基地"))
        assertEquals("巴克什（舊版）", MapAutoDetector.detect("巴克什"))
    }
    @Test fun excludesBrakkeshTwoPointZero() {
        assertNull(MapAutoDetector.detect("巴克什 2.0"))
        assertNull(MapAutoDetector.detect("巴克什2.0 作戰區"))
    }
    @Test fun requiresTwoConsecutiveReadsBeforeLocking() {
        val tracker = MapSessionTracker(2)
        assertNull(tracker.observe("零號大壩"))
        assertEquals("零號大壩", tracker.observe("零號大壩"))
        assertEquals("零號大壩", tracker.currentMap())
        assertNull(tracker.observe("長弓溪谷"))
        assertEquals("零號大壩", tracker.currentMap())
    }
    @Test fun differentReadResetsConfirmationAndUnsupportedTextDoesNotLock() {
        val tracker = MapSessionTracker(2)
        assertNull(tracker.observe("零號大壩"))
        assertNull(tracker.observe("航天基地"))
        assertNull(tracker.observe("巴克什 2.0"))
        assertNull(tracker.currentMap())
        assertNull(tracker.observe("航天基地"))
        assertEquals("航天基地", tracker.observe("航天基地"))
    }
}
