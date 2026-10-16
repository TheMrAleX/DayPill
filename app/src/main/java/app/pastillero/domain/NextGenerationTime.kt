package app.pastillero.domain

import java.time.LocalDate
import java.time.ZoneId

/** A weekly schedule can fall outside the persisted today+2 window. Wake at its actual time. */
object NextGenerationTime {
    fun find(today: LocalDate, zone: ZoneId, medications: List<Medication>, schedules: List<Schedule>, doses: List<DoseLog>): Long? {
        val active = medications.filterNot { it.archived }.map { it.id }.toSet()
        val existing = doses.map { it.scheduleId to it.localDate }.toSet()
        return (3L..9L).asSequence().map { today.plusDays(it) }.flatMap { date ->
            schedules.asSequence().filter {
                it.medicationId in active && it.daysMask and (1 shl (date.dayOfWeek.value - 1)) != 0 && (it.id to date) !in existing
            }.map { DoseGenerator.scheduledAt(date, it.minuteOfDay, zone) }
        }.minOrNull()
    }
}
