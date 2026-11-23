package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.lyrics.LyricMatcher
import com.example.R
import com.example.RoraxApplication
import com.example.data.db.DownloadTaskEntity
import com.example.data.repo.LyricLookupOutcome
import com.example.model.DownloadTaskStatus
import com.example.model.LyricSyncStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class BulkLyricsDownloadWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val app = applicationContext as RoraxApplication
    val localMusicRepo = app.localMusicRepository
    val lyricsRepo = app.lyricsRepository
    val taskDao = app.database.downloadTaskDao()
    val lyricsDao = app.database.lyricsDao()

    val retryOnly = inputData.getBoolean(KEY_RETRY_ONLY, false)
    val forceRefresh = inputData.getBoolean(KEY_FORCE_REFRESH, false)

    ensureChannel()

    val songs =
      if (localMusicRepo.songs.value.isNotEmpty()) {
        localMusicRepo.songs.value
      } else {
        localMusicRepo.scanLibrary().getOrDefault(emptyList())
      }

    if (songs.isEmpty()) {
      return Result.success()
    }

    val existingLyrics = lyricsDao.getAll().associateBy { it.songId }
    val existingTasks = taskDao.getAll().associateBy { it.songId }

    val targetSongs =
      songs.filter { song ->
        val cachedStatus =
          existingLyrics[song.id]?.status?.let {
            runCatching { LyricSyncStatus.valueOf(it) }.getOrNull()
          }
        val taskStatus =
          existingTasks[song.id]?.status?.let {
            runCatching { DownloadTaskStatus.valueOf(it) }.getOrNull()
          }

        if (retryOnly) {
          taskStatus == DownloadTaskStatus.FAILED_RETRYABLE ||
            taskStatus == DownloadTaskStatus.CANCELLED ||
            cachedStatus == LyricSyncStatus.FAILED_RETRYABLE
        } else {
          // Skip valid cached synced lyrics, but redo ones timed for a longer version of the song.
          cachedStatus != LyricSyncStatus.CACHED_SYNCED ||
            LyricMatcher.isMistimed(existingLyrics[song.id]?.syncedLyricsLrc, song.durationMs)
        }
      }

    if (targetSongs.isEmpty()) {
      return Result.success()
    }

    // Queue all target tasks in Room immediately so queue state survives process death
    val now = System.currentTimeMillis()
    taskDao.upsertAll(
      targetSongs.map { song ->
        val prevAttempts = existingTasks[song.id]?.attemptCount ?: 0
        DownloadTaskEntity(
          songId = song.id,
          title = song.title,
          artist = song.artist,
          album = song.album,
          durationSec = song.durationSec,
          status = DownloadTaskStatus.QUEUED.name,
          errorMessage = null,
          attemptCount = prevAttempts,
          updatedAtMs = now,
        )
      }
    )

    val total = targetSongs.size
    var completed = 0

    setForeground(createForegroundInfo(0, total, "Getting started…"))

    for (song in targetSongs) {
      if (isStopped) {
        taskDao.updateStatuses(
          targetStatuses = listOf(DownloadTaskStatus.QUEUED.name, DownloadTaskStatus.SEARCHING.name),
          newStatus = DownloadTaskStatus.CANCELLED.name,
        )
        return Result.success()
      }

      val prevTask = taskDao.getBySongId(song.id)
      val attempt = (prevTask?.attemptCount ?: 0) + 1

      taskDao.upsert(
        DownloadTaskEntity(
          songId = song.id,
          title = song.title,
          artist = song.artist,
          album = song.album,
          durationSec = song.durationSec,
          status = DownloadTaskStatus.SEARCHING.name,
          errorMessage = null,
          attemptCount = attempt,
          updatedAtMs = System.currentTimeMillis(),
        )
      )

      setForeground(createForegroundInfo(completed, total, "${song.title} — ${song.artist}"))

      try {
        val outcome = lyricsRepo.fetchAndCacheLyrics(song, forceRefresh = forceRefresh)
        val (taskStatus, errorMsg) =
          when (outcome) {
            is LyricLookupOutcome.Saved -> {
              when (outcome.record.status) {
                LyricSyncStatus.CACHED_SYNCED -> DownloadTaskStatus.DOWNLOADED_SYNCED to null
                LyricSyncStatus.CACHED_PLAIN -> DownloadTaskStatus.DOWNLOADED_PLAIN to "Lyrics available (not timed to the song)"
                LyricSyncStatus.INSTRUMENTAL -> DownloadTaskStatus.INSTRUMENTAL to null
                else -> DownloadTaskStatus.DOWNLOADED_PLAIN to null
              }
            }
            is LyricLookupOutcome.NeedsReview ->
              DownloadTaskStatus.NEEDS_REVIEW to "${outcome.candidates.size} possible matches"
            is LyricLookupOutcome.NotFound ->
              DownloadTaskStatus.NOT_FOUND to "No lyrics found"
            is LyricLookupOutcome.Failed -> {
              val status =
                if (outcome.retryable) DownloadTaskStatus.FAILED_RETRYABLE
                else DownloadTaskStatus.FAILED_PERMANENT
              status to outcome.message
            }
          }

        taskDao.upsert(
          DownloadTaskEntity(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationSec = song.durationSec,
            status = taskStatus.name,
            errorMessage = errorMsg,
            attemptCount = attempt,
            updatedAtMs = System.currentTimeMillis(),
          )
        )
      } catch (e: CancellationException) {
        taskDao.updateStatuses(
          targetStatuses = listOf(DownloadTaskStatus.QUEUED.name, DownloadTaskStatus.SEARCHING.name),
          newStatus = DownloadTaskStatus.CANCELLED.name,
        )
        throw e
      } catch (e: Exception) {
        taskDao.upsert(
          DownloadTaskEntity(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            durationSec = song.durationSec,
            status = DownloadTaskStatus.FAILED_RETRYABLE.name,
            errorMessage = "Couldn't get lyrics",
            attemptCount = attempt,
            updatedAtMs = System.currentTimeMillis(),
          )
        )
      }

      completed++
      // Pace requests so the lyric services are never flooded
      if (completed < total && !isStopped) {
        delay(380L)
      }
    }

    return Result.success()
  }

  private fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          "Lyric Downloads",
          NotificationManager.IMPORTANCE_LOW,
        ).apply {
          description = "Shows lyric download progress"
        }
      nm.createNotificationChannel(channel)
    }
  }

  private fun createForegroundInfo(completed: Int, total: Int, subtitle: String): ForegroundInfo {
    val notification =
      NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Downloading lyrics ($completed of $total)")
        .setContentText(subtitle)
        .setProgress(total.coerceAtLeast(1), completed, false)
        .setOngoing(true)
        .setSilent(true)
        .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ForegroundInfo(
        NOTIFICATION_ID,
        notification,
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
    } else {
      ForegroundInfo(NOTIFICATION_ID, notification)
    }
  }

  companion object {
    const val UNIQUE_WORK_NAME = "rorax_bulk_lyrics_download"
    private const val CHANNEL_ID = "rorax_lyrics_sync_channel"
    private const val NOTIFICATION_ID = 4201
    const val KEY_RETRY_ONLY = "retry_only"
    const val KEY_FORCE_REFRESH = "force_refresh"

    fun enqueue(
      context: Context,
      wifiOnly: Boolean,
      retryOnly: Boolean = false,
      forceRefresh: Boolean = false,
    ) {
      val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
      val constraints = Constraints.Builder().setRequiredNetworkType(networkType).build()
      val data =
        Data.Builder()
          .putBoolean(KEY_RETRY_ONLY, retryOnly)
          .putBoolean(KEY_FORCE_REFRESH, forceRefresh)
          .build()

      val request =
        OneTimeWorkRequestBuilder<BulkLyricsDownloadWorker>()
          .setConstraints(constraints)
          .setInputData(data)
          .build()

      WorkManager.getInstance(context)
        .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
      WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun observeIsRunning(context: Context): Flow<Boolean> {
      return WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME)
        .map { infos ->
          infos.any {
            it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
          }
        }
    }
  }
}
