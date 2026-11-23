package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.lyrics.LrcParser
import com.example.model.LyricSyncStatus
import com.example.model.PlaylistWithSongs
import com.example.model.Song
import com.example.player.PlaybackUiState

@Composable
fun RoraxLogoMark(
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
  animated: Boolean = false,
) {
  val primary = MaterialTheme.colorScheme.primary
  val secondary = MaterialTheme.colorScheme.secondary
  val onSurface = MaterialTheme.colorScheme.onBackground

  val pulse =
    if (animated) {
      val transition = rememberInfiniteTransition(label = "logo_pulse")
      transition.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse_scale",
      ).value
    } else {
      1f
    }

  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val barWidth = w * 0.14f
    val gap = w * 0.08f
    val startX = (w - (barWidth * 4 + gap * 3)) / 2f
    val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

    val heights = listOf(0.46f, 0.84f * pulse, 0.56f, 0.42f * pulse)
    val colors = listOf(primary, onSurface, primary, onSurface)

    heights.forEachIndexed { idx, frac ->
      val bh = h * frac
      val x = startX + idx * (barWidth + gap)
      val y =
        if (idx == 2) {
          (h - bh) * 0.34f
        } else {
          (h - bh) / 2f
        }
      drawRoundRect(
        color = colors[idx],
        topLeft = Offset(x, y),
        size = Size(barWidth, bh),
        cornerRadius = radius,
      )
    }

    // Lyric beat dot under 3rd bar
    val dotX = startX + 2 * (barWidth + gap) + barWidth / 2f
    val dotY = h * 0.82f
    drawCircle(
      color = secondary,
      radius = barWidth * 0.55f,
      center = Offset(dotX, dotY),
    )
  }
}

@Composable
fun AlbumArtwork(
  artUri: Uri?,
  title: String,
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 12.dp,
  iconSize: Dp = 26.dp,
) {
  val context = LocalContext.current
  val primary = MaterialTheme.colorScheme.primary
  val container = MaterialTheme.colorScheme.surfaceContainerHigh

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(
          Brush.linearGradient(
            listOf(container, primary.copy(alpha = 0.22f))
          )
        ),
    contentAlignment = Alignment.Center,
  ) {
    if (artUri != null) {
      SubcomposeAsyncImage(
        model =
          ImageRequest.Builder(context)
            .data(artUri)
            .crossfade(true)
            .build(),
        contentDescription = stringResource(R.string.cd_album_art) + ": $title",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        error = {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = primary.copy(alpha = 0.85f),
            modifier = Modifier.size(iconSize),
          )
        },
        loading = {
          Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = primary.copy(alpha = 0.5f),
            modifier = Modifier.size(iconSize),
          )
        },
      )
    } else {
      Icon(
        imageVector = Icons.Rounded.MusicNote,
        contentDescription = null,
        tint = primary.copy(alpha = 0.85f),
        modifier = Modifier.size(iconSize),
      )
    }
  }
}

@Composable
fun LyricStatusBadge(status: LyricSyncStatus?, modifier: Modifier = Modifier) {
  val (label, tint) =
    when (status) {
      LyricSyncStatus.CACHED_SYNCED -> "SYNCED" to MaterialTheme.colorScheme.primary
      LyricSyncStatus.CACHED_PLAIN -> "TEXT" to MaterialTheme.colorScheme.secondary
      LyricSyncStatus.NEEDS_REVIEW -> "REVIEW" to MaterialTheme.colorScheme.secondary
      LyricSyncStatus.INSTRUMENTAL -> "INSTRUMENTAL" to MaterialTheme.colorScheme.onSurfaceVariant
      LyricSyncStatus.FAILED_RETRYABLE,
      LyricSyncStatus.FAILED_PERMANENT -> "RETRY" to MaterialTheme.colorScheme.error
      else -> return
    }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(6.dp),
    color = tint.copy(alpha = 0.15f),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = tint,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

@Composable
fun SongRowItem(
  song: Song,
  isPlayingCurrent: Boolean,
  isFavorite: Boolean,
  lyricStatus: LyricSyncStatus?,
  onClick: () -> Unit,
  onToggleFavorite: () -> Unit,
  onAddToPlaylist: () -> Unit,
  onFetchLyrics: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .clickable(onClick = onClick)
        .testTag("song_row_${song.id}"),
    color =
      if (isPlayingCurrent) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
      } else {
        Color.Transparent
      },
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      AlbumArtwork(
        artUri = song.albumArtUri,
        title = song.title,
        modifier = Modifier.size(50.dp),
        cornerRadius = 10.dp,
      )
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isPlayingCurrent) {
            Icon(
              imageVector = Icons.Rounded.GraphicEq,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(16.dp).padding(end = 4.dp),
            )
          }
          Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium,
            color =
              if (isPlayingCurrent) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Spacer(Modifier.height(2.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Text(
            text = "• ${LrcParser.formatTime(song.durationMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      IconButton(
        onClick = onToggleFavorite,
        modifier = Modifier.minimumInteractiveComponentSize(),
      ) {
        Icon(
          imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
          contentDescription = stringResource(R.string.cd_favorite),
          tint =
            if (isFavorite) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Box {
        IconButton(
          onClick = { menuExpanded = true },
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(
            imageVector = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.cd_more_options),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
          DropdownMenuItem(
            text = { Text("Add to playlist") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
            onClick = {
              menuExpanded = false
              onAddToPlaylist()
            },
          )
          DropdownMenuItem(
            text = { Text("Find lyrics") },
            leadingIcon = { Icon(Icons.Rounded.Lyrics, null) },
            onClick = {
              menuExpanded = false
              onFetchLyrics()
            },
          )
        }
      }
    }
  }
}

@Composable
fun MiniPlayerBar(
  playbackState: PlaybackUiState,
  onOpenPlayer: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onNext: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val song = playbackState.currentSong ?: return
  val progress =
    if (playbackState.durationMs > 0L) {
      (playbackState.positionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
      0f
    }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 6.dp)
        .clip(RoundedCornerShape(18.dp))
        .clickable(onClick = onOpenPlayer)
        .testTag("mini_player_bar"),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 6.dp,
    shadowElevation = 6.dp,
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        AlbumArtwork(
          artUri = song.albumArtUri,
          title = song.title,
          modifier = Modifier.size(44.dp),
          cornerRadius = 10.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(
          onClick = onTogglePlayPause,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_player_play_pause"),
        ) {
          Icon(
            imageVector =
              if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription =
              stringResource(
                if (playbackState.isPlaying) R.string.cd_pause else R.string.cd_play
              ),
            tint = MaterialTheme.colorScheme.primary,
          )
        }
        IconButton(
          onClick = onNext,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("mini_player_next"),
        ) {
          Icon(
            imageVector = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.cd_next),
          )
        }
      }
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(2.5.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
      )
    }
  }
}

@Composable
fun PermissionRequestCard(
  onRequestPermission: () -> Unit,
  onOpenSettings: () -> Unit,
  showSettingsFallback: Boolean,
  modifier: Modifier = Modifier,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    shape = RoundedCornerShape(20.dp),
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Rounded.MusicNote,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        }
        Spacer(Modifier.width(12.dp))
        Text(
          text = stringResource(R.string.permission_audio_title),
          style = MaterialTheme.typography.titleLarge,
        )
      }
      Spacer(Modifier.height(10.dp))
      Text(
        text = stringResource(R.string.permission_audio_rationale),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(16.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = onRequestPermission,
          modifier = Modifier.testTag("grant_audio_permission_button"),
        ) {
          Text(stringResource(R.string.permission_audio_grant))
        }
        if (showSettingsFallback) {
          OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.permission_audio_settings))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
  song: Song,
  playlists: List<PlaylistWithSongs>,
  onDismiss: () -> Unit,
  onCreatePlaylist: (String) -> Unit,
  onSelectPlaylist: (Long) -> Unit,
) {
  var newName by remember { mutableStateOf("") }
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
      Text(
        text = "Add \"${song.title}\" to Playlist",
        style = MaterialTheme.typography.titleLarge,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(12.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = newName,
          onValueChange = { newName = it },
          label = { Text("New playlist name") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        Button(
          onClick = {
            if (newName.isNotBlank()) {
              onCreatePlaylist(newName.trim())
              newName = ""
            }
          },
          enabled = newName.isNotBlank(),
        ) {
          Icon(Icons.Rounded.Add, null)
          Spacer(Modifier.width(4.dp))
          Text("Create")
        }
      }
      Spacer(Modifier.height(14.dp))
      if (playlists.isEmpty()) {
        Text(
          text = "No playlists yet",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(vertical = 16.dp),
        )
      } else {
        playlists.forEach { playlist ->
          Surface(
            modifier =
              Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  onSelectPlaylist(playlist.id)
                  onDismiss()
                },
            color = Color.Transparent,
          ) {
            Row(
              modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                Text(
                  "${playlist.songs.size} tracks",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}
