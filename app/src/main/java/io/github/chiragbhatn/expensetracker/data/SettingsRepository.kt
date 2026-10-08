package io.github.chiragbhatn.expensetracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.chiragbhatn.expensetracker.backup.BackupWorkbook
import io.github.chiragbhatn.expensetracker.domain.AppSettings
import io.github.chiragbhatn.expensetracker.domain.Currency
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** One settings file per process, as DataStore requires. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App settings, plus bookkeeping such as which reminders were already shown. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = store.data.map { preferences ->
        val values = SETTING_KEYS.mapNotNull { key -> preferences[stringPreferencesKey(key)]?.let { key to it } }.toMap()
        AppSettings.fromMap(values).also { Currency.display = it.currency }
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { preferences ->
            val values = SETTING_KEYS.mapNotNull { key -> preferences[stringPreferencesKey(key)]?.let { key to it } }.toMap()
            val updated = transform(AppSettings.fromMap(values))
            updated.toMap().forEach { (key, value) -> preferences[stringPreferencesKey(key)] = value }
        }
    }

    /** Restores settings from a backup, except ones that belong to the device (such as the app lock). */
    suspend fun restore(values: Map<String, String>) {
        val safe = values.filterKeys { it in SETTING_KEYS && it !in BackupWorkbook.DEVICE_SETTINGS }
        if (safe.isEmpty()) return
        update { current -> AppSettings.fromMap(current.toMap() + safe) }
    }

    /** Reminder keys already shown, so each reminder is shown once. */
    suspend fun notifiedReminders(): Set<String> = store.data.first()[NOTIFIED].orEmpty()

    suspend fun markNotified(keys: Collection<String>) {
        if (keys.isEmpty()) return
        store.edit { preferences ->
            // Keep the most recent few hundred; old keys belong to past due dates.
            preferences[NOTIFIED] = (preferences[NOTIFIED].orEmpty() + keys).toList().takeLast(500).toSet()
        }
    }

    val lastBackupAt: Flow<Long?> = store.data.map { it[LAST_BACKUP] }

    suspend fun setLastBackupAt(millis: Long) {
        store.edit { it[LAST_BACKUP] = millis }
    }

    companion object {
        private val SETTING_KEYS = AppSettings().toMap().keys
        private val NOTIFIED = stringSetPreferencesKey("notified_reminders")
        private val LAST_BACKUP = longPreferencesKey("last_backup_at")
    }
}
