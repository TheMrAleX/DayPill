package app.pastillero.data

import androidx.room.withTransaction
import app.pastillero.domain.DoseLog
import app.pastillero.domain.Medication
import app.pastillero.domain.Schedule
import kotlinx.coroutines.flow.map

class RoomMedicationStore(private val database: PastilleroDatabase) : MedicationStore {
    private val dao = database.medicationDao()
    override suspend fun <T> transaction(block: suspend () -> T): T = database.withTransaction { block() }
    override suspend fun medications() = dao.medications().map { it.toModel() }
    override suspend fun schedules() = dao.schedules().map { it.toModel() }
    override suspend fun doses() = dao.doses().map { it.toModel() }
    override val activeMedications = dao.observeMedications().map { rows -> rows.map { it.toModel() } }
    override val activeSchedules = dao.observeSchedules().map { rows -> rows.map { it.toModel() } }
    override val allDoses = dao.observeDoses().map { rows -> rows.map { it.toModel() } }

    override suspend fun putMedication(row: Medication): Long {
        if (row.id == 0L) return dao.insertMedication(row.toEntity())
        dao.updateMedication(row.toEntity())
        return row.id
    }

    override suspend fun putSchedule(row: Schedule): Long {
        if (row.id == 0L) return dao.insertSchedule(row.toEntity())
        dao.updateSchedule(row.toEntity())
        return row.id
    }

    override suspend fun insertMissingDoses(rows: List<DoseLog>) = dao.insertMissingDoses(rows.map { it.toEntity() })
    override suspend fun updateDoses(rows: List<DoseLog>) = dao.updateDoses(rows.map { it.toEntity() })
    override suspend fun deleteDoses(ids: List<Long>) = dao.deleteDoses(ids)

    override suspend fun replaceAll(backup: BackupData) {
        dao.clearDoses()
        dao.clearSchedules()
        dao.clearMedications()
        dao.restoreMedications(backup.medications.map { it.toEntity() })
        dao.restoreSchedules(backup.schedules.map { it.toEntity() })
        dao.restoreDoses(backup.doses.map { it.toEntity() })
    }
}
