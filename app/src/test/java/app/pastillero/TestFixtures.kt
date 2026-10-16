package app.pastillero

import app.pastillero.data.BackupData
import app.pastillero.data.MedicationStore
import app.pastillero.data.SettingsRepository
import app.pastillero.domain.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

val today: LocalDate = LocalDate.of(2026, 10, 16)
val testClock: Clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC)
val testMedication = Medication(1, "Medication", "1 tablet", 4, false, 100)
val testSchedule = Schedule(1, 1, 480, 127)

fun dose(
    id: Long = 1,
    date: LocalDate = today,
    minute: Int = 480,
    status: DoseStatus = DoseStatus.PENDING,
    notifyAt: Long? = DoseGenerator.scheduledAt(date, minute, ZoneOffset.UTC),
    scheduleId: Long = 1,
): DoseLog = DoseLog(
    id, 1, scheduleId, "Original name", "Original dose", 4, date, minute,
    DoseGenerator.scheduledAt(date, minute, ZoneOffset.UTC), status,
    if (status == DoseStatus.TAKEN) testClock.millis() else null,
    if (status == DoseStatus.PENDING) notifyAt else null,
)

class MemorySettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    override val settings = MutableStateFlow(initial)
    var failWrites = false
    override suspend fun setRepeatMinutes(minutes: Int) = replace(settings.value.copy(repeatMinutes = minutes))
    override suspend fun setTheme(theme: AppTheme) = replace(settings.value.copy(theme = theme))
    override suspend fun replace(settings: AppSettings) {
        check(!failWrites) { "Simulated storage failure" }
        this.settings.value = settings
    }
}

class MemoryStore : MedicationStore {
    val medicationRows = MutableStateFlow<List<Medication>>(emptyList())
    val scheduleRows = MutableStateFlow<List<Schedule>>(emptyList())
    val doseRows = MutableStateFlow<List<DoseLog>>(emptyList())
    private val mutex = Mutex()
    var failCommit = false
    override suspend fun <T> transaction(block: suspend () -> T): T = mutex.withLock {
        val old = Triple(medicationRows.value, scheduleRows.value, doseRows.value)
        try {
            val result = block()
            check(!failCommit) { "Simulated commit failure" }
            result
        } catch (error: Throwable) {
            medicationRows.value = old.first
            scheduleRows.value = old.second
            doseRows.value = old.third
            throw error
        }
    }
    override suspend fun medications() = medicationRows.value
    override suspend fun schedules() = scheduleRows.value
    override suspend fun doses() = doseRows.value
    override val activeMedications = medicationRows.map { it.filterNot { row -> row.archived } }
    override val activeSchedules = scheduleRows.map { it.filter { row -> row.daysMask != 0 } }
    override val allDoses = doseRows
    override suspend fun putMedication(row: Medication): Long {
        val id = if (row.id == 0L) (medications().maxOfOrNull { it.id } ?: 0) + 1 else row.id
        medicationRows.value = medications().filterNot { it.id == id } + row.copy(id = id)
        return id
    }
    override suspend fun putSchedule(row: Schedule): Long {
        val id = if (row.id == 0L) (schedules().maxOfOrNull { it.id } ?: 0) + 1 else row.id
        scheduleRows.value = schedules().filterNot { it.id == id } + row.copy(id = id)
        return id
    }
    override suspend fun insertMissingDoses(rows: List<DoseLog>) {
        for (row in rows) {
            if (doses().any { it.scheduleId == row.scheduleId && it.localDate == row.localDate }) continue
            val id = if (row.id == 0L) (doses().maxOfOrNull { it.id } ?: 0) + 1 else row.id
            doseRows.value = doses() + row.copy(id = id)
        }
    }
    override suspend fun updateDoses(rows: List<DoseLog>) {
        val updates = rows.associateBy { it.id }
        doseRows.value = doses().map { updates[it.id] ?: it }
    }
    override suspend fun deleteDoses(ids: List<Long>) { doseRows.value = doses().filterNot { it.id in ids } }
    override suspend fun replaceAll(backup: BackupData) {
        medicationRows.value = backup.medications
        scheduleRows.value = backup.schedules
        doseRows.value = backup.doses
    }
}
