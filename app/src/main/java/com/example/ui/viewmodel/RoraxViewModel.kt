package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.RoraxApplication
import com.example.data.db.DownloadTaskEntity
import com.example.data.repo.AiInsightResult
import com.example.data.repo.LyricLookupOutcome
import com.example.model.AccentPalette
import com.example.model.AlbumSummary
import com.example.model.ArtistSummary
import com.example.model.LyricCandidate
import com.example.model.LyricRecord
import com.example.model.LyricSyncStatus
import com.example.model.PlaylistWithSongs
import com.example.model.Song
import com.example.model.ThemeMode
import com.example.model.UserPreferences
import com.example.player.PlaybackUiState
import com.example.worker.BulkLyricsDownloadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SplashInitState {
  data object Initializing : SplashInitState

  data object Ready : SplashInitState

  data class Error(val message: String) : SplashInitState
}

data class ManualSearchUiState(
  val song: Song? = null,
  val query: String = "",
  val isLoading: Boolean = false,
  val results: List<LyricCandidate> = emptyList(),
  val error: String? = null,
)

data class AiSheetUiState(
  val isVisible: Boolean = false,
  val isLoading: Boolean = false,
  val result: AiInsightResult? = null,
)

class RoraxViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application as RoraxApplication
  private val prefsRepo = app.preferencesRepository
  private val musicRepo = app.localMusicRepository
  private val lyricsRepo = app.lyricsRepository
  private val aiRepo = app.aiAssistantRepository
  val playbackController = app.playbackController

  private val _splashState = MutableStateFlow<SplashInitState>(SplashInitState.Initializing)
  val splashState: StateFlow<SplashInitState> = _splashState.asStateFlow()

  private val _hasAudioPermission = MutableStateFlow(musicRepo.hasAudioPermission())
  val hasAudioPermission: StateFlow<Boolean> = _hasAudioPermission.asStateFlow()

  private val _searchQuery = MutableStateFlow("")
  val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

  private val _snackbarMessage = MutableStateFlow<String?>(null)
  val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

  private val _manualSearchState = MutableStateFlow(ManualSearchUiState())
  val manualSearchState: StateFlow<ManualSearchUiState> = _manualSearchState.asStateFlow()

  private val _aiSheetState = MutableStateFlow(AiSheetUiState())
  val aiSheetState: StateFlow<AiSheetUiState> = _aiSheetState.asStateFlow()

  private val _inFlightLyricSongIds = MutableStateFlow<Set<Long>>(emptySet())
  val inFlightLyricSongIds: StateFlow<Set<Long>> = _inFlightLyricSongIds.asStateFlow()

  val preferences: StateFlow<UserPreferences> =
    prefsRepo.preferencesFlow.stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = UserPreferences(),
    )

  val songs: StateFlow<List<Song>> = musicRepo.songs
  val isScanning: StateFlow<Boolean> = musicRepo.isScanning
  val scanError: StateFlow<String?> = musicRepo.scanError

  val filteredSongs: StateFlow<List<Song>> =
    combine(songs, _searchQuery) { list, q ->
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
          list
        } else {
          list.filter { song ->
            song.title.contains(trimmed, ignoreCase = true) ||
              song.artist.contains(trimmed, ignoreCase = true) ||
              song.album.contains(trimmed, ignoreCase = true)
          }
        }
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val albums: StateFlow<List<AlbumSummary>> =
    songs
      .map { musicRepo.computeAlbums(it) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val artists: StateFlow<List<ArtistSummary>> =
    songs
      .map { musicRepo.computeArtists(it) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val favoriteIds: StateFlow<Set<Long>> =
    musicRepo.favoriteIds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

  val recentSongs: StateFlow<List<Song>> =
    musicRepo.recentSongs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val playlists: StateFlow<List<PlaylistWithSongs>> =
    musicRepo.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val lyricsMap: StateFlow<Map<Long, LyricRecord>> =
    lyricsRepo.allLyricsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

  val downloadTasks: StateFlow<List<DownloadTaskEntity>> =
    lyricsRepo.downloadTasksFlow.stateIn(
      viewModelScope,
      SharingStarted.WhileSubscribed(5000),
      emptyList(),
    )

  val isBulkDownloading: StateFlow<Boolean> =
    BulkLyricsDownloadWorker.observeIsRunning(app)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

  val cacheSizeBytes: StateFlow<Long> =
    lyricsRepo.cacheSizeBytesFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

  val playbackState: StateFlow<PlaybackUiState> = playbackController.state

  init {
    initializeApp()
    observeCurrentTrackForAutoLyrics()
  }

  fun initializeApp() {
    viewModelScope.launch {
      _splashState.value = SplashInitState.Initializing
      try {
        playbackController.connect()
        val granted = musicRepo.hasAudioPermission()
        _hasAudioPermission.value = granted
        if (granted) {
          musicRepo.scanLibrary()
          playbackController.restoreLastSessionIfEnabled()
        }
        _splashState.value = SplashInitState.Ready
      } catch (e: Exception) {
        _splashState.value =
          SplashInitState.Error("Something went wrong while loading your library. Please try again.")
      }
    }
  }

  private fun observeCurrentTrackForAutoLyrics() {
    viewModelScope.launch {
      playbackState.collect { state ->
        val song = state.currentSong ?: return@collect
        if (!preferences.value.autoLyricLookupOnPlay) return@collect
        val cached = lyricsMap.value[song.id]
        if (cached == null || cached.status == LyricSyncStatus.NONE) {
          fetchLyricsForSong(song, forceRefresh = false, showUserToast = false)
        }
      }
    }
  }

  fun requiredAudioPermission(): String = musicRepo.requiredAudioPermission()

  fun onAudioPermissionResult(granted: Boolean) {
    _hasAudioPermission.value = granted || musicRepo.hasAudioPermission()
    if (_hasAudioPermission.value) {
      rescanLibrary()
    }
  }

  fun refreshPermissionStatus() {
    val granted = musicRepo.hasAudioPermission()
    val changed = granted != _hasAudioPermission.value
    _hasAudioPermission.value = granted
    if (granted && (changed || songs.value.isEmpty())) {
      rescanLibrary()
    }
  }

  fun rescanLibrary() {
    viewModelScope.launch {
      val result = musicRepo.scanLibrary()
      result.onSuccess { list ->
        playbackController.restoreLastSessionIfEnabled()
        if (list.isEmpty()) {
          showMessage("No music found on this device.")
        }
      }
      result.onFailure { err ->
        showMessage("Couldn't refresh your library. Please try again.")
      }
    }
  }

  fun updateSearchQuery(query: String) {
    _searchQuery.value = query
  }

  fun playSongs(queue: List<Song>, startIndex: Int) {
    playbackController.playQueue(queue, startIndex)
  }

  fun toggleFavorite(songId: Long) {
    viewModelScope.launch { musicRepo.toggleFavorite(songId) }
  }

  fun createPlaylist(name: String) {
    viewModelScope.launch {
      val id = musicRepo.createPlaylist(name)
      if (id > 0L) showMessage("Playlist \"$name\" created.")
    }
  }

  fun deletePlaylist(playlistId: Long) {
    viewModelScope.launch {
      musicRepo.deletePlaylist(playlistId)
      showMessage("Playlist deleted.")
    }
  }

  fun addSongToPlaylist(playlistId: Long, songId: Long) {
    viewModelScope.launch {
      musicRepo.addSongToPlaylist(playlistId, songId)
      showMessage("Added to playlist.")
    }
  }

  fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
    viewModelScope.launch { musicRepo.removeSongFromPlaylist(playlistId, songId) }
  }

  fun fetchLyricsForSong(
    song: Song,
    forceRefresh: Boolean = true,
    showUserToast: Boolean = true,
  ) {
    if (song.id in _inFlightLyricSongIds.value) return
    viewModelScope.launch {
      _inFlightLyricSongIds.update { it + song.id }
      try {
        when (val outcome = lyricsRepo.fetchAndCacheLyrics(song, forceRefresh = forceRefresh)) {
          is LyricLookupOutcome.Saved -> {
            if (showUserToast) {
              val label =
                when (outcome.record.status) {
                  LyricSyncStatus.CACHED_SYNCED -> "Lyrics downloaded."
                  LyricSyncStatus.CACHED_PLAIN -> "Lyrics saved (not timed to the song)."
                  LyricSyncStatus.INSTRUMENTAL -> "Marked as instrumental."
                  else -> "Lyrics updated."
                }
              showMessage(label)
            }
          }
          is LyricLookupOutcome.NeedsReview -> {
            if (showUserToast) {
              showMessage("${outcome.candidates.size} possible matches found. Review to pick one.")
            }
          }
          is LyricLookupOutcome.NotFound -> {
            if (showUserToast) showMessage("No lyrics found for \"${song.title}\".")
          }
          is LyricLookupOutcome.Failed -> {
            if (showUserToast) showMessage(outcome.message)
          }
        }
      } finally {
        _inFlightLyricSongIds.update { it - song.id }
      }
    }
  }

  fun openManualSearch(song: Song) {
    val defaultQuery =
      if (song.artist.equals("Unknown Artist", ignoreCase = true)) {
        song.title
      } else {
        "${song.title} ${song.artist}"
      }
    _manualSearchState.value = ManualSearchUiState(song = song, query = defaultQuery)
    executeManualSearch(defaultQuery)
  }

  fun updateManualSearchQuery(query: String) {
    _manualSearchState.update { it.copy(query = query) }
  }

  fun executeManualSearch(queryOverride: String? = null) {
    val current = _manualSearchState.value
    val song = current.song ?: return
    val query = (queryOverride ?: current.query).trim()
    if (query.isEmpty()) return

    viewModelScope.launch {
      _manualSearchState.update { it.copy(isLoading = true, error = null) }
      val result = lyricsRepo.manualSearch(song, query)
      result.onSuccess { candidates ->
        _manualSearchState.update { it.copy(isLoading = false, results = candidates) }
      }
      result.onFailure { err ->
        _manualSearchState.update {
          it.copy(isLoading = false, error = err.localizedMessage ?: "Search failed.")
        }
      }
    }
  }

  fun closeManualSearch() {
    _manualSearchState.value = ManualSearchUiState()
  }

  fun applyCandidate(songId: Long, candidate: LyricCandidate) {
    viewModelScope.launch {
      lyricsRepo.applyCandidate(songId, candidate)
      closeManualSearch()
      showMessage("Saved lyrics for \"${candidate.trackName}\".")
    }
  }

  fun importLrcFile(song: Song, uri: Uri) {
    viewModelScope.launch {
      val result = lyricsRepo.importLrcFromUri(song, uri)
      result.onSuccess { record ->
        val msg =
          if (record.status == LyricSyncStatus.CACHED_SYNCED) {
            "Lyrics added."
          } else {
            "Lyrics added (not timed to the song)."
          }
        showMessage(msg)
      }
      result.onFailure { err ->
        showMessage(err.localizedMessage ?: "Failed to import .LRC file.")
      }
    }
  }

  fun startBulkDownload(forceRefresh: Boolean = false) {
    if (!hasAudioPermission.value || songs.value.isEmpty()) {
      showMessage("Refresh your library first.")
      return
    }
    if (!lyricsRepo.isNetworkAvailable()) {
      showMessage("You are offline. Connect to the internet to download lyrics.")
      return
    }
    val wifiOnly = preferences.value.wifiOnlyBulkDownloads
    if (wifiOnly && !lyricsRepo.isOnWifi()) {
      showMessage("Wi-Fi only is on in Settings. Connect to Wi-Fi, or turn it off.")
      return
    }
    BulkLyricsDownloadWorker.enqueue(app, wifiOnly = wifiOnly, retryOnly = false, forceRefresh = forceRefresh)
    showMessage("Downloading lyrics in the background.")
  }

  fun retryFailedDownloads() {
    if (!lyricsRepo.isNetworkAvailable()) {
      showMessage("You are offline. Connect to retry failed downloads.")
      return
    }
    val wifiOnly = preferences.value.wifiOnlyBulkDownloads
    if (wifiOnly && !lyricsRepo.isOnWifi()) {
      showMessage("Wi-Fi only mode is enabled in Settings.")
      return
    }
    BulkLyricsDownloadWorker.enqueue(app, wifiOnly = wifiOnly, retryOnly = true, forceRefresh = false)
    showMessage("Retrying…")
  }

  fun cancelBulkDownload() {
    BulkLyricsDownloadWorker.cancel(app)
    showMessage("Download paused. Lyrics already downloaded are saved.")
  }

  // Optional AI features (Placeholder when Firebase declined)
  fun requestAiLyricExplanation(song: Song, selectedLine: String, contextLines: List<String>) {
    if (!preferences.value.aiFeaturesEnabled) {
      showMessage("Enable optional AI Features in Settings first.")
      return
    }
    viewModelScope.launch {
      _aiSheetState.value = AiSheetUiState(isVisible = true, isLoading = true)
      val res = aiRepo.explainLyric(song, selectedLine, contextLines)
      _aiSheetState.value = AiSheetUiState(isVisible = true, isLoading = false, result = res)
    }
  }

  fun requestAiMatchSuggestion(song: Song, candidates: List<LyricCandidate>) {
    if (!preferences.value.aiFeaturesEnabled) {
      showMessage("Enable optional AI Features in Settings first.")
      return
    }
    viewModelScope.launch {
      _aiSheetState.value = AiSheetUiState(isVisible = true, isLoading = true)
      val res = aiRepo.suggestBestCandidate(song, candidates)
      _aiSheetState.value = AiSheetUiState(isVisible = true, isLoading = false, result = res)
    }
  }

  fun dismissAiSheet() {
    _aiSheetState.value = AiSheetUiState()
  }

  // Settings mutations
  fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { prefsRepo.setThemeMode(mode) }

  fun setAccentPalette(palette: AccentPalette) =
    viewModelScope.launch { prefsRepo.setAccentPalette(palette) }

  fun setLyricsFontSize(sizeSp: Float) =
    viewModelScope.launch { prefsRepo.setLyricsFontSize(sizeSp) }

  fun setReducedMotion(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setReducedMotion(enabled) }

  fun setRomanizeLyrics(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setRomanizeLyrics(enabled) }

  fun setAutoLyricLookup(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setAutoLyricLookup(enabled) }

  fun setWifiOnlyDownloads(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setWifiOnlyDownloads(enabled) }

  fun setResumeLastSession(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setResumeLastSession(enabled) }

  fun setDefaultPlaybackSpeed(speed: Float) = playbackController.setPlaybackSpeed(speed)

  fun setAiFeaturesEnabled(enabled: Boolean) =
    viewModelScope.launch { prefsRepo.setAiFeaturesEnabled(enabled) }

  fun clearLyricCache() {
    viewModelScope.launch {
      lyricsRepo.clearLyricCache()
      showMessage("Downloaded lyrics cleared.")
    }
  }

  fun showMessage(msg: String) {
    _snackbarMessage.value = msg
  }

  fun consumeMessage() {
    _snackbarMessage.value = null
  }
}
