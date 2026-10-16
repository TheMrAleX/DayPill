package app.pastillero.domain

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

object DoseTiming {
    fun rebaseFuture(doses: List<DoseLog>, clock: Clock, zone: ZoneId): List<DoseLog> {
        val today = LocalDate.now(clock.withZone(zone))
        return doses.map { dose ->
            if (dose.status != DoseStatus.PENDING || dose.localDate < today) return@map dose
            val scheduledAt = DoseGenerator.scheduledAt(dose.localDate, dose.minuteOfDay, zone)
            if (dose.scheduledAt < clock.millis() && scheduledAt < clock.millis()) return@map dose
            dose.copy(
                scheduledAt = scheduledAt,
                // A snooze is an elapsed-time promise; only an unsnoozed reminder follows wall time.
                notifyAt = if (dose.notifyAt == dose.scheduledAt) scheduledAt else dose.notifyAt,
            )
        }
    }
}
