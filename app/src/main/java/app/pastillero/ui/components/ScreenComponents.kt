package app.pastillero.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pastillero.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

@Composable
fun currentTimeMillis(): Long {
    val ticks = remember {
        flow {
            while (true) {
                emit(System.currentTimeMillis())
                delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
    }
    return ticks.collectAsStateWithLifecycle(initialValue = System.currentTimeMillis()).value
}

fun formatTime(minute: Int): String = LocalTime.of(minute / 60, minute % 60)
    .format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))

@Composable
fun EmptyMedicationState(@StringRes title: Int, modifier: Modifier = Modifier, onAdd: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Icon(painterResource(R.drawable.ic_capsule), contentDescription = null,
            modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(title), style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onAdd != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAdd) { Text(stringResource(R.string.add_medication)) }
        }
    }
}

@Composable
fun SectionLabel(@StringRes title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
