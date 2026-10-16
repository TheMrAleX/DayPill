package app.pastillero.ui.meds

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pastillero.R
import app.pastillero.data.MedicationRepository
import app.pastillero.ui.components.SectionLabel
import app.pastillero.ui.components.formatTime
import app.pastillero.ui.theme.MedicationColors
import app.pastillero.ui.theme.TimeTextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditorScreen(repository: MedicationRepository, medicationId: Long?, onBack: () -> Unit) {
    val factory = remember(repository, medicationId) { viewModelFactory {
        initializer { MedicationEditorViewModel(repository, medicationId, createSavedStateHandle()) }
    } }
    val model: MedicationEditorViewModel = viewModel(factory = factory)
    val enabled = model.ready && !model.busy && !model.finished
    var deleteDialog by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var originalMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    var doseFocused by remember { mutableStateOf(false) }
    var permissionLaunched by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onBack() }
    LaunchedEffect(model.finished) {
        if (model.finished) {
            if (model.requestPermission && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (!permissionLaunched) {
                    permissionLaunched = true
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            } else onBack()
        }
    }
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                title = { Text(stringResource(if (medicationId == null) R.string.new_medication else R.string.edit_medication), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), Modifier.size(24.dp))
                } },
                actions = { if (medicationId != null) IconButton(onClick = { deleteDialog = true }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Delete, stringResource(R.string.delete), Modifier.size(24.dp))
                } })
        },
        bottomBar = {
            Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).padding(20.dp)) {
                Button(onClick = model::save, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(stringResource(R.string.save))
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            OutlinedTextField(value = model.name, onValueChange = model::updateName, enabled = enabled,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.name)) }, isError = model.nameError,
                supportingText = if (model.nameError) ({ Text(stringResource(R.string.name_required)) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            OutlinedTextField(value = model.dose, onValueChange = model::updateDose, enabled = enabled,
                modifier = Modifier.fillMaxWidth().onFocusChanged { doseFocused = it.isFocused }, singleLine = true,
                label = { Text(stringResource(R.string.dose)) },
                placeholder = if (doseFocused) ({ Text(stringResource(R.string.dose_placeholder)) }) else null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            Column {
                SectionLabel(R.string.times)
                model.minutes.forEach { minute ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f).heightIn(min = 56.dp).clickable(enabled = enabled) {
                            originalMinute = minute; showTimePicker = true
                        }, contentAlignment = Alignment.CenterStart) { Text(formatTime(minute), style = TimeTextStyle) }
                        if (model.minutes.size > 1) IconButton(onClick = { model.removeTime(minute) }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.Close, stringResource(R.string.remove_time, formatTime(minute)), Modifier.size(24.dp))
                        }
                    }
                }
                FilledTonalButton(onClick = { originalMinute = null; showTimePicker = true }, enabled = enabled) { Text(stringResource(R.string.add_time)) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(R.string.days)
                val letters = stringArrayResource(R.array.weekday_letters)
                val fullDays = stringArrayResource(R.array.weekday_names)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    letters.forEachIndexed { index, letter ->
                        val selected = model.daysMask and (1 shl index) != 0
                        Box(Modifier.size(44.dp).selectable(selected = selected, enabled = enabled, role = Role.Checkbox, onClick = { model.toggleDay(index) })
                            .semantics { contentDescription = fullDays[index] }
                            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)),
                            contentAlignment = Alignment.Center) {
                            Text(letter, style = MaterialTheme.typography.labelLarge, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(R.string.color)
                val names = stringArrayResource(R.array.color_names)
                val outline = MaterialTheme.colorScheme.onSurface
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MedicationColors.forEachIndexed { index, color ->
                        val selected = model.colorIndex == index
                        Box(Modifier.size(36.dp).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { model.setColor(index) })
                            .semantics { contentDescription = names[index] }.drawBehind {
                                if (selected) drawCircle(outline, radius = size.minDimension / 2 + 3.5.dp.toPx(), style = Stroke(3.dp.toPx()))
                            }.background(color, CircleShape), contentAlignment = Alignment.Center) {
                            if (selected) Icon(Icons.Default.Check, null, Modifier.size(16.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
    }
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false },
        title = { Text(stringResource(R.string.delete_medication)) },
        confirmButton = { TextButton(onClick = { deleteDialog = false; model.delete() }, enabled = enabled) {
            Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        } }, dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text(stringResource(R.string.cancel)) } })
    if (showTimePicker) MedicationTimePicker(originalMinute ?: 480, onDismiss = { showTimePicker = false }) { minute ->
        model.setTime(originalMinute, minute)
        showTimePicker = false
    }
}

/** Material 3 time picker in a dialog, with system 12/24-hour preference. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationTimePicker(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(20.dp).widthIn(max = 360.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(stringResource(R.string.save)) }
                }
            }
        }
    }
}
