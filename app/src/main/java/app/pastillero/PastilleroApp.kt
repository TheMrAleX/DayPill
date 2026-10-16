package app.pastillero

import android.app.Application
import android.content.Context
import androidx.room.Room
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
}

class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.databaseBuilder(
        context.applicationContext, PastilleroDatabase::class.java, "pastillero.db",
    ).build()
    private val store = RoomMedicationStore(database)
    val settings = PreferencesSettingsRepository(context.applicationContext.settingsDataStore)
    val medications = MedicationRepository(store, Clock.systemUTC(), ZoneId::systemDefault)
    val backups = BackupRepository(store, settings)
}
