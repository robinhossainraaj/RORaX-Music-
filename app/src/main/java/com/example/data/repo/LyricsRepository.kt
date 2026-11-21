package com.example.data.repo

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.data.db.DownloadTaskDao
import com.example.data.db.DownloadTaskEntity
import com.example.data.db.LyricsDao
import com.example.data.db.LyricsEntity
import com.example.lyrics.LrcParser
import com.example.lyrics.LrclibApi
import com.example.lyrics.LrclibTrackDto
import com.example.lyrics.LyricMatcher
import com.example.lyrics.MatchEvaluation
import com.example.model.DownloadTaskStatus
import com.example.model.LrclibCandidate
import com.example.model.LyricRecord
import com.example.model.LyricSource
import com.example.model.LyricSyncStatus
import com.example.model.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response

sealed interface LyricLookupOutcome {
  data class Saved(val record: LyricRecord) : LyricLookupOutcome

  data class NeedsReview(val record: LyricRecord, val candidates: List<LrclibCandidate>) :
    LyricLookupOutcome

  data class NotFound(val record: LyricRecord) : LyricLookupOutcome

  data class Failed(val message: String, val retryable: Boolean, val preservedExisting: Boolean) :
    LyricLookupOutcome
}

class LyricsRepository(
  private val context: Context,
  private val lyricsDao: LyricsDao,
  private val downloadTaskDao: DownloadTaskDao,
  private val lrclibApi: LrclibApi,
  private val moshi: Moshi,
) {
  private val candidateListType =
    Types.newParameterizedType(List::class.java, LrclibCandidate::class.java)
  private val candidateAdapter = moshi.adapter<List<LrclibCandidate>>(candidateListType)

  val allLyricsFlow: Flow<Map<Long, LyricRecord>> =
    lyricsDao.observeAll().map { entities -> entities.associate { it.songId to it.toDomain() } }

  val downloadTasksFlow: Flow<List<DownloadTaskEntity>> = downloadTaskDao.observeAll()

  val cacheSizeBytesFlow: Flow<Long> = lyricsDao.observeCacheSizeBytes()

  fun observeLyricsForSong(songId: Long): Flow<LyricRecord?> =
    lyricsDao.observeBySongId(songId).map { it?.toDomain() }

  suspend fun getCachedLyrics(songId: Long): LyricRecord? =
    lyricsDao.getBySongId(songId)?.toDomain()

  fun isNetworkAvailable(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }

  fun isOnWifi(): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
      caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
  }

  /**
   * Fetches lyrics for a single song from LRCLIB using `/api/get` then `/api/search` if needed.
   * Never overwrites existing [LyricSyncStatus.CACHED_SYNCED] lyrics on failure or not-found.
   */
  suspend fun fetchAndCacheLyrics(
    song: Song,
    forceRefresh: Boolean = false,
  ): LyricLookupOutcome = withContext(Dispatchers.IO) {
    val existing = lyricsDao.getBySongId(song.id)?.toDomain()
    if (!forceRefresh && existing?.status == LyricSyncStatus.CACHED_SYNCED) {
      return@withContext LyricLookupOutcome.Saved(existing)
    }

    if (!isNetworkAvailable()) {
      return@withContext LyricLookupOutcome.Failed(
        message = "Offline — connect to the internet to look up lyrics.",
        retryable = true,
        preservedExisting = existing != null,
      )
    }

    try {
      val cleanTitle = LyricMatcher.normalizeForQuery(song.title).ifEmpty { song.title }
      val cleanArtist =
        if (song.artist.equals("Unknown Artist", ignoreCase = true)) "" else song.artist
      val cleanAlbum =
        if (song.album.equals("Unknown Album", ignoreCase = true)) null else song.album

      val collectedDtos = mutableListOf<LrclibTrackDto>()

      // 1. Exact endpoint when we have a real artist name
      if (cleanArtist.isNotEmpty()) {
        val getResp: Response<LrclibTrackDto> =
          lrclibApi.getLyrics(
            trackName = cleanTitle,
            artistName = cleanArtist,
            albumName = cleanAlbum,
            durationSec = song.durationSec.takeIf { it > 0 },
          )
        when {
          getResp.isSuccessful -> getResp.body()?.let { collectedDtos.add(it) }
          getResp.code() == 429 -> {
            val retryAfter = getResp.headers()["Retry-After"]
            return@withContext recordFailure(
              song = song,
              existing = existing,
              message = "LRCLIB rate limit reached${retryAfter?.let { " (retry after ${it}s)" } ?: ""}.",
              retryable = true,
            )
          }
          getResp.code() >= 500 -> {
            return@withContext recordFailure(
              song = song,
              existing = existing,
              message = "LRCLIB server error (${getResp.code()}).",
              retryable = true,
            )
          }
          // 404 is normal when exact metadata doesn't match; fall through to /api/search
        }
      }

      // Evaluate exact result first if found
      if (collectedDtos.isNotEmpty()) {
        when (val eval = LyricMatcher.evaluateCandidates(song, collectedDtos)) {
          is MatchEvaluation.ConfidentMatch -> {
            if (eval.candidate.hasSyncedLyrics) {
              val saved = saveCandidate(song, eval.candidate, LyricSource.LRCLIB)
              return@withContext LyricLookupOutcome.Saved(saved)
            }
          }
          else -> Unit
        }
      }

      // 2. Search fallback using /api/search
      val searchResp =
        if (cleanArtist.isNotEmpty()) {
          lrclibApi.searchLyrics(trackName = cleanTitle, artistName = cleanArtist)
        } else {
          lrclibApi.searchLyrics(query = cleanTitle)
        }

      when {
        searchResp.isSuccessful -> {
          searchResp.body()?.let { collectedDtos.addAll(it) }
        }
        searchResp.code() == 429 -> {
          return@withContext recordFailure(
            song = song,
            existing = existing,
            message = "LRCLIB rate limit reached. Please retry shortly.",
            retryable = true,
          )
        }
        searchResp.code() >= 500 -> {
          return@withContext recordFailure(
            song = song,
            existing = existing,
            message = "LRCLIB server error (${searchResp.code()}).",
            retryable = true,
          )
        }
      }

      when (val eval = LyricMatcher.evaluateCandidates(song, collectedDtos)) {
        is MatchEvaluation.ConfidentMatch -> {
          val saved = saveCandidate(song, eval.candidate, LyricSource.LRCLIB)
          LyricLookupOutcome.Saved(saved)
        }
        is MatchEvaluation.NeedsReview -> {
          // Never overwrite good cached synced lyrics with Needs Review
          if (existing?.status == LyricSyncStatus.CACHED_SYNCED) {
            return@withContext LyricLookupOutcome.Saved(existing)
          }
          val entity =
            LyricsEntity(
              songId = song.id,
              status = LyricSyncStatus.NEEDS_REVIEW.name,
              source = LyricSource.LRCLIB.name,
              candidatesJson = candidateAdapter.toJson(eval.candidates),
              lastError = "Multiple plausible lyric matches found",
              updatedAtMs = System.currentTimeMillis(),
            )
          lyricsDao.upsert(entity)
          LyricLookupOutcome.NeedsReview(entity.toDomain(), eval.candidates)
        }
        MatchEvaluation.NoValidMatch -> {
          if (existing?.status == LyricSyncStatus.CACHED_SYNCED || existing?.status == LyricSyncStatus.CACHED_PLAIN) {
            return@withContext LyricLookupOutcome.Saved(existing)
          }
          val entity =
            LyricsEntity(
              songId = song.id,
              status = LyricSyncStatus.NOT_FOUND.name,
              source = LyricSource.NONE.name,
              lastError = "No matching lyrics found on LRCLIB",
              updatedAtMs = System.currentTimeMillis(),
            )
          lyricsDao.upsert(entity)
          LyricLookupOutcome.NotFound(entity.toDomain())
        }
      }
    } catch (e: IOException) {
      recordFailure(
        song = song,
        existing = existing,
        message = "Network error: ${e.localizedMessage ?: "connection failed"}",
        retryable = true,
      )
    } catch (e: Exception) {
      recordFailure(
        song = song,
        existing = existing,
        message = "Unexpected lookup error: ${e.localizedMessage ?: "malformed response"}",
        retryable = false,
      )
    }
  }

  suspend fun manualSearch(song: Song, customQuery: String): Result<List<LrclibCandidate>> =
    withContext(Dispatchers.IO) {
      if (!isNetworkAvailable()) {
        return@withContext Result.failure(IOException("Offline — internet connection required to search LRCLIB."))
      }
      try {
        val resp = lrclibApi.searchLyrics(query = customQuery.trim())
        if (!resp.isSuccessful) {
          return@withContext Result.failure(IOException("LRCLIB returned HTTP ${resp.code()}"))
        }
        val candidates =
          resp.body()
            .orEmpty()
            .map { LyricMatcher.scoreCandidate(song, it) }
            .filter { it.hasSyncedLyrics || it.hasPlainLyrics || it.instrumental }
            .sortedByDescending { it.matchScore }
        Result.success(candidates)
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun applyCandidate(songId: Long, candidate: LrclibCandidate): LyricRecord =
    withContext(Dispatchers.IO) {
      val status =
        when {
          candidate.hasSyncedLyrics -> LyricSyncStatus.CACHED_SYNCED
          candidate.hasPlainLyrics -> LyricSyncStatus.CACHED_PLAIN
          candidate.instrumental -> LyricSyncStatus.INSTRUMENTAL
          else -> LyricSyncStatus.NOT_FOUND
        }
      val entity =
        LyricsEntity(
          songId = songId,
          status = status.name,
          source = LyricSource.MANUAL_SELECTION.name,
          lrclibId = candidate.id,
          matchedTrackName = candidate.trackName,
          matchedArtistName = candidate.artistName,
          matchedAlbumName = candidate.albumName,
          syncedLyricsLrc = candidate.syncedLyrics,
          plainLyrics = candidate.plainLyrics,
          candidatesJson = null,
          lastError = null,
          updatedAtMs = System.currentTimeMillis(),
        )
      lyricsDao.upsert(entity)
      downloadTaskDao.upsert(
        DownloadTaskEntity(
          songId = songId,
          title = candidate.trackName,
          artist = candidate.artistName,
          album = candidate.albumName.orEmpty(),
          durationSec = candidate.durationSec ?: 0,
          status =
            when (status) {
              LyricSyncStatus.CACHED_SYNCED -> DownloadTaskStatus.DOWNLOADED_SYNCED.name
              LyricSyncStatus.CACHED_PLAIN -> DownloadTaskStatus.DOWNLOADED_PLAIN.name
              LyricSyncStatus.INSTRUMENTAL -> DownloadTaskStatus.INSTRUMENTAL.name
              else -> DownloadTaskStatus.NOT_FOUND.name
            },
          updatedAtMs = System.currentTimeMillis(),
        )
      )
      entity.toDomain()
    }

  suspend fun importLrcFromUri(song: Song, uri: Uri): Result<LyricRecord> =
    withContext(Dispatchers.IO) {
      try {
        val rawText =
          context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return@withContext Result.failure(IOException("Unable to read selected file."))
        if (rawText.isBlank()) {
          return@withContext Result.failure(IllegalArgumentException("The selected file is empty."))
        }
        val parsed = LrcParser.parse(rawText)
        val status =
          if (parsed.hasValidTimestamps) LyricSyncStatus.CACHED_SYNCED else LyricSyncStatus.CACHED_PLAIN
        val entity =
          LyricsEntity(
            songId = song.id,
            status = status.name,
            source = LyricSource.LOCAL_LRC.name,
            matchedTrackName = parsed.titleTag ?: song.title,
            matchedArtistName = parsed.artistTag ?: song.artist,
            matchedAlbumName = parsed.albumTag ?: song.album,
            syncedLyricsLrc = if (parsed.hasValidTimestamps) rawText else null,
            plainLyrics = if (!parsed.hasValidTimestamps) rawText else null,
            candidatesJson = null,
            lastError = null,
            updatedAtMs = System.currentTimeMillis(),
          )
        lyricsDao.upsert(entity)
        downloadTaskDao.upsert(
          DownloadTaskEntity(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationSec = song.durationSec,
            status =
              if (status == LyricSyncStatus.CACHED_SYNCED) {
                DownloadTaskStatus.DOWNLOADED_SYNCED.name
              } else {
                DownloadTaskStatus.DOWNLOADED_PLAIN.name
              },
            updatedAtMs = System.currentTimeMillis(),
          )
        )
        Result.success(entity.toDomain())
      } catch (e: Exception) {
        Result.failure(e)
      }
    }

  suspend fun clearLyricCache() = withContext(Dispatchers.IO) {
    lyricsDao.clearAll()
    downloadTaskDao.clearAll()
  }

  private suspend fun saveCandidate(
    song: Song,
    candidate: LrclibCandidate,
    source: LyricSource,
  ): LyricRecord {
    val status =
      when {
        candidate.hasSyncedLyrics -> LyricSyncStatus.CACHED_SYNCED
        candidate.hasPlainLyrics -> LyricSyncStatus.CACHED_PLAIN
        candidate.instrumental -> LyricSyncStatus.INSTRUMENTAL
        else -> LyricSyncStatus.NOT_FOUND
      }
    val entity =
      LyricsEntity(
        songId = song.id,
        status = status.name,
        source = source.name,
        lrclibId = candidate.id,
        matchedTrackName = candidate.trackName,
        matchedArtistName = candidate.artistName,
        matchedAlbumName = candidate.albumName,
        syncedLyricsLrc = candidate.syncedLyrics,
        plainLyrics = candidate.plainLyrics,
        candidatesJson = null,
        lastError = null,
        updatedAtMs = System.currentTimeMillis(),
      )
    lyricsDao.upsert(entity)
    return entity.toDomain()
  }

  private suspend fun recordFailure(
    song: Song,
    existing: LyricRecord?,
    message: String,
    retryable: Boolean,
  ): LyricLookupOutcome.Failed {
    // Preserve good cached lyrics instead of overwriting them on failure
    if (existing == null || existing.status == LyricSyncStatus.NONE || existing.status == LyricSyncStatus.FAILED_RETRYABLE) {
      val failStatus = if (retryable) LyricSyncStatus.FAILED_RETRYABLE else LyricSyncStatus.FAILED_PERMANENT
      lyricsDao.upsert(
        LyricsEntity(
          songId = song.id,
          status = failStatus.name,
          source = LyricSource.NONE.name,
          lastError = message,
          updatedAtMs = System.currentTimeMillis(),
        )
      )
    }
    return LyricLookupOutcome.Failed(
      message = message,
      retryable = retryable,
      preservedExisting = existing != null && existing.status in setOf(LyricSyncStatus.CACHED_SYNCED, LyricSyncStatus.CACHED_PLAIN),
    )
  }

  private fun LyricsEntity.toDomain(): LyricRecord {
    val parsedCandidates =
      if (!candidatesJson.isNullOrBlank()) {
        runCatching { candidateAdapter.fromJson(candidatesJson) }.getOrNull().orEmpty()
      } else {
        emptyList()
      }
    return LyricRecord(
      songId = songId,
      status = runCatching { LyricSyncStatus.valueOf(status) }.getOrDefault(LyricSyncStatus.NONE),
      source = runCatching { LyricSource.valueOf(source) }.getOrDefault(LyricSource.NONE),
      lrclibId = lrclibId,
      matchedTrackName = matchedTrackName,
      matchedArtistName = matchedArtistName,
      matchedAlbumName = matchedAlbumName,
      syncedLyricsLrc = syncedLyricsLrc,
      plainLyrics = plainLyrics,
      candidates = parsedCandidates,
      lastError = lastError,
      updatedAtMs = updatedAtMs,
    )
  }
}
