package app.pastillero.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object DoseGenerator {
    fun scheduledAt(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
        date.atTime(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
            .atZone(zone).withEarlierOffsetAtOverlap().toInstant().toEpochMilli()

    /** Returns only missing rows; the database unique index also protects concurrent callers. */
    fun ensureDoses(
        today: LocalDate,
        medications: List<Medication>,
        schedules: List<Schedule>,
        existing: List<DoseLog>,
        zone: ZoneId,
    ): List<DoseLog> {
        val active = medications.filterNot { it.archived }.associateBy { it.id }
        val keys = existing.mapTo(mutableSetOf()) { it.scheduleId to it.localDate }
        return buildList {
            for (offset in 0L..2L) {
                val date = today.plusDays(offset)
                val dayBit = 1 shl (date.dayOfWeek.value - 1)
                for (schedule in schedules) {
                    val medication = active[schedule.medicationId] ?: continue
                    if (schedule.daysMask and dayBit == 0) continue
                    if (!keys.add(schedule.id to date)) continue
                    add(DoseLog(
                        medicationId = medication.id,
                        scheduleId = schedule.id,
                        nameSnapshot = medication.name,
                        doseSnapshot = medication.dose,
                        colorIndexSnapshot = medication.colorIndex,
                        localDate = date,
                        minuteOfDay = schedule.minuteOfDay,
                        scheduledAt = scheduledAt(date, schedule.minuteOfDay, zone),
                    ))
                }
            }
        }
    }
}
