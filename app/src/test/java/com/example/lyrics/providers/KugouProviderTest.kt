package com.example.lyrics.providers

import com.example.model.LyricSource
import com.example.model.RemoteLyricTrack
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KugouProviderTest {

  private val track =
    RemoteLyricTrack(LyricSource.KUGOU, 1L, "Song", "Artist", null, 200.0)

  @Test
  fun `parses songs and keeps the hash`() {
    val body =
      """{"status":1,"data":{"info":[
        {"hash":"0123456789ABCDEF0123456789ABCDEF","songname":"Tum Hi Ho","singername":"Arijit Singh","album_name":"Aashiqui 2","duration":262}
      ]}}"""

    val hits = KugouProvider.parseSearch(body)

    assertEquals(1, hits.size)
    assertEquals("0123456789ABCDEF0123456789ABCDEF", hits[0].hash)
    assertEquals("Tum Hi Ho", hits[0].track.trackName)
    assertEquals(LyricSource.KUGOU, hits[0].track.provider)
  }

  @Test
  fun `songs without a usable hash are skipped`() {
    val body = """{"data":{"info":[{"hash":"zz","songname":"X"},{"songname":"Y"}]}}"""
    assertTrue(KugouProvider.parseSearch(body).isEmpty())
  }

  @Test
  fun `reads the first lyric candidate`() {
    val body = """{"candidates":[{"id":"777","accesskey":"KEY"},{"id":"888","accesskey":"OTHER"}]}"""
    assertEquals("777" to "KEY", KugouProvider.parseLyricCandidate(body))
    assertNull(KugouProvider.parseLyricCandidate("""{"candidates":[]}"""))
    assertNull(KugouProvider.parseLyricCandidate("nope"))
  }

  @Test
  fun `decodes base64 timed lyrics`() {
    val lrc = "[ar:Artist]\n[00:01.00]আমি তোমাকে ভালোবাসি\n[00:05.00]Second line"
    val encoded = Base64.getEncoder().encodeToString(lrc.toByteArray(Charsets.UTF_8))

    val result = KugouProvider.parseLyrics(track, """{"content":"$encoded"}""")

    assertNotNull(result)
    assertTrue(result!!.syncedLyrics!!.contains("আমি তোমাকে ভালোবাসি"))
    assertEquals("আমি তোমাকে ভালোবাসি\nSecond line", result.plainLyrics)
  }

  @Test
  fun `lyrics without timing are rejected`() {
    val encoded = Base64.getEncoder().encodeToString("just words".toByteArray())
    assertNull(KugouProvider.parseLyrics(track, """{"content":"$encoded"}"""))
    assertNull(KugouProvider.parseLyrics(track, """{"content":"%%%not-base64%%%"}"""))
    assertNull(KugouProvider.parseLyrics(track, null))
  }
}
