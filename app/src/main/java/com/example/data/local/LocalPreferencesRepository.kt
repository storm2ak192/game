package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aljasoos_settings")

data class LocalAppSettings(
    val soundEffectsEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val defaultPlayerName: String = "",
    val defaultAvatarIndex: Int = 0,
    val offlineMatchesPlayed: Int = 0,
    val lastWinnerSummary: String = ""
)

class LocalPreferencesRepository(private val context: Context) {

    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val DEFAULT_NAME = stringPreferencesKey("default_player_name")
        val DEFAULT_AVATAR = intPreferencesKey("default_avatar_index")
        val OFFLINE_MATCHES_PLAYED = intPreferencesKey("offline_matches_played")
        val LAST_WINNER_SUMMARY = stringPreferencesKey("last_winner_summary")
    }

    val settingsFlow: Flow<LocalAppSettings> = context.applicationContext.dataStore.data.map { prefs ->
        LocalAppSettings(
            soundEffectsEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            defaultPlayerName = prefs[Keys.DEFAULT_NAME] ?: "",
            defaultAvatarIndex = prefs[Keys.DEFAULT_AVATAR] ?: 0,
            offlineMatchesPlayed = prefs[Keys.OFFLINE_MATCHES_PLAYED] ?: 0,
            lastWinnerSummary = prefs[Keys.LAST_WINNER_SUMMARY] ?: ""
        )
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.applicationContext.dataStore.edit { it[Keys.SOUND_ENABLED] = enabled }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.applicationContext.dataStore.edit { it[Keys.VIBRATION_ENABLED] = enabled }
    }

    suspend fun saveDefaultProfile(name: String, avatarIndex: Int) {
        context.applicationContext.dataStore.edit {
            it[Keys.DEFAULT_NAME] = name.trim().take(30)
            it[Keys.DEFAULT_AVATAR] = avatarIndex.coerceIn(0, 14)
        }
    }

    suspend fun recordOfflineMatchSummary(winnerNames: List<String>, topScore: Int) {
        context.applicationContext.dataStore.edit { prefs ->
            val prevCount = prefs[Keys.OFFLINE_MATCHES_PLAYED] ?: 0
            prefs[Keys.OFFLINE_MATCHES_PLAYED] = prevCount + 1
            val winnersText = winnerNames.joinToString(" و ")
            prefs[Keys.LAST_WINNER_SUMMARY] = "آخر فوز: $winnersText ($topScore نقطة)"
        }
    }
}
