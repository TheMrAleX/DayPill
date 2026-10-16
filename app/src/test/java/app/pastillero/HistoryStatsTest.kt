package app.pastillero

import app.pastillero.domain.*
import org.junit.Assert.*
import org.junit.Test

class HistoryStatsTest {
    @Test fun `seven day percentage includes today and six preceding days only`() {
        val rows = listOf(dose(status = DoseStatus.TAKEN), dose(id = 2, date = today.minusDays(6)),
            dose(id = 3, date = today.minusDays(7), status = DoseStatus.TAKEN),
            dose(id = 4, date = today.plusDays(1)))
        assertEquals(50, HistoryStats.sevenDayPercentage(rows, today))
    }
    @Test fun `empty history has no percentage and rounds to nearest whole percent`() {
        assertNull(HistoryStats.sevenDayPercentage(emptyList(), today))
        assertEquals(33, HistoryStats.sevenDayPercentage(listOf(dose(status = DoseStatus.TAKEN),
            dose(id = 2), dose(id = 3)), today))
    }
    @Test fun `points are chronological within reverse chronological days`() {
        val rows = listOf(dose(id = 1, minute = 1200),
            dose(id = 2, minute = 480, status = DoseStatus.TAKEN),
            dose(id = 3, date = today.minusDays(1)),
            dose(id = 4, date = today.minusDays(1), minute = 1200, status = DoseStatus.SKIPPED),
            dose(id = 5, date = today.minusDays(30)), dose(id = 6, date = today.plusDays(1)))
        val days = HistoryStats.days(rows, today)
        assertEquals(listOf(today, today.minusDays(1)), days.map { it.date })
        assertEquals(listOf(HistoryPoint.TAKEN, HistoryPoint.PENDING), days[0].points)
        assertEquals(listOf(HistoryPoint.MISSED, HistoryPoint.SKIPPED), days[1].points)
        assertEquals(1, days[0].taken)
        assertEquals(2, days[0].total)
    }
    @Test fun `thirty day boundary is inclusive and days without rows are absent`() {
        assertEquals(listOf(today.minusDays(29)), HistoryStats.days(
            listOf(dose(date = today.minusDays(29))), today).map { it.date })
    }
}
