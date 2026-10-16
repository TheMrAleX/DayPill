package app.pastillero.alarm

import app.pastillero.data.MedicationRepository
import app.pastillero.data.SettingsRepository
import app.pastillero.domain.DoseAction
import app.pastillero.domain.DoseLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface ReminderScheduler { fun schedule(triggerAt: Long?) }
interface ReminderNotifications {
    fun post(dose: DoseLog)
    fun cancel(id: Long)
    fun cancelExcept(visibleDoseIds: Set<Long>)
}

/** Serializes alarm delivery, notification actions and OS reconciliation in one process. */
class ReminderCoordinator(
    private val repository: MedicationRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
    private val notifications: ReminderNotifications,
) {
    private val mutex = Mutex()

    suspend fun reconcile() = mutex.withLock { reconcileInternal() }

    suspend fun onAlarm() = mutex.withLock {
        try {
            repository.deliverDue(settings.settings.first().repeatMinutes, notifications::post)
        } finally { reconcileInternal() }
    }

    suspend fun onAction(id: Long, action: DoseAction) = mutex.withLock {
        repository.applyNotificationAction(id, action, settings.settings.first().repeatMinutes)
        notifications.cancel(id)
        reconcileInternal()
    }

    private suspend fun reconcileInternal() {
        val snapshot = repository.reminderSnapshot()
        notifications.cancelExcept(snapshot.visibleDoseIds)
        scheduler.schedule(snapshot.triggerAt)
    }
}
