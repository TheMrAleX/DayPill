package app.pastillero.ui.meds

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastillero.R
import app.pastillero.data.MedicationRepository
import app.pastillero.ui.components.EmptyMedicationState
import app.pastillero.ui.components.formatTime
import app.pastillero.ui.theme.MedicationColors

@Composable
fun MedicationsScreen(repository: MedicationRepository, onEdit: (Long) -> Unit) {
    val medications by repository.medications.collectAsStateWithLifecycle(initialValue = emptyList())
    val schedules by repository.schedules.collectAsStateWithLifecycle(initialValue = emptyList())
    val days = stringArrayResource(R.array.weekday_letters)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val emptyHeight = (maxHeight - 96.dp).coerceAtLeast(160.dp)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 88.dp)) {
            item {
                Text(stringResource(R.string.medications), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(24.dp))
            }
            if (medications.isEmpty()) item { EmptyMedicationState(R.string.no_medications, Modifier.heightIn(min = emptyHeight)) }
            items(medications, key = { it.id }) { medication ->
                val times = schedules.filter { it.medicationId == medication.id }.sortedBy { it.minuteOfDay }
                val mask = times.fold(0) { mask, schedule -> mask or schedule.daysMask }
                val subtitle = times.joinToString(" · ") { formatTime(it.minuteOfDay) } +
                    if (mask != 127) " · " + days.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.joinToString(" ") else ""
                Surface(onClick = { onEdit(medication.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).background(MedicationColors[medication.colorIndex], CircleShape))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(medication.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
