package com.example.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics_cache")
data class LyricsEntity(
  @PrimaryKey val songId: Long,
  val status: String,
  val source: String,
  val lrclibId: Long? = null,
  val matchedTrackName: String? = null,
  val matchedArtistName: String? = null,
  val matchedAlbumName: String? = null,
  val syncedLyricsLrc: String? = null,
  val plainLyrics: String? = null,
  val candidatesJson: String? = null,
  val lastError: String? = null,
  val updatedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
  @PrimaryKey val songId: Long,
  val addedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "recent_plays")
data class RecentPlayEntity(
  @PrimaryKey val songId: Long,
  val playedAtMs: Long = System.currentTimeMillis(),
  val playCount: Int = 1,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val name: String,
  val createdAtMs: Long = System.currentTimeMillis(),
)

@Entity(
  tableName = "playlist_songs",
  primaryKeys = ["playlistId", "songId"],
  foreignKeys = [
    ForeignKey(
      entity = PlaylistEntity::class,
      parentColumns = ["id"],
      childColumns = ["playlistId"],
      onDelete = ForeignKey.CASCADE,
    )
  ],
  indices = [Index("songId")],
)
data class PlaylistSongCrossRef(
  val playlistId: Long,
  val songId: Long,
  val position: Int = 0,
  val addedAtMs: Long = System.currentTimeMillis(),
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
  @PrimaryKey val songId: Long,
  val title: String,
  val artist: String,
  val album: String,
  val durationSec: Int,
  val status: String,
  val errorMessage: String? = null,
  val attemptCount: Int = 0,
  val updatedAtMs: Long = System.currentTimeMillis(),
)
