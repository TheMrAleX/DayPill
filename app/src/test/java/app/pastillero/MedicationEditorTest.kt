package app.pastillero

import androidx.lifecycle.SavedStateHandle
import app.pastillero.data.MedicationRepository
import app.pastillero.ui.meds.MedicationEditorViewModel
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationEditorTest {
    private val store = MemoryStore()
    private val repository = MedicationRepository(store, testClock) { ZoneOffset.UTC }
    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun `blank name cannot create medication or finish editor`() = runTest {
        val editor = MedicationEditorViewModel(repository, null, SavedStateHandle())
        editor.updateName("   ")
        editor.save()
        assertTrue(editor.nameError)
        assertFalse(editor.finished)
        assertFalse(repository.hasAnyMedication())
    }
    @Test fun `unfinished form survives saved state restoration without resetting selected days`() {
        val state = SavedStateHandle()
        val editor = MedicationEditorViewModel(repository, null, state)
        editor.updateName("Medication")
        editor.updateDose("1 tablet")
        editor.setTime(480, 540)
        editor.toggleDay(1)
        editor.setColor(6)
        val restored = MedicationEditorViewModel(repository, null, state)
        assertEquals("Medication", restored.name)
        assertEquals("1 tablet", restored.dose)
        assertEquals(listOf(540), restored.minutes)
        assertEquals(125, restored.daysMask)
        assertEquals(6, restored.colorIndex)
    }
    @Test fun `time editing rejects duplicates and always retains one time`() {
        val editor = MedicationEditorViewModel(repository, null, SavedStateHandle())
        editor.setTime(null, 480)
        editor.setTime(null, 1200)
        editor.setTime(1200, 480)
        assertEquals(listOf(480, 1200), editor.minutes)
        editor.removeTime(480)
        editor.removeTime(1200)
        assertEquals(listOf(1200), editor.minutes)
    }
    @Test fun `last active day cannot be deselected`() {
        val editor = MedicationEditorViewModel(repository, null, SavedStateHandle())
        (1..6).forEach(editor::toggleDay)
        editor.toggleDay(0)
        assertEquals(1, editor.daysMask)
    }
    @Test fun `first save completes only after data and doses exist and requests permission once ever`() = runTest {
        val editor = MedicationEditorViewModel(repository, null, SavedStateHandle())
        editor.updateName("  First  ")
        editor.save()
        assertTrue(editor.finished)
        assertTrue(editor.requestPermission)
        assertEquals("First", store.medications().single().name)
        assertEquals(3, store.doses().size)
        repository.archiveMedication(store.medications().single().id)
        val second = MedicationEditorViewModel(repository, null, SavedStateHandle())
        second.updateName("Second")
        second.save()
        assertTrue(second.finished)
        assertFalse(second.requestPermission)
    }
    @Test fun `edit loads current hours and excludes retired schedules`() = runTest {
        val id = repository.saveMedication("Before", "", 2, listOf(480, 1200), 127)
        repository.saveMedication("After", "2 tablets", 3, listOf(540), 5, id)
        val editor = MedicationEditorViewModel(repository, id, SavedStateHandle())
        assertTrue(editor.ready)
        assertEquals("After", editor.name)
        assertEquals(listOf(540), editor.minutes)
        assertEquals(5, editor.daysMask)
        editor.updateName("Draft")
        val state = SavedStateHandle(mapOf("initialized" to true, "name" to "Restored", "minutes" to intArrayOf(600), "days" to 1))
        val restored = MedicationEditorViewModel(repository, id, state)
        assertEquals("Restored", restored.name)
        assertEquals(listOf(600), restored.minutes)
    }
}
