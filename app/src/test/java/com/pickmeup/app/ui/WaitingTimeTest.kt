package com.pickmeup.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class WaitingTimeTest {
    @Test
    fun fifteenMinuteOptionKeepsItsValueUntilTheNextMinute() {
        assertEquals(15, remainingMinutesForAdvice(900_000))
        assertEquals(15, remainingMinutesForAdvice(899_999))
        assertEquals(15, remainingMinutesForAdvice(840_001))
        assertEquals(14, remainingMinutesForAdvice(840_000))
        assertEquals(0, remainingMinutesForAdvice(-1))
    }
}
