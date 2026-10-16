package app.pastillero.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pastillero.AppContainer
import app.pastillero.PastilleroApp
import app.pastillero.domain.DoseAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmScheduler.ACTION_ALARM) runAsync(context) { it.reminders.onAlarm() }
    }
}

class DoseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = DoseAction.entries.firstOrNull { it.name == intent.action } ?: return
        val id = intent.getLongExtra(NotificationFactory.EXTRA_DOSE_ID, 0)
        if (id <= 0) return
        runAsync(context) { it.reminders.onAction(id, action) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> runAsync(context) { it.medications.onTimeChanged() }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> runAsync(context) { it.medications.ensureDoses() }
        }
    }
}

private fun BroadcastReceiver.runAsync(context: Context, work: suspend (AppContainer) -> Unit) {
    val pending = goAsync()
    val container = (context.applicationContext as PastilleroApp).container
    container.scope.launch {
        try {
            withTimeout(8_000) { work(container) }
        } catch (error: Exception) {
            Log.e("PastilleroReceiver", "Reminder broadcast could not complete", error)
        } finally { pending.finish() }
    }
}
