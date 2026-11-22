package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AlbumSummary
import com.example.model.ArtistSummary
import com.example.model.LyricRecord
import com.example.model.PlaylistWithSongs
import com.example.model.Song
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.AlbumArtwork
import com.example.ui.components.PermissionRequestCard
import com.example.ui.components.RoraxLogoMark
import com.example.ui.components.SongRowItem

private enum class HomeCategory(val label: String) {
  OVERVIEW("Overview"),
  SONGS("Songs"),
  ALBUMS("Albums"),
  ARTISTS("Artists"),
  PLAYLISTS("Playlists"),
  FAVORITES("Favorites"),
}

private sealed interface DrilldownTarget {
  data class Album(val album: AlbumSummary) : DrilldownTarget

  data class Artist(val artist: ArtistSummary) : DrilldownTarget

  data class Playlist(val playlistId: Long) : DrilldownTarget
}

@Composable
fun HomeScreen(
  songs: List<Song>,
  filteredSongs: List<Song>,
  albums: List<AlbumSummary>,
  artists: List<ArtistSummary>,
  recentSongs: List<Song>,
  favoriteIds: Set<Long>,
  playlists: List<PlaylistWithSongs>,
  lyricsMap: Map<Long, LyricRecord>,
  currentPlayingSongId: Long?,
  searchQuery: String,
  hasAudioPermission: Boolean,
  showPermissionSettingsFallback: Boolean,
  isScanning: Boolean,
  onSearchQueryChange: (String) -> Unit,
  onRequestPermission: () -> Unit,
  onOpenAppSettings: () -> Unit,
  onRescanLibrary: () -> Unit,
  onPlayQueue: (List<Song>, Int) -> Unit,
  onToggleFavorite: (Long) -> Unit,
  onFetchLyrics: (Song) -> Unit,
  onCreatePlaylist: (String) -> Unit,
  onDeletePlaylist: (Long) -> Unit,
  onAddSongToPlaylist: (Long, Long) -> Unit,
  onRemoveSongFromPlaylist: (Long, Long) -> Unit,
  onOpenSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedCategory by remember { mutableStateOf(HomeCategory.OVERVIEW) }
  var searchActive by remember { mutableStateOf(false) }
  var drilldown by remember { mutableStateOf<DrilldownTarget?>(null) }
  var songForPlaylistSheet by remember { mutableStateOf<Song?>(null) }
  var showCreatePlaylistDialog by remember { mutableStateOf(false) }

  if (drilldown != null) {
    BackHandler { drilldown = null }
  }

  Column(modifier = modifier.fillMaxSize().testTag("home_screen")) {
    // Top Header
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (drilldown != null) {
        IconButton(
          onClick = { drilldown = null },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("home_drilldown_back"),
        ) {
          Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
          )
        }
      } else {
        RoraxLogoMark(size = 32.dp)
        Spacer(Modifier.width(10.dp))
      }

      val headerTitle =
        when (val target = drilldown) {
          is DrilldownTarget.Album -> target.album.name
          is DrilldownTarget.Artist -> target.artist.name
          is DrilldownTarget.Playlist ->
            playlists.firstOrNull { it.id == target.playlistId }?.name ?: "Playlist"
          null -> stringResource(R.string.app_name)
        }

      Text(
        text = headerTitle,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )

      IconButton(
        onClick = {
          searchActive = !searchActive
          if (!searchActive) onSearchQueryChange("")
        },
        modifier = Modifier.minimumInteractiveComponentSize().testTag("home_search_toggle"),
      ) {
        Icon(
          imageVector = if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
          contentDescription = stringResource(R.string.cd_search),
        )
      }

      IconButton(
        onClick = onOpenSettings,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("home_settings_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Settings,
          contentDescription = stringResource(R.string.settings_title),
        )
      }
    }

    if (searchActive) {
      OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
          if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { onSearchQueryChange("") }) {
              Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_search))
            }
          }
        },
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp)
            .testTag("home_search_input"),
      )
    }

    if (!hasAudioPermission) {
      Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
        PermissionRequestCard(
          onRequestPermission = onRequestPermission,
          onOpenSettings = onOpenAppSettings,
          showSettingsFallback = showPermissionSettingsFallback,
        )
      }
      return
    }

    if (isScanning && songs.isEmpty()) {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(Modifier.height(12.dp))
          Text("Looking for your music…", style = MaterialTheme.typography.bodyMedium)
        }
      }
      return
    }

    if (songs.isEmpty()) {
      EmptyLibraryState(onRescan = onRescanLibrary)
      return
    }

    // Active Search Results View
    if (searchQuery.isNotBlank()) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
      ) {
        if (filteredSongs.isEmpty()) {
          item {
            Text(
              text = "No local songs match \"$searchQuery\".",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(24.dp),
            )
          }
        } else {
          itemsIndexed(filteredSongs, key = { _, s -> s.id }) { idx, song ->
            SongRowItem(
              song = song,
              isPlayingCurrent = song.id == currentPlayingSongId,
              isFavorite = song.id in favoriteIds,
              lyricStatus = lyricsMap[song.id]?.status,
              onClick = { onPlayQueue(filteredSongs, idx) },
              onToggleFavorite = { onToggleFavorite(song.id) },
              onAddToPlaylist = { songForPlaylistSheet = song },
              onFetchLyrics = { onFetchLyrics(song) },
            )
          }
        }
      }
      songForPlaylistSheet?.let { s ->
        AddToPlaylistSheet(
          song = s,
          playlists = playlists,
          onDismiss = { songForPlaylistSheet = null },
          onCreatePlaylist = onCreatePlaylist,
          onSelectPlaylist = { pid -> onAddSongToPlaylist(pid, s.id) },
        )
      }
      return
    }

    // Drilldown View (Album / Artist / Playlist detail)
    when (val target = drilldown) {
      is DrilldownTarget.Album -> {
        val albumSongs = songs.filter { it.albumId == target.album.albumId && it.album == target.album.name }
        DetailSongList(
          subtitle = "${target.album.artist} • ${albumSongs.size} tracks",
          songs = albumSongs,
          favoriteIds = favoriteIds,
          lyricsMap = lyricsMap,
          currentPlayingSongId = currentPlayingSongId,
          onPlayAll = { if (albumSongs.isNotEmpty()) onPlayQueue(albumSongs, 0) },
          onPlayAt = { idx -> onPlayQueue(albumSongs, idx) },
          onToggleFavorite = onToggleFavorite,
          onAddToPlaylist = { songForPlaylistSheet = it },
          onFetchLyrics = onFetchLyrics,
        )
      }
      is DrilldownTarget.Artist -> {
        val artistSongs = songs.filter { it.artist == target.artist.name }
        DetailSongList(
          subtitle = "${target.artist.albumCount} albums • ${artistSongs.size} tracks",
          songs = artistSongs,
          favoriteIds = favoriteIds,
          lyricsMap = lyricsMap,
          currentPlayingSongId = currentPlayingSongId,
          onPlayAll = { if (artistSongs.isNotEmpty()) onPlayQueue(artistSongs, 0) },
          onPlayAt = { idx -> onPlayQueue(artistSongs, idx) },
          onToggleFavorite = onToggleFavorite,
          onAddToPlaylist = { songForPlaylistSheet = it },
          onFetchLyrics = onFetchLyrics,
        )
      }
      is DrilldownTarget.Playlist -> {
        val pl = playlists.firstOrNull { it.id == target.playlistId }
        if (pl == null) {
          drilldown = null
        } else {
          DetailSongList(
            subtitle = "${pl.songs.size} tracks in playlist",
            songs = pl.songs,
            favoriteIds = favoriteIds,
            lyricsMap = lyricsMap,
            currentPlayingSongId = currentPlayingSongId,
            onPlayAll = { if (pl.songs.isNotEmpty()) onPlayQueue(pl.songs, 0) },
            onPlayAt = { idx -> onPlayQueue(pl.songs, idx) },
            onToggleFavorite = onToggleFavorite,
            onAddToPlaylist = { songForPlaylistSheet = it },
            onFetchLyrics = onFetchLyrics,
            extraAction = {
              OutlinedButton(
                onClick = {
                  onDeletePlaylist(pl.id)
                  drilldown = null
                }
              ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Delete Playlist")
              }
            },
            onRemoveFromPlaylist = { songId -> onRemoveSongFromPlaylist(pl.id, songId) },
          )
        }
      }
      null -> {
        // Category Chips Row
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          HomeCategory.entries.forEach { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { selectedCategory = cat },
              label = { Text(cat.label) },
              modifier = Modifier.testTag("home_chip_${cat.name.lowercase()}"),
            )
          }
        }

        when (selectedCategory) {
          HomeCategory.OVERVIEW -> {
            val recentlyAdded = remember(songs) { songs.sortedByDescending { it.dateAddedSec }.take(12) }
            val favoriteSongs = remember(songs, favoriteIds) { songs.filter { it.id in favoriteIds } }

            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(bottom = 16.dp),
            ) {
              if (recentSongs.isNotEmpty()) {
                item {
                  SectionHeader(title = stringResource(R.string.home_recently_played))
                  LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    itemsIndexed(recentSongs.take(10), key = { _, s -> "recent_${s.id}" }) { idx, song ->
                      RecentSquareCard(
                        song = song,
                        isPlaying = song.id == currentPlayingSongId,
                        onClick = { onPlayQueue(recentSongs, idx) },
                      )
                    }
                  }
                  Spacer(Modifier.height(16.dp))
                }
              }

              if (favoriteSongs.isNotEmpty()) {
                item {
                  SectionHeader(
                    title = "${stringResource(R.string.home_favorites)} (${favoriteSongs.size})",
                    actionLabel = "See all",
                    onAction = { selectedCategory = HomeCategory.FAVORITES },
                  )
                  LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                  ) {
                    itemsIndexed(favoriteSongs.take(10), key = { _, s -> "fav_${s.id}" }) { idx, song ->
                      RecentSquareCard(
                        song = song,
                        isPlaying = song.id == currentPlayingSongId,
                        onClick = { onPlayQueue(favoriteSongs, idx) },
                      )
                    }
                  }
                  Spacer(Modifier.height(16.dp))
                }
              }

              if (recentlyAdded.isNotEmpty()) {
                item {
                  SectionHeader(title = stringResource(R.string.home_recently_added))
                }
                itemsIndexed(recentlyAdded.take(5), key = { _, s -> "added_${s.id}" }) { idx, song ->
                  SongRowItem(
                    song = song,
                    isPlayingCurrent = song.id == currentPlayingSongId,
                    isFavorite = song.id in favoriteIds,
                    lyricStatus = lyricsMap[song.id]?.status,
                    onClick = { onPlayQueue(recentlyAdded, idx) },
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onAddToPlaylist = { songForPlaylistSheet = song },
                    onFetchLyrics = { onFetchLyrics(song) },
                    modifier = Modifier.padding(horizontal = 4.dp),
                  )
                }
                item { Spacer(Modifier.height(12.dp)) }
              }

              item {
                SectionHeader(
                  title = "${stringResource(R.string.home_all_songs)} (${songs.size})",
                  actionLabel = "Play All",
                  onAction = { onPlayQueue(songs, 0) },
                )
              }
              itemsIndexed(songs, key = { _, s -> "all_${s.id}" }) { idx, song ->
                SongRowItem(
                  song = song,
                  isPlayingCurrent = song.id == currentPlayingSongId,
                  isFavorite = song.id in favoriteIds,
                  lyricStatus = lyricsMap[song.id]?.status,
                  onClick = { onPlayQueue(songs, idx) },
                  onToggleFavorite = { onToggleFavorite(song.id) },
                  onAddToPlaylist = { songForPlaylistSheet = song },
                  onFetchLyrics = { onFetchLyrics(song) },
                  modifier = Modifier.padding(horizontal = 4.dp),
                )
              }
            }
          }

          HomeCategory.SONGS -> {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
            ) {
              itemsIndexed(songs, key = { _, s -> s.id }) { idx, song ->
                SongRowItem(
                  song = song,
                  isPlayingCurrent = song.id == currentPlayingSongId,
                  isFavorite = song.id in favoriteIds,
                  lyricStatus = lyricsMap[song.id]?.status,
                  onClick = { onPlayQueue(songs, idx) },
                  onToggleFavorite = { onToggleFavorite(song.id) },
                  onAddToPlaylist = { songForPlaylistSheet = song },
                  onFetchLyrics = { onFetchLyrics(song) },
                )
              }
            }
          }

          HomeCategory.ALBUMS -> {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              items(albums, key = { "${it.albumId}_${it.name}" }) { album ->
                Card(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(16.dp))
                      .clickable { drilldown = DrilldownTarget.Album(album) },
                  colors =
                    CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    AlbumArtwork(
                      artUri = album.albumArtUri,
                      title = album.name,
                      modifier = Modifier.size(56.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        album.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        "${album.artist} • ${album.songCount} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                    }
                    Icon(
                      Icons.Rounded.Album,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
              }
            }
          }

          HomeCategory.ARTISTS -> {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              items(artists, key = { it.name }) { artist ->
                Card(
                  modifier =
                    Modifier.fillMaxWidth()
                      .clip(RoundedCornerShape(16.dp))
                      .clickable { drilldown = DrilldownTarget.Artist(artist) },
                  colors =
                    CardDefaults.cardColors(
                      containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                  ) {
                    Surface(
                      shape = RoundedCornerShape(12.dp),
                      color = MaterialTheme.colorScheme.primaryContainer,
                      modifier = Modifier.size(46.dp),
                    ) {
                      Box(contentAlignment = Alignment.Center) {
                        Icon(
                          Icons.Rounded.Person,
                          contentDescription = null,
                          tint = MaterialTheme.colorScheme.primary,
                        )
                      }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        artist.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      Text(
                        "${artist.songCount} songs • ${artist.albumCount} albums",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                }
              }
            }
          }

          HomeCategory.PLAYLISTS -> {
            LazyColumn(
              modifier = Modifier.fillMaxSize(),
              contentPadding = PaddingValues(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              item {
                Button(
                  onClick = { showCreatePlaylistDialog = true },
                  modifier = Modifier.fillMaxWidth().testTag("create_playlist_button"),
                ) {
                  Icon(Icons.Rounded.Add, contentDescription = null)
                  Spacer(Modifier.width(8.dp))
                  Text("Create Local Playlist")
                }
              }
              if (playlists.isEmpty()) {
                item {
                  Text(
                    text = "No playlists yet. Create one to organize your music.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                  )
                }
              } else {
                items(playlists, key = { it.id }) { pl ->
                  Card(
                    modifier =
                      Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { drilldown = DrilldownTarget.Playlist(pl.id) },
                    colors =
                      CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                      ),
                  ) {
                    Row(
                      modifier = Modifier.fillMaxWidth().padding(14.dp),
                      verticalAlignment = Alignment.CenterVertically,
                    ) {
                      Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(46.dp),
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                          )
                        }
                      }
                      Spacer(Modifier.width(12.dp))
                      Column(modifier = Modifier.weight(1f)) {
                        Text(pl.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                          "${pl.songs.size} tracks",
                          style = MaterialTheme.typography.bodySmall,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                    }
                  }
                }
              }
            }
          }

          HomeCategory.FAVORITES -> {
            val favSongs = songs.filter { it.id in favoriteIds }
            if (favSongs.isEmpty()) {
              Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(44.dp),
                  )
                  Spacer(Modifier.height(10.dp))
                  Text("No favorite songs yet", style = MaterialTheme.typography.titleMedium)
                  Text(
                    "Tap the heart icon on any track to save it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            } else {
              LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
              ) {
                itemsIndexed(favSongs, key = { _, s -> s.id }) { idx, song ->
                  SongRowItem(
                    song = song,
                    isPlayingCurrent = song.id == currentPlayingSongId,
                    isFavorite = true,
                    lyricStatus = lyricsMap[song.id]?.status,
                    onClick = { onPlayQueue(favSongs, idx) },
                    onToggleFavorite = { onToggleFavorite(song.id) },
                    onAddToPlaylist = { songForPlaylistSheet = song },
                    onFetchLyrics = { onFetchLyrics(song) },
                  )
                }
              }
            }
          }
        }
      }
    }
  }

  songForPlaylistSheet?.let { s ->
    AddToPlaylistSheet(
      song = s,
      playlists = playlists,
      onDismiss = { songForPlaylistSheet = null },
      onCreatePlaylist = onCreatePlaylist,
      onSelectPlaylist = { pid -> onAddSongToPlaylist(pid, s.id) },
    )
  }

  if (showCreatePlaylistDialog) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showCreatePlaylistDialog = false },
      title = { Text("New Playlist") },
      text = {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text("Playlist name") },
          singleLine = true,
        )
      },
      confirmButton = {
        Button(
          onClick = {
            if (name.isNotBlank()) {
              onCreatePlaylist(name.trim())
              showCreatePlaylistDialog = false
            }
          },
          enabled = name.isNotBlank(),
        ) {
          Text("Create")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun SectionHeader(
  title: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleLarge,
      modifier = Modifier.weight(1f),
    )
    if (actionLabel != null && onAction != null) {
      TextButton(onClick = onAction) {
        Text(actionLabel, color = MaterialTheme.colorScheme.primary)
      }
    }
  }
}

@Composable
private fun RecentSquareCard(
  song: Song,
  isPlaying: Boolean,
  onClick: () -> Unit,
) {
  Card(
    modifier =
      Modifier.width(136.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          else MaterialTheme.colorScheme.surfaceContainer
      ),
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      AlbumArtwork(
        artUri = song.albumArtUri,
        title = song.title,
        modifier = Modifier.fillMaxWidth().height(112.dp),
        cornerRadius = 12.dp,
        iconSize = 36.dp,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = song.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color =
          if (isPlaying) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = song.artist,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun DetailSongList(
  subtitle: String,
  songs: List<Song>,
  favoriteIds: Set<Long>,
  lyricsMap: Map<Long, LyricRecord>,
  currentPlayingSongId: Long?,
  onPlayAll: () -> Unit,
  onPlayAt: (Int) -> Unit,
  onToggleFavorite: (Long) -> Unit,
  onAddToPlaylist: (Song) -> Unit,
  onFetchLyrics: (Song) -> Unit,
  extraAction: (@Composable () -> Unit)? = null,
  onRemoveFromPlaylist: ((Long) -> Unit)? = null,
) {
  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 16.dp),
  ) {
    item {
      Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Button(onClick = onPlayAll, enabled = songs.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Play All")
          }
          extraAction?.invoke()
        }
        Spacer(Modifier.height(8.dp))
      }
    }
    itemsIndexed(songs, key = { _, s -> s.id }) { idx, song ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
          SongRowItem(
            song = song,
            isPlayingCurrent = song.id == currentPlayingSongId,
            isFavorite = song.id in favoriteIds,
            lyricStatus = lyricsMap[song.id]?.status,
            onClick = { onPlayAt(idx) },
            onToggleFavorite = { onToggleFavorite(song.id) },
            onAddToPlaylist = { onAddToPlaylist(song) },
            onFetchLyrics = { onFetchLyrics(song) },
          )
        }
        if (onRemoveFromPlaylist != null) {
          IconButton(onClick = { onRemoveFromPlaylist(song.id) }) {
            Icon(
              Icons.Rounded.Close,
              contentDescription = "Remove from playlist",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun EmptyLibraryState(onRescan: () -> Unit) {
  Box(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    contentAlignment = Alignment.Center,
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(
        imageVector = Icons.Rounded.LibraryMusic,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp),
      )
      Spacer(Modifier.height(16.dp))
      Text(
        text = stringResource(R.string.home_empty_library_title),
        style = MaterialTheme.typography.titleLarge,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.home_empty_library_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(18.dp))
      Button(onClick = onRescan, modifier = Modifier.testTag("empty_rescan_button")) {
        Icon(Icons.Rounded.Refresh, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.action_rescan_library))
      }
    }
  }
}
