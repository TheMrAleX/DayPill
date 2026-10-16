package app.pastillero.domain

import java.time.Duration

enum class DoseAction { TAKEN, SNOOZE }

object ReminderPolicy {
    fun afterNotification(dose: DoseLog, now: Long, repeatMinutes: Int): DoseLog {
        require(repeatMinutes in setOf(5, 10, 15, 30))
        require(dose.status == DoseStatus.PENDING)
        return if (dose.repeatCount < 3) dose.copy(
            repeatCount = dose.repeatCount + 1,
            notifyAt = now + Duration.ofMinutes(repeatMinutes.toLong()).toMillis(),
        ) else dose.copy(notifyAt = null)
    }

    fun applyAction(dose: DoseLog, action: DoseAction, now: Long, repeatMinutes: Int): DoseLog {
        require(repeatMinutes in setOf(5, 10, 15, 30))
        if (dose.status != DoseStatus.PENDING) return dose
        return when (action) {
            DoseAction.TAKEN -> dose.copy(status = DoseStatus.TAKEN, takenAt = now, notifyAt = null)
            DoseAction.SNOOZE -> dose.copy(notifyAt = now + Duration.ofMinutes(repeatMinutes.toLong()).toMillis())
        }
    }
}

data class ReminderSnapshot(val triggerAt: Long?, val visibleDoseIds: Set<Long>)
