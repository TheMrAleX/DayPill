package app.pastillero.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import androidx.core.net.toUri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import app.pastillero.MainActivity
import app.pastillero.R
import app.pastillero.domain.DoseAction
import app.pastillero.domain.DoseLog
import app.pastillero.ui.components.formatTime
import app.pastillero.ui.theme.MedicationColors

class NotificationFactory(private val context: Context) : ReminderNotifications {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.doses_channel), NotificationManager.IMPORTANCE_HIGH).apply {
            enableVibration(true)
            enableLights(false)
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI, AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
        }
        manager.createNotificationChannel(channel)
    }

    override fun post(dose: DoseLog) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED || !manager.areNotificationsEnabled()) return
        val content = PendingIntent.getActivity(context, 0,
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_TODAY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capsule)
            .setContentTitle(context.getString(R.string.medication_time))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).build()
        val time = formatTime(dose.minuteOfDay)
        val body = if (dose.doseSnapshot.isEmpty()) time else "$time · ${dose.doseSnapshot}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_capsule)
            .setColor(MedicationColors[dose.colorIndexSnapshot].toArgb())
            .setContentTitle(dose.nameSnapshot).setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(false).setAutoCancel(false).setOngoing(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
            .setContentIntent(content)
            .addAction(0, context.getString(R.string.status_taken), actionIntent(dose.id, DoseAction.TAKEN))
            .addAction(0, context.getString(R.string.snooze), actionIntent(dose.id, DoseAction.SNOOZE))
            .build()
        try {
            manager.notify(dose.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the binder call.
        }
    }

    private fun actionIntent(id: Long, action: DoseAction): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, DoseActionReceiver::class.java).setAction(action.name)
            .setData("pastillero://dose/$id/${action.name}".toUri())
            .putExtra(EXTRA_DOSE_ID, id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    override fun cancel(id: Long) { manager.cancel(id.toInt()) }
    override fun cancelExcept(visibleDoseIds: Set<Long>) {
        val valid = visibleDoseIds.map { it.toInt() }.toSet()
        manager.activeNotifications.filter { it.notification.channelId == CHANNEL_ID && it.id !in valid }
            .forEach { manager.cancel(it.id) }
    }

    companion object {
        const val CHANNEL_ID = "doses"
        const val EXTRA_DOSE_ID = "dose_id"
        const val ACTION_OPEN_TODAY = "app.pastillero.OPEN_TODAY"
    }
}
