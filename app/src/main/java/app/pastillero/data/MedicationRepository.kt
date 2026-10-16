package app.pastillero.data

import app.pastillero.domain.DoseAction
import app.pastillero.domain.DoseLog
import app.pastillero.domain.ReminderPolicy
import app.pastillero.domain.ReminderSnapshot
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import app.pastillero.domain.DoseGenerator
import app.pastillero.domain.DoseStatus
import app.pastillero.domain.DoseTiming
import app.pastillero.domain.Medication
import app.pastillero.domain.NextAlarm
import app.pastillero.domain.NextAlarmCalculator
import app.pastillero.domain.NextGenerationTime
import app.pastillero.domain.Schedule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class MedicationRepository(
    private val store: MedicationStore,
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId,
    private val onChanged: suspend () -> Unit = {},
) {
    val medications = store.activeMedications
    val schedules = store.activeSchedules
    val doses = store.allDoses

    suspend fun hasAnyMedication(): Boolean = store.transaction { store.medications().isNotEmpty() }

    suspend fun findMedication(id: Long): Pair<Medication, List<Schedule>>? = store.transaction {
        store.medications().find { it.id == id && !it.archived }?.let { medication ->
            medication to store.schedules().filter { it.medicationId == id && it.daysMask != 0 }
        }
    }

    suspend fun saveMedication(
        name: String,
        dose: String,
        colorIndex: Int,
        minutes: List<Int>,
        daysMask: Int,
        id: Long? = null,
    ): Long = store.transaction {
        require(minutes.isNotEmpty() && minutes.all { it in 0..1439 })
        require(daysMask in 1..127)
        val old = id?.let { medicationId ->
            requireNotNull(store.medications().find { it.id == medicationId && !it.archived })
        }
        val medicationId = store.putMedication(Medication(
            id = old?.id ?: 0,
            name = name.trim(), dose = dose, colorIndex = colorIndex,
            createdAt = old?.createdAt ?: clock.millis(),
        ))
        val previous = store.schedules().filter { it.medicationId == medicationId }
        val uniqueMinutes = minutes.distinct().sorted()
        // Retire removed schedules instead of cascading deletion of TAKEN/SKIPPED rows.
        previous.filter { it.minuteOfDay !in uniqueMinutes }.forEach {
            store.putSchedule(it.copy(daysMask = 0))
        }
        uniqueMinutes.forEach { minute ->
            val schedule = previous.firstOrNull { it.minuteOfDay == minute }
            store.putSchedule(Schedule(schedule?.id ?: 0, medicationId, minute, daysMask))
        }
        val today = today()
        store.deleteDoses(store.doses().filter {
            it.medicationId == medicationId && it.status == DoseStatus.PENDING && it.localDate >= today
        }.map { it.id })
        ensureDosesInternal(today)
        expireRemindersInternal()
        medicationId
    }.also { changed() }

    suspend fun archiveMedication(id: Long) = store.transaction {
        val medication = requireNotNull(store.medications().find { it.id == id })
        store.putMedication(medication.copy(archived = true))
        val pending = store.doses().filter { it.medicationId == id && it.status == DoseStatus.PENDING }
        store.deleteDoses(pending.filter { it.scheduledAt >= clock.millis() }.map { it.id })
        store.updateDoses(pending.filter { it.scheduledAt < clock.millis() }.map { it.copy(notifyAt = null) })
    }.also { changed() }

    suspend fun ensureDoses(): NextAlarm? = store.transaction {
        ensureDosesInternal(today())
        expireRemindersInternal()
    }.also { changed() }

    suspend fun nextAlarm(): NextAlarm? = store.transaction { expireRemindersInternal() }

    suspend fun onTimeChanged(): NextAlarm? = store.transaction {
        val rows = store.doses()
        val rebased = DoseTiming.rebaseFuture(rows, clock, zoneProvider())
        store.updateDoses(rebased.zip(rows).filter { (new, old) -> new != old }.map { it.first })
        ensureDosesInternal(today())
        expireRemindersInternal()
    }.also { changed() }

    suspend fun setTaken(id: Long, taken: Boolean): NextAlarm? = store.transaction {
        val row = requireNotNull(store.doses().find { it.id == id })
        val active = store.medications().any { it.id == row.medicationId && !it.archived }
        val updated = if (taken) {
            row.copy(status = DoseStatus.TAKEN, takenAt = clock.millis(), notifyAt = null)
        } else {
            row.copy(status = DoseStatus.PENDING, takenAt = null,
                notifyAt = if (active) row.scheduledAt else null)
        }
        store.updateDoses(listOf(updated))
        expireRemindersInternal()
    }.also { changed() }

    /** OS side effects are serialized by ReminderCoordinator; these methods do not recurse into it. */
    suspend fun deliverDue(repeatMinutes: Int, post: (DoseLog) -> Unit) = store.transaction {
        ensureDosesInternal(today())
        expireRemindersInternal()
        val active = store.medications().filterNot { it.archived }.map { it.id }.toSet()
        val now = clock.millis()
        store.doses().filter {
            it.medicationId in active && it.status == DoseStatus.PENDING && it.notifyAt?.let { time -> time <= now } == true
        }.sortedWith(compareBy<DoseLog> { it.notifyAt }.thenBy { it.id }).forEach { dose ->
            post(dose)
            store.updateDoses(listOf(ReminderPolicy.afterNotification(dose, now, repeatMinutes)))
        }
    }

    suspend fun applyNotificationAction(id: Long, action: DoseAction, repeatMinutes: Int) = store.transaction {
        val row = store.doses().find { it.id == id } ?: return@transaction
        val active = store.medications().any { it.id == row.medicationId && !it.archived }
        if (!active) return@transaction
        val updated = ReminderPolicy.applyAction(row, action, clock.millis(), repeatMinutes)
        if (updated != row) store.updateDoses(listOf(updated))
    }

    suspend fun reminderSnapshot(): ReminderSnapshot = store.transaction {
        val next = expireRemindersInternal()
        val active = store.medications().filterNot { it.archived }.map { it.id }.toSet()
        val generation = NextGenerationTime.find(today(), zoneProvider(), store.medications(), store.schedules(), store.doses())
        ReminderSnapshot(listOfNotNull(next?.triggerAt, generation).minOrNull(), store.doses().filter {
            it.status == DoseStatus.PENDING && it.medicationId in active
        }.map { it.id }.toSet())
    }

    private suspend fun changed() {
        // Once committed, finish OS reconciliation even if the editor leaves its composition.
        withContext(NonCancellable) { onChanged() }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zoneProvider()))

    private suspend fun ensureDosesInternal(today: LocalDate) {
        store.insertMissingDoses(DoseGenerator.ensureDoses(
            today, store.medications(), store.schedules(), store.doses(), zoneProvider(),
        ))
    }

    private suspend fun expireRemindersInternal(): NextAlarm? {
        val doses = store.doses()
        val active = store.medications().filterNot { it.archived }.map { it.id }.toSet()
        val result = NextAlarmCalculator.calculate(doses.filter { it.medicationId in active }, clock)
        val expired = result.expiredDoseIds.toSet()
        // Restored backups can contain archived pending rows with stale reminder timestamps.
        store.updateDoses(doses.filter {
            it.id in expired || (it.status == DoseStatus.PENDING && it.medicationId !in active && it.notifyAt != null)
        }.map { it.copy(notifyAt = null) })
        return result.next
    }
}
