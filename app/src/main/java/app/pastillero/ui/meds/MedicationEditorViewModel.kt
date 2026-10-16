package app.pastillero.ui.meds

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pastillero.data.MedicationRepository
import kotlinx.coroutines.launch

/** Primitive saved state keeps an unfinished form across rotation and process recreation. */
class MedicationEditorViewModel(
    private val repository: MedicationRepository,
    private val medicationId: Long?,
    private val saved: SavedStateHandle,
) : ViewModel() {
    var name by mutableStateOf(saved.get<String>("name") ?: ""); private set
    var dose by mutableStateOf(saved.get<String>("dose") ?: ""); private set
    var minutes by mutableStateOf(saved.get<IntArray>("minutes")?.toList() ?: listOf(480)); private set
    var daysMask by mutableStateOf(saved.get<Int>("days") ?: 127); private set
    var colorIndex by mutableStateOf(saved.get<Int>("color") ?: 4); private set
    var nameError by mutableStateOf(false); private set
    var ready by mutableStateOf(medicationId == null); private set
    var busy by mutableStateOf(false); private set
    var finished by mutableStateOf(saved.get<Boolean>("finished") ?: false); private set
    var requestPermission by mutableStateOf(saved.get<Boolean>("permission") ?: false); private set

    init {
        if (medicationId != null) viewModelScope.launch {
            val existing = repository.findMedication(medicationId)
            if (existing == null) {
                finish(false)
            } else if (saved.get<Boolean>("initialized") != true) {
                name = existing.first.name
                dose = existing.first.dose
                colorIndex = existing.first.colorIndex
                minutes = existing.second.map { it.minuteOfDay }.distinct().sorted().ifEmpty { listOf(480) }
                daysMask = existing.second.fold(0) { mask, schedule -> mask or schedule.daysMask }.takeIf { it != 0 } ?: 127
                persist()
            }
            ready = true
        }
    }

    fun updateName(value: String) { name = value.take(40); nameError = false; persist() }
    fun updateDose(value: String) { dose = value.take(30); persist() }
    fun setColor(index: Int) { colorIndex = index; persist() }
    fun toggleDay(index: Int) {
        val updated = daysMask xor (1 shl index)
        if (updated != 0) { daysMask = updated; persist() }
    }
    fun setTime(original: Int?, minute: Int) {
        if (minute in minutes && minute != original) return
        minutes = (if (original == null) minutes + minute else minutes.map { if (it == original) minute else it }).distinct().sorted()
        persist()
    }
    fun removeTime(minute: Int) { if (minutes.size > 1) { minutes = minutes - minute; persist() } }

    fun save() {
        if (busy || !ready || finished) return
        if (name.trim().isEmpty()) { nameError = true; return }
        busy = true
        viewModelScope.launch {
            try {
                val first = !repository.hasAnyMedication()
                repository.saveMedication(name.trim(), dose, colorIndex, minutes, daysMask, medicationId)
                repository.nextAlarm()
                finish(first)
            } finally { busy = false }
        }
    }

    fun delete() {
        if (busy || !ready || medicationId == null || finished) return
        busy = true
        viewModelScope.launch {
            try {
                repository.archiveMedication(medicationId)
                repository.nextAlarm()
                finish(false)
            } finally { busy = false }
        }
    }

    private fun finish(permission: Boolean) {
        requestPermission = permission
        saved["permission"] = permission
        finished = true
        saved["finished"] = true
    }

    private fun persist() {
        saved["name"] = name
        saved["dose"] = dose
        saved["minutes"] = minutes.toIntArray()
        saved["days"] = daysMask
        saved["color"] = colorIndex
        saved["initialized"] = true
    }
}
