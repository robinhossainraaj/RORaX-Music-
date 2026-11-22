package com.example.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.repo.LocalMusicRepository
import com.example.data.repo.UserPreferencesRepository
import com.example.model.Song
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackUiState(
  val currentSong: Song? = null,
  val queue: List<Song> = emptyList(),
  val currentIndex: Int = -1,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val durationMs: Long = 0L,
  val shuffleEnabled: Boolean = false,
  val repeatMode: Int = Player.REPEAT_MODE_OFF,
  val playbackSpeed: Float = 1.0f,
  val playerError: String? = null,
  val isConnected: Boolean = false,
)

class PlaybackController(
  private val context: Context,
  private val localMusicRepository: LocalMusicRepository,
  private val preferencesRepository: UserPreferencesRepository,
) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _state = MutableStateFlow(PlaybackUiState())
  val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

  private var controllerFuture: ListenableFuture<MediaController>? = null
  private var controller: MediaController? = null
  private var positionPollJob: Job? = null
  private var sessionSaveCounter = 0
  private var hasRestoredSession = false

  private var currentQueueSongs: List<Song> = emptyList()

  private val playerListener =
    object : Player.Listener {
      override fun onIsPlayingChanged(isPlaying: Boolean) {
        syncFromPlayer()
        if (isPlaying) {
          startPositionPolling()
        } else {
          stopPositionPolling()
          persistSessionSnapshot()
        }
      }

      override fun onPlaybackStateChanged(playbackState: Int) {
        syncFromPlayer()
      }

      override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        syncFromPlayer()
        val activeSong = _state.value.currentSong
        if (activeSong != null) {
          scope.launch { localMusicRepository.recordPlay(activeSong.id) }
          persistSessionSnapshot()
        }
      }

      override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
      ) {
        syncFromPlayer()
      }

      override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        syncFromPlayer()
        persistSessionSnapshot()
      }

      override fun onRepeatModeChanged(repeatMode: Int) {
        syncFromPlayer()
        persistSessionSnapshot()
      }

      override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        syncFromPlayer()
      }

      override fun onPlayerError(error: PlaybackException) {
        _state.update {
          it.copy(
            isPlaying = false,
            playerError = error.localizedMessage ?: "Unable to play this audio file.",
          )
        }
      }
    }

  fun connect() {
    if (controllerFuture != null) return
    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    controllerFuture = future
    future.addListener(
      {
        runCatching { future.get() }
          .onSuccess { mc ->
            controller = mc
            mc.addListener(playerListener)
            _state.update { it.copy(isConnected = true) }
            syncFromPlayer()
            if (mc.isPlaying) startPositionPolling()
            if (!hasRestoredSession && mc.mediaItemCount == 0) {
              scope.launch { restoreLastSessionIfEnabled() }
            }
          }
          .onFailure { err ->
            _state.update { it.copy(playerError = err.localizedMessage) }
          }
      },
      MoreExecutors.directExecutor(),
    )
  }

  suspend fun restoreLastSessionIfEnabled() {
    if (hasRestoredSession) return
    hasRestoredSession = true
    val prefs = preferencesRepository.preferencesFlow.first()
    val mc = controller ?: return

    mc.shuffleModeEnabled = prefs.shuffleEnabled
    mc.repeatMode = prefs.repeatMode
    mc.setPlaybackSpeed(prefs.defaultPlaybackSpeed)

    if (!prefs.resumeLastSession) return
    val lastId = prefs.lastPlayedSongId ?: return
    val library = localMusicRepository.songs.value
    val startIndex = library.indexOfFirst { it.id == lastId }
    if (startIndex < 0) return

    currentQueueSongs = library
    val mediaItems = library.map { it.toMediaItem() }
    mc.setMediaItems(mediaItems, startIndex, prefs.lastPositionMs.coerceAtLeast(0L))
    mc.prepare()
    mc.pause()
    syncFromPlayer()
  }

  fun playQueue(songs: List<Song>, startIndex: Int) {
    if (songs.isEmpty()) return
    val safeIndex = startIndex.coerceIn(0, songs.lastIndex)
    currentQueueSongs = songs
    val mc = controller
    if (mc == null) {
      connect()
      return
    }
    _state.update { it.copy(playerError = null) }
    val mediaItems = songs.map { it.toMediaItem() }
    mc.setMediaItems(mediaItems, safeIndex, 0L)
    mc.prepare()
    mc.play()
    syncFromPlayer()
    scope.launch { localMusicRepository.recordPlay(songs[safeIndex].id) }
  }

  fun togglePlayPause() {
    val mc = controller ?: return
    if (mc.isPlaying) {
      mc.pause()
    } else {
      if (mc.playbackState == Player.STATE_IDLE && mc.mediaItemCount > 0) {
        mc.prepare()
      }
      mc.play()
    }
  }

  /** Jumps to [positionMs] and makes sure playback is running (used when tapping a lyric line). */
  fun seekToAndPlay(positionMs: Long) {
    val mc = controller ?: return
    val clamped = positionMs.coerceAtLeast(0L)
    if (mc.playbackState == Player.STATE_IDLE && mc.mediaItemCount > 0) {
      mc.prepare()
    }
    mc.seekTo(clamped)
    if (!mc.isPlaying) mc.play()
    _state.update { it.copy(positionMs = clamped, isPlaying = true) }
  }

  fun seekTo(positionMs: Long) {
    val mc = controller ?: return
    val clamped = positionMs.coerceAtLeast(0L)
    mc.seekTo(clamped)
    _state.update { it.copy(positionMs = clamped) }
  }

  fun seekToQueueIndex(index: Int) {
    val mc = controller ?: return
    if (index in 0 until mc.mediaItemCount) {
      mc.seekToDefaultPosition(index)
      mc.play()
      syncFromPlayer()
    }
  }

  fun next() {
    val mc = controller ?: return
    if (mc.hasNextMediaItem()) {
      mc.seekToNextMediaItem()
    } else if (mc.mediaItemCount > 0) {
      mc.seekToDefaultPosition(0)
    }
    mc.play()
  }

  fun previous() {
    val mc = controller ?: return
    if (mc.currentPosition > 3500L) {
      mc.seekTo(0L)
    } else if (mc.hasPreviousMediaItem()) {
      mc.seekToPreviousMediaItem()
    } else {
      mc.seekTo(0L)
    }
  }

  fun toggleShuffle() {
    val mc = controller ?: return
    mc.shuffleModeEnabled = !mc.shuffleModeEnabled
    syncFromPlayer()
  }

  fun cycleRepeatMode() {
    val mc = controller ?: return
    mc.repeatMode =
      when (mc.repeatMode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
      }
    syncFromPlayer()
  }

  fun setPlaybackSpeed(speed: Float) {
    val mc = controller ?: return
    val clamped = speed.coerceIn(0.5f, 2.0f)
    mc.setPlaybackSpeed(clamped)
    _state.update { it.copy(playbackSpeed = clamped) }
    scope.launch { preferencesRepository.setDefaultPlaybackSpeed(clamped) }
  }

  fun removeFromQueue(index: Int) {
    val mc = controller ?: return
    if (index !in 0 until mc.mediaItemCount) return
    mc.removeMediaItem(index)
    if (index in currentQueueSongs.indices) {
      currentQueueSongs = currentQueueSongs.toMutableList().apply { removeAt(index) }
    }
    syncFromPlayer()
  }

  fun clearError() {
    _state.update { it.copy(playerError = null) }
  }

  private fun startPositionPolling() {
    if (positionPollJob?.isActive == true) return
    positionPollJob =
      scope.launch {
        while (isActive) {
          val mc = controller
          if (mc != null) {
            val pos = mc.currentPosition.coerceAtLeast(0L)
            val dur = mc.duration.takeIf { it > 0L } ?: _state.value.currentSong?.durationMs ?: 0L
            _state.update { it.copy(positionMs = pos, durationMs = dur, isPlaying = mc.isPlaying) }
            sessionSaveCounter++
            if (sessionSaveCounter % 30 == 0) {
              persistSessionSnapshot()
            }
          }
          delay(100L)
        }
      }
  }

  private fun stopPositionPolling() {
    positionPollJob?.cancel()
    positionPollJob = null
  }

  private fun syncFromPlayer() {
    val mc = controller ?: return
    val index = mc.currentMediaItemIndex
    val activeId = mc.currentMediaItem?.mediaId?.toLongOrNull()
    val library = localMusicRepository.songs.value

    val song =
      currentQueueSongs.getOrNull(index)?.takeIf { activeId == null || it.id == activeId }
        ?: library.firstOrNull { it.id == activeId }
        ?: currentQueueSongs.firstOrNull { it.id == activeId }

    val resolvedQueue =
      if (currentQueueSongs.isNotEmpty()) {
        currentQueueSongs
      } else {
        (0 until mc.mediaItemCount).mapNotNull { i ->
          val id = mc.getMediaItemAt(i).mediaId.toLongOrNull()
          library.firstOrNull { it.id == id }
        }
      }

    val dur = mc.duration.takeIf { it > 0L } ?: song?.durationMs ?: 0L
    val pos = mc.currentPosition.coerceAtLeast(0L)

    _state.update {
      it.copy(
        currentSong = song,
        queue = resolvedQueue,
        currentIndex = index,
        isPlaying = mc.isPlaying,
        positionMs = pos,
        durationMs = dur,
        shuffleEnabled = mc.shuffleModeEnabled,
        repeatMode = mc.repeatMode,
        playbackSpeed = mc.playbackParameters.speed,
      )
    }
  }

  private fun persistSessionSnapshot() {
    val snapshot = _state.value
    val song = snapshot.currentSong ?: return
    scope.launch {
      preferencesRepository.savePlaybackSession(
        songId = song.id,
        positionMs = snapshot.positionMs,
        shuffleEnabled = snapshot.shuffleEnabled,
        repeatMode = snapshot.repeatMode,
      )
    }
  }

  private fun Song.toMediaItem(): MediaItem {
    val extras = Bundle().apply { putLong("song_id", id) }
    val metadata =
      MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(albumArtUri)
        .setExtras(extras)
        .build()

    return MediaItem.Builder()
      .setMediaId(id.toString())
      .setUri(contentUri)
      .setMediaMetadata(metadata)
      .build()
  }
}
