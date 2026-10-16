package app.pastillero.data

import app.pastillero.domain.AppSettings
import app.pastillero.domain.DoseLog
import app.pastillero.domain.Medication
import app.pastillero.domain.Schedule
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val formatVersion: Int,
    val medications: List<Medication>,
    val schedules: List<Schedule>,
    val doses: List<DoseLog>,
    val settings: AppSettings,
)

object BackupCodec {
    private val json = Json { encodeDefaults = true; prettyPrint = true }

    fun encode(backup: BackupData): String {
        validate(backup)
        return json.encodeToString(backup)
    }

    fun decode(content: String): BackupData {
        val backup = try {
            json.decodeFromString<BackupData>(content)
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid backup", error)
        }
        validate(backup)
        return backup
    }

    fun validate(backup: BackupData) {
        require(backup.formatVersion == 1)
        fun uniqueIds(ids: List<Long>) { require(ids.all { it > 0 } && ids.size == ids.toSet().size) }
        uniqueIds(backup.medications.map { it.id })
        uniqueIds(backup.schedules.map { it.id })
        uniqueIds(backup.doses.map { it.id })
        val medications = backup.medications.associateBy { it.id }
        val schedules = backup.schedules.associateBy { it.id }
        require(backup.schedules.all { it.medicationId in medications })
        require(backup.doses.all {
            it.medicationId in medications && schedules[it.scheduleId]?.medicationId == it.medicationId
        })
        require(backup.doses.map { it.scheduleId to it.localDate }.toSet().size == backup.doses.size)
        require(backup.schedules.map { it.medicationId to it.minuteOfDay }.toSet().size == backup.schedules.size)
        val activeSchedules = backup.schedules.filter { it.daysMask != 0 }
        require(activeSchedules.map { it.medicationId to it.minuteOfDay }.toSet().size == activeSchedules.size)
        require(backup.medications.filterNot { it.archived }.all { medication ->
            val rows = activeSchedules.filter { it.medicationId == medication.id }
            rows.isNotEmpty() && rows.map { it.daysMask }.distinct().size == 1
        })
    }

    fun historyCsv(doses: List<DoseLog>, zone: ZoneId): String = buildString {
        append("fecha,hora,medicamento,dosis,estado,tomada_a\r\n")
        doses.sortedWith(compareBy<DoseLog> { it.localDate }.thenBy { it.minuteOfDay }.thenBy { it.id })
            .forEach { dose ->
                val hour = "%02d:%02d".format(java.util.Locale.ROOT, dose.minuteOfDay / 60, dose.minuteOfDay % 60)
                val taken = dose.takenAt?.let {
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(Instant.ofEpochMilli(it).atZone(zone))
                }.orEmpty()
                append(listOf(dose.localDate.toString(), hour, dose.nameSnapshot, dose.doseSnapshot,
                    dose.status.name, taken).joinToString(",") { csvField(it) })
                append("\r\n")
            }
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
