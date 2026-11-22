package com.example.lyrics

import com.example.model.LyricQuery
import com.example.model.LyricSource
import com.example.model.RemoteLyricTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricMatcherTest {

  private fun query(
    title: String = "Ocean Lights",
    artist: String = "Glass Harbor",
    durationSec: Int = 210,
  ) = LyricQuery(title = title, artist = artist, album = "Night Drive", durationSec = durationSec)

  private fun track(
    id: Long,
    name: String,
    artist: String,
    durationSec: Double,
    synced: String? = "[00:05.00]Line one\n[00:09.00]Line two",
    provider: LyricSource = LyricSource.LRCLIB,
    album: String? = "Night Drive",
  ) =
    RemoteLyricTrack(
      provider = provider,
      id = id,
      trackName = name,
      artistName = artist,
      albumName = album,
      durationSec = durationSec,
      syncedLyrics = synced,
    )

  @Test
  fun `confident studio match wins over live or remix mismatch`() {
    val studio = track(101L, "Ocean Lights", "Glass Harbor", 210.0)
    val live = track(102L, "Ocean Lights (Live)", "Glass Harbor", 265.0, album = "Tour 2025")

    val evaluation = LyricMatcher.evaluateCandidates(query(), listOf(live, studio))

    assertTrue(evaluation is MatchEvaluation.ConfidentMatch)
    assertEquals(101L, (evaluation as MatchEvaluation.ConfidentMatch).candidate.id)
  }

  @Test
  fun `rejects completely unrelated durations and titles`() {
    val wrong = track(201L, "Completely Different Song", "Someone Else", 320.0, album = null)

    val evaluation =
      LyricMatcher.evaluateCandidates(query("Silent Horizon", "Kora", 180), listOf(wrong))

    assertTrue(evaluation is MatchEvaluation.NoValidMatch)
  }

  @Test
  fun `matches a latin-script tag to a lyric entry written in Devanagari`() {
    val q = LyricQuery(title = "Tum Hi Ho", artist = "Arijit Singh", album = "Aashiqui 2", durationSec = 262)
    val hindi =
      track(
        301L,
        "तुम ही हो",
        "Arijit Singh",
        262.0,
        provider = LyricSource.NETEASE,
        album = "Aashiqui 2",
      )

    val evaluation = LyricMatcher.evaluateCandidates(q, listOf(hindi))

    assertTrue("expected a match but got $evaluation", evaluation !is MatchEvaluation.NoValidMatch)
  }

  @Test
  fun `matches a latin-script tag to a lyric entry written in Bengali`() {
    val q = LyricQuery(title = "Tumi Amar Jibon", artist = "Unknown Artist", album = "", durationSec = 200)
    val bengali =
      track(401L, "তুমি আমার জীবন", "Unknown", 200.0, provider = LyricSource.KUGOU, album = null)

    val evaluation = LyricMatcher.evaluateCandidates(q, listOf(bengali))

    assertTrue("expected a match but got $evaluation", evaluation !is MatchEvaluation.NoValidMatch)
  }

  @Test
  fun `the same song from two sources is not offered twice`() {
    val a = track(1L, "Ocean Lights", "Glass Harbor", 210.0, provider = LyricSource.LRCLIB)
    val b = track(1L, "Ocean Lights", "Glass Harbor", 210.0, provider = LyricSource.NETEASE)

    val evaluation = LyricMatcher.evaluateCandidates(query(), listOf(a, b))

    assertTrue(evaluation is MatchEvaluation.ConfidentMatch)
  }
}
