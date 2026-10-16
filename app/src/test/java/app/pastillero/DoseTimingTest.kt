package app.pastillero

import app.pastillero.domain.*
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class DoseTimingTest {
    @Test fun `future pending schedule and unsnoozed reminder follow new zone`() {
        val row = dose(date = today.plusDays(1))
        val rebased = DoseTiming.rebaseFuture(listOf(row), testClock, ZoneId.of("Europe/Madrid")).single()
        assertEquals(row.scheduledAt - 2 * 3600000L, rebased.scheduledAt)
        assertEquals(rebased.scheduledAt, rebased.notifyAt)
    }
    @Test fun `snooze instant is preserved when rebasing schedule`() {
        val row = dose(date = today.plusDays(1), notifyAt = testClock.millis() + 600000)
        val rebased = DoseTiming.rebaseFuture(listOf(row), testClock, ZoneId.of("Europe/Madrid")).single()
        assertEquals(row.notifyAt, rebased.notifyAt)
        assertNotEquals(row.scheduledAt, rebased.scheduledAt)
    }
    @Test fun `past completed and skipped rows never change`() {
        val rows = listOf(dose(date = today.minusDays(1)), dose(id = 2, status = DoseStatus.TAKEN),
            dose(id = 3, status = DoseStatus.SKIPPED), dose(id = 4))
        assertEquals(rows, DoseTiming.rebaseFuture(rows, testClock, ZoneId.of("Europe/Madrid")))
    }
}
