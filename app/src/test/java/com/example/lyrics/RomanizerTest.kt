package com.example.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomanizerTest {

  @Test
  fun `bengali sentence becomes readable latin text`() {
    assertEquals("Ami tomake bhalobashi", Romanizer.romanize("আমি তোমাকে ভালোবাসি"))
  }

  @Test
  fun `more bengali lines`() {
    assertEquals("Tumi amar jibon", Romanizer.romanize("তুমি আমার জীবন"))
    assertEquals("Amar shonar bangla, ami tomay bhalobashi", Romanizer.romanize("আমার সোনার বাংলা, আমি তোমায় ভালোবাসি"))
  }

  @Test
  fun `hindi lines`() {
    assertEquals("Tum hi ho", Romanizer.romanize("तुम ही हो"))
    assertEquals("Dil divana", Romanizer.romanize("दिल दीवाना"))
  }

  @Test
  fun `latin text is returned untouched`() {
    assertEquals("Hello, World!", Romanizer.romanize("Hello, World!"))
    assertEquals("", Romanizer.romanize(""))
  }

  @Test
  fun `mixed script keeps both parts`() {
    val out = Romanizer.romanize("Baby তুমি mine")
    assertTrue(out.startsWith("Baby"))
    assertTrue(out.endsWith("mine"))
    assertFalse(Romanizer.containsNonLatin(out))
  }

  @Test
  fun `output contains no bengali or devanagari letters`() {
    val out = Romanizer.romanize("আমি তুম मैं तेरे")
    assertFalse(Romanizer.containsNonLatin(out))
  }

  @Test
  fun `romanizing each line keeps every timestamp and line count`() {
    val lrc = "[00:01.00]আমি তোমাকে ভালোবাসি\n[00:05.50]तुम ही हो\n[00:09.00]Hello"
    val original = LrcParser.parse(lrc)
    val romanized = original.lines.map { it.copy(text = Romanizer.romanize(it.text)) }

    assertEquals(original.lines.size, romanized.size)
    assertEquals(original.lines.map { it.timestampMs }, romanized.map { it.timestampMs })
    assertEquals("Ami tomake bhalobashi", romanized[0].text)
  }

  @Test
  fun `different spellings of the same word share a phonetic key`() {
    assertEquals(Romanizer.phoneticKey("Pyaar"), Romanizer.phoneticKey("Pyar"))
  }
}
