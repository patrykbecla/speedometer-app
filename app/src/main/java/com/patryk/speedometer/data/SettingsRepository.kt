package com.patryk.speedometer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val unitKey = stringPreferencesKey("speed_unit")
    private val intervalKey = longPreferencesKey("sampling_interval_ms")

    val unit: Flow<SpeedUnit> = context.dataStore.data.map { prefs ->
        prefs[unitKey]
            ?.let { runCatching { SpeedUnit.valueOf(it) }.getOrNull() }
            ?: SpeedUnit.KMH
    }

    /** Sampling interval in ms for recording. Default 1s. */
    val samplingIntervalMs: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[intervalKey] ?: 1_000L
    }

    suspend fun setUnit(unit: SpeedUnit) {
        context.dataStore.edit { it[unitKey] = unit.name }
    }

    suspend fun setSamplingInterval(ms: Long) {
        context.dataStore.edit { it[intervalKey] = ms }
    }
}
