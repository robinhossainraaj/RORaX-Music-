package com.example.data.repo

import com.example.model.LrclibCandidate
import com.example.model.Song

sealed interface AiInsightResult {
  data class SampleResponse(
    val title: String,
    val content: String,
    val notice: String,
  ) : AiInsightResult

  data class Unavailable(val reason: String) : AiInsightResult
}

interface AiAssistantRepository {
  val isLiveGeminiConnected: Boolean

  suspend fun explainLyric(
    song: Song,
    selectedLine: String,
    surroundingLines: List<String>,
  ): AiInsightResult

  suspend fun suggestBestCandidate(
    song: Song,
    candidates: List<LrclibCandidate>,
  ): AiInsightResult
}

/**
 * Placeholder implementation used when Firebase AI Logic is not enabled.
 * Clearly labels sample responses and explains that live Gemini inference requires Firebase.
 */
class PlaceholderAiAssistantRepository : AiAssistantRepository {
  override val isLiveGeminiConnected: Boolean = false

  override suspend fun explainLyric(
    song: Song,
    selectedLine: String,
    surroundingLines: List<String>,
  ): AiInsightResult {
    val snippet = selectedLine.trim().ifEmpty { song.title }
    return AiInsightResult.SampleResponse(
      title = "Sample Lyric Insight — \"${snippet.take(44)}${if (snippet.length > 44) "…" else ""}\"",
      content =
        "In \"${song.title}\" by ${song.artist}, this line emphasizes emotional contrast and " +
          "narrative pacing within the surrounding verse. Core playback and offline lyric " +
          "synchronization work locally without AI.",
      notice = "AI features require Firebase AI Logic (not enabled). Showing a sample response.",
    )
  }

  override suspend fun suggestBestCandidate(
    song: Song,
    candidates: List<LrclibCandidate>,
  ): AiInsightResult {
    val best = candidates.maxByOrNull { it.matchScore }
    val recommendation =
      if (best != null) {
        "Highest metadata score (${best.matchScore}): \"${best.trackName}\" by ${best.artistName} " +
          "(${best.matchReason})."
      } else {
        "No candidates available to evaluate."
      }
    return AiInsightResult.SampleResponse(
      title = "Sample Match Suggestion",
      content = recommendation,
      notice = "AI features require Firebase AI Logic (not enabled). Using local metadata score.",
    )
  }
}
