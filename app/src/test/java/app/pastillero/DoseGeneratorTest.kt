package app.pastillero

import app.pastillero.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class DoseGeneratorTest {
    private fun generate(date: LocalDate = today, schedules: List<Schedule> = listOf(testSchedule),
        existing: List<DoseLog> = emptyList(), meds: List<Medication> = listOf(testMedication)) =
        DoseGenerator.ensureDoses(date, meds, schedules, existing, ZoneOffset.UTC)

    @Test fun `generates today and the following two days with snapshots`() {
        val rows = generate()
        assertEquals(listOf(today, today.plusDays(1), today.plusDays(2)), rows.map { it.localDate })
        assertTrue(rows.all { it.nameSnapshot == testMedication.name && it.doseSnapshot == testMedication.dose })
        assertTrue(rows.all { it.status == DoseStatus.PENDING && it.notifyAt == it.scheduledAt })
    }

    @Test fun `day bits are Monday through Sunday`() {
        val monday = LocalDate.of(2026, 10, 12)
        for (day in 0..6) {
            val date = monday.plusDays(day.toLong())
            val rows = generate(date, listOf(testSchedule.copy(daysMask = 1 shl day)))
            assertEquals(listOf(date), rows.map { it.localDate })
        }
    }

    @Test fun `repeated calls and duplicate schedules do not duplicate rows`() {
        val first = generate(schedules = listOf(testSchedule, testSchedule))
        assertEquals(3, first.size)
        assertTrue(generate(existing = first).isEmpty())
    }

    @Test fun `taken and skipped rows also block regeneration`() {
        assertEquals(1, generate(existing = listOf(
            dose(status = DoseStatus.TAKEN),
            dose(id = 2, date = today.plusDays(1), status = DoseStatus.SKIPPED),
        )).size)
    }

    @Test fun `archived medications and retired schedules are ignored`() {
        assertTrue(generate(meds = listOf(testMedication.copy(archived = true))).isEmpty())
        assertTrue(generate(schedules = listOf(testSchedule.copy(daysMask = 0))).isEmpty())
    }

    @Test fun `spring gap shifts 0230 to 0330 in Madrid`() {
        val result = DoseGenerator.scheduledAt(LocalDate.of(2026, 3, 29), 150, ZoneId.of("Europe/Madrid"))
        assertEquals(Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(), result)
        assertEquals(3, Instant.ofEpochMilli(result).atZone(ZoneId.of("Europe/Madrid")).hour)
    }

    @Test fun `autumn overlap uses the first occurrence`() {
        val result = DoseGenerator.scheduledAt(LocalDate.of(2026, 10, 25), 150, ZoneId.of("Europe/Madrid"))
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), result)
    }
}
