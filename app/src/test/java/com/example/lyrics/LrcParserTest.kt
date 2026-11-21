package com.example.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

  @Test
  fun `parses centisecond and millisecond timestamps and sorts chronologically`() {
    val lrc =
      """
      [ti:Midnight Echo]
      [ar:Aurora Waves]
      [00:15.50]Second line
      [00:05.250]First line
      [00:30:10]Third line
      """.trimIndent()

    val parsed = LrcParser.parse(lrc)
    assertTrue(parsed.hasValidTimestamps)
    assertEquals("Midnight Echo", parsed.titleTag)
    assertEquals("Aurora Waves", parsed.artistTag)
    assertEquals(3, parsed.lines.size)
    assertEquals(5_250L, parsed.lines[0].timestampMs)
    assertEquals("First line", parsed.lines[0].text)
    assertEquals(15_500L, parsed.lines[1].timestampMs)
    assertEquals(30_100L, parsed.lines[2].timestampMs)
  }

  @Test
  fun `expands multiple timestamps on one line and applies offset`() {
    val lrc =
      """
      [offset:500]
      [00:10.00][01:00.00]Chorus repeats
      """.trimIndent()

    val parsed = LrcParser.parse(lrc)
    assertEquals(2, parsed.lines.size)
    assertEquals(9_500L, parsed.lines[0].timestampMs)
    assertEquals(59_500L, parsed.lines[1].timestampMs)
    assertEquals("Chorus repeats", parsed.lines[0].text)
    assertEquals("Chorus repeats", parsed.lines[1].text)
  }

  @Test
  fun `findActiveLineIndex tracks forward and backward seeking accurately`() {
    val lines =
      LrcParser.parse(
          """
          [00:04.00]Line zero
          [00:10.00]Line one
          [00:20.00]Line two
          [00:30.00]Line three
          """.trimIndent()
        )
        .lines

    assertEquals(-1, LrcParser.findActiveLineIndex(lines, 1_500L))
    assertEquals(0, LrcParser.findActiveLineIndex(lines, 4_000L))
    assertEquals(1, LrcParser.findActiveLineIndex(lines, 15_900L))
    assertEquals(3, LrcParser.findActiveLineIndex(lines, 45_000L))
    // Backward seek
    assertEquals(0, LrcParser.findActiveLineIndex(lines, 6_000L))
  }

  @Test
  fun `plain text without timestamps produces empty lines list`() {
    val plain = "Verse one\nChorus line\nNo timestamps here"
    val parsed = LrcParser.parse(plain)
    assertFalse(parsed.hasValidTimestamps)
    assertTrue(parsed.lines.isEmpty())
  }
}
