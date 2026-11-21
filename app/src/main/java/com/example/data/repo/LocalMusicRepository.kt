package com.example.data.repo

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.data.db.FavoriteEntity
import com.example.data.db.LibraryInteractionDao
import com.example.data.db.PlaylistEntity
import com.example.data.db.PlaylistSongCrossRef
import com.example.model.AlbumSummary
import com.example.model.ArtistSummary
import com.example.model.PlaylistWithSongs
import com.example.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class LocalMusicRepository(
  private val context: Context,
  private val interactionDao: LibraryInteractionDao,
) {
  private val _songs = MutableStateFlow<List<Song>>(emptyList())
  val songs: StateFlow<List<Song>> = _songs.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _scanError = MutableStateFlow<String?>(null)
  val scanError: StateFlow<String?> = _scanError.asStateFlow()

  val favoriteIds: Flow<Set<Long>> =
    interactionDao.observeFavoriteIds().combine(MutableStateFlow(Unit)) { ids, _ -> ids.toSet() }

  val recentSongs: Flow<List<Song>> =
    combine(interactionDao.observeRecentPlays(), _songs) { recents, allSongs ->
      val byId = allSongs.associateBy { it.id }
      recents.mapNotNull { byId[it.songId] }
    }

  val playlists: Flow<List<PlaylistWithSongs>> =
    combine(
      interactionDao.observePlaylists(),
      interactionDao.observeAllPlaylistSongs(),
      _songs,
    ) { playlistEntities, crossRefs, allSongs ->
      val songById = allSongs.associateBy { it.id }
      val refsByPlaylist = crossRefs.groupBy { it.playlistId }
      playlistEntities.map { pl ->
        val plSongs =
          refsByPlaylist[pl.id]
            .orEmpty()
            .sortedBy { it.position }
            .mapNotNull { ref -> songById[ref.songId] }
        PlaylistWithSongs(
          id = pl.id,
          name = pl.name,
          createdAtMs = pl.createdAtMs,
          songs = plSongs,
        )
      }
    }

  fun requiredAudioPermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      Manifest.permission.READ_MEDIA_AUDIO
    } else {
      Manifest.permission.READ_EXTERNAL_STORAGE
    }
  }

  fun hasAudioPermission(): Boolean {
    return ContextCompat.checkSelfPermission(context, requiredAudioPermission()) ==
      PackageManager.PERMISSION_GRANTED
  }

  suspend fun scanLibrary(): Result<List<Song>> {
    if (!hasAudioPermission()) {
      _songs.value = emptyList()
      return Result.success(emptyList())
    }
    _isScanning.value = true
    _scanError.value = null
    return withContext(Dispatchers.IO) {
      try {
        val loaded = queryMediaStoreAudio()
        _songs.value = loaded
        Result.success(loaded)
      } catch (e: SecurityException) {
        _scanError.value = "Audio permission was revoked."
        Result.failure(e)
      } catch (e: Exception) {
        _scanError.value = e.localizedMessage ?: "Failed to scan local music."
        Result.failure(e)
      } finally {
        _isScanning.value = false
      }
    }
  }

  private fun queryMediaStoreAudio(): List<Song> {
    val result = mutableListOf<Song>()
    val collection =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
      } else {
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
      }

    val projection =
      arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.SIZE,
      )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

    context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
      val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
      val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
      val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
      val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
      val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
      val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
      val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
      val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
      val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
      val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
      val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

      val albumArtBase = Uri.parse("content://media/external/audio/albumart")

      while (cursor.moveToNext()) {
        val id = cursor.getLong(idCol)
        val durationMs = cursor.getLong(durationCol)
        if (durationMs < 1500L) continue // Skip tiny notification/UI sound clips

        val displayName = cursor.getString(displayCol)?.substringBeforeLast('.')?.trim().orEmpty()
        val rawTitle = cursor.getString(titleCol)?.trim().orEmpty()
        val title =
          when {
            rawTitle.isNotEmpty() -> rawTitle
            displayName.isNotEmpty() -> displayName
            else -> "Untitled Track #$id"
          }

        val rawArtist = cursor.getString(artistCol)?.trim().orEmpty()
        val artist =
          if (rawArtist.isEmpty() || rawArtist.equals("<unknown>", ignoreCase = true)) {
            "Unknown Artist"
          } else {
            rawArtist
          }

        val rawAlbum = cursor.getString(albumCol)?.trim().orEmpty()
        val album =
          if (rawAlbum.isEmpty() || rawAlbum.equals("<unknown>", ignoreCase = true)) {
            "Unknown Album"
          } else {
            rawAlbum
          }

        val albumId = cursor.getLong(albumIdCol)
        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        val artUri = if (albumId > 0L) ContentUris.withAppendedId(albumArtBase, albumId) else null

        result.add(
          Song(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            durationMs = durationMs,
            contentUri = contentUri,
            albumArtUri = artUri,
            dateAddedSec = cursor.getLong(dateAddedCol),
            trackNumber = cursor.getInt(trackCol),
            mimeType = cursor.getString(mimeCol) ?: "audio/*",
            sizeBytes = cursor.getLong(sizeCol),
          )
        )
      }
    }
    return result
  }

  fun computeAlbums(songs: List<Song>): List<AlbumSummary> {
    return songs
      .groupBy { it.albumId to it.album }
      .map { (key, group) ->
        AlbumSummary(
          albumId = key.first,
          name = key.second,
          artist = group.firstOrNull()?.artist ?: "Unknown Artist",
          songCount = group.size,
          albumArtUri = group.firstOrNull()?.albumArtUri,
        )
      }
      .sortedBy { it.name.lowercase() }
  }

  fun computeArtists(songs: List<Song>): List<ArtistSummary> {
    return songs
      .groupBy { it.artist }
      .map { (artist, group) ->
        ArtistSummary(
          name = artist,
          songCount = group.size,
          albumCount = group.map { it.album }.distinct().size,
        )
      }
      .sortedBy { it.name.lowercase() }
  }

  suspend fun toggleFavorite(songId: Long) {
    if (interactionDao.isFavorite(songId)) {
      interactionDao.removeFavorite(songId)
    } else {
      interactionDao.addFavorite(FavoriteEntity(songId = songId))
    }
  }

  suspend fun recordPlay(songId: Long) {
    interactionDao.recordSongPlay(songId)
  }

  suspend fun createPlaylist(name: String): Long {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return -1L
    return interactionDao.insertPlaylist(PlaylistEntity(name = trimmed))
  }

  suspend fun deletePlaylist(playlistId: Long) {
    interactionDao.deletePlaylist(playlistId)
  }

  suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
    val pos = interactionDao.nextPlaylistPosition(playlistId)
    interactionDao.addSongToPlaylist(
      PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = pos)
    )
  }

  suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
    interactionDao.removeSongFromPlaylist(playlistId, songId)
  }
}
