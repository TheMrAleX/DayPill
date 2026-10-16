package app.pastillero

import app.pastillero.data.*
import app.pastillero.domain.*
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MedicationRepositoryTest {
    private val store = MemoryStore()
    private val repository = MedicationRepository(store, testClock, zoneProvider = { ZoneOffset.UTC })
    private suspend fun create() = repository.saveMedication("  Medication  ", "1 tablet", 4, listOf(480, 1200, 480), 127)

    @Test fun `save trims name deduplicates hours and generates three days`() = runTest {
        create()
        assertEquals("Medication", store.medications().single().name)
        assertEquals(2, store.schedules().size)
        assertEquals(6, store.doses().size)
        repository.ensureDoses()
        assertEquals(6, store.doses().size)
    }
    @Test fun `edit preserves completed skipped past rows and historical schedule keys`() = runTest {
        val id = create()
        val first = store.doses().first { it.localDate == today && it.minuteOfDay == 480 }
        repository.setTaken(first.id, true)
        val tomorrow = store.doses().first { it.localDate == today.plusDays(1) && it.minuteOfDay == 480 }
        store.updateDoses(listOf(tomorrow.copy(status = DoseStatus.SKIPPED, notifyAt = null)))
        val past = dose(id = 100, date = today.minusDays(1))
        store.insertMissingDoses(listOf(past))
        repository.saveMedication("New name", "New dose", 2, listOf(540), 127, id)
        assertEquals(first.nameSnapshot, store.doses().first { it.id == first.id }.nameSnapshot)
        assertEquals(DoseStatus.TAKEN, store.doses().first { it.id == first.id }.status)
        assertEquals(tomorrow.copy(status = DoseStatus.SKIPPED, notifyAt = null), store.doses().first { it.id == tomorrow.id })
        // The expired reminder is silenced; all historical fields remain unchanged.
        assertEquals(past.copy(notifyAt = null), store.doses().first { it.id == past.id })
        assertEquals(3, store.doses().count { it.nameSnapshot == "New name" })
        assertEquals(0, store.schedules().first { it.id == first.scheduleId }.daysMask)
        assertTrue(store.doses().all { row -> store.schedules().any { it.id == row.scheduleId } })
    }
    @Test fun `editing same time preserves taken row instead of regenerating it`() = runTest {
        val id = create()
        val first = store.doses().first()
        repository.setTaken(first.id, true)
        repository.saveMedication("Changed", "", 0, listOf(480, 1200), 127, id)
        assertEquals(6, store.doses().size)
        assertEquals("Medication", store.doses().first { it.id == first.id }.nameSnapshot)
    }
    @Test fun `archive removes pending future but preserves history and silences overdue doses`() = runTest {
        val id = create()
        val completed = store.doses().first { it.minuteOfDay == 1200 && it.localDate == today }
        repository.setTaken(completed.id, true)
        repository.archiveMedication(id)
        assertTrue(store.medications().single().archived)
        assertEquals(2, store.doses().size)
        assertTrue(store.doses().all { it.notifyAt == null })
        repository.ensureDoses()
        assertEquals(2, store.doses().size)
    }
    @Test fun `marking pending clears taken timestamp and reinstates its reminder`() = runTest {
        create()
        val row = store.doses().first()
        repository.setTaken(row.id, true)
        assertEquals(testClock.millis(), store.doses().first { it.id == row.id }.takenAt)
        repository.setTaken(row.id, false)
        val pending = store.doses().first { it.id == row.id }
        assertEquals(DoseStatus.PENDING, pending.status)
        assertNull(pending.takenAt)
        assertEquals(pending.scheduledAt, pending.notifyAt)
    }
    @Test fun `expired reminder is cleared persistently while history row remains`() = runTest {
        create()
        val row = store.doses().first()
        store.updateDoses(listOf(row.copy(notifyAt = testClock.millis() - 13 * 3600000L)))
        repository.nextAlarm()
        assertNull(store.doses().first { it.id == row.id }.notifyAt)
        assertEquals(6, store.doses().size)
    }
    @Test fun `invalid input changes no data`() = runTest {
        create()
        val before = store.doses()
        try {
            repository.saveMedication("Invalid", "", 4, emptyList(), 127)
            fail("Expected input validation failure")
        } catch (_: IllegalArgumentException) { }
        assertEquals(before, store.doses())
        assertEquals(1, store.medications().size)
    }
    @Test fun `reusing retired time preserves existing completed dose`() = runTest {
        val id = create()
        val completed = store.doses().first()
        repository.setTaken(completed.id, true)
        repository.saveMedication("Changed", "", 4, listOf(540), 127, id)
        repository.saveMedication("Changed again", "", 4, listOf(480), 127, id)
        assertEquals(1, store.doses().count { it.localDate == today && it.minuteOfDay == 480 })
        assertEquals(DoseStatus.TAKEN, store.doses().first { it.id == completed.id }.status)
    }
}
