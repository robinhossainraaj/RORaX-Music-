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
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.R
import com.example.lyrics.LrcParser
import com.example.lyrics.Romanizer
import com.example.model.LyricRecord
import com.example.model.LyricSyncStatus
import com.example.model.Song
import com.example.model.UserPreferences
import com.example.player.PlaybackUiState
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.QueueBottomSheet
import com.example.ui.components.ReviewCandidatesSheet

@Composable
fun PlayerScreen(
  playbackState: PlaybackUiState,
  livePositionMs: () -> Long,
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
  onChangeSpeed: (Float) -> Unit,
  onToggleFavorite: (Long) -> Unit,
  onSelectQueueIndex: (Int) -> Unit,
  onRemoveQueueIndex: (Int) -> Unit,
  onFetchLyrics: (Song) -> Unit,
  onOpenManualSearch: (Song) -> Unit,
  onApplyCandidate: (Long, com.example.model.LyricCandidate) -> Unit,
  onImportLrc: (Song, Uri) -> Unit,
  onRequestAiExplain: (Song, String, List<String>) -> Unit,
  onRequestAiSuggest: (Song, List<com.example.model.LyricCandidate>) -> Unit,
) {
  val song = playbackState.currentSong
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val bg = MaterialTheme.colorScheme.background

  var showMenu by remember { mutableStateOf(false) }
  var showQueueSheet by remember { mutableStateOf(false) }
  var showReviewSheet by remember { mutableStateOf(false) }
  var isDraggingSlider by remember { mutableStateOf(false) }
  var sliderDragFraction by remember { mutableFloatStateOf(0f) }

  val lrcLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null && song != null) {
        onImportLrc(song, uri)
      }
    }

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

  val effectivePositionMs =
    if (isDraggingSlider && playbackState.durationMs > 0L) {
      (sliderDragFraction * playbackState.durationMs).toLong()
    } else {
      playbackState.positionMs
    }

  // The lyric clock reads the player's real position on every frame while the song plays, so the
  // highlighted line is never a poll behind the audio. Only the line number is observed, so the
  // screen redraws when the line changes, not 60 times a second.
  var lyricPositionMs by remember { mutableLongStateOf(playbackState.positionMs) }
  val isPlayingNow by rememberUpdatedState(playbackState.isPlaying)
  val durationNow by rememberUpdatedState(playbackState.durationMs)
  val lyricOffsetNow by rememberUpdatedState(preferences.lyricsOffsetMs)

  LaunchedEffect(song?.id) {
    while (isActive) {
      lyricPositionMs = livePositionMs()
      if (isPlayingNow) withFrameNanos { } else delay(100L)
    }
  }

  val activeLineIndex by
    remember(parsedLrc) {
      derivedStateOf {
        if (!parsedLrc.hasValidTimestamps) {
          -1
        } else {
          val positionMs =
            if (isDraggingSlider && durationNow > 0L) (sliderDragFraction * durationNow).toLong()
            else lyricPositionMs
          LrcParser.findActiveLineIndex(parsedLrc.lines, positionMs + lyricOffsetNow)
        }
      }
    }

  val lyricListState = rememberLazyListState()

  // Also runs when a drag ends, when the song changes and when the lyrics load, so the list never
  // stays scrolled to the wrong place after a seek or a track change.
  LaunchedEffect(activeLineIndex, song?.id, parsedLrc, isDraggingSlider) {
    if (!parsedLrc.hasValidTimestamps) return@LaunchedEffect
    val target = (activeLineIndex - 2).coerceAtLeast(0)
    val distance = kotlin.math.abs(target - lyricListState.firstVisibleItemIndex)
    if (preferences.reducedMotion || isDraggingSlider || distance > 6) {
      lyricListState.scrollToItem(target)
    } else {
      lyricListState.animateScrollToItem(target)
    }
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(
              primary.copy(alpha = 0.14f),
              bg,
              bg,
            )
          )
        )
        .systemBarsPadding()
        .testTag("player_screen")
  ) {
    if (song == null) {
      Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Text(
          text = stringResource(R.string.player_no_song_selected),
          style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNavigateBack) { Text("Back to Library") }
      }
      return@Box
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
      // 1. Top Bar
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("player_back_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.cd_back),
            modifier = Modifier.size(30.dp),
          )
        }
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "NOW PLAYING",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = song.album,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Box {
          IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("player_more_button"),
          ) {
            Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
          }
          DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.player_search_lyrics)) },
              leadingIcon = { Icon(Icons.Rounded.Search, null) },
              onClick = {
                showMenu = false
                onOpenManualSearch(song)
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.player_import_lrc)) },
              leadingIcon = { Icon(Icons.Rounded.FileOpen, null) },
              onClick = {
                showMenu = false
                lrcLauncher.launch(arrayOf("*/*"))
              },
            )
            DropdownMenuItem(
              text = { Text("Refresh lyrics") },
              leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
              onClick = {
                showMenu = false
                onFetchLyrics(song)
              },
            )
            if (preferences.aiFeaturesEnabled) {
              val activeSnippet =
                if (activeLineIndex in parsedLrc.lines.indices) {
                  parsedLrc.lines[activeLineIndex].text
                } else {
                  song.title
                }
              val contextSnippet =
                if (activeLineIndex >= 0) {
                  parsedLrc.lines
                    .subList(
                      (activeLineIndex - 1).coerceAtLeast(0),
                      (activeLineIndex + 2).coerceAtMost(parsedLrc.lines.size),
                    )
                    .map { it.text }
                } else {
                  emptyList()
                }
              DropdownMenuItem(
                text = { Text(stringResource(R.string.player_ai_explain)) },
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                onClick = {
                  showMenu = false
                  onRequestAiExplain(song, activeSnippet, contextSnippet)
                },
              )
            }
          }
        }
      }

      Spacer(Modifier.height(8.dp))

      // 2. Compact Track Identity Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AlbumArtwork(
          artUri = song.albumArtUri,
          title = song.title,
          modifier = Modifier.size(64.dp),
          cornerRadius = 14.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = song.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = song.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Spacer(Modifier.height(4.dp))
          val (statusLabel, statusTint) =
            when {
              isLookingUpLyrics -> "Looking up lyrics…" to primary
              lyricRecord?.status == LyricSyncStatus.CACHED_SYNCED ->
                stringResource(R.string.player_lyrics_synced) to primary
              lyricRecord?.status == LyricSyncStatus.CACHED_PLAIN ->
                stringResource(R.string.player_lyrics_plain) to secondary
              lyricRecord?.status == LyricSyncStatus.INSTRUMENTAL ->
                stringResource(R.string.player_lyrics_instrumental) to
                  MaterialTheme.colorScheme.onSurfaceVariant
              lyricRecord?.status == LyricSyncStatus.NEEDS_REVIEW -> "Review Matches" to secondary
              else -> stringResource(R.string.player_lyrics_none) to
                MaterialTheme.colorScheme.onSurfaceVariant
            }
          Surface(
            color = statusTint.copy(alpha = 0.16f),
            shape = RoundedCornerShape(8.dp),
            onClick = {
              if (lyricRecord?.status == LyricSyncStatus.NEEDS_REVIEW && lyricRecord.candidates.isNotEmpty()) {
                showReviewSheet = true
              }
            },
          ) {
            Text(
              text = statusLabel,
              style = MaterialTheme.typography.labelSmall,
              color = statusTint,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
        IconButton(
          onClick = { onToggleFavorite(song.id) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("player_favorite_button"),
        ) {
          Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(R.string.cd_favorite),
            tint = if (isFavorite) secondary else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(Modifier.height(14.dp))

      // 3. Hero Cinematic Lyrics Area (Primary Product Differentiator)
      Surface(
        modifier = Modifier.fillMaxWidth().weight(1f).testTag("player_lyrics_viewport"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        shape = RoundedCornerShape(24.dp),
      ) {
        when {
          parsedLrc.hasValidTimestamps -> {
            LazyColumn(
              state = lyricListState,
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 20.dp, vertical = 36.dp),
              verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
              itemsIndexed(
                parsedLrc.lines,
                key = { idx, line -> "${idx}_${line.timestampMs}" },
              ) { idx, line ->
                val isCurrent = idx == activeLineIndex
                val isPast = idx < activeLineIndex

                val targetColor =
                  when {
                    isCurrent -> MaterialTheme.colorScheme.onBackground
                    isPast -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f)
                  }
                val color by
                  animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = tween(if (preferences.reducedMotion) 0 else 120),
                    label = "lyric_color",
                  )
                val scale by
                  animateFloatAsState(
                    targetValue = if (isCurrent && !preferences.reducedMotion) 1.03f else 1.0f,
                    animationSpec = tween(if (preferences.reducedMotion) 0 else 120),
                    label = "lyric_scale",
                  )

                val displayText = line.text.ifBlank { "• • •" }
                Text(
                  text = displayText,
                  style =
                    MaterialTheme.typography.headlineMedium.copy(
                      fontSize = preferences.lyricsFontSizeSp.sp,
                      lineHeight = (preferences.lyricsFontSizeSp * 1.35f).sp,
                      fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    ),
                  color = color,
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(12.dp))
                      .clickable { onSeekTo(line.timestampMs) }
                      .padding(vertical = 4.dp, horizontal = 4.dp)
                      .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                      },
                )
              }
            }
          }

          !lyricRecord?.plainLyrics.isNullOrBlank() -> {
            Column(
              modifier =
                Modifier.fillMaxSize()
                  .verticalScroll(rememberScrollState())
                  .padding(20.dp),
            ) {
              Surface(
                color = secondary.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
              ) {
                Text(
                  text = stringResource(R.string.player_plain_notice),
                  style = MaterialTheme.typography.bodySmall,
                  color = secondary,
                  modifier = Modifier.padding(10.dp),
                )
              }
              Spacer(Modifier.height(16.dp))
              Text(
                text = plainLyricsText,
                style =
                  MaterialTheme.typography.bodyLarge.copy(
                    fontSize = (preferences.lyricsFontSizeSp * 0.85f).sp,
                    lineHeight = (preferences.lyricsFontSizeSp * 1.25f).sp,
                  ),
                color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }

          lyricRecord?.status == LyricSyncStatus.INSTRUMENTAL -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  Icons.Rounded.Lyrics,
                  contentDescription = null,
                  tint = primary,
                  modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text("Instrumental Track", style = MaterialTheme.typography.titleLarge)
                Text(
                  "This song has no lyrics.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }

          else -> {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  Icons.Rounded.Lyrics,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(42.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                  text = stringResource(R.string.player_no_lyrics_title),
                  style = MaterialTheme.typography.titleLarge,
                  textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                  text = stringResource(R.string.player_no_lyrics_body),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  Button(onClick = { onOpenManualSearch(song) }) {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_search_lyrics))
                  }
                  OutlinedButton(onClick = { lrcLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Rounded.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.player_import_lrc))
                  }
                }
              }
            }
          }
        }
      }

      Spacer(Modifier.height(12.dp))

      // 4. Seek Bar & Timestamps
      val durationMs = playbackState.durationMs.coerceAtLeast(1L)
      val currentFraction =
        if (isDraggingSlider) sliderDragFraction
        else (playbackState.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

      Slider(
        value = currentFraction,
        onValueChange = { frac ->
          isDraggingSlider = true
          sliderDragFraction = frac
        },
        onValueChangeFinished = {
          val targetMs = (sliderDragFraction * durationMs).toLong()
          isDraggingSlider = false
          onSeekTo(targetMs)
        },
        colors =
          SliderDefaults.colors(
            thumbColor = primary,
            activeTrackColor = primary,
            inactiveTrackColor = MaterialTheme.colorScheme.outline,
          ),
        modifier = Modifier.fillMaxWidth().testTag("player_seek_slider"),
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
          text = LrcParser.formatTime(effectivePositionMs),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = LrcParser.formatTime(playbackState.durationMs),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Spacer(Modifier.height(6.dp))

      // 5. Primary Playback Controls
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
            tint =
              if (playbackState.shuffleEnabled) primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        IconButton(
          onClick = onPrevious,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("player_previous_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.cd_previous),
            modifier = Modifier.size(34.dp),
          )
        }

        Surface(
          onClick = onTogglePlayPause,
          shape = CircleShape,
          color = primary,
          contentColor = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.size(64.dp).testTag("player_play_pause_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector =
                if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
              contentDescription =
                stringResource(
                  if (playbackState.isPlaying) R.string.cd_pause else R.string.cd_play
                ),
              modifier = Modifier.size(34.dp),
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
            modifier = Modifier.size(34.dp),
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
              else MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // 6. Secondary Utility Row (Speed + Queue)
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)
        val nextSpeed =
          speeds[(speeds.indexOfFirst { kotlin.math.abs(it - playbackState.playbackSpeed) < 0.05f } + 1) % speeds.size]

        Surface(
          onClick = { onChangeSpeed(nextSpeed) },
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
          Text(
            text = "${playbackState.playbackSpeed}x",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          )
        }

        Surface(
          onClick = { showQueueSheet = true },
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          modifier = Modifier.testTag("player_queue_button"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.AutoMirrored.Rounded.QueueMusic,
              contentDescription = stringResource(R.string.cd_queue),
              modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
              text = "Queue (${playbackState.queue.size})",
              style = MaterialTheme.typography.labelMedium,
            )
          }
        }
      }
    }
  }

  if (showQueueSheet) {
    QueueBottomSheet(
      queue = playbackState.queue,
      currentIndex = playbackState.currentIndex,
      onDismiss = { showQueueSheet = false },
      onSelectIndex = { idx -> onSelectQueueIndex(idx) },
      onRemoveIndex = { idx -> onRemoveQueueIndex(idx) },
    )
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
