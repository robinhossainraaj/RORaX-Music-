package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.model.AccentPalette
import com.example.model.ThemeMode

private val RoraxShapes =
  Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
  )

@Composable
fun RoraxMusicTheme(
  themeMode: ThemeMode = ThemeMode.DARK,
  accentPalette: AccentPalette = AccentPalette.CYAN,
  content: @Composable () -> Unit,
) {
  val useDark =
    when (themeMode) {
      ThemeMode.DARK -> true
      ThemeMode.LIGHT -> false
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

  val primary = Color(accentPalette.primaryHex)
  val secondary = Color(accentPalette.secondaryHex)

  val colorScheme =
    if (useDark) {
      darkColorScheme(
        primary = primary,
        onPrimary = Color(0xFF061014),
        primaryContainer = primary.copy(alpha = 0.16f),
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = Color(0xFF141006),
        secondaryContainer = secondary.copy(alpha = 0.18f),
        onSecondaryContainer = secondary,
        tertiary = Ultraviolet,
        background = ObsidianBg,
        onBackground = ObsidianTextPrimary,
        surface = ObsidianSurface,
        onSurface = ObsidianTextPrimary,
        surfaceVariant = ObsidianSurfaceContainer,
        onSurfaceVariant = ObsidianTextSecondary,
        surfaceContainerLowest = ObsidianBg,
        surfaceContainerLow = ObsidianSurface,
        surfaceContainer = ObsidianSurfaceContainer,
        surfaceContainerHigh = ObsidianSurfaceHigh,
        surfaceContainerHighest = Color(0xFF262F42),
        outline = ObsidianOutline,
        outlineVariant = ObsidianOutline.copy(alpha = 0.5f),
        error = SemanticError,
        onError = Color(0xFF1E050A),
      )
    } else {
      val lightPrimary =
        when (accentPalette) {
          AccentPalette.CYAN -> Color(0xFF008F7A)
          AccentPalette.AMBER -> Color(0xFFB45309)
          AccentPalette.VIOLET -> Color(0xFF6D28D9)
          AccentPalette.CORAL -> Color(0xFFBE123C)
        }
      lightColorScheme(
        primary = lightPrimary,
        onPrimary = Color.White,
        primaryContainer = lightPrimary.copy(alpha = 0.12f),
        onPrimaryContainer = lightPrimary,
        secondary = secondary,
        onSecondary = Color(0xFF141006),
        background = StudioBg,
        onBackground = StudioTextPrimary,
        surface = StudioSurface,
        onSurface = StudioTextPrimary,
        surfaceVariant = StudioSurfaceContainer,
        onSurfaceVariant = StudioTextSecondary,
        surfaceContainerLowest = StudioSurface,
        surfaceContainerLow = StudioBg,
        surfaceContainer = StudioSurfaceContainer,
        surfaceContainerHigh = StudioSurfaceHigh,
        outline = StudioOutline,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
      )
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = RoraxTypography,
    shapes = RoraxShapes,
    content = content,
  )
}
