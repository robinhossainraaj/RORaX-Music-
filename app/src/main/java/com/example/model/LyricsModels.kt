package com.example.model

import com.squareup.moshi.JsonClass

data class LyricLine(
  val timestampMs: Long,
  val text: String,
)

data class ParsedLrc(
  val lines: List<LyricLine>,
  val titleTag: String? = null,
  val artistTag: String? = null,
  val albumTag: String? = null,
  val offsetMs: Long = 0L,
) {
  val hasValidTimestamps: Boolean
    get() = lines.isNotEmpty()
}

enum class LyricSyncStatus {
  NONE,
  CACHED_SYNCED,
  CACHED_PLAIN,
  NEEDS_REVIEW,
  NOT_FOUND,
  FAILED_RETRYABLE,
  FAILED_PERMANENT,
  INSTRUMENTAL,
}

enum class LyricSource {
  LRCLIB,
  LOCAL_LRC,
  MANUAL_SELECTION,
  NONE,
}

enum class DownloadTaskStatus {
  QUEUED,
  SEARCHING,
  DOWNLOADED_SYNCED,
  DOWNLOADED_PLAIN,
  INSTRUMENTAL,
  NEEDS_REVIEW,
  NOT_FOUND,
  FAILED_RETRYABLE,
  FAILED_PERMANENT,
  CANCELLED,
}

@JsonClass(generateAdapter = true)
data class LrclibCandidate(
  val id: Long,
  val trackName: String,
  val artistName: String,
  val albumName: String?,
  val durationSec: Int?,
  val instrumental: Boolean,
  val plainLyrics: String?,
  val syncedLyrics: String?,
  val matchScore: Int = 0,
  val matchReason: String = "",
) {
  val hasSyncedLyrics: Boolean
    get() = !syncedLyrics.isNullOrBlank()

  val hasPlainLyrics: Boolean
    get() = !plainLyrics.isNullOrBlank()
}

data class LyricRecord(
  val songId: Long,
  val status: LyricSyncStatus,
  val source: LyricSource,
  val lrclibId: Long?,
  val matchedTrackName: String?,
  val matchedArtistName: String?,
  val matchedAlbumName: String?,
  val syncedLyricsLrc: String?,
  val plainLyrics: String?,
  val candidates: List<LrclibCandidate>,
  val lastError: String?,
  val updatedAtMs: Long,
)
