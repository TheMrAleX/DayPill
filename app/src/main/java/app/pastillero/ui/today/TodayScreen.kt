package app.pastillero.ui.today

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastillero.R
import app.pastillero.data.MedicationRepository
import app.pastillero.domain.DoseLog
import app.pastillero.domain.DoseStatus
import app.pastillero.ui.components.*
import app.pastillero.ui.theme.MedicationColors
import app.pastillero.ui.theme.TimeTextStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

@Composable
fun TodayScreen(repository: MedicationRepository, onSettings: () -> Unit, onAdd: () -> Unit) {
    val doses by repository.doses.collectAsStateWithLifecycle(initialValue = emptyList())
    val now = currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    LaunchedEffect(today, zone) { repository.ensureDoses() }
    val rows = doses.filter { it.localDate == today }.sortedWith(compareBy<DoseLog> { it.minuteOfDay }.thenBy { it.id })
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val locale = LocalConfiguration.current.locales[0]
    val density = LocalDensity.current
    var headerHeight by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val emptyHeight = (maxHeight - with(density) { headerHeight.toDp() } - 24.dp).coerceAtLeast(160.dp)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            item {
                Column(Modifier.onSizeChanged { headerHeight = it.height }.padding(top = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(today.format(DateTimeFormatter.ofPattern("EEEE", locale)),
                                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(today.format(DateTimeFormatter.ofPattern("d MMMM", locale)), style = MaterialTheme.typography.headlineMedium)
                        }
                        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Settings, stringResource(R.string.settings), Modifier.size(24.dp))
                        }
                    }
                    if (!notificationsEnabled) {
                        Spacer(Modifier.height(16.dp))
                        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                Text(stringResource(R.string.notifications_disabled), style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(onClick = {
                                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                                }) { Text(stringResource(R.string.enable_notifications), color = MaterialTheme.colorScheme.onErrorContainer) }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    ProgressCard(rows.count { it.status == DoseStatus.TAKEN }, rows.size)
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (rows.isEmpty()) {
                item { EmptyMedicationState(R.string.nothing_today, Modifier.heightIn(min = emptyHeight), onAdd) }
            }
            itemsIndexed(rows, key = { _, dose -> dose.id }) { index, dose ->
                DoseCard(dose, now) { taken -> repository.setTaken(dose.id, taken) }
                if (index < rows.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ProgressCard(taken: Int, total: Int) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if (total > 0 && taken == total) stringResource(R.string.all_done)
                else stringResource(R.string.dose_count, taken, total), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(progress = { if (total == 0) 0f else taken.toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                gapSize = 0.dp, drawStopIndicator = {})
        }
    }
}

@Composable
private fun DoseCard(dose: DoseLog, now: Long, onToggle: suspend (Boolean) -> Unit) {
    val taken = dose.status == DoseStatus.TAKEN
    val description = stringResource(when (dose.status) {
        DoseStatus.TAKEN -> R.string.status_taken
        DoseStatus.PENDING -> R.string.status_pending
        DoseStatus.SKIPPED -> R.string.status_skipped
    })
    val actionDescription = stringResource(if (taken) R.string.mark_pending else R.string.mark_taken)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    LaunchedEffect(taken) { if (taken) { scale.snapTo(.9f); scale.animateTo(1f, tween(150)) } }
    Surface(modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { stateDescription = description },
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box {
            Box(Modifier.align(Alignment.CenterStart).padding(start = 8.dp).width(4.dp).height(32.dp)
                .background(MedicationColors[dose.colorIndexSnapshot], RoundedCornerShape(2.dp)))
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(dose.minuteOfDay), style = TimeTextStyle,
                    color = if (!taken && dose.status == DoseStatus.PENDING && dose.scheduledAt < now) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(64.dp).alpha(if (taken) .6f else 1f))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f).alpha(if (taken) .6f else 1f)) {
                    Text(dose.nameSnapshot, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (dose.doseSnapshot.isNotEmpty()) Text(dose.doseSnapshot, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = {
                    if (!busy) scope.launch {
                        busy = true
                        try {
                            onToggle(!taken)
                            if (!taken) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        } finally { busy = false }
                    }
                }, enabled = !busy, modifier = Modifier.size(48.dp).semantics { contentDescription = actionDescription }.graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                    .then(if (taken) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier.border(2.dp, MaterialTheme.colorScheme.outline, CircleShape))) {
                    if (taken) Icon(Icons.Default.Check, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}
