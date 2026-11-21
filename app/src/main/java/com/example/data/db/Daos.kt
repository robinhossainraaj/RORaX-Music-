package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
  @Query("SELECT * FROM lyrics_cache")
  fun observeAll(): Flow<List<LyricsEntity>>

  @Query("SELECT * FROM lyrics_cache WHERE songId = :songId LIMIT 1")
  fun observeBySongId(songId: Long): Flow<LyricsEntity?>

  @Query("SELECT * FROM lyrics_cache WHERE songId = :songId LIMIT 1")
  suspend fun getBySongId(songId: Long): LyricsEntity?

  @Query("SELECT * FROM lyrics_cache")
  suspend fun getAll(): List<LyricsEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: LyricsEntity)

  @Query("DELETE FROM lyrics_cache WHERE songId = :songId")
  suspend fun deleteBySongId(songId: Long)

  @Query("DELETE FROM lyrics_cache")
  suspend fun clearAll()

  @Query(
    "SELECT COALESCE(SUM(LENGTH(COALESCE(syncedLyricsLrc, '')) + LENGTH(COALESCE(plainLyrics, '')) + LENGTH(COALESCE(candidatesJson, ''))), 0) FROM lyrics_cache"
  )
  fun observeCacheSizeBytes(): Flow<Long>
}

@Dao
interface LibraryInteractionDao {
  @Query("SELECT songId FROM favorites ORDER BY addedAtMs DESC")
  fun observeFavoriteIds(): Flow<List<Long>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addFavorite(entity: FavoriteEntity)

  @Query("DELETE FROM favorites WHERE songId = :songId")
  suspend fun removeFavorite(songId: Long)

  @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
  suspend fun isFavorite(songId: Long): Boolean

  @Query("SELECT * FROM recent_plays ORDER BY playedAtMs DESC LIMIT 30")
  fun observeRecentPlays(): Flow<List<RecentPlayEntity>>

  @Query("SELECT * FROM recent_plays WHERE songId = :songId LIMIT 1")
  suspend fun getRecentPlay(songId: Long): RecentPlayEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertRecentPlay(entity: RecentPlayEntity)

  @Query("SELECT * FROM playlists ORDER BY createdAtMs DESC")
  fun observePlaylists(): Flow<List<PlaylistEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertPlaylist(playlist: PlaylistEntity): Long

  @Query("DELETE FROM playlists WHERE id = :playlistId")
  suspend fun deletePlaylist(playlistId: Long)

  @Query("SELECT * FROM playlist_songs ORDER BY position ASC, addedAtMs ASC")
  fun observeAllPlaylistSongs(): Flow<List<PlaylistSongCrossRef>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

  @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
  suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

  @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
  suspend fun nextPlaylistPosition(playlistId: Long): Int

  @Transaction
  suspend fun recordSongPlay(songId: Long) {
    val existing = getRecentPlay(songId)
    val nextCount = (existing?.playCount ?: 0) + 1
    upsertRecentPlay(
      RecentPlayEntity(
        songId = songId,
        playedAtMs = System.currentTimeMillis(),
        playCount = nextCount,
      )
    )
  }
}

@Dao
interface DownloadTaskDao {
  @Query("SELECT * FROM download_tasks ORDER BY updatedAtMs DESC")
  fun observeAll(): Flow<List<DownloadTaskEntity>>

  @Query("SELECT * FROM download_tasks")
  suspend fun getAll(): List<DownloadTaskEntity>

  @Query("SELECT * FROM download_tasks WHERE songId = :songId LIMIT 1")
  suspend fun getBySongId(songId: Long): DownloadTaskEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(tasks: List<DownloadTaskEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(task: DownloadTaskEntity)

  @Query("UPDATE download_tasks SET status = :newStatus, updatedAtMs = :now WHERE status IN (:targetStatuses)")
  suspend fun updateStatuses(targetStatuses: List<String>, newStatus: String, now: Long = System.currentTimeMillis())

  @Query("DELETE FROM download_tasks")
  suspend fun clearAll()
}
