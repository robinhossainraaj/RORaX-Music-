package com.example.model

import android.net.Uri

data class Song(
  val id: Long,
  val title: String,
  val artist: String,
  val album: String,
  val albumId: Long,
  val durationMs: Long,
  val contentUri: Uri,
  val albumArtUri: Uri?,
  val dateAddedSec: Long,
  val trackNumber: Int,
  val mimeType: String,
  val sizeBytes: Long,
) {
  val durationSec: Int
    get() = (durationMs / 1000L).toInt().coerceAtLeast(0)
}

data class AlbumSummary(
  val albumId: Long,
  val name: String,
  val artist: String,
  val songCount: Int,
  val albumArtUri: Uri?,
)

data class ArtistSummary(
  val name: String,
  val songCount: Int,
  val albumCount: Int,
)

data class PlaylistWithSongs(
  val id: Long,
  val name: String,
  val createdAtMs: Long,
  val songs: List<Song>,
)
