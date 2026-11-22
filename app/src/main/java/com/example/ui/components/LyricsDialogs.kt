package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.repo.AiInsightResult
import com.example.lyrics.LrcParser
import com.example.model.LyricCandidate
import com.example.model.Song
import com.example.ui.viewmodel.AiSheetUiState
import com.example.ui.viewmodel.ManualSearchUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualLyricSearchSheet(
  state: ManualSearchUiState,
  aiEnabled: Boolean,
  onQueryChange: (String) -> Unit,
  onSearch: () -> Unit,
  onSelectCandidate: (LyricCandidate) -> Unit,
  onRequestAiSuggest: (Song, List<LyricCandidate>) -> Unit,
  onDismiss: () -> Unit,
) {
  val song = state.song ?: return
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
      Text(
        text = "Find Lyrics",
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        text = "${song.title} • ${song.artist} (${LrcParser.formatTime(song.durationMs)})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(12.dp))
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        OutlinedTextField(
          value = state.query,
          onValueChange = onQueryChange,
          label = { Text("Song or artist") },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSearch) {
          Icon(Icons.Rounded.Search, contentDescription = "Search")
        }
      }

      if (aiEnabled && state.results.size > 1) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { onRequestAiSuggest(song, state.results) }) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("AI Match Suggestion")
        }
      }

      Spacer(Modifier.height(12.dp))
      when {
        state.isLoading -> {
          Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
        state.error != null -> {
          Text(
            text = state.error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 20.dp),
          )
        }
        state.results.isEmpty() -> {
          Text(
            text = "No lyrics found. Try different words, or add a lyrics file.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 20.dp),
          )
        }
        else -> {
          LazyColumn(
            modifier = Modifier.height(340.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(state.results, key = { it.id }) { cand ->
              CandidateRow(candidate = cand, onSelect = { onSelectCandidate(cand) })
            }
          }
        }
      }
      Spacer(Modifier.height(20.dp))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewCandidatesSheet(
  song: Song,
  candidates: List<LyricCandidate>,
  aiEnabled: Boolean,
  onSelectCandidate: (LyricCandidate) -> Unit,
  onOpenManualSearch: () -> Unit,
  onRequestAiSuggest: () -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
      Text(
        text = "Choose Lyric Match",
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        text = "We found a few possible matches for \"${song.title}\". Pick the right one:",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      if (aiEnabled && candidates.size > 1) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onRequestAiSuggest) {
          Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
          Spacer(Modifier.width(6.dp))
          Text("AI Match Suggestion")
        }
      }
      Spacer(Modifier.height(12.dp))
      LazyColumn(
        modifier = Modifier.height(320.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(candidates, key = { it.id }) { cand ->
          CandidateRow(candidate = cand, onSelect = { onSelectCandidate(cand) })
        }
      }
      Spacer(Modifier.height(10.dp))
      OutlinedButton(
        onClick = {
          onDismiss()
          onOpenManualSearch()
        },
        modifier = Modifier.fillMaxWidth(),
      ) {
        Icon(Icons.Rounded.Search, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Search Manually")
      }
      Spacer(Modifier.height(20.dp))
    }
  }
}

@Composable
private fun CandidateRow(candidate: LyricCandidate, onSelect: () -> Unit) {
  Card(
    modifier =
      Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onSelect),
    colors =
      CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = candidate.trackName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text =
            buildString {
              append(candidate.artistName)
              candidate.albumName?.let { append(" • $it") }
              candidate.durationSec?.let { append(" • ${LrcParser.formatTime(it * 1000L)}") }
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          val badgeText =
            when {
              candidate.hasSyncedLyrics -> "SYNCED"
              candidate.hasPlainLyrics -> "TEXT ONLY"
              candidate.instrumental -> "INSTRUMENTAL"
              else -> "NO LYRICS"
            }
          val badgeColor =
            if (candidate.hasSyncedLyrics) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.secondary
          Surface(
            color = badgeColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(6.dp),
          ) {
            Text(
              text = badgeText,
              style = MaterialTheme.typography.labelSmall,
              color = badgeColor,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
          }
        }
      }
      Spacer(Modifier.width(8.dp))
      Icon(
        imageVector = Icons.Rounded.CheckCircle,
        contentDescription = "Apply match",
        tint = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
fun AiInsightDialog(
  state: AiSheetUiState,
  onDismiss: () -> Unit,
) {
  if (!state.isVisible) return
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Icon(
        imageVector = Icons.Rounded.AutoAwesome,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    title = {
      val heading =
        when (val res = state.result) {
          is AiInsightResult.SampleResponse -> res.title
          is AiInsightResult.Unavailable -> "AI Assistance Unavailable"
          null -> "Analyzing lyric…"
        }
      Text(heading, style = MaterialTheme.typography.titleLarge)
    },
    text = {
      when {
        state.isLoading -> {
          Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        }
        state.result is AiInsightResult.SampleResponse -> {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(state.result.content, style = MaterialTheme.typography.bodyMedium)
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(10.dp),
            ) {
              Text(
                text = state.result.notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(10.dp),
              )
            }
          }
        }
        state.result is AiInsightResult.Unavailable -> {
          Text(state.result.reason, style = MaterialTheme.typography.bodyMedium)
        }
      }
    },
    confirmButton = {
      Button(onClick = onDismiss) { Text("Close") }
    },
  )
}
