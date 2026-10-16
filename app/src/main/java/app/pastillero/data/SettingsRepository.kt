package app.pastillero.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.pastillero.domain.AppSettings
import app.pastillero.domain.AppTheme
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "settings")

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setRepeatMinutes(minutes: Int)
    suspend fun setTheme(theme: AppTheme)
    suspend fun replace(settings: AppSettings)
}

class PreferencesSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    private val repeatKey = intPreferencesKey("repeatMinutes")
    private val themeKey = stringPreferencesKey("theme")

    override val settings: Flow<AppSettings> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { preferences ->
        AppSettings(
            repeatMinutes = preferences[repeatKey]?.takeIf { it in setOf(5, 10, 15, 30) } ?: 10,
            theme = AppTheme.entries.firstOrNull { it.name == preferences[themeKey] } ?: AppTheme.SYSTEM,
        )
    }

    override suspend fun setRepeatMinutes(minutes: Int) {
        require(minutes in setOf(5, 10, 15, 30))
        store.edit { it[repeatKey] = minutes }
    }

    override suspend fun setTheme(theme: AppTheme) { store.edit { it[themeKey] = theme.name } }
    override suspend fun replace(settings: AppSettings) {
        store.edit {
            it[repeatKey] = settings.repeatMinutes
            it[themeKey] = settings.theme.name
        }
    }
}
