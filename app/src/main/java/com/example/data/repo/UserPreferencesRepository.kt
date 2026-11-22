package com.example.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AccentPalette
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.roraxPrefsDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "rorax_preferences")

class UserPreferencesRepository(private val context: Context) {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
    val LYRICS_FONT_SIZE = floatPreferencesKey("lyrics_font_size_sp")
    val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    val AUTO_LYRIC_LOOKUP = booleanPreferencesKey("auto_lyric_lookup_on_play")
    val ROMANIZE_LYRICS = booleanPreferencesKey("romanize_lyrics")
    val WIFI_ONLY_DOWNLOADS = booleanPreferencesKey("wifi_only_bulk_downloads")
    val RESUME_SESSION = booleanPreferencesKey("resume_last_session")
    val DEFAULT_SPEED = floatPreferencesKey("default_playback_speed")
    val AI_ENABLED = booleanPreferencesKey("ai_features_enabled")
    val LAST_SONG_ID = longPreferencesKey("last_played_song_id")
    val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
    val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
    val REPEAT_MODE = intPreferencesKey("repeat_mode")
  }

  val preferencesFlow: Flow<UserPreferences> =
    context.roraxPrefsDataStore.data.map { prefs ->
      UserPreferences(
        themeMode =
          prefs[Keys.THEME_MODE]?.let { name ->
            ThemeMode.entries.firstOrNull { it.name == name }
          } ?: ThemeMode.DARK,
        accentPalette =
          prefs[Keys.ACCENT_PALETTE]?.let { name ->
            AccentPalette.entries.firstOrNull { it.name == name }
          } ?: AccentPalette.CYAN,
        lyricsFontSizeSp = (prefs[Keys.LYRICS_FONT_SIZE] ?: 22f).coerceIn(16f, 34f),
        reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
        autoLyricLookupOnPlay = prefs[Keys.AUTO_LYRIC_LOOKUP] ?: true,
        romanizeLyrics = prefs[Keys.ROMANIZE_LYRICS] ?: true,
        wifiOnlyBulkDownloads = prefs[Keys.WIFI_ONLY_DOWNLOADS] ?: true,
        resumeLastSession = prefs[Keys.RESUME_SESSION] ?: true,
        defaultPlaybackSpeed = (prefs[Keys.DEFAULT_SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
        aiFeaturesEnabled = prefs[Keys.AI_ENABLED] ?: false,
        lastPlayedSongId = prefs[Keys.LAST_SONG_ID],
        lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L,
        shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
        repeatMode = prefs[Keys.REPEAT_MODE] ?: 0,
      )
    }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.roraxPrefsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
  }

  suspend fun setAccentPalette(palette: AccentPalette) {
    context.roraxPrefsDataStore.edit { it[Keys.ACCENT_PALETTE] = palette.name }
  }

  suspend fun setLyricsFontSize(sizeSp: Float) {
    context.roraxPrefsDataStore.edit { it[Keys.LYRICS_FONT_SIZE] = sizeSp.coerceIn(16f, 34f) }
  }

  suspend fun setReducedMotion(enabled: Boolean) {
    context.roraxPrefsDataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
  }

  suspend fun setAutoLyricLookup(enabled: Boolean) {
    context.roraxPrefsDataStore.edit { it[Keys.AUTO_LYRIC_LOOKUP] = enabled }
  }

  suspend fun setRomanizeLyrics(enabled: Boolean) {
    context.roraxPrefsDataStore.edit { it[Keys.ROMANIZE_LYRICS] = enabled }
  }

  suspend fun setWifiOnlyDownloads(enabled: Boolean) {
    context.roraxPrefsDataStore.edit { it[Keys.WIFI_ONLY_DOWNLOADS] = enabled }
  }

  suspend fun setResumeLastSession(enabled: Boolean) {
    context.roraxPrefsDataStore.edit { it[Keys.RESUME_SESSION] = enabled }
  }

  suspend fun setDefaultPlaybackSpeed(speed: Float) {
    context.roraxPrefsDataStore.edit { it[Keys.DEFAULT_SPEED] = speed.coerceIn(0.5f, 2.0f) }
  }

  suspend fun setAiFeaturesEnabled(enabled: Boolean) {
    context.roraxPrefsDataStore.edit { it[Keys.AI_ENABLED] = enabled }
  }

  suspend fun savePlaybackSession(
    songId: Long,
    positionMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
  ) {
    context.roraxPrefsDataStore.edit { prefs ->
      prefs[Keys.LAST_SONG_ID] = songId
      prefs[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
      prefs[Keys.SHUFFLE_ENABLED] = shuffleEnabled
      prefs[Keys.REPEAT_MODE] = repeatMode
    }
  }
}
