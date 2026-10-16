package app.pastillero.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastillero.R
import app.pastillero.data.MedicationRepository
import app.pastillero.domain.HistoryPoint
import app.pastillero.domain.HistoryStats
import app.pastillero.ui.components.currentTimeMillis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(repository: MedicationRepository) {
    val doses by repository.doses.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = Instant.ofEpochMilli(currentTimeMillis()).atZone(ZoneId.systemDefault()).toLocalDate()
    val days = HistoryStats.days(doses, today)
    val percentage = HistoryStats.sevenDayPercentage(doses, today)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        var headerHeight by remember { mutableStateOf(0.dp) }
        val density = androidx.compose.ui.platform.LocalDensity.current
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp)) {
            item {
                Column(Modifier.onSizeChanged { headerHeight = with(density) { it.height.toDp() } }) {
                    Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(24.dp))
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(stringResource(R.string.seven_days), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(percentage?.let { stringResource(R.string.percentage, it) } ?: stringResource(R.string.no_percentage), style = MaterialTheme.typography.displaySmall)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (days.isEmpty()) item {
                Box(Modifier.fillMaxWidth().heightIn(min = (maxHeight - headerHeight - 40.dp).coerceAtLeast(120.dp)), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_history), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(days, key = { it.date }) { day ->
                Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(when (day.date) {
                            today -> stringResource(R.string.today)
                            today.minusDays(1) -> stringResource(R.string.yesterday)
                            else -> day.date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
                        }, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            day.points.forEach { point ->
                                val description = stringResource(when (point) {
                                    HistoryPoint.TAKEN -> R.string.status_taken
                                    HistoryPoint.PENDING -> R.string.status_pending
                                    HistoryPoint.MISSED -> R.string.status_missed
                                    HistoryPoint.SKIPPED -> R.string.status_skipped
                                })
                                Box(Modifier.size(10.dp).semantics { contentDescription = description }.then(
                                    if (point == HistoryPoint.TAKEN) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                    else Modifier.border(1.5.dp, if (point == HistoryPoint.PENDING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)))
                            }
                        }
                        Text(stringResource(R.string.dose_count, day.taken, day.total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
