package app.pastillero.data

import app.pastillero.domain.DoseLog
import app.pastillero.domain.Medication
import app.pastillero.domain.Schedule
import kotlinx.coroutines.flow.Flow

interface MedicationStore {
    suspend fun <T> transaction(block: suspend () -> T): T
    suspend fun medications(): List<Medication>
    suspend fun schedules(): List<Schedule>
    suspend fun doses(): List<DoseLog>
    val activeMedications: Flow<List<Medication>>
    val activeSchedules: Flow<List<Schedule>>
    val allDoses: Flow<List<DoseLog>>
    suspend fun putMedication(row: Medication): Long
    suspend fun putSchedule(row: Schedule): Long
    suspend fun insertMissingDoses(rows: List<DoseLog>)
    suspend fun updateDoses(rows: List<DoseLog>)
    suspend fun deleteDoses(ids: List<Long>)
    suspend fun replaceAll(backup: BackupData)
}
