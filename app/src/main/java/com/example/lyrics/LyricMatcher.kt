package com.example.lyrics

import com.example.model.LyricCandidate
import com.example.model.LyricQuery
import com.example.model.RemoteLyricTrack
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

sealed interface MatchEvaluation {
  data class ConfidentMatch(val candidate: LyricCandidate) : MatchEvaluation

  data class NeedsReview(val candidates: List<LyricCandidate>) : MatchEvaluation

  data object NoValidMatch : MatchEvaluation
}

object LyricMatcher {

  private val VERSION_KEYWORDS =
    listOf(
      "live",
      "acoustic",
      "remix",
      "instrumental",
      "radio edit",
      "remaster",
      "remastered",
      "demo",
      "sped up",
      "slowed",
      "karaoke",
      "cover",
      "unplugged",
      "deluxe",
      "reprise",
    )

  private val CRITICAL_VERSIONS =
    setOf("live", "remix", "acoustic", "instrumental", "karaoke", "cover", "sped up", "slowed")

  // Text in brackets that describes the release rather than the song, e.g. (Official Audio) or
  // (From "Aashiqui 2").
  private val NOISE_PARENS =
    Regex(
      """[\[(](official.*?|audio|lyric.*?|lyrical.*?|hd|hq|explicit|clean|feat\..*?|ft\..*?|from\b.*?|full\s+(song|video).*?|video.*?|with lyrics)[\])]""",
      RegexOption.IGNORE_CASE,
    )

  private val TRAILING_FROM = Regex("""\s[-–]\s*from\b.*$""", RegexOption.IGNORE_CASE)

  fun normalizeForQuery(raw: String): String {
    return NOISE_PARENS.replace(raw, " ")
      .replace(TRAILING_FROM, " ")
      .replace(Regex("""\s+"""), " ")
      .trim()
  }

  fun normalizeForCompare(raw: String): String {
    return raw
      .lowercase(Locale.ROOT)
      // Keep combining marks (\p{M}); Bengali, Hindi and other scripts store vowel signs there.
      .replace(Regex("""[^\p{L}\p{M}\p{N}\s]"""), " ")
      .replace(Regex("""\s+"""), " ")
      .trim()
  }

  fun extractVersionFlags(text: String): Set<String> {
    val lower = text.lowercase(Locale.ROOT)
    return VERSION_KEYWORDS.filter { keyword ->
        Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(lower)
      }
      .toSet()
  }

  /** Scores how well [track] describes the same recording as [query], ignoring lyric content. */
  fun scoreMetadata(query: LyricQuery, track: RemoteLyricTrack): Pair<Int, List<String>> {
    val trackName = track.trackName?.trim().orEmpty()
    val artistName = track.artistName?.trim().orEmpty()
    val albumName = track.albumName?.trim()?.takeIf { it.isNotEmpty() }
    val durationSec = track.durationSec?.toInt()

    val queryTitle = normalizeForQuery(query.title)
    val candidateTitle = normalizeForQuery(trackName)

    var score = 0
    val reasons = mutableListOf<String>()

    // 1. Title similarity (works across scripts, e.g. "Tum Hi Ho" vs "तुम ही हो")
    val titleScore = similarity(queryTitle, candidateTitle)
    score += (titleScore * 45).toInt()
    if (titleScore >= 0.999 && queryTitle.isNotEmpty()) {
      score += 12
      reasons.add("Exact title")
    }

    // 2. Artist similarity (only penalize heavily if the song has a real artist tag)
    val songArtistNorm = normalizeForCompare(query.artist)
    val hasKnownArtist =
      songArtistNorm.isNotEmpty() && songArtistNorm != "unknown artist" && songArtistNorm != "unknown"
    if (hasKnownArtist) {
      val artistScore = similarity(query.artist, artistName)
      score += (artistScore * 30).toInt()
      if (artistScore < 0.25) {
        score -= 30
        reasons.add("Artist mismatch")
      } else if (artistScore >= 0.85) {
        reasons.add("Artist match")
      }
    } else {
      score += 10
    }

    // 3. Version tag alignment (live/acoustic/remix/instrumental/radio edit/remaster)
    val songVersions = extractVersionFlags(query.title)
    val candVersions = extractVersionFlags(trackName)
    if (songVersions != candVersions) {
      val criticalDiff = (songVersions + candVersions) - (songVersions intersect candVersions)
      if (criticalDiff.any { it in CRITICAL_VERSIONS }) {
        score -= 32
        reasons.add("Version mismatch (${criticalDiff.joinToString()})")
      } else {
        score -= 10
      }
    } else if (songVersions.isNotEmpty()) {
      score += 8
      reasons.add("Version aligned")
    }

    // 4. Duration comparison
    if (durationSec != null && query.durationSec > 0) {
      val diff = abs(query.durationSec - durationSec)
      when {
        diff <= 2 -> {
          score += 20
          reasons.add("Exact duration")
        }
        diff <= MAX_SYNC_DRIFT_SEC -> {
          score += 14
          reasons.add("Close duration (±${diff}s)")
        }
        // A different length means a different edit, so its line timings would not match.
        diff <= 8 -> {
          score -= 12
          reasons.add("Duration differs by ${diff}s")
        }
        diff <= 25 -> {
          score -= 26
          reasons.add("Duration differs by ${diff}s")
        }
        else -> {
          score -= 40
          reasons.add("Duration differs by ${diff}s")
        }
      }
    }

    // 5. Album bonus
    val queryAlbumNorm = normalizeForCompare(query.album)
    if (
      queryAlbumNorm.isNotEmpty() &&
        queryAlbumNorm != "unknown album" &&
        albumName != null &&
        similarity(query.album, albumName) >= 0.7
    ) {
      score += 8
      reasons.add("Album match")
    }

    return score to reasons
  }

  fun scoreCandidate(query: LyricQuery, track: RemoteLyricTrack): LyricCandidate {
    val trackName = track.trackName?.trim().orEmpty()
    val artistName = track.artistName?.trim().orEmpty()
    val albumName = track.albumName?.trim()?.takeIf { it.isNotEmpty() }
    val durationSec = track.durationSec?.toInt()

    var (score, reasons) = scoreMetadata(query, track)
    val reasonList = reasons.toMutableList()

    // Lyrics availability bonus
    val hasValidSynced =
      !track.syncedLyrics.isNullOrBlank() && LrcParser.parse(track.syncedLyrics).hasValidTimestamps
    val hasPlain = !track.plainLyrics.isNullOrBlank()
    if (hasValidSynced) {
      score += 12
      reasonList.add("Timestamped LRC")
    } else if (hasPlain) {
      score += 4
      reasonList.add("Plain text")
    } else if (track.instrumental) {
      score += 4
      reasonList.add("Instrumental")
    } else {
      score -= 50
    }

    return LyricCandidate(
      id = track.id,
      trackName = trackName.ifEmpty { query.title },
      artistName = artistName.ifEmpty { query.artist },
      albumName = albumName,
      durationSec = durationSec,
      instrumental = track.instrumental,
      plainLyrics = track.plainLyrics?.takeIf { it.isNotBlank() },
      syncedLyrics = if (hasValidSynced) track.syncedLyrics else null,
      matchScore = score,
      matchReason = reasonList.joinToString(" • ").ifEmpty { "Metadata candidate" },
      provider = track.provider.name,
    )
  }

  /** Largest length difference, in seconds, at which another copy's line timings still line up. */
  const val MAX_SYNC_DRIFT_SEC = 3
  private const val END_MARGIN_MS = 3_000L

  /**
   * True when [syncedLrc] has a line later than the song ends. Those timings were made for a
   * longer version, so every line would be off against this file.
   */
  fun isMistimed(syncedLrc: String?, songDurationMs: Long): Boolean {
    if (syncedLrc.isNullOrBlank() || songDurationMs <= 0L) return false
    val lastLineMs = LrcParser.parse(syncedLrc).lines.maxOfOrNull { it.timestampMs } ?: return false
    return lastLineMs > songDurationMs + END_MARGIN_MS
  }

  private fun isTimingDoubtful(query: LyricQuery, candidate: LyricCandidate): Boolean {
    val candidateSec = candidate.durationSec
    if (candidateSec != null && query.durationSec > 0 &&
      abs(query.durationSec - candidateSec) > MAX_SYNC_DRIFT_SEC
    ) {
      return true
    }
    return isMistimed(candidate.syncedLyrics, query.durationSec * 1000L)
  }

  /** Timed lyrics that may not line up with this file are left for the user to confirm. */
  private fun confidentUnlessMistimed(
    query: LyricQuery,
    top: LyricCandidate,
    usable: List<LyricCandidate>,
  ): MatchEvaluation =
    if (top.hasSyncedLyrics && isTimingDoubtful(query, top)) {
      MatchEvaluation.NeedsReview(usable.take(6))
    } else {
      MatchEvaluation.ConfidentMatch(top)
    }

  fun evaluateCandidates(query: LyricQuery, tracks: List<RemoteLyricTrack>): MatchEvaluation {
    val usable =
      tracks
        .map { scoreCandidate(query, it) }
        .filter { it.hasSyncedLyrics || it.hasPlainLyrics || it.instrumental }
        .distinctBy { it.provider to it.id }
        .sortedByDescending { it.matchScore }

    if (usable.isEmpty()) return MatchEvaluation.NoValidMatch

    val top = usable.first()
    if (top.matchScore < 42) {
      val plausible = usable.filter { it.matchScore >= 28 }.take(6)
      return if (plausible.isNotEmpty()) {
        MatchEvaluation.NeedsReview(plausible)
      } else {
        MatchEvaluation.NoValidMatch
      }
    }

    // Multiple closely competing candidates with different content/versions need a human decision.
    val closeCompetitors =
      usable.drop(1).filter { cand ->
        cand.matchScore >= 45 && abs(top.matchScore - cand.matchScore) <= 6
      }

    // A very strong top candidate that is clearly ahead (or the only one with timestamps) wins.
    if (
      top.matchScore >= 78 &&
        (closeCompetitors.isEmpty() || !closeCompetitors.first().hasSyncedLyrics && top.hasSyncedLyrics)
    ) {
      return confidentUnlessMistimed(query, top, usable)
    }

    if (closeCompetitors.isNotEmpty()) {
      val distinctGroup = (listOf(top) + closeCompetitors).take(6)
      val differentSongs =
        distinctGroup.any {
          Romanizer.phoneticKey(it.trackName) != Romanizer.phoneticKey(top.trackName) ||
            Romanizer.phoneticKey(it.artistName) != Romanizer.phoneticKey(top.artistName) ||
            extractVersionFlags(it.trackName) != extractVersionFlags(top.trackName)
        }
      if (differentSongs) {
        return MatchEvaluation.NeedsReview(distinctGroup)
      }
    }

    return if (top.matchScore >= 58) {
      confidentUnlessMistimed(query, top, usable)
    } else {
      MatchEvaluation.NeedsReview(usable.take(6))
    }
  }

  /**
   * Similarity between two names in the range 0..1. Handles different scripts and spelling
   * variants by comparing romanized, phonetically folded forms as well as raw tokens.
   */
  internal fun similarity(a: String, b: String): Double {
    val normA = normalizeForCompare(a)
    val normB = normalizeForCompare(b)
    if (normA.isEmpty() || normB.isEmpty()) return 0.0
    val direct = tokenSimilarity(normA, normB)
    if (direct >= 1.0) return 1.0

    val nonLatinA = Romanizer.containsNonLatin(a)
    val nonLatinB = Romanizer.containsNonLatin(b)
    var best = direct
    if (nonLatinA || nonLatinB) {
      best = max(best, tokenSimilarity(Romanizer.foldForComparison(a), Romanizer.foldForComparison(b)))
    }
    val chars = charSimilarity(Romanizer.phoneticKey(a), Romanizer.phoneticKey(b))
    if (nonLatinA != nonLatinB || nonLatinA) {
      if (chars >= 0.7) best = max(best, chars * 0.97)
    } else if (chars >= 0.88) {
      // Same script: only trust very close spellings ("Pyar" vs "Pyaar").
      best = max(best, chars * 0.95)
    }
    return best
  }

  private fun charSimilarity(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    if (a.length < 3 || b.length < 3) return 0.0
    val distance = levenshtein(a, b)
    return 1.0 - distance.toDouble() / max(a.length, b.length)
  }

  private fun levenshtein(a: String, b: String): Int {
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
      current[0] = i
      for (j in 1..b.length) {
        val cost = if (a[i - 1] == b[j - 1]) 0 else 1
        current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
      }
      val swap = previous
      previous = current
      current = swap
    }
    return previous[b.length]
  }

  private fun tokenSimilarity(a: String, b: String): Double {
    if (a.isEmpty() || b.isEmpty()) return 0.0
    if (a == b) return 1.0
    val tokensA = a.split(' ').filter { it.isNotEmpty() }.toSet()
    val tokensB = b.split(' ').filter { it.isNotEmpty() }.toSet()
    if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
    val intersection = tokensA.intersect(tokensB).size.toDouble()
    val union = tokensA.union(tokensB).size.toDouble()
    val jaccard = intersection / union
    val containment =
      if (a.contains(b) || b.contains(a)) {
        0.85
      } else {
        0.0
      }
    return max(jaccard, containment)
  }
}
