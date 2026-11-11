package com.watermelon.runhelper

import org.junit.Assert.assertEquals
import org.junit.Test

class FootstepHeuristicClassifierTest {
    @Test fun insufficientSamplesStayUnknown() {
        val result = FootstepHeuristicClassifier().addRms(1200, 1000)
        assertEquals("不確定", result.label)
    }
    @Test fun regularSoundPulsesAreOnlyTentativelyClassified() {
        val classifier = FootstepHeuristicClassifier()
        classifier.addRms(1500, 1000)
        var result = classifier.addRms(100, 1300)
        for (time in listOf(1400L, 1800L, 2200L, 2600L, 3000L)) {
            classifier.addRms(100, time - 100)
            result = classifier.addRms(1500, time)
        }
        assertEquals("AI 傾向", result.label)
        assertEquals("低", result.confidence)
    }
}
