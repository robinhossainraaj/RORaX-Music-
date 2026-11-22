package com.example.lyrics.providers

import com.example.model.LyricSource
import com.example.model.RemoteLyricTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseProviderTest {

  private val hit =
    RemoteLyricTrack(LyricSource.NETEASE, 42L, "Tum Hi Ho", "Arijit Singh", "Aashiqui 2", 262.0)

  @Test
  fun `parses songs from a search response`() {
    val body =
      """{"code":200,"result":{"songs":[
        {"id":42,"name":"Tum Hi Ho","artists":[{"name":"Arijit Singh"}],"album":{"name":"Aashiqui 2"},"duration":262000},
        {"id":43,"name":"Other","artists":[{"name":"A"},{"name":"B"}],"duration":180000}
      ]}}"""

    val tracks = NeteaseProvider.parseSearch(body)

    assertEquals(2, tracks.size)
    assertEquals("Tum Hi Ho", tracks[0].trackName)
    assertEquals("Arijit Singh", tracks[0].artistName)
    assertEquals(262.0, tracks[0].durationSec!!, 0.001)
    assertEquals("A, B", tracks[1].artistName)
    assertEquals(LyricSource.NETEASE, tracks[0].provider)
  }

  @Test
  fun `malformed or empty search responses give an empty list`() {
    assertTrue(NeteaseProvider.parseSearch(null).isEmpty())
    assertTrue(NeteaseProvider.parseSearch("").isEmpty())
    assertTrue(NeteaseProvider.parseSearch("not json").isEmpty())
    assertTrue(NeteaseProvider.parseSearch("""{"code":200,"result":{}}""").isEmpty())
  }

  @Test
  fun `timed lyrics are kept as synced lyrics`() {
    val body = """{"lrc":{"lyric":"[00:01.00]Line one\n[00:05.00]Line two"}}"""

    val track = NeteaseProvider.parseLyrics(hit, body)

    assertNotNull(track)
    assertTrue(track!!.syncedLyrics!!.contains("[00:05.00]"))
    assertEquals("Line one\nLine two", track.plainLyrics)
  }

  @Test
  fun `untimed lyrics are kept as plain text only`() {
    val track = NeteaseProvider.parseLyrics(hit, """{"lrc":{"lyric":"Just words\nNo timing"}}""")

    assertNotNull(track)
    assertNull(track!!.syncedLyrics)
    assertTrue(track.plainLyrics!!.contains("Just words"))
  }

  @Test
  fun `instrumental songs are flagged`() {
    val track = NeteaseProvider.parseLyrics(hit, """{"nolyric":true}""")
    assertTrue(track!!.instrumental)
  }

  @Test
  fun `missing or broken lyric responses give nothing`() {
    assertNull(NeteaseProvider.parseLyrics(hit, null))
    assertNull(NeteaseProvider.parseLyrics(hit, "oops"))
    assertNull(NeteaseProvider.parseLyrics(hit, """{"lrc":{"lyric":"   "}}"""))
  }
}
