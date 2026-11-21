package com.example.lyrics

import com.example.model.LrclibCandidate
import com.example.model.Song
import java.util.Locale
import kotlin.math.abs

sealed interface MatchEvaluation {
  data class ConfidentMatch(val candidate: LrclibCandidate) : MatchEvaluation

  data class NeedsReview(val candidates: List<LrclibCandidate>) : MatchEvaluation

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
    )

  private val NOISE_PARENS =
    Regex("""[\[(](official.*?|audio|lyric.*?|hd|hq|explicit|clean|feat\..*?|ft\..*?)[\])]""", RegexOption.IGNORE_CASE)

  fun normalizeForQuery(raw: String): String {
    return NOISE_PARENS.replace(raw, " ").replace(Regex("""\s+"""), " ").trim()
  }

  fun normalizeForCompare(raw: String): String {
    return raw
      .lowercase(Locale.US)
      .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
      .replace(Regex("""\s+"""), " ")
      .trim()
  }

  fun extractVersionFlags(text: String): Set<String> {
    val lower = text.lowercase(Locale.US)
    return VERSION_KEYWORDS.filter { keyword ->
        Regex("""\b${Regex.escape(keyword)}\b""").containsMatchIn(lower)
      }
      .toSet()
  }

  fun scoreCandidate(song: Song, dto: LrclibTrackDto): LrclibCandidate {
    val trackName = dto.trackName?.trim().orEmpty()
    val artistName = dto.artistName?.trim().orEmpty()
    val albumName = dto.albumName?.trim()?.takeIf { it.isNotEmpty() }
    val durationSec = dto.duration?.toInt()
    val instrumental = dto.instrumental == true

    val songTitleNorm = normalizeForCompare(normalizeForQuery(song.title))
    val candTitleNorm = normalizeForCompare(normalizeForQuery(trackName))
    val songArtistNorm = normalizeForCompare(song.artist)
    val candArtistNorm = normalizeForCompare(artistName)

    var score = 0
    val reasons = mutableListOf<String>()

    // 1. Title similarity
    val titleScore = tokenSimilarity(songTitleNorm, candTitleNorm)
    score += (titleScore * 45).toInt()
    if (songTitleNorm == candTitleNorm && songTitleNorm.isNotEmpty()) {
      score += 12
      reasons.add("Exact title")
    }

    // 2. Artist similarity (only penalize heavily if song has a real artist tag)
    val hasKnownArtist =
      songArtistNorm.isNotEmpty() &&
        songArtistNorm != "unknown artist" &&
        songArtistNorm != "unknown"
    if (hasKnownArtist) {
      val artistScore = tokenSimilarity(songArtistNorm, candArtistNorm)
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
    val songVersions = extractVersionFlags(song.title)
    val candVersions = extractVersionFlags(trackName)
    if (songVersions != candVersions) {
      val criticalDiff = (songVersions + candVersions) - (songVersions intersect candVersions)
      if (criticalDiff.any { it in setOf("live", "remix", "acoustic", "instrumental", "karaoke", "cover", "sped up", "slowed") }) {
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
    if (durationSec != null && song.durationSec > 0) {
      val diff = abs(song.durationSec - durationSec)
      when {
        diff <= 2 -> {
          score += 20
          reasons.add("Exact duration")
        }
        diff <= 5 -> {
          score += 14
          reasons.add("Close duration (±${diff}s)")
        }
        diff <= 12 -> {
          score += 4
        }
        diff > 25 -> {
          score -= 28
          reasons.add("Duration differs by ${diff}s")
        }
        else -> {
          score -= 12
        }
      }
    }

    // 5. Album bonus
    val songAlbumNorm = normalizeForCompare(song.album)
    val candAlbumNorm = normalizeForCompare(albumName.orEmpty())
    if (
      songAlbumNorm.isNotEmpty() &&
        songAlbumNorm != "unknown album" &&
        candAlbumNorm.isNotEmpty() &&
        tokenSimilarity(songAlbumNorm, candAlbumNorm) >= 0.7
    ) {
      score += 8
      reasons.add("Album match")
    }

    // 6. Lyrics availability bonus
    val hasValidSynced = !dto.syncedLyrics.isNullOrBlank() && LrcParser.parse(dto.syncedLyrics).hasValidTimestamps
    val hasPlain = !dto.plainLyrics.isNullOrBlank()
    if (hasValidSynced) {
      score += 12
      reasons.add("Timestamped LRC")
    } else if (hasPlain) {
      score += 4
      reasons.add("Plain text")
    } else if (instrumental) {
      score += 4
      reasons.add("Instrumental")
    } else {
      score -= 50
    }

    return LrclibCandidate(
      id = dto.id,
      trackName = trackName.ifEmpty { song.title },
      artistName = artistName.ifEmpty { song.artist },
      albumName = albumName,
      durationSec = durationSec,
      instrumental = instrumental,
      plainLyrics = dto.plainLyrics?.takeIf { it.isNotBlank() },
      syncedLyrics = if (hasValidSynced) dto.syncedLyrics else null,
      matchScore = score,
      matchReason = reasons.joinToString(" • ").ifEmpty { "Metadata candidate" },
    )
  }

  fun evaluateCandidates(song: Song, dtos: List<LrclibTrackDto>): MatchEvaluation {
    val usable =
      dtos
        .map { scoreCandidate(song, it) }
        .filter { it.hasSyncedLyrics || it.hasPlainLyrics || it.instrumental }
        .distinctBy { it.id }
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

    // Check if there are multiple closely competing plausible candidates with different content/versions
    val closeCompetitors =
      usable.drop(1).filter { cand ->
        cand.matchScore >= 45 && abs(top.matchScore - cand.matchScore) <= 6
      }

    // If top candidate is very strong (>= 78) and clearly ahead or has synced lyrics while close ones don't, accept confidently
    if (top.matchScore >= 78 && (closeCompetitors.isEmpty() || !closeCompetitors.first().hasSyncedLyrics && top.hasSyncedLyrics)) {
      return MatchEvaluation.ConfidentMatch(top)
    }

    // If multiple distinct candidates are nearly tied and ambiguous, mark Needs Review
    if (closeCompetitors.isNotEmpty()) {
      val distinctGroup = (listOf(top) + closeCompetitors).take(6)
      val differentSongs =
        distinctGroup.any {
          normalizeForCompare(it.trackName) != normalizeForCompare(top.trackName) ||
            normalizeForCompare(it.artistName) != normalizeForCompare(top.artistName) ||
            extractVersionFlags(it.trackName) != extractVersionFlags(top.trackName)
        }
      if (differentSongs) {
        return MatchEvaluation.NeedsReview(distinctGroup)
      }
    }

    return if (top.matchScore >= 58) {
      MatchEvaluation.ConfidentMatch(top)
    } else {
      MatchEvaluation.NeedsReview(usable.take(6))
    }
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
    return maxOf(jaccard, containment)
  }
}
