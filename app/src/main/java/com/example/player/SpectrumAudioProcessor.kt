@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Thread-safe hand-off between the audio thread (producer) and the UI (consumer).
 *
 * The audio thread publishes one frame of frequency-band levels roughly every 11 ms. Audio is
 * processed slightly before it is heard (sink buffering), so the UI asks for the frame from
 * [DEFAULT_DELAY_MS] ago to line the visuals up with what the listener actually hears.
 */
object SpectrumBus {
  const val BANDS = 32
  const val DEFAULT_DELAY_MS = 140L
  private const val RING = 48
  private const val MAX_AGE_NS = 600_000_000L

  private val times = LongArray(RING)
  private val frames = Array(RING) { FloatArray(BANDS) }
  private val bass = FloatArray(RING)
  private var head = 0
  private var count = 0

  @Synchronized
  fun publish(bands: FloatArray, bassLevel: Float, nowNanos: Long) {
    System.arraycopy(bands, 0, frames[head], 0, BANDS)
    bass[head] = bassLevel
    times[head] = nowNanos
    head = (head + 1) % RING
    if (count < RING) count++
  }

  /**
   * Copies the frame that was current [delayMs] ago into [out] and returns its bass level, or
   * `null` when there is no recent audio data (paused, or a format that can't be analysed).
   */
  @Synchronized
  fun sample(nowNanos: Long, out: FloatArray, delayMs: Long = DEFAULT_DELAY_MS): Float? {
    if (count == 0) return null
    val cutoff = nowNanos - delayMs * 1_000_000L
    var idx = (head - 1 + RING) % RING
    var seen = 0
    while (seen < count) {
      val t = times[idx]
      if (nowNanos - t > MAX_AGE_NS + delayMs * 1_000_000L) return null
      if (t <= cutoff) {
        System.arraycopy(frames[idx], 0, out, 0, BANDS)
        return bass[idx]
      }
      idx = (idx - 1 + RING) % RING
      seen++
    }
    return null
  }

  @Synchronized
  fun clear() {
    count = 0
    head = 0
  }
}

/**
 * Pass-through audio processor: the sound is not modified at all. It only reads the PCM samples
 * to compute a 32-band spectrum for the beat visualizer. Needs no microphone permission.
 */
class SpectrumAudioProcessor : BaseAudioProcessor() {

  private var channelCount = 2
  private var bytesPerSample = 2
  private var encoding = C.ENCODING_PCM_16BIT
  private var sampleRate = 44_100

  private val window = FloatArray(FFT_SIZE)
  private var filled = 0
  private var sinceLast = 0

  private val re = FloatArray(FFT_SIZE)
  private val im = FloatArray(FFT_SIZE)
  private val bands = FloatArray(SpectrumBus.BANDS)
  private var bandEdges = IntArray(SpectrumBus.BANDS + 1)
  private var peak = 1f

  override fun onConfigure(
    inputAudioFormat: AudioProcessor.AudioFormat
  ): AudioProcessor.AudioFormat {
    bytesPerSample =
      when (inputAudioFormat.encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        // Unknown format: stay inactive so playback is never affected.
        else -> return AudioProcessor.AudioFormat.NOT_SET
      }
    encoding = inputAudioFormat.encoding
    channelCount = max(1, inputAudioFormat.channelCount)
    sampleRate = max(8_000, inputAudioFormat.sampleRate)
    buildBandEdges()
    return inputAudioFormat
  }

  override fun queueInput(inputBuffer: ByteBuffer) {
    val size = inputBuffer.remaining()
    if (size == 0) return
    try {
      analyse(inputBuffer.duplicate().order(ByteOrder.nativeOrder()))
    } catch (_: Throwable) {
      // Visuals must never break playback.
    }
    val out = replaceOutputBuffer(size)
    out.put(inputBuffer)
    out.flip()
  }

  override fun onFlush() {
    filled = 0
    sinceLast = 0
    peak = 1f
    SpectrumBus.clear()
  }

  override fun onReset() {
    onFlush()
  }

  private fun buildBandEdges() {
    // Log-spaced bands from ~40 Hz to ~12 kHz, mapped onto FFT bins.
    val binHz = sampleRate.toFloat() / FFT_SIZE
    val maxBin = FFT_SIZE / 2 - 1
    val lo = 40f
    val hi = min(12_000f, sampleRate / 2f - 1f)
    val edges = IntArray(SpectrumBus.BANDS + 1)
    for (i in 0..SpectrumBus.BANDS) {
      val f = lo * (hi / lo).pow(i.toFloat() / SpectrumBus.BANDS)
      edges[i] = (f / binHz).toInt().coerceIn(1, maxBin)
    }
    // Make sure every band owns at least one bin.
    for (i in 1..SpectrumBus.BANDS) {
      if (edges[i] <= edges[i - 1]) edges[i] = min(edges[i - 1] + 1, maxBin)
    }
    bandEdges = edges
  }

  private fun analyse(buf: ByteBuffer) {
    val frameSize = bytesPerSample * channelCount
    var pos = buf.position()
    val limit = buf.limit()
    while (pos + frameSize <= limit) {
      var sum = 0f
      var p = pos
      for (c in 0 until channelCount) {
        sum += readSample(buf, p)
        p += bytesPerSample
      }
      pos += frameSize
      pushSample(sum / channelCount)
    }
  }

  private fun readSample(buf: ByteBuffer, p: Int): Float =
    when (encoding) {
      C.ENCODING_PCM_16BIT -> buf.getShort(p) / 32768f
      C.ENCODING_PCM_FLOAT -> buf.getFloat(p)
      C.ENCODING_PCM_32BIT -> buf.getInt(p) / 2_147_483_648f
      else -> { // 24-bit little endian
        val v =
          (buf.get(p).toInt() and 0xFF) or
            ((buf.get(p + 1).toInt() and 0xFF) shl 8) or
            (buf.get(p + 2).toInt() shl 16)
        v / 8_388_608f
      }
    }

  private fun pushSample(s: Float) {
    if (filled < FFT_SIZE) {
      window[filled++] = s
    } else {
      System.arraycopy(window, 1, window, 0, FFT_SIZE - 1)
      window[FFT_SIZE - 1] = s
    }
    if (filled == FFT_SIZE) {
      sinceLast++
      if (sinceLast >= HOP) {
        sinceLast = 0
        computeSpectrum()
      }
    }
  }

  private fun computeSpectrum() {
    for (i in 0 until FFT_SIZE) {
      re[i] = window[i] * HANN[i]
      im[i] = 0f
    }
    fft(re, im)

    var frameMax = 0f
    for (b in 0 until SpectrumBus.BANDS) {
      val from = bandEdges[b]
      val to = max(from + 1, bandEdges[b + 1])
      var acc = 0f
      for (k in from until to) acc += sqrt(re[k] * re[k] + im[k] * im[k])
      val mag = acc / (to - from)
      val v = ln(1f + 60f * mag / (FFT_SIZE / 4f))
      bands[b] = v
      if (v > frameMax) frameMax = v
    }
    // Slow automatic gain so quiet and loud songs both fill the display.
    peak = max(max(peak * 0.9985f, frameMax), 0.35f)
    var bassSum = 0f
    for (b in 0 until SpectrumBus.BANDS) {
      bands[b] = (bands[b] / peak).coerceIn(0f, 1f)
      if (b < 4) bassSum += bands[b]
    }
    SpectrumBus.publish(bands, bassSum / 4f, System.nanoTime())
  }

  private companion object {
    const val FFT_SIZE = 1024
    const val HOP = 512

    val HANN =
      FloatArray(FFT_SIZE) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / (FFT_SIZE - 1))).toFloat() }

    /** In-place iterative radix-2 FFT. */
    fun fft(re: FloatArray, im: FloatArray) {
      val n = re.size
      var j = 0
      for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
          j = j xor bit
          bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
          val tr = re[i]; re[i] = re[j]; re[j] = tr
          val ti = im[i]; im[i] = im[j]; im[j] = ti
        }
      }
      var len = 2
      while (len <= n) {
        val ang = -2.0 * PI / len
        val wr = cos(ang).toFloat()
        val wi = sin(ang).toFloat()
        var i = 0
        while (i < n) {
          var cr = 1f
          var ci = 0f
          for (k in 0 until len / 2) {
            val ur = re[i + k]
            val ui = im[i + k]
            val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
            val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
            re[i + k] = ur + vr
            im[i + k] = ui + vi
            re[i + k + len / 2] = ur - vr
            im[i + k + len / 2] = ui - vi
            val ncr = cr * wr - ci * wi
            ci = cr * wi + ci * wr
            cr = ncr
          }
          i += len
        }
        len = len shl 1
      }
    }
  }
}
