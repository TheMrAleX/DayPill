package app.pastillero

import app.pastillero.domain.*
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test

class NextAlarmCalculatorTest {
    private val now = testClock.millis()
    @Test fun `selects the smallest notify time rather than schedule time`() {
        val result = NextAlarmCalculator.calculate(listOf(
            dose(id = 1, notifyAt = now + 2000), dose(id = 2, notifyAt = now + 1000)), testClock)
        assertEquals(NextAlarm(2, now + 1000), result.next)
    }
    @Test fun `ignores completed skipped and silenced doses`() {
        val result = NextAlarmCalculator.calculate(listOf(dose(status = DoseStatus.TAKEN),
            dose(id = 2, status = DoseStatus.SKIPPED), dose(id = 3, notifyAt = null)), testClock)
        assertNull(result.next)
        assertTrue(result.expiredDoseIds.isEmpty())
    }
    @Test fun `less than twelve hours late fires immediately`() {
        assertEquals(NextAlarm(1, now), NextAlarmCalculator.calculate(
            listOf(dose(notifyAt = now - Duration.ofHours(12).toMillis() + 1)), testClock).next)
    }
    @Test fun `twelve hours or more is discarded and reported for persistence`() {
        val result = NextAlarmCalculator.calculate(listOf(
            dose(id = 1, notifyAt = now - Duration.ofHours(13).toMillis()),
            dose(id = 2, notifyAt = now - Duration.ofHours(12).toMillis()),
            dose(id = 3, notifyAt = now + 1)), testClock)
        assertEquals(listOf(1L, 2L), result.expiredDoseIds)
        assertEquals(NextAlarm(3, now + 1), result.next)
    }
    @Test fun `ties are deterministic and empty input has no alarm`() {
        assertEquals(1L, NextAlarmCalculator.calculate(listOf(dose(id = 2, notifyAt = now),
            dose(id = 1, notifyAt = now)), testClock).next!!.doseId)
        assertNull(NextAlarmCalculator.calculate(emptyList(), testClock).next)
    }
}
