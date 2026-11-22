package com.example.lyrics

import java.text.Normalizer
import java.util.Locale

/**
 * Converts lyrics written in non-Latin scripts into readable Latin text (for example
 * "আমি তোমাকে ভালোবাসি" -> "Ami tomake bhalobashi").
 *
 * Bengali, Hindi (Devanagari), Punjabi (Gurmukhi) and Gujarati are handled by a dedicated
 * engine that applies everyday spelling conventions and word-final vowel rules. Any other
 * non-Latin script is passed to [fallback], which is backed by the platform's ICU transliterator
 * on Android.
 *
 * The class has no Android dependencies so it can be unit-tested on the JVM.
 */
object Romanizer {

  /** Transliterates runs of scripts the built-in engine does not cover. */
  @Volatile
  var fallback: (String) -> String = IcuFallback::transliterate

  /** True when [text] contains at least one letter that is not Latin. */
  fun containsNonLatin(text: String): Boolean {
    for (ch in text) {
      if (ch.code < 0x250) continue
      if (Character.isLetter(ch) && classify(ch) != CharClass.LATIN_OR_COMMON) return true
    }
    return false
  }

  /** Romanizes a single line or block of text. Latin text is returned unchanged. */
  fun romanize(input: String): String {
    if (input.isEmpty() || !containsNonLatin(input)) return input
    val text = Normalizer.normalize(input, Normalizer.Form.NFC)
    val out = StringBuilder(text.length + 16)
    var i = 0
    while (i < text.length) {
      val ch = text[i]
      when (val cls = classify(ch)) {
        CharClass.LATIN_OR_COMMON -> {
          out.append(commonChar(ch))
          i++
        }
        CharClass.OTHER -> {
          val start = i
          while (i < text.length && classify(text[i]) == CharClass.OTHER) i++
          val run = text.substring(start, i)
          out.append(runCatching { fallback(run) }.getOrDefault(run))
        }
        else -> {
          val start = i
          while (i < text.length && classify(text[i]) == cls) i++
          out.append(IndicEngine.romanizeRun(text.substring(start, i), cls))
        }
      }
    }
    return tidy(out.toString())
  }

  /** Lower-cases and strips accents/punctuation so strings can be compared across scripts. */
  fun foldForComparison(input: String): String {
    val latin = romanize(input)
    val decomposed = Normalizer.normalize(latin, Normalizer.Form.NFD)
    return decomposed
      .replace(Regex("""\p{M}+"""), "")
      .lowercase(Locale.ROOT)
      .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
      .replace(Regex("""\s+"""), " ")
      .trim()
  }

  /**
   * Collapses common spelling variants (ph/f, bh/b, aa/a, w/v, doubled letters...) so that
   * "Pyaar", "Pyar" and "प्यार" produce the same key.
   */
  fun phoneticKey(input: String): String {
    var s = foldForComparison(input).replace(" ", "")
    if (s.isEmpty()) return s
    val rules =
      listOf(
        "chh" to "c", "ch" to "c", "sh" to "s", "ph" to "f", "bh" to "b", "dh" to "d",
        "gh" to "g", "kh" to "k", "th" to "t", "jh" to "j", "oo" to "u", "ee" to "i",
        "aa" to "a", "ou" to "u", "w" to "v", "z" to "j", "q" to "k", "x" to "ks",
      )
    for ((from, to) in rules) s = s.replace(from, to)
    // Collapse repeated letters.
    val sb = StringBuilder(s.length)
    for (c in s) if (sb.isEmpty() || sb.last() != c) sb.append(c)
    return sb.toString()
  }

  // ---------------------------------------------------------------------------------------

  internal enum class CharClass { LATIN_OR_COMMON, DEVANAGARI, BENGALI, GURMUKHI, GUJARATI, OTHER }

  internal fun classify(ch: Char): CharClass {
    val code = ch.code
    return when {
      code in 0x0900..0x097F -> {
        // The danda (U+0964/0965) is shared punctuation.
        if (code == 0x0964 || code == 0x0965) CharClass.LATIN_OR_COMMON else CharClass.DEVANAGARI
      }
      code in 0x0980..0x09FF -> CharClass.BENGALI
      code in 0x0A00..0x0A7F -> CharClass.GURMUKHI
      code in 0x0A80..0x0AFF -> CharClass.GUJARATI
      code < 0x250 -> CharClass.LATIN_OR_COMMON
      code == 0x200C || code == 0x200D -> CharClass.LATIN_OR_COMMON
      !Character.isLetter(ch) && !isCombiningMark(ch) -> CharClass.LATIN_OR_COMMON
      else -> {
        when (Character.UnicodeScript.of(code)) {
          Character.UnicodeScript.LATIN,
          Character.UnicodeScript.COMMON,
          Character.UnicodeScript.INHERITED -> CharClass.LATIN_OR_COMMON
          else -> CharClass.OTHER
        }
      }
    }
  }

  private fun isCombiningMark(ch: Char): Boolean {
    val type = Character.getType(ch)
    return type == Character.NON_SPACING_MARK.toInt() ||
      type == Character.COMBINING_SPACING_MARK.toInt() ||
      type == Character.ENCLOSING_MARK.toInt()
  }

  private fun commonChar(ch: Char): String {
    val code = ch.code
    return when {
      code == 0x0964 || code == 0x0965 -> "" // danda marks end of a line; drop it
      code == 0x200C || code == 0x200D -> ""
      Character.isDigit(ch) && code > 0x7F -> Character.digit(ch, 10).toString()
      else -> ch.toString()
    }
  }

  private fun tidy(s: String): String {
    val collapsed = s.replace(Regex("""[ \t]{2,}"""), " ").trim()
    if (collapsed.isEmpty()) return collapsed
    return collapsed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
  }
}

/** ICU-based transliteration for scripts the built-in engine does not handle. */
internal object IcuFallback {
  private val transliterator: Any? by lazy {
    try {
      android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII")
    } catch (_: Throwable) {
      null
    }
  }

  fun transliterate(text: String): String {
    return try {
      (transliterator as? android.icu.text.Transliterator)?.transliterate(text) ?: text
    } catch (_: Throwable) {
      text
    }
  }
}

/** Rule-based romanization for Indic scripts that share the Brahmic structure. */
internal object IndicEngine {

  private class Syllable(
    var consonant: String = "",
    var vowel: String = "",
    var inherent: Boolean = false,
    var halant: Boolean = false,
    var coda: String = "",
    var deleted: Boolean = false,
    var geminate: Boolean = false,
  ) {
    val keepsVowel: Boolean
      get() = vowel.isNotEmpty() || (inherent && !deleted)
  }

  // Devanagari consonants, indexed by code point (also used for Gurmukhi/Gujarati via offsets).
  private val devaConsonants: Map<Int, String> =
    mapOf(
      0x0915 to "k", 0x0916 to "kh", 0x0917 to "g", 0x0918 to "gh", 0x0919 to "n",
      0x091A to "ch", 0x091B to "chh", 0x091C to "j", 0x091D to "jh", 0x091E to "n",
      0x091F to "t", 0x0920 to "th", 0x0921 to "d", 0x0922 to "dh", 0x0923 to "n",
      0x0924 to "t", 0x0925 to "th", 0x0926 to "d", 0x0927 to "dh", 0x0928 to "n",
      0x0929 to "n", 0x092A to "p", 0x092B to "ph", 0x092C to "b", 0x092D to "bh",
      0x092E to "m", 0x092F to "y", 0x0930 to "r", 0x0931 to "r", 0x0932 to "l",
      0x0933 to "l", 0x0934 to "l", 0x0935 to "v", 0x0936 to "sh", 0x0937 to "sh",
      0x0938 to "s", 0x0939 to "h",
    )

  private val devaNukta: Map<Int, String> =
    mapOf(
      0x0915 to "q", 0x0916 to "kh", 0x0917 to "g", 0x091C to "z", 0x0921 to "r",
      0x0922 to "rh", 0x092B to "f", 0x092F to "y", 0x0938 to "sh",
    )

  // Independent vowels.
  private val devaVowels: Map<Int, String> =
    mapOf(
      0x0904 to "a", 0x0905 to "a", 0x0906 to "aa", 0x0907 to "i", 0x0908 to "ee",
      0x0909 to "u", 0x090A to "oo", 0x090B to "ri", 0x090C to "li", 0x090D to "e",
      0x090E to "e", 0x090F to "e", 0x0910 to "ai", 0x0911 to "o", 0x0912 to "o",
      0x0913 to "o", 0x0914 to "au", 0x0950 to "om",
    )

  private val devaMatras: Map<Int, String> =
    mapOf(
      0x093E to "a", 0x093F to "i", 0x0940 to "i", 0x0941 to "u", 0x0942 to "u",
      0x0943 to "ri", 0x0944 to "ri", 0x0945 to "e", 0x0946 to "e", 0x0947 to "e",
      0x0948 to "ai", 0x0949 to "o", 0x094A to "o", 0x094B to "o", 0x094C to "au",
    )

  private const val DEVA_NUKTA = 0x093C
  private const val DEVA_VIRAMA = 0x094D
  private const val DEVA_CANDRABINDU = 0x0901
  private const val DEVA_ANUSVARA = 0x0902
  private const val DEVA_VISARGA = 0x0903

  // Bengali.
  private val bengConsonants: Map<Int, String> =
    mapOf(
      0x0995 to "k", 0x0996 to "kh", 0x0997 to "g", 0x0998 to "gh", 0x0999 to "ng",
      0x099A to "ch", 0x099B to "chh", 0x099C to "j", 0x099D to "jh", 0x099E to "n",
      0x099F to "t", 0x09A0 to "th", 0x09A1 to "d", 0x09A2 to "dh", 0x09A3 to "n",
      0x09A4 to "t", 0x09A5 to "th", 0x09A6 to "d", 0x09A7 to "dh", 0x09A8 to "n",
      0x09AA to "p", 0x09AB to "ph", 0x09AC to "b", 0x09AD to "bh", 0x09AE to "m",
      0x09AF to "j", 0x09B0 to "r", 0x09B2 to "l", 0x09B6 to "sh", 0x09B7 to "sh",
      0x09B8 to "sh", 0x09B9 to "h", 0x09CE to "t", 0x09DC to "r", 0x09DD to "rh",
      0x09DF to "y",
    )

  private val bengNukta: Map<Int, String> =
    mapOf(0x09A1 to "r", 0x09A2 to "rh", 0x09AF to "y")

  private val bengVowels: Map<Int, String> =
    mapOf(
      0x0985 to "o", 0x0986 to "a", 0x0987 to "i", 0x0988 to "i", 0x0989 to "u",
      0x098A to "u", 0x098B to "ri", 0x098C to "li", 0x098F to "e", 0x0990 to "oi",
      0x0993 to "o", 0x0994 to "ou",
    )

  private val bengMatras: Map<Int, String> =
    mapOf(
      0x09BE to "a", 0x09BF to "i", 0x09C0 to "i", 0x09C1 to "u", 0x09C2 to "u",
      0x09C3 to "ri", 0x09C7 to "e", 0x09C8 to "oi", 0x09CB to "o", 0x09CC to "ou",
    )

  private const val BENG_NUKTA = 0x09BC
  private const val BENG_VIRAMA = 0x09CD
  private const val BENG_CANDRABINDU = 0x0981
  private const val BENG_ANUSVARA = 0x0982
  private const val BENG_VISARGA = 0x0983
  private const val BENG_SA = 0x09B8
  private const val BENG_JA = 0x099C
  private const val BENG_NYA = 0x099E
  private const val BENG_KA = 0x0995
  private const val BENG_SSA = 0x09B7
  private const val BENG_BA = 0x09AC
  private const val BENG_YA = 0x09AF

  // Bengali "sa" keeps a plain "s" before these conjunct partners (e.g. স্কুল, স্টেশন).
  private val bengHardSPartners = setOf(0x0995, 0x0996, 0x099F, 0x09A0, 0x09A4, 0x09A5, 0x09AA, 0x09AB, 0x09A8, 0x09AE)

  private const val GURMUKHI_TIPPI = 0x0A70
  private const val GURMUKHI_ADDAK = 0x0A71

  fun romanizeRun(run: String, cls: Romanizer.CharClass): String {
    val bengali = cls == Romanizer.CharClass.BENGALI
    val codes =
      when (cls) {
        Romanizer.CharClass.GURMUKHI -> normalizeBlock(run, 0x0A00, 0x0100, GURMUKHI_TIPPI to DEVA_ANUSVARA)
        Romanizer.CharClass.GUJARATI -> normalizeBlock(run, 0x0A80, 0x0180, null)
        else -> run.map { it.code }
      }
    val out = StringBuilder()
    val word = ArrayList<Int>()
    for (code in codes) {
      if (isLetterCode(code, bengali)) {
        word.add(code)
      } else {
        if (word.isNotEmpty()) {
          out.append(romanizeWord(word, bengali))
          word.clear()
        }
        out.append(punctuation(code))
      }
    }
    if (word.isNotEmpty()) out.append(romanizeWord(word, bengali))
    return out.toString()
  }

  private fun normalizeBlock(run: String, blockStart: Int, offset: Int, special: Pair<Int, Int>?): List<Int> {
    val result = ArrayList<Int>(run.length)
    for (ch in run) {
      val code = ch.code
      when {
        code == GURMUKHI_ADDAK -> result.add(GURMUKHI_ADDAK)
        special != null && code == special.first -> result.add(special.second)
        code in blockStart..(blockStart + 0x7F) -> result.add(code - offset)
        else -> result.add(code)
      }
    }
    return result
  }

  private fun isLetterCode(code: Int, bengali: Boolean): Boolean {
    if (code == GURMUKHI_ADDAK) return true
    return if (bengali) {
      code in 0x0981..0x0983 || code in 0x0985..0x09CE && code != 0x09BD || code == 0x09DC ||
        code == 0x09DD || code == 0x09DF || code == 0x09D7
    } else {
      code in 0x0900..0x0963 && code != 0x0964
    }
  }

  private fun punctuation(code: Int): String {
    val ch = code.toChar()
    return when {
      Character.isDigit(ch) -> Character.digit(ch, 10).toString()
      code in 0x0964..0x0965 -> ""
      code == 0x09FD -> ""
      else -> ""
    }
  }

  private fun romanizeWord(chars: List<Int>, bengali: Boolean): String {
    val syllables = parse(chars, bengali)
    if (syllables.isEmpty()) return ""
    if (bengali) applyBengaliRules(syllables) else applyHindiRules(syllables)
    return render(syllables, bengali)
  }

  private fun parse(chars: List<Int>, bengali: Boolean): List<Syllable> {
    val consonants = if (bengali) bengConsonants else devaConsonants
    val nuktas = if (bengali) bengNukta else devaNukta
    val vowels = if (bengali) bengVowels else devaVowels
    val matras = if (bengali) bengMatras else devaMatras
    val nuktaCode = if (bengali) BENG_NUKTA else DEVA_NUKTA
    val viramaCode = if (bengali) BENG_VIRAMA else DEVA_VIRAMA
    val chandra = if (bengali) BENG_CANDRABINDU else DEVA_CANDRABINDU
    val anusvara = if (bengali) BENG_ANUSVARA else DEVA_ANUSVARA
    val visarga = if (bengali) BENG_VISARGA else DEVA_VISARGA

    val result = ArrayList<Syllable>()
    var pendingGemination = false
    var i = 0

    fun absorbModifiers(target: Syllable) {
      while (i < chars.size) {
        val c = chars[i]
        when (c) {
          anusvara -> target.coda += if (bengali) "ng" else "n"
          chandra -> target.coda += "n"
          visarga -> target.coda += "h"
          else -> return
        }
        i++
      }
    }

    while (i < chars.size) {
      val c = chars[i]
      when {
        c == GURMUKHI_ADDAK -> {
          pendingGemination = true
          i++
        }
        c in vowels -> {
          val s = Syllable(vowel = vowels.getValue(c))
          i++
          absorbModifiers(s)
          result.add(s)
        }
        c in consonants -> {
          var base = consonants.getValue(c)
          var effective = c
          i++
          if (i < chars.size && chars[i] == nuktaCode) {
            base = nuktas[c] ?: base
            // Bengali য় is pronounced as a glide, never as the plain "j".
            i++
          }
          val s = Syllable(consonant = base)
          if (bengali && effective == BENG_SA && i + 1 < chars.size && chars[i] == viramaCode &&
            chars[i + 1] in bengHardSPartners
          ) {
            s.consonant = "s"
          }
          // Special conjuncts.
          if (i + 1 < chars.size && chars[i] == viramaCode) {
            val partner = chars[i + 1]
            if (bengali) {
              if (effective == BENG_JA && partner == BENG_NYA) {
                s.consonant = if (result.isEmpty()) "gy" else "gg"
                i += 2
                effective = BENG_NYA
              } else if (effective == BENG_KA && partner == BENG_SSA) {
                s.consonant = if (result.isEmpty()) "kh" else "kkh"
                i += 2
                effective = BENG_SSA
              }
            } else if (effective == 0x091C && partner == 0x091E) {
              s.consonant = "gy"
              i += 2
              effective = 0x091E
            }
          }
          if (pendingGemination) {
            // Gurmukhi addak doubles the following consonant: ਮੱਖਣ -> "makkhan".
            s.consonant = s.consonant.take(1) + s.consonant
            pendingGemination = false
          }
          if (bengali && effective == BENG_BA && result.isNotEmpty() && result.last().halant) {
            // ba-phala: the "b" of a conjunct is not pronounced separately.
            s.consonant = ""
          }
          if (bengali && effective == BENG_YA && result.isNotEmpty() && result.last().halant) {
            // ya-phala: "ত্য" is read "tyo", not "tjo".
            s.consonant = "y"
          }
          if (i < chars.size && chars[i] == viramaCode) {
            s.halant = true
            i++
          } else if (i < chars.size && chars[i] in matras) {
            s.vowel = matras.getValue(chars[i])
            i++
          } else {
            s.inherent = true
          }
          absorbModifiers(s)
          result.add(s)
        }
        else -> i++ // stray matra, sign or unsupported mark
      }
    }
    return result
  }

  private fun applyHindiRules(units: List<Syllable>) {
    val n = units.size
    // Word-final inherent vowel is silent ("tum", "dil", "pyar").
    if (n > 1) {
      val last = units[n - 1]
      if (last.inherent && last.coda.isEmpty()) last.deleted = true
    }
    // Medial inherent vowel is dropped between two vowelled syllables ("sapna", "chalna").
    for (i in n - 2 downTo 1) {
      val u = units[i]
      if (!u.inherent || u.deleted || u.coda.isNotEmpty()) continue
      val prev = units[i - 1]
      val next = units[i + 1]
      if (prev.keepsVowel && prev.coda.isEmpty() && !prev.halant && next.keepsVowel) {
        u.deleted = true
      }
    }
  }

  private fun applyBengaliRules(units: List<Syllable>) {
    val n = units.size
    // A final consonant loses its "o" ("mon", "amar"), but keeps it after a conjunct ("shopno")
    // or a visarga ("duhkho").
    if (n > 1) {
      val last = units[n - 1]
      val before = units[n - 2]
      val closesCluster = before.halant || before.coda.endsWith("h")
      if (last.inherent && last.coda.isEmpty() && !closesCluster) last.deleted = true
    }
  }

  private fun render(units: List<Syllable>, bengali: Boolean): String {
    val inherentVowel = if (bengali) "o" else "a"
    val sb = StringBuilder()
    for ((index, u) in units.withIndex()) {
      sb.append(u.consonant)
      when {
        u.vowel.isNotEmpty() -> sb.append(u.vowel)
        u.inherent && !u.deleted -> sb.append(inherentVowel)
      }
      if (u.coda.isNotEmpty()) {
        var coda = u.coda
        if (!bengali && coda == "n") {
          val nextStart = units.getOrNull(index + 1)?.consonant?.firstOrNull()
          if (nextStart == 'p' || nextStart == 'b' || nextStart == 'm') coda = "m"
          if (u.vowel == "e") coda = "in"
        }
        sb.append(coda)
      }
    }
    return sb.toString()
  }
}
