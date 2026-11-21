package com.example.lyrics

import android.net.Uri
import com.example.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LyricMatcherTest {

  private fun sampleSong(title: String = "Ocean Lights", artist: String = "Glass Harbor", durationSec: Int = 210) =
    Song(
      id = 1L,
      title = title,
      artist = artist,
      album = "Night Drive",
      albumId = 10L,
      durationMs = durationSec * 1000L,
      contentUri = Uri.parse("content://media/external/audio/media/1"),
      albumArtUri = null,
      dateAddedSec = 1000L,
      trackNumber = 1,
      mimeType = "audio/mpeg",
      sizeBytes = 4_000_000L,
    )

  @Test
  fun `confident studio match wins over live or remix mismatch`() {
    val song = sampleSong()
    val studio =
      LrclibTrackDto(
        id = 101L,
        trackName = "Ocean Lights",
        artistName = "Glass Harbor",
        albumName = "Night Drive",
        duration = 210.0,
        instrumental = false,
        syncedLyrics = "[00:05.00]Drift into the blue",
      )
    val liveMismatch =
      LrclibTrackDto(
        id = 102L,
        trackName = "Ocean Lights (Live)",
        artistName = "Glass Harbor",
        albumName = "Tour 2025",
        duration = 265.0,
        instrumental = false,
        syncedLyrics = "[00:08.00]Live crowd",
      )

    val evaluation = LyricMatcher.evaluateCandidates(song, listOf(liveMismatch, studio))
    assertTrue(evaluation is MatchEvaluation.ConfidentMatch)
    assertEquals(101L, (evaluation as MatchEvaluation.ConfidentMatch).candidate.id)
  }

  @Test
  fun `rejects completely unrelated durations and titles`() {
    val song = sampleSong(title = "Silent Horizon", artist = "Kora", durationSec = 180)
    val wrong =
      LrclibTrackDto(
        id = 201L,
        trackName = "Completely Different Song",
        artistName = "Someone Else",
        duration = 320.0,
        syncedLyrics = "[00:01.00]Wrong",
      )

    val evaluation = LyricMatcher.evaluateCandidates(song, listOf(wrong))
    assertTrue(evaluation is MatchEvaluation.NoValidMatch)
  }
}
