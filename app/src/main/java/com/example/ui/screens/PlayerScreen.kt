package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import android.os.Build
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.example.R
import com.example.lyrics.LrcParser
import com.example.lyrics.Romanizer
import com.example.ui.components.BeatVisualizerPanel
import com.example.model.LyricRecord
import com.example.model.LyricSyncStatus
import com.example.model.Song
import com.example.model.UserPreferences
import com.example.player.PlaybackUiState
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.ReviewCandidatesSheet

@Composable
fun PlayerScreen(
  playbackState: PlaybackUiState,
  lyricRecord: LyricRecord?,
  isFavorite: Boolean,
  preferences: UserPreferences,
  isLookingUpLyrics: Boolean,
  onNavigateBack: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onNext: () -> Unit,
  onPrevious: () -> Unit,
  onToggleShuffle: () -> Unit,
  onCycleRepeat: () -> Unit,
  onToggleFavorite: (Long) -> Unit,
  onOpenManualSearch: (Song) -> Unit,
  onApplyCandidate: (Long, com.example.model.LyricCandidate) -> Unit,
  onRequestAiSuggest: (Song, List<com.example.model.LyricCandidate>) -> Unit,
) {
  val song = playbackState.currentSong
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val ink = Color(0xFF0A0610)

  var showReviewSheet by remember { mutableStateOf(false) }
  var isDraggingSlider by remember { mutableStateOf(false) }
  var sliderDragFraction by remember { mutableFloatStateOf(0f) }
  // After a seek, keep showing the position the user chose until the player has caught up, so
  // the lyrics never flash back to the old line.
  var pendingSeekMs by remember { mutableStateOf<Long?>(null) }

  // Lyrics are cached exactly as received; the Latin-letter version is produced for display only.
  val romanize = preferences.romanizeLyrics
  val parsedLrc =
    remember(lyricRecord?.syncedLyricsLrc, romanize) {
      val parsed = LrcParser.parse(lyricRecord?.syncedLyricsLrc)
      if (romanize) {
        parsed.copy(lines = parsed.lines.map { it.copy(text = Romanizer.romanize(it.text)) })
      } else {
        parsed
      }
    }
  val plainLyricsText =
    remember(lyricRecord?.plainLyrics, romanize) {
      val plain = lyricRecord?.plainLyrics.orEmpty()
      if (romanize) Romanizer.romanize(plain) else plain
    }

  val pending = pendingSeekMs
  LaunchedEffect(pending, playbackState.positionMs) {
    if (pending != null && kotlin.math.abs(playbackState.positionMs - pending) < 1_500L) {
      pendingSeekMs = null
    }
  }
  LaunchedEffect(pending) {
    if (pending != null) {
      delay(1_500L)
      pendingSeekMs = null
    }
  }
  LaunchedEffect(song?.id) { pendingSeekMs = null }

  val effectivePositionMs =
    when {
      isDraggingSlider && playbackState.durationMs > 0L ->
        (sliderDragFraction * playbackState.durationMs).toLong()
      pending != null -> pending
      else -> playbackState.positionMs
    }

  val activeLineIndex =
    remember(parsedLrc, effectivePositionMs) {
      if (parsedLrc.hasValidTimestamps) {
        LrcParser.findActiveLineIndex(parsedLrc.lines, effectivePositionMs)
      } else {
        -1
      }
    }

  val lyricListState = rememberLazyListState()

  // isDraggingSlider is a key on purpose: while the thumb is held we don't scroll, and when it is
  // released we must scroll to the new line even if the line index ended up unchanged.
  LaunchedEffect(activeLineIndex, song?.id, isDraggingSlider, parsedLrc) {
    if (activeLineIndex >= 0 && parsedLrc.hasValidTimestamps && !isDraggingSlider) {
      val keepAbove = (lyricListState.layoutInfo.viewportSize.height * 0.28f).toInt()
      val far =
        kotlin.math.abs(activeLineIndex - lyricListState.firstVisibleItemIndex) > 5
      if (preferences.reducedMotion || far) {
        lyricListState.scrollToItem(activeLineIndex, -keepAbove)
      } else {
        lyricListState.animateScrollToItem(activeLineIndex, -keepAbove)
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize().background(ink).testTag("player_screen")) {
    // Backdrop: the album art, blurred (Android 12+), or a soft color wash on older phones.
    if (song?.albumArtUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      AsyncImage(
        model = song.albumArtUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.85f,
        modifier = Modifier.fillMaxSize().blur(80.dp),
      )
    } else {
      Box(
        Modifier.fillMaxSize()
          .background(
            Brush.linearGradient(
              listOf(primary.copy(alpha = 0.55f), secondary.copy(alpha = 0.35f), ink)
            )
          )
      )
    }
    Box(
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(ink.copy(alpha = 0.25f), ink.copy(alpha = 0.55f), ink.copy(alpha = 0.90f))
          )
        )
    )

    if (song == null) {
      Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = stringResource(R.string.player_no_song_selected),
          style = MaterialTheme.typography.titleLarge,
          color = Color.White,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onNavigateBack) { Text("Back") }
      }
      return@Box
    }

    Column(
      modifier =
        Modifier.fillMaxSize().systemBarsPadding().padding(start = 28.dp, end = 28.dp, bottom = 20.dp)
    ) {
      // Top: just a way back.
      Row(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onNavigateBack, modifier = Modifier.offset(x = (-12).dp)) {
          Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.cd_back),
            tint = Color.White,
            modifier = Modifier.size(30.dp),
          )
        }
      }

      Spacer(Modifier.height(8.dp))

      // Track identity.
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AlbumArtwork(
          artUri = song.albumArtUri,
          title = song.title,
          modifier = Modifier.size(56.dp),
          cornerRadius = 12.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = song.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.70f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(
          onClick = { onToggleFavorite(song.id) },
          modifier =
            Modifier.offset(x = 12.dp).minimumInteractiveComponentSize().testTag("player_favorite_button"),
        ) {
          Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(R.string.cd_favorite),
            tint = if (isFavorite) secondary else Color.White,
            modifier = Modifier.size(26.dp),
          )
        }
      }

      // Lyrics sit directly on the artwork: bold sans-serif, no card, fading at the edges.
      Box(modifier = Modifier.fillMaxWidth().weight(1f).testTag("player_lyrics_viewport")) {
        val lyricFont = FontFamily.SansSerif
        when {
          parsedLrc.hasValidTimestamps -> {
            LazyColumn(
              state = lyricListState,
              modifier =
                Modifier.fillMaxSize()
                  .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                  .drawWithContent {
                    drawContent()
                    drawRect(
                      brush =
                        Brush.verticalGradient(
                          0f to Color.Transparent,
                          0.14f to Color.Black,
                          0.86f to Color.Black,
                          1f to Color.Transparent,
                        ),
                      blendMode = BlendMode.DstIn,
                    )
                  },
              contentPadding = PaddingValues(end = 20.dp, top = 40.dp, bottom = 120.dp),
              verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
              itemsIndexed(
                parsedLrc.lines,
                key = { idx, line -> "${idx}_${line.timestampMs}" },
              ) { idx, line ->
                val isCurrent = idx == activeLineIndex
                val color by
                  animateColorAsState(
                    targetValue = if (isCurrent) Color.White else Color.White.copy(alpha = 0.38f),
                    animationSpec = tween(if (preferences.reducedMotion) 0 else 220),
                    label = "lyric_color",
                  )
                val scale by
                  animateFloatAsState(
                    targetValue = if (isCurrent && !preferences.reducedMotion) 1.12f else 1.0f,
                    animationSpec = tween(if (preferences.reducedMotion) 0 else 220),
                    label = "lyric_scale",
                  )
                Text(
                  text = line.text.ifBlank { "\u2022 \u2022 \u2022" },
                  fontFamily = lyricFont,
                  fontWeight = FontWeight.ExtraBold,
                  fontSize = (preferences.lyricsFontSizeSp + 4f).sp,
                  lineHeight = ((preferences.lyricsFontSizeSp + 4f) * 1.3f).sp,
                  color = color,
                  modifier =
                    Modifier.fillMaxWidth().graphicsLayer {
                      scaleX = scale
                      scaleY = scale
                      transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                )
              }
            }
          }

          !lyricRecord?.plainLyrics.isNullOrBlank() -> {
            Text(
              text = plainLyricsText,
              fontFamily = lyricFont,
              fontWeight = FontWeight.Bold,
              fontSize = preferences.lyricsFontSizeSp.sp,
              lineHeight = (preferences.lyricsFontSizeSp * 1.4f).sp,
              color = Color.White.copy(alpha = 0.92f),
              modifier =
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
            )
          }

          else -> {
            BeatVisualizerPanel(
              song = song,
              isPlaying = playbackState.isPlaying,
              reducedMotion = preferences.reducedMotion,
              accentFallback = primary,
              modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
            )
            if (lyricRecord?.status == LyricSyncStatus.NEEDS_REVIEW && lyricRecord.candidates.isNotEmpty()) {
              TextButton(
                onClick = { showReviewSheet = true },
                modifier = Modifier.align(Alignment.BottomCenter),
              ) {
                Text("Review matches", color = Color.White)
              }
            }
          }
        }
      }

      // Progress.
      val durationMs = playbackState.durationMs.coerceAtLeast(1L)
      val currentFraction =
        if (isDraggingSlider) sliderDragFraction
        else (effectivePositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

      Slider(
        value = currentFraction,
        onValueChange = { frac ->
          isDraggingSlider = true
          sliderDragFraction = frac
        },
        onValueChangeFinished = {
          val targetMs = (sliderDragFraction * durationMs).toLong()
          pendingSeekMs = targetMs
          isDraggingSlider = false
          onSeekTo(targetMs)
        },
        colors =
          SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.25f),
          ),
        modifier = Modifier.fillMaxWidth().offset(y = 4.dp).testTag("player_seek_slider"),
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = LrcParser.formatTime(effectivePositionMs),
          style = MaterialTheme.typography.labelMedium,
          color = Color.White.copy(alpha = 0.70f),
        )
        Text(
          text = LrcParser.formatTime(playbackState.durationMs),
          style = MaterialTheme.typography.labelMedium,
          color = Color.White.copy(alpha = 0.70f),
        )
      }

      Spacer(Modifier.height(12.dp))

      // Controls.
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onToggleShuffle,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("player_shuffle_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.cd_shuffle),
            tint = if (playbackState.shuffleEnabled) primary else Color.White.copy(alpha = 0.75f),
          )
        }

        IconButton(
          onClick = onPrevious,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("player_previous_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.cd_previous),
            tint = Color.White,
            modifier = Modifier.size(36.dp),
          )
        }

        Surface(
          onClick = onTogglePlayPause,
          shape = CircleShape,
          color = Color.White,
          contentColor = ink,
          modifier = Modifier.size(72.dp).testTag("player_play_pause_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector =
                if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription =
                stringResource(if (playbackState.isPlaying) R.string.cd_pause else R.string.cd_play),
              modifier = Modifier.size(36.dp),
            )
          }
        }

        IconButton(
          onClick = onNext,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("player_next_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.cd_next),
            tint = Color.White,
            modifier = Modifier.size(36.dp),
          )
        }

        IconButton(
          onClick = onCycleRepeat,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("player_repeat_button"),
        ) {
          Icon(
            imageVector =
              if (playbackState.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
              else Icons.Rounded.Repeat,
            contentDescription = stringResource(R.string.cd_repeat),
            tint =
              if (playbackState.repeatMode != Player.REPEAT_MODE_OFF) primary
              else Color.White.copy(alpha = 0.75f),
          )
        }
      }
    }
  }

  if (showReviewSheet && song != null && lyricRecord != null) {
    ReviewCandidatesSheet(
      song = song,
      candidates = lyricRecord.candidates,
      aiEnabled = preferences.aiFeaturesEnabled,
      onSelectCandidate = { cand ->
        onApplyCandidate(song.id, cand)
        showReviewSheet = false
      },
      onOpenManualSearch = { onOpenManualSearch(song) },
      onRequestAiSuggest = { onRequestAiSuggest(song, lyricRecord.candidates) },
      onDismiss = { showReviewSheet = false },
    )
  }
}
