package app.pastillero.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY createdAt, id")
    suspend fun medications(): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE archived = 0 ORDER BY createdAt, id")
    fun observeMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM schedules ORDER BY minuteOfDay, id")
    suspend fun schedules(): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE daysMask != 0 ORDER BY minuteOfDay, id")
    fun observeSchedules(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM dose_logs ORDER BY localDate, minuteOfDay, id")
    suspend fun doses(): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs ORDER BY localDate, minuteOfDay, id")
    fun observeDoses(): Flow<List<DoseLogEntity>>

    @Insert suspend fun insertMedication(row: MedicationEntity): Long
    @Update suspend fun updateMedication(row: MedicationEntity)
    @Insert suspend fun insertSchedule(row: ScheduleEntity): Long
    @Update suspend fun updateSchedule(row: ScheduleEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMissingDoses(rows: List<DoseLogEntity>)
    @Update suspend fun updateDoses(rows: List<DoseLogEntity>)

    @Query("DELETE FROM dose_logs WHERE id IN (:ids)")
    suspend fun deleteDoses(ids: List<Long>)

    @Query("DELETE FROM dose_logs") suspend fun clearDoses()
    @Query("DELETE FROM schedules") suspend fun clearSchedules()
    @Query("DELETE FROM medications") suspend fun clearMedications()
    @Insert suspend fun restoreMedications(rows: List<MedicationEntity>)
    @Insert suspend fun restoreSchedules(rows: List<ScheduleEntity>)
    @Insert suspend fun restoreDoses(rows: List<DoseLogEntity>)
}
