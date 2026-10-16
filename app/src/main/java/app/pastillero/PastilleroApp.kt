package app.pastillero

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.pastillero.alarm.AlarmScheduler
import app.pastillero.alarm.NotificationFactory
import app.pastillero.alarm.ReminderCoordinator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import app.pastillero.data.BackupRepository
import app.pastillero.data.MedicationRepository
import app.pastillero.data.PastilleroDatabase
import app.pastillero.data.PreferencesSettingsRepository
import app.pastillero.data.RoomMedicationStore
import app.pastillero.data.settingsDataStore
import java.time.Clock
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PastilleroApp : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
    override fun onCreate() {
        super.onCreate()
        container.notifications.createChannel()
        container.observeSettings()
    }
}

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.databaseBuilder(
        context.applicationContext, PastilleroDatabase::class.java, "pastillero.db",
    ).build()
    private val store = RoomMedicationStore(database)
    val settings = PreferencesSettingsRepository(context.applicationContext.settingsDataStore)
    val notifications = NotificationFactory(context.applicationContext)
    private val scheduler = AlarmScheduler(context.applicationContext)
    val medications: MedicationRepository = MedicationRepository(store, Clock.systemUTC(), ZoneId::systemDefault,
        onChanged = { reminders.reconcile() })
    val reminders: ReminderCoordinator by lazy { ReminderCoordinator(medications, settings, scheduler, notifications) }
    val backups = BackupRepository(store, settings, onChanged = { medications.ensureDoses() })

    fun observeSettings() {
        scope.launch { settings.settings.distinctUntilChanged().collect { reminders.reconcile() } }
    }
}
