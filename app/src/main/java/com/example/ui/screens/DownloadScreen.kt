package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.DownloadTaskEntity
import com.example.model.DownloadTaskStatus
import com.example.model.LyricRecord
import com.example.model.LyricSyncStatus
import com.example.model.Song
import com.example.ui.components.LyricStatusBadge
import com.example.ui.components.PermissionRequestCard
import com.example.ui.components.ReviewCandidatesSheet

private enum class DownloadFilter(val label: String) {
  ALL("All"),
  MISSING("Missing"),
  DOWNLOADED("Downloaded"),
  NEEDS_REVIEW("Review"),
  FAILED("Failed"),
}

@Composable
fun DownloadScreen(
  songs: List<Song>,
  lyricsMap: Map<Long, LyricRecord>,
  downloadTasks: List<DownloadTaskEntity>,
  inFlightSongIds: Set<Long>,
  isBulkDownloading: Boolean,
  hasAudioPermission: Boolean,
  showPermissionSettingsFallback: Boolean,
  aiEnabled: Boolean,
  onRequestPermission: () -> Unit,
  onOpenAppSettings: () -> Unit,
  onStartBulkDownload: () -> Unit,
  onCancelBulkDownload: () -> Unit,
  onRetryFailed: () -> Unit,
  onRetrySingleSong: (Song) -> Unit,
  onOpenManualSearch: (Song) -> Unit,
  onApplyCandidate: (Long, com.example.model.LyricCandidate) -> Unit,
  onImportLrc: (Song, Uri) -> Unit,
  onRequestAiSuggest: (Song, List<com.example.model.LyricCandidate>) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedFilter by remember { mutableStateOf(DownloadFilter.ALL) }
  var songForReview by remember { mutableStateOf<Song?>(null) }
  var pendingLrcImportSong by remember { mutableStateOf<Song?>(null) }

  val lrcPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      val target = pendingLrcImportSong
      pendingLrcImportSong = null
      if (uri != null && target != null) {
        onImportLrc(target, uri)
      }
    }

  val taskBySong = remember(downloadTasks) { downloadTasks.associateBy { it.songId } }

  // Real summary counts derived from local library + cached Room records + queue tasks
  val totalSongs = songs.size
  val syncedCount = songs.count { lyricsMap[it.id]?.status == LyricSyncStatus.CACHED_SYNCED }
  val plainCount = songs.count { lyricsMap[it.id]?.status == LyricSyncStatus.CACHED_PLAIN }
  val reviewCount = songs.count { lyricsMap[it.id]?.status == LyricSyncStatus.NEEDS_REVIEW }
  val notFoundCount = songs.count { lyricsMap[it.id]?.status == LyricSyncStatus.NOT_FOUND }
  val failedCount =
    songs.count {
      val s = lyricsMap[it.id]?.status
      val t = taskBySong[it.id]?.status
      s == LyricSyncStatus.FAILED_RETRYABLE ||
        s == LyricSyncStatus.FAILED_PERMANENT ||
        t == DownloadTaskStatus.FAILED_RETRYABLE.name ||
        t == DownloadTaskStatus.FAILED_PERMANENT.name ||
        t == DownloadTaskStatus.CANCELLED.name
    }
  val queuedCount =
    downloadTasks.count {
      it.status == DownloadTaskStatus.QUEUED.name || it.status == DownloadTaskStatus.SEARCHING.name
    }

  val completedInQueue =
    downloadTasks.count {
      it.status != DownloadTaskStatus.QUEUED.name && it.status != DownloadTaskStatus.SEARCHING.name
    }
  val totalInQueue = downloadTasks.size

  val filteredList =
    remember(songs, lyricsMap, taskBySong, selectedFilter) {
      songs.filter { song ->
        val record = lyricsMap[song.id]
        val taskStatus = taskBySong[song.id]?.status
        when (selectedFilter) {
          DownloadFilter.ALL -> true
          DownloadFilter.MISSING ->
            record == null ||
              record.status == LyricSyncStatus.NONE ||
              record.status == LyricSyncStatus.NOT_FOUND
          DownloadFilter.DOWNLOADED ->
            record?.status == LyricSyncStatus.CACHED_SYNCED ||
              record?.status == LyricSyncStatus.CACHED_PLAIN ||
              record?.status == LyricSyncStatus.INSTRUMENTAL
          DownloadFilter.NEEDS_REVIEW -> record?.status == LyricSyncStatus.NEEDS_REVIEW
          DownloadFilter.FAILED ->
            record?.status == LyricSyncStatus.FAILED_RETRYABLE ||
              record?.status == LyricSyncStatus.FAILED_PERMANENT ||
              taskStatus == DownloadTaskStatus.FAILED_RETRYABLE.name ||
              taskStatus == DownloadTaskStatus.FAILED_PERMANENT.name ||
              taskStatus == DownloadTaskStatus.CANCELLED.name
        }
      }
    }

  Column(modifier = modifier.fillMaxSize().testTag("download_screen")) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
      Text(
        text = stringResource(R.string.downloads_title),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )
      Text(
        text = stringResource(R.string.downloads_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (!hasAudioPermission) {
      Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        PermissionRequestCard(
          onRequestPermission = onRequestPermission,
          onOpenSettings = onOpenAppSettings,
          showSettingsFallback = showPermissionSettingsFallback,
        )
      }
      return
    }

    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(bottom = 20.dp),
    ) {
      // Summary Stats & Bulk Controls Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
          colors =
            CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
          shape = RoundedCornerShape(20.dp),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              StatTile("Total", totalSongs, MaterialTheme.colorScheme.onSurface)
              StatTile("Synced", syncedCount, MaterialTheme.colorScheme.primary)
              StatTile("Text only", plainCount, MaterialTheme.colorScheme.secondary)
              StatTile("Review", reviewCount, MaterialTheme.colorScheme.secondary)
            }
            Spacer(Modifier.height(10.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              StatTile("Waiting", queuedCount, MaterialTheme.colorScheme.primary)
              StatTile("Not Found", notFoundCount, MaterialTheme.colorScheme.onSurfaceVariant)
              StatTile("Failed", failedCount, MaterialTheme.colorScheme.error)
              StatTile(
                "Missing",
                (totalSongs - syncedCount - plainCount).coerceAtLeast(0),
                MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            if (isBulkDownloading || (totalInQueue > 0 && queuedCount > 0)) {
              Spacer(Modifier.height(14.dp))
              val progress =
                if (totalInQueue > 0) (completedInQueue.toFloat() / totalInQueue).coerceIn(0f, 1f)
                else 0f
              LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
              )
              Spacer(Modifier.height(6.dp))
              Text(
                text = "$completedInQueue of $totalInQueue songs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              if (isBulkDownloading) {
                Button(
                  onClick = onCancelBulkDownload,
                  modifier = Modifier.weight(1f).testTag("pause_bulk_download_button"),
                ) {
                  Icon(Icons.Rounded.Pause, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(R.string.downloads_pause))
                }
              } else {
                Button(
                  onClick = onStartBulkDownload,
                  enabled = songs.isNotEmpty(),
                  modifier = Modifier.weight(1f).testTag("download_all_lyrics_button"),
                ) {
                  Icon(Icons.Rounded.Download, contentDescription = null)
                  Spacer(Modifier.width(6.dp))
                  Text(stringResource(R.string.downloads_download_all))
                }
              }

              if (!isBulkDownloading && failedCount > 0) {
                OutlinedButton(
                  onClick = onRetryFailed,
                  modifier = Modifier.testTag("retry_failed_downloads_button"),
                ) {
                  Icon(Icons.Rounded.Refresh, contentDescription = null)
                  Spacer(Modifier.width(4.dp))
                  Text(stringResource(R.string.downloads_retry_failed))
                }
              }
            }
          }
        }
        Spacer(Modifier.height(12.dp))
      }

      // Filter Chips
      item {
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(horizontal = 16.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DownloadFilter.entries.forEach { filter ->
            FilterChip(
              selected = selectedFilter == filter,
              onClick = { selectedFilter = filter },
              label = { Text(filter.label) },
              modifier = Modifier.testTag("download_filter_${filter.name.lowercase()}"),
            )
          }
        }
        Spacer(Modifier.height(6.dp))
      }

      if (filteredList.isEmpty()) {
        item {
          Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text =
                if (songs.isEmpty()) "No local audio songs found."
                else "Nothing to show here.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        items(filteredList, key = { it.id }) { song ->
          val record = lyricsMap[song.id]
          val task = taskBySong[song.id]
          val isWorking =
            song.id in inFlightSongIds || task?.status == DownloadTaskStatus.SEARCHING.name

          DownloadSongCard(
            song = song,
            record = record,
            task = task,
            isWorking = isWorking,
            onRetry = { onRetrySingleSong(song) },
            onReview = { songForReview = song },
            onSearch = { onOpenManualSearch(song) },
            onImportLrc = {
              pendingLrcImportSong = song
              lrcPickerLauncher.launch(arrayOf("*/*"))
            },
          )
        }
      }
    }
  }

  songForReview?.let { targetSong ->
    val candidates = lyricsMap[targetSong.id]?.candidates.orEmpty()
    ReviewCandidatesSheet(
      song = targetSong,
      candidates = candidates,
      aiEnabled = aiEnabled,
      onSelectCandidate = { cand ->
        onApplyCandidate(targetSong.id, cand)
        songForReview = null
      },
      onOpenManualSearch = { onOpenManualSearch(targetSong) },
      onRequestAiSuggest = { onRequestAiSuggest(targetSong, candidates) },
      onDismiss = { songForReview = null },
    )
  }
}

@Composable
private fun StatTile(label: String, count: Int, color: androidx.compose.ui.graphics.Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = count.toString(),
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      color = color,
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun DownloadSongCard(
  song: Song,
  record: LyricRecord?,
  task: DownloadTaskEntity?,
  isWorking: Boolean,
  onRetry: () -> Unit,
  onReview: () -> Unit,
  onSearch: () -> Unit,
  onImportLrc: () -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = song.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (isWorking) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
          LyricStatusBadge(record?.status)
        }
      }

      val statusDetail =
        when {
          isWorking -> "Searching…"
          task?.status == DownloadTaskStatus.QUEUED.name -> "Waiting"
          record?.status == LyricSyncStatus.CACHED_SYNCED ->
            "Ready offline"
          record?.status == LyricSyncStatus.CACHED_PLAIN ->
            "Lyrics available (not timed to the song)"
          record?.status == LyricSyncStatus.NEEDS_REVIEW ->
            "${record.candidates.size} possible matches — tap Review"
          record?.status == LyricSyncStatus.NOT_FOUND ->
            "No lyrics found. Try searching manually or add a lyrics file."
          record?.status == LyricSyncStatus.FAILED_RETRYABLE ||
            record?.status == LyricSyncStatus.FAILED_PERMANENT ->
            record.lastError ?: "Couldn't get lyrics"
          task?.errorMessage != null -> task.errorMessage
          else -> "Not downloaded"
        }

      Spacer(Modifier.height(6.dp))
      Text(
        text = statusDetail,
        style = MaterialTheme.typography.bodySmall,
        color =
          if (record?.status == LyricSyncStatus.FAILED_RETRYABLE ||
            record?.status == LyricSyncStatus.FAILED_PERMANENT
          ) {
            MaterialTheme.colorScheme.error
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
      )

      Spacer(Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (record?.status == LyricSyncStatus.NEEDS_REVIEW && record.candidates.isNotEmpty()) {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(10.dp),
            onClick = onReview,
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp),
              )
              Spacer(Modifier.width(4.dp))
              Text(
                "Review",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
            }
          }
        }

        TextButton(onClick = onSearch) {
          Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Search")
        }

        TextButton(onClick = onImportLrc) {
          Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(4.dp))
          Text("Add file")
        }

        Spacer(Modifier.weight(1f))

        IconButton(
          onClick = onRetry,
          enabled = !isWorking,
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Icon(Icons.Rounded.Refresh, contentDescription = "Find lyrics again")
        }
      }
    }
  }
}
