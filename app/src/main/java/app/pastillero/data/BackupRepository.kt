package app.pastillero.data

import java.time.ZoneId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class BackupRepository(
    private val store: MedicationStore,
    private val settings: SettingsRepository,
) {
    suspend fun exportJson(): String = store.transaction {
        BackupCodec.encode(BackupData(1, store.medications(), store.schedules(), store.doses(), settings.settings.first()))
    }

    suspend fun importJson(content: String) {
        // Decode and validate the entire graph before changing either persistent store.
        val backup = BackupCodec.decode(content)
        val previousSettings = settings.settings.first()
        var settingsWritten = false
        try {
            store.transaction {
                store.replaceAll(backup)
                settings.replace(backup.settings)
                settingsWritten = true
            }
        } catch (error: Throwable) {
            if (settingsWritten) withContext(NonCancellable) {
                try { settings.replace(previousSettings) } catch (restoreError: Throwable) {
                    error.addSuppressed(restoreError)
                }
            }
            throw error
        }
    }

    suspend fun exportHistory(zone: ZoneId): String = store.transaction {
        BackupCodec.historyCsv(store.doses(), zone)
    }
}
