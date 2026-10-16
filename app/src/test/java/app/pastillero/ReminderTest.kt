package app.pastillero

import app.pastillero.alarm.*
import app.pastillero.data.MedicationRepository
import app.pastillero.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReminderTest {
    private val store = MemoryStore()
    private val settings = MemorySettings()
    private val clock = MutableClock(testClock.millis())
    private val repository = MedicationRepository(store, clock, { ZoneOffset.UTC })
    private val notifications = FakeNotifications()
    private val scheduler = FakeScheduler()
    private val coordinator = ReminderCoordinator(repository, settings, scheduler, notifications)

    private suspend fun create(minutes: List<Int> = listOf(720)): Long = repository.saveMedication("Medication", "1 tablet", 4, minutes, 127)

    @Test fun `initial delivery and exactly three repeats then silence without losing pending history`() = runTest {
        create()
        repeat(4) { iteration ->
            coordinator.onAlarm()
            val row = store.doses().first { it.localDate == today }
            assertEquals(minOf(iteration + 1, 3), row.repeatCount)
            assertEquals(DoseStatus.PENDING, row.status)
            if (iteration < 3) {
                assertEquals(clock.millis() + 600000, row.notifyAt)
                clock.now += 600000
            } else assertNull(row.notifyAt)
        }
        assertEquals(4, notifications.posted.size)
        coordinator.onAlarm()
        assertEquals(4, notifications.posted.size)
        assertNotNull(scheduler.trigger) // tomorrow's original time remains scheduled
    }

    @Test fun `simultaneous doses post separately and duplicate alarm cannot consume another repeat`() = runTest {
        create()
        repository.saveMedication("Another", "", 0, listOf(720), 127)
        listOf(async { coordinator.onAlarm() }, async { coordinator.onAlarm() }).awaitAll()
        assertEquals(2, notifications.posted.size)
        assertEquals(2, notifications.posted.map { it.id }.distinct().size)
        assertTrue(store.doses().filter { it.localDate == today }.all { it.repeatCount == 1 })
    }

    @Test fun `snooze uses current repeat interval and preserves repeat count`() = runTest {
        create()
        coordinator.onAlarm()
        val row = notifications.posted.single()
        settings.setRepeatMinutes(5)
        coordinator.onAction(row.id, DoseAction.SNOOZE)
        val snoozed = store.doses().first { it.id == row.id }
        assertEquals(clock.millis() + 300000, snoozed.notifyAt)
        assertEquals(1, snoozed.repeatCount)
        assertFalse(row.id in notifications.visible)
        assertEquals(snoozed.notifyAt, scheduler.trigger)
    }

    @Test fun `taken action cancels notification and stale repeated actions cannot overwrite completion`() = runTest {
        create()
        coordinator.onAlarm()
        val id = notifications.posted.single().id
        coordinator.onAction(id, DoseAction.TAKEN)
        val completed = store.doses().first { it.id == id }
        assertEquals(clock.millis(), completed.takenAt)
        assertNull(completed.notifyAt)
        assertEquals(DoseStatus.TAKEN, completed.status)
        clock.now += 1000
        coordinator.onAction(id, DoseAction.TAKEN)
        coordinator.onAction(id, DoseAction.SNOOZE)
        assertEquals(completed, store.doses().first { it.id == id })
        assertFalse(id in notifications.visible)
    }

    @Test fun `expired reminder is never posted while a recent overdue reminder is delivered now`() = runTest {
        create(listOf(0, 660))
        clock.now += 1
        coordinator.onAlarm()
        assertEquals(listOf(660), notifications.posted.map { it.minuteOfDay })
        assertNull(store.doses().first { it.localDate == today && it.minuteOfDay == 0 }.notifyAt)
    }

    @Test fun `archiving cancels outstanding notifications and the last scheduled alarm`() = runTest {
        val id = create()
        coordinator.onAlarm()
        repository.archiveMedication(id)
        coordinator.reconcile()
        assertTrue(notifications.visible.isEmpty())
        assertNull(scheduler.trigger)
        val row = store.doses().firstOrNull { it.localDate == today }
        assertTrue(row == null || row.notifyAt == null)
    }

    @Test fun `late notification actions for deleted rows are harmless`() = runTest {
        coordinator.onAction(500, DoseAction.TAKEN)
        coordinator.onAction(500, DoseAction.SNOOZE)
        assertTrue(store.doses().isEmpty())
        assertNull(scheduler.trigger)
    }

    @Test fun `notification failure rolls back retry count so a dose is not silently consumed`() = runTest {
        create()
        try {
            repository.deliverDue(10) { error("Notification service unavailable") }
            fail("Expected publication failure")
        } catch (_: IllegalStateException) { }
        assertEquals(0, store.doses().first().repeatCount)
        coordinator.onAlarm()
        assertEquals(1, notifications.posted.size)
    }

    @Test fun `mutations reconcile only after a successful commit and without recursive callbacks`() = runTest {
        lateinit var managed: MedicationRepository
        var callbacks = 0
        managed = MedicationRepository(store, clock, { ZoneOffset.UTC }, onChanged = {
            callbacks++
            managed.reminderSnapshot() // a new transaction must be possible
        })
        val id = managed.saveMedication("Medication", "", 4, listOf(720), 127)
        managed.ensureDoses()
        managed.setTaken(store.doses().first().id, true)
        managed.onTimeChanged()
        managed.archiveMedication(id)
        assertEquals(5, callbacks)
        store.failCommit = true
        try { managed.saveMedication("Failed", "", 4, listOf(720), 127) } catch (_: IllegalStateException) { }
        assertEquals(5, callbacks)
    }

    @Test fun `weekly gap wakes at the next dose without persisting beyond today plus two`() = runTest {
        // Friday -> Monday, which is outside the initially generated window.
        repository.saveMedication("Weekly", "", 4, listOf(480), 1)
        assertTrue(store.doses().isEmpty())
        coordinator.reconcile()
        assertEquals(Instant.parse("2026-10-19T08:00:00Z").toEpochMilli(), scheduler.trigger)
        clock.now = scheduler.trigger!!
        coordinator.onAlarm()
        assertEquals(1, notifications.posted.size)
        coordinator.onAction(notifications.posted.single().id, DoseAction.TAKEN)
        assertEquals(Instant.parse("2026-10-26T08:00:00Z").toEpochMilli(), scheduler.trigger)
        assertEquals(1, store.doses().size)
    }

    @Test fun `time zone rebase reschedules pending doses and leaves completed snapshots untouched`() = runTest {
        var zone: ZoneId = ZoneOffset.UTC
        val repo = MedicationRepository(store, clock, { zone })
        repo.saveMedication("Medication", "", 4, listOf(960, 1200), 127)
        val completed = store.doses().first()
        repo.setTaken(completed.id, true)
        val before = store.doses().first { it.minuteOfDay == 1200 && it.localDate == today }
        zone = ZoneId.of("Europe/Madrid")
        repo.onTimeChanged()
        val after = store.doses().first { it.id == before.id }
        assertEquals(before.scheduledAt - 7200000, after.scheduledAt)
        assertEquals(after.scheduledAt, after.notifyAt)
        assertEquals(completed.scheduledAt, store.doses().first { it.id == completed.id }.scheduledAt)
    }
}

private class FakeScheduler : ReminderScheduler {
    var trigger: Long? = null
    override fun schedule(triggerAt: Long?) { trigger = triggerAt }
}
private class FakeNotifications : ReminderNotifications {
    val posted = mutableListOf<DoseLog>()
    val visible = mutableSetOf<Long>()
    override fun post(dose: DoseLog) { posted += dose; visible += dose.id }
    override fun cancel(id: Long) { visible -= id }
    override fun cancelExcept(visibleDoseIds: Set<Long>) { visible.retainAll(visibleDoseIds) }
}
private class MutableClock(var now: Long) : Clock() {
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
}
