package app.pastillero.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmScheduler(private val context: Context) : ReminderScheduler {
    private val manager = context.getSystemService(AlarmManager::class.java)

    override fun schedule(triggerAt: Long?) {
        val intent = Intent(context, DoseAlarmReceiver::class.java).setAction(ACTION_ALARM)
        if (triggerAt == null) {
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                ?.let { manager.cancel(it) }
            return
        }
        if (!manager.canScheduleExactAlarms()) {
            Log.e("PastilleroAlarm", "USE_EXACT_ALARM is unavailable")
            return
        }
        val pending = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Reusing this exact identity atomically replaces the app's single pending alarm.
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
    }

    companion object { const val ACTION_ALARM = "app.pastillero.DOSE_ALARM" }
}
