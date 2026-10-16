package app.pastillero

import app.pastillero.data.*
import app.pastillero.domain.*
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ExportImportTest {
    private fun backup() = BackupData(1, listOf(testMedication), listOf(testSchedule), listOf(
        dose().copy(repeatCount = 2, notifyAt = testClock.millis() + 1000),
        dose(id = 2, date = today.minusDays(1), status = DoseStatus.TAKEN),
        dose(id = 3, date = today.minusDays(2), status = DoseStatus.SKIPPED),
    ), AppSettings(30, AppTheme.DARK))

    @Test fun `JSON round trip preserves every field including nulls and settings`() {
        val original = backup()
        assertEquals(original, BackupCodec.decode(BackupCodec.encode(original)))
        assertTrue(BackupCodec.encode(original).contains("2026-10-16"))
    }
    @Test fun `round trip supports archived medication and retired schedules`() {
        val value = backup().copy(medications = listOf(testMedication.copy(archived = true)),
            schedules = listOf(testSchedule.copy(daysMask = 0)))
        assertEquals(value, BackupCodec.decode(BackupCodec.encode(value)))
    }
    @Test fun `empty database round trips without sample data`() {
        val empty = BackupData(1, emptyList(), emptyList(), emptyList(), AppSettings())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }
    @Test fun `malformed unsupported and orphaned backups are rejected`() {
        val valid = BackupCodec.encode(backup())
        listOf("not json", "{}", valid.replace("\"formatVersion\": 1", "\"formatVersion\": 2"),
            valid.replace("\"medicationId\": 1", "\"medicationId\": 999"),
            valid.replace("\"repeatMinutes\": 30", "\"repeatMinutes\": 99"),
            valid.replace("2026-10-16", "2026-99-99"),
            valid.replace("\"colorIndex\": 4", "\"colorIndex\": 9"),
            valid.replace("\"status\": \"PENDING\"", "\"status\": \"UNKNOWN\""),
        ).forEach { content ->
            assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(content) }
        }
    }
    @Test fun `duplicate IDs and schedule date pairs are rejected`() {
        val value = backup()
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(value.copy(
            medications = value.medications + testMedication)) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(value.copy(
            doses = value.doses + dose(id = 99))) }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.validate(value.copy(
            schedules = value.schedules + testSchedule.copy(id = 2))) }
    }
    @Test fun `CSV quotes commas quotes and newlines and exports snapshots`() {
        val row = dose(status = DoseStatus.TAKEN).copy(nameSnapshot = "A, \"B\"", doseSnapshot = "line1\nline2")
        val csv = BackupCodec.historyCsv(listOf(row), ZoneOffset.UTC)
        assertTrue(csv.startsWith("fecha,hora,medicamento,dosis,estado,tomada_a\r\n"))
        assertTrue(csv.contains("2026-10-16,08:00,\"A, \"\"B\"\"\",\"line1\nline2\",TAKEN,2026-10-16T12:00:00Z\r\n"))
    }
    @Test fun `repository import replaces all rows and restores exported settings`() = runTest {
        val store = MemoryStore()
        val settings = MemorySettings()
        val repository = BackupRepository(store, settings)
        repository.importJson(BackupCodec.encode(backup()))
        assertEquals(backup(), BackupCodec.decode(repository.exportJson()))
        repository.importJson(BackupCodec.encode(BackupData(1, emptyList(), emptyList(), emptyList(), AppSettings())))
        assertTrue(store.medications().isEmpty())
        assertTrue(store.doses().isEmpty())
        assertEquals(AppSettings(), settings.settings.value)
    }
    @Test fun `invalid import leaves existing data untouched`() = runTest {
        val store = MemoryStore()
        val settings = MemorySettings()
        val repository = BackupRepository(store, settings)
        repository.importJson(BackupCodec.encode(backup()))
        val before = repository.exportJson()
        try { repository.importJson("{}"); fail("Expected validation failure") } catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.exportJson())
    }
    @Test fun `settings write failure rolls back database replacement`() = runTest {
        val store = MemoryStore()
        store.putMedication(testMedication)
        val settings = MemorySettings().apply { failWrites = true }
        try {
            BackupRepository(store, settings).importJson(BackupCodec.encode(backup()))
            fail("Expected settings failure")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf(testMedication), store.medications())
        assertTrue(store.schedules().isEmpty())
    }
    @Test fun `database commit failure restores previous settings`() = runTest {
        val store = MemoryStore().apply { failCommit = true }
        val settings = MemorySettings(AppSettings(5, AppTheme.LIGHT))
        try {
            BackupRepository(store, settings).importJson(BackupCodec.encode(backup()))
            fail("Expected transaction failure")
        } catch (_: IllegalStateException) { }
        assertEquals(AppSettings(5, AppTheme.LIGHT), settings.settings.value)
        assertTrue(store.medications().isEmpty())
    }
}
