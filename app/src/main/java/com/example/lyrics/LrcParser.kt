package com.example.lyrics

import com.example.model.LyricLine
import com.example.model.ParsedLrc
import java.util.Locale

object LrcParser {

  private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
  private val TAG_REGEX = Regex("""^\[([a-zA-Z]+):(.*)]$""")

  fun parse(rawInput: String?): ParsedLrc {
    if (rawInput.isNullOrBlank()) {
      return ParsedLrc(lines = emptyList())
    }

    val normalized = rawInput.removePrefix("\uFEFF")
    val rawLines = normalized.split('\n', '\r')

    var titleTag: String? = null
    var artistTag: String? = null
    var albumTag: String? = null
    var offsetMs = 0L
    val entries = mutableListOf<Pair<Long, String>>()

    for (rawLine in rawLines) {
      val line = rawLine.trim()
      if (line.isEmpty()) continue

      val timestamps = TIMESTAMP_REGEX.findAll(line).toList()
      if (timestamps.isEmpty()) {
        val tagMatch = TAG_REGEX.matchEntire(line)
        if (tagMatch != null) {
          val key = tagMatch.groupValues[1].lowercase(Locale.US)
          val value = tagMatch.groupValues[2].trim()
          when (key) {
            "ti" -> if (value.isNotEmpty()) titleTag = value
            "ar" -> if (value.isNotEmpty()) artistTag = value
            "al" -> if (value.isNotEmpty()) albumTag = value
            "offset" -> offsetMs = value.toLongOrNull() ?: 0L
          }
        }
        continue
      }

      // Remove all timestamp tags from the line to extract lyric text cleanly.
      val lyricText = TIMESTAMP_REGEX.replace(line, "").trim()

      for (match in timestamps) {
        val minutes = match.groupValues[1].toLongOrNull() ?: continue
        val seconds = match.groupValues[2].toLongOrNull() ?: continue
        if (seconds >= 60L) continue
        val fractionStr = match.groupValues[3]
        val fractionMs =
          when (fractionStr.length) {
            0 -> 0L
            1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
            2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
            else -> fractionStr.take(3).toLongOrNull() ?: 0L
          }
        val baseMs = (minutes * 60_000L) + (seconds * 1_000L) + fractionMs
        entries.add(baseMs to lyricText)
      }
    }

    if (entries.isEmpty()) {
      return ParsedLrc(
        lines = emptyList(),
        titleTag = titleTag,
        artistTag = artistTag,
        albumTag = albumTag,
        offsetMs = offsetMs,
      )
    }

    // Apply offset and sort stably by timestamp.
    val parsedLines =
      entries
        .map { (ts, text) ->
          val adjusted = (ts - offsetMs).coerceAtLeast(0L)
          LyricLine(timestampMs = adjusted, text = text)
        }
        .sortedBy { it.timestampMs }

    return ParsedLrc(
      lines = parsedLines,
      titleTag = titleTag,
      artistTag = artistTag,
      albumTag = albumTag,
      offsetMs = offsetMs,
    )
  }

  /**
   * Returns the index of the latest lyric line whose timestamp is <= [positionMs], or -1 if
   * playback has not reached the first lyric timestamp yet. Handles equal timestamps by returning
   * the last matching line at that timestamp (or non-empty line if available).
   */
  fun findActiveLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (lines.isEmpty() || positionMs < lines.first().timestampMs) {
      return -1
    }
    var low = 0
    var high = lines.lastIndex
    var result = 0
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (lines[mid].timestampMs <= positionMs) {
        result = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    // If multiple lines share the exact same timestamp, prefer the last non-empty one at that ts
    val targetTs = lines[result].timestampMs
    var best = result
    var i = result
    while (i >= 0 && lines[i].timestampMs == targetTs) {
      if (lines[i].text.isNotBlank()) {
        best = i
        break
      }
      i--
    }
    return best
  }

  fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
      String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
      String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
  }

  fun formatLrcTimestamp(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val minutes = clamped / 60_000L
    val seconds = (clamped % 60_000L) / 1000L
    val centis = (clamped % 1000L) / 10L
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, centis)
  }
}
