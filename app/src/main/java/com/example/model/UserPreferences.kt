package com.example.model

enum class ThemeMode(val label: String) {
  DARK("Obsidian Dark"),
  SYSTEM("Follow System"),
  LIGHT("Studio Light"),
}

enum class AccentPalette(
  val label: String,
  val primaryHex: Long,
  val secondaryHex: Long,
) {
  CYAN("Electric Cyan", 0xFF00E5C4, 0xFFFFB347),
  AMBER("Warm Amber", 0xFFFFB347, 0xFF00E5C4),
  VIOLET("Ultraviolet", 0xFF9B87F5, 0xFF38BDF8),
  CORAL("Crimson Pulse", 0xFFFF5E7E, 0xFFFFB347),
}

data class UserPreferences(
  val themeMode: ThemeMode = ThemeMode.DARK,
  val accentPalette: AccentPalette = AccentPalette.CYAN,
  val lyricsFontSizeSp: Float = 22f,
  val reducedMotion: Boolean = false,
  val autoLyricLookupOnPlay: Boolean = true,
  val wifiOnlyBulkDownloads: Boolean = true,
  val resumeLastSession: Boolean = true,
  val defaultPlaybackSpeed: Float = 1.0f,
  val aiFeaturesEnabled: Boolean = false,
  val lastPlayedSongId: Long? = null,
  val lastPositionMs: Long = 0L,
  val shuffleEnabled: Boolean = false,
  val repeatMode: Int = 0,
)
