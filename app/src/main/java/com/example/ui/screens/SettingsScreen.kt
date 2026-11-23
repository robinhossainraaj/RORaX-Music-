package com.example.ui.screens

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AccentPalette
import com.example.model.ThemeMode
import com.example.model.UserPreferences

@Composable
fun SettingsScreen(
  preferences: UserPreferences,
  songCount: Int,
  cacheSizeBytes: Long,
  hasAudioPermission: Boolean,
  onSetThemeMode: (ThemeMode) -> Unit,
  onSetAccentPalette: (AccentPalette) -> Unit,
  onSetLyricsFontSize: (Float) -> Unit,
  onSetLyricsOffset: (Int) -> Unit,
  onSetReducedMotion: (Boolean) -> Unit,
  onSetResumeSession: (Boolean) -> Unit,
  onSetDefaultSpeed: (Float) -> Unit,
  onSetAutoLookup: (Boolean) -> Unit,
  onSetRomanizeLyrics: (Boolean) -> Unit,
  onSetWifiOnly: (Boolean) -> Unit,
  onRescanLibrary: () -> Unit,
  onRequestOrOpenPermission: () -> Unit,
  onClearLyricCache: () -> Unit,
  onSetAiEnabled: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showClearCacheDialog by remember { mutableStateOf(false) }
  var showNoticesDialog by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier.fillMaxSize().testTag("settings_screen"),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    item {
      Text(
        text = stringResource(R.string.settings_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
    }

    // Appearance
    item {
      SettingsGroupCard(title = stringResource(R.string.settings_appearance)) {
        Text("Theme", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ThemeMode.entries.forEach { mode ->
            FilterChip(
              selected = preferences.themeMode == mode,
              onClick = { onSetThemeMode(mode) },
              label = { Text(mode.label) },
            )
          }
        }
        Spacer(Modifier.height(14.dp))
        Text("Accent Color", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          AccentPalette.entries.forEach { palette ->
            val selected = preferences.accentPalette == palette
            val swatch = Color(palette.primaryHex)
            Box(
              modifier =
                Modifier.size(40.dp)
                  .clip(CircleShape)
                  .background(swatch)
                  .then(
                    if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    else Modifier
                  )
                  .clickable { onSetAccentPalette(palette) },
              contentAlignment = Alignment.Center,
            ) {
              if (selected) {
                Icon(
                  Icons.Rounded.Check,
                  contentDescription = palette.label,
                  tint = Color(0xFF080A0F),
                  modifier = Modifier.size(20.dp),
                )
              }
            }
          }
        }
        Spacer(Modifier.height(14.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Lyrics Text Size", style = MaterialTheme.typography.labelLarge)
          Text(
            text = "${preferences.lyricsFontSizeSp.toInt()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = preferences.lyricsFontSizeSp,
          onValueChange = onSetLyricsFontSize,
          valueRange = 16f..34f,
        )
        Text(
          text = "Sample active lyric line",
          style = MaterialTheme.typography.titleMedium.copy(fontSize = preferences.lyricsFontSizeSp.sp),
          color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Lyrics Timing", style = MaterialTheme.typography.labelLarge)
          Text(
            text =
              when {
                preferences.lyricsOffsetMs > 0 -> "${preferences.lyricsOffsetMs} ms earlier"
                preferences.lyricsOffsetMs < 0 -> "${-preferences.lyricsOffsetMs} ms later"
                else -> "On time"
              },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = preferences.lyricsOffsetMs.toFloat(),
          onValueChange = { onSetLyricsOffset(it.toInt()) },
          valueRange = -500f..500f,
          steps = 19,
        )
        Text(
          text = "Lyrics late? Slide right. Lyrics early? Slide left. Bluetooth speakers and headphones often need a little extra.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        SettingsSwitchRow(
          title = "Reduced Motion",
          subtitle = "Minimize lyric transitions and logo pulse animations",
          checked = preferences.reducedMotion,
          onCheckedChange = onSetReducedMotion,
        )
      }
    }

    // Playback
    item {
      SettingsGroupCard(title = stringResource(R.string.settings_playback)) {
        SettingsSwitchRow(
          title = "Resume Last Session",
          subtitle = "Restore last track, position, shuffle, and repeat mode on launch",
          checked = preferences.resumeLastSession,
          onCheckedChange = onSetResumeSession,
        )
        Spacer(Modifier.height(12.dp))
        Text("Default Playback Speed", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { speed ->
            FilterChip(
              selected = kotlin.math.abs(preferences.defaultPlaybackSpeed - speed) < 0.05f,
              onClick = { onSetDefaultSpeed(speed) },
              label = { Text("${speed}x") },
            )
          }
        }
      }
    }

    // Lyrics
    item {
      SettingsGroupCard(title = stringResource(R.string.settings_lyrics)) {
        SettingsSwitchRow(
          title = "Find Missing Lyrics Automatically",
          subtitle = "Look up lyrics when you play a song that doesn't have any yet",
          checked = preferences.autoLyricLookupOnPlay,
          onCheckedChange = onSetAutoLookup,
        )
        Spacer(Modifier.height(12.dp))
        SettingsSwitchRow(
          title = "Show Lyrics in English Letters",
          subtitle = "Shows Bangla, Hindi and other languages in Latin letters, like \"Ami tomake bhalobashi\"",
          checked = preferences.romanizeLyrics,
          onCheckedChange = onSetRomanizeLyrics,
        )
        Spacer(Modifier.height(12.dp))
        SettingsSwitchRow(
          title = "Wi-Fi Only Downloads",
          subtitle = "Don't use mobile data to download lyrics",
          checked = preferences.wifiOnlyBulkDownloads,
          onCheckedChange = onSetWifiOnly,
        )
      }
    }

    // Library
    item {
      SettingsGroupCard(title = stringResource(R.string.settings_library)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Music Access", style = MaterialTheme.typography.labelLarge)
            Text(
              text =
                if (hasAudioPermission) {
                  "${stringResource(R.string.permission_status_granted)} • $songCount songs"
                } else {
                  stringResource(R.string.permission_status_denied)
                },
              style = MaterialTheme.typography.bodySmall,
              color =
                if (hasAudioPermission) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
          }
          OutlinedButton(onClick = onRequestOrOpenPermission) {
            Text(if (hasAudioPermission) "Settings" else "Grant")
          }
        }
        Spacer(Modifier.height(12.dp))
        Button(
          onClick = onRescanLibrary,
          enabled = hasAudioPermission,
          modifier = Modifier.fillMaxWidth().testTag("settings_rescan_button"),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text(stringResource(R.string.action_rescan_library))
        }
      }
    }

    // Storage
    item {
      SettingsGroupCard(title = stringResource(R.string.settings_storage)) {
        val formattedSize = Formatter.formatShortFileSize(context, cacheSizeBytes)
        Text("Lyrics saved on this device: $formattedSize", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(
          text = "Clearing lyrics never deletes your music, favorites or playlists.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
          onClick = { showClearCacheDialog = true },
          colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
          modifier = Modifier.fillMaxWidth().testTag("settings_clear_cache_button"),
        ) {
          Icon(Icons.Rounded.DeleteOutline, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Clear Downloaded Lyrics")
        }
      }
    }

    // About
    item {
      SettingsGroupCard(title = stringResource(R.string.settings_about)) {
        Text("RORaX Music • Version 1.0", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
          text = "Your music, with lyrics that move line by line.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { showNoticesDialog = true }) {
          Icon(Icons.Rounded.Info, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("Credits & Notices")
        }
      }
    }
  }

  if (showClearCacheDialog) {
    AlertDialog(
      onDismissRequest = { showClearCacheDialog = false },
      title = { Text(stringResource(R.string.settings_clear_cache_title)) },
      text = { Text(stringResource(R.string.settings_clear_cache_body)) },
      confirmButton = {
        Button(
          onClick = {
            onClearLyricCache()
            showClearCacheDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        ) {
          Text("Clear")
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearCacheDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showNoticesDialog) {
    AlertDialog(
      onDismissRequest = { showNoticesDialog = false },
      title = { Text("Credits & Notices") },
      text = {
        Text(
          "• Lyrics come from public online lyric services. They belong to their respective owners and are saved on your device for your personal offline use.\n" +
            "• Song titles and artist names are sent to these services to find the right lyrics. Your music files are never uploaded.\n" +
            "• RORaX Music is built with open-source Android libraries.",
          style = MaterialTheme.typography.bodySmall,
        )
      },
      confirmButton = {
        Button(onClick = { showNoticesDialog = false }) { Text("Close") }
      },
    )
  }
}

@Composable
private fun SettingsGroupCard(
  title: String,
  content: @Composable () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = RoundedCornerShape(20.dp),
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
      )
      Spacer(Modifier.height(12.dp))
      content()
    }
  }
}

@Composable
private fun SettingsSwitchRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.labelLarge)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}
