package app.pastillero.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.pastillero.domain.DoseLog
import app.pastillero.domain.DoseStatus
import app.pastillero.domain.Medication
import app.pastillero.domain.Schedule
import java.time.LocalDate

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dose: String = "",
    val colorIndex: Int = 4,
    val archived: Boolean = false,
    val createdAt: Long,
)

fun MedicationEntity.toModel() = Medication(id, name, dose, colorIndex, archived, createdAt)
fun Medication.toEntity() = MedicationEntity(id, name, dose, colorIndex, archived, createdAt)

@Entity(
    tableName = "schedules",
    foreignKeys = [ForeignKey(
        entity = MedicationEntity::class, parentColumns = ["id"],
        childColumns = ["medicationId"], onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("medicationId")],
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val minuteOfDay: Int,
    val daysMask: Int = 127,
)

fun ScheduleEntity.toModel() = Schedule(id, medicationId, minuteOfDay, daysMask)
fun Schedule.toEntity() = ScheduleEntity(id, medicationId, minuteOfDay, daysMask)

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(entity = MedicationEntity::class, parentColumns = ["id"],
            childColumns = ["medicationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ScheduleEntity::class, parentColumns = ["id"],
            childColumns = ["scheduleId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("medicationId"), Index(value = ["scheduleId", "localDate"], unique = true)],
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long,
    val nameSnapshot: String,
    val doseSnapshot: String,
    val colorIndexSnapshot: Int,
    val localDate: LocalDate,
    val minuteOfDay: Int,
    val scheduledAt: Long,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: Long? = null,
    val notifyAt: Long? = scheduledAt,
    val repeatCount: Int = 0,
)

fun DoseLogEntity.toModel() = DoseLog(id, medicationId, scheduleId, nameSnapshot, doseSnapshot, colorIndexSnapshot, localDate, minuteOfDay, scheduledAt, status, takenAt, notifyAt, repeatCount)
fun DoseLog.toEntity() = DoseLogEntity(id, medicationId, scheduleId, nameSnapshot, doseSnapshot, colorIndexSnapshot, localDate, minuteOfDay, scheduledAt, status, takenAt, notifyAt, repeatCount)
