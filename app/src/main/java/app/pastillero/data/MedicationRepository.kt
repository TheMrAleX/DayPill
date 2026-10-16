package app.pastillero.data

import app.pastillero.domain.DoseGenerator
import app.pastillero.domain.DoseStatus
import app.pastillero.domain.DoseTiming
import app.pastillero.domain.Medication
import app.pastillero.domain.NextAlarm
import app.pastillero.domain.NextAlarmCalculator
import app.pastillero.domain.Schedule
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

class MedicationRepository(
    private val store: MedicationStore,
    private val clock: Clock,
    private val zoneProvider: () -> ZoneId,
) {
    val medications = store.activeMedications
    val schedules = store.activeSchedules
    val doses = store.allDoses

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
    }

    suspend fun archiveMedication(id: Long) = store.transaction {
        val medication = requireNotNull(store.medications().find { it.id == id })
        store.putMedication(medication.copy(archived = true))
        val pending = store.doses().filter { it.medicationId == id && it.status == DoseStatus.PENDING }
        store.deleteDoses(pending.filter { it.scheduledAt >= clock.millis() }.map { it.id })
        store.updateDoses(pending.filter { it.scheduledAt < clock.millis() }.map { it.copy(notifyAt = null) })
    }

    suspend fun ensureDoses(): NextAlarm? = store.transaction {
        ensureDosesInternal(today())
        expireRemindersInternal()
    }

    suspend fun nextAlarm(): NextAlarm? = store.transaction { expireRemindersInternal() }

    suspend fun onTimeChanged(): NextAlarm? = store.transaction {
        val rows = store.doses()
        val rebased = DoseTiming.rebaseFuture(rows, clock, zoneProvider())
        store.updateDoses(rebased.zip(rows).filter { (new, old) -> new != old }.map { it.first })
        ensureDosesInternal(today())
        expireRemindersInternal()
    }

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
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(zoneProvider()))

    private suspend fun ensureDosesInternal(today: LocalDate) {
        store.insertMissingDoses(DoseGenerator.ensureDoses(
            today, store.medications(), store.schedules(), store.doses(), zoneProvider(),
        ))
    }

    private suspend fun expireRemindersInternal(): NextAlarm? {
        val doses = store.doses()
        val result = NextAlarmCalculator.calculate(doses, clock)
        val expired = result.expiredDoseIds.toSet()
        store.updateDoses(doses.filter { it.id in expired }.map { it.copy(notifyAt = null) })
        return result.next
    }
}
