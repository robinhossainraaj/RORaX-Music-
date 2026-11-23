package com.example.ui.components

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.example.model.Song
import com.example.player.SpectrumBus
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_PARTICLES = 48
private const val BAR_ROWS = 27

/** Mutable animation state, advanced once per frame and only read while drawing. */
private class VizState {
  val levels = FloatArray(SpectrumBus.BANDS)
  val raw = FloatArray(SpectrumBus.BANDS)
  var bass = 0f
  var prevBassRaw = 0f
  var beatPulse = 0f
  var sinceBeat = 1f
  var sinceAmbient = 0f
  var time = 0f
  var lastNanos = 0L
  var sinceReal = 0f

  val px = FloatArray(MAX_PARTICLES)
  val py = FloatArray(MAX_PARTICLES)
  val vx = FloatArray(MAX_PARTICLES)
  val vy = FloatArray(MAX_PARTICLES)
  val size = FloatArray(MAX_PARTICLES)
  val life = FloatArray(MAX_PARTICLES)
  val maxLife = FloatArray(MAX_PARTICLES) { 1f }
  var nextParticle = 0

  fun spawn(w: Float, h: Float, unit: Float, count: Int) {
    repeat(count) {
      val i = nextParticle
      nextParticle = (nextParticle + 1) % MAX_PARTICLES
      px[i] = Random.nextFloat() * w
      py[i] = h * (0.08f + Random.nextFloat() * 0.84f)
      vx[i] = (Random.nextFloat() - 0.5f) * 14f * unit
      vy[i] = -(6f + Random.nextFloat() * 26f) * unit
      size[i] = (1.6f + Random.nextFloat() * 3.4f) * unit
      maxLife[i] = 1.2f + Random.nextFloat() * 1.8f
      life[i] = maxLife[i]
    }
  }

  fun anyParticleAlive(): Boolean = life.any { it > 0f }

  fun isSettled(): Boolean = levels.all { it < 0.02f } && bass < 0.02f && !anyParticleAlive()
}

/**
 * Shown in the player when a song has no lyrics: album art with audio bars on both sides, a
 * pulsing glow and floating particles that move with the real audio of the song.
 */
@Composable
fun BeatVisualizerPanel(
  song: Song,
  isPlaying: Boolean,
  reducedMotion: Boolean,
  accentFallback: Color,
  modifier: Modifier = Modifier,
  note: String = "",
  actions: @Composable RowScope.() -> Unit = {},
) {
  val accent by
    animateColorAsState(
      targetValue = rememberArtAccent(song.albumArtUri, accentFallback),
      animationSpec = tween(600),
      label = "viz_accent",
    )
  val state = remember(song.id) { VizState() }
  val frameTick = remember { mutableLongStateOf(0L) }

  BoxWithConstraints(
    modifier = modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF07070B))
  ) {
    val wPx = constraints.maxWidth.toFloat()
    val hPx = constraints.maxHeight.toFloat()
    val artPx = min(wPx * 0.46f, hPx * 0.40f)
    val cx = wPx / 2f
    val cy = hPx * 0.34f
    val artDp = with(LocalDensity.current) { artPx.toDp() }
    val unit = with(LocalDensity.current) { 1.dp.toPx() }

    LaunchedEffect(isPlaying, reducedMotion, wPx, hPx, song.id) {
      if (reducedMotion) {
        // No motion: a calm, static equalizer.
        for (i in state.levels.indices) {
          state.levels[i] = 0.16f * (1f - 0.5f * i / state.levels.size)
        }
        state.bass = 0.1f
        frameTick.longValue++
        return@LaunchedEffect
      }
      state.lastNanos = 0L
      while (true) {
        withFrameNanos { now ->
          stepViz(state, now, isPlaying, wPx, hPx, unit)
          frameTick.longValue = now
        }
        if (!isPlaying && state.isSettled()) break
      }
    }

    // Blurred, dimmed album art as the backdrop (blur needs Android 12+; older phones get color).
    if (song.albumArtUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      AsyncImage(
        model = song.albumArtUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = 0.40f,
        modifier = Modifier.fillMaxSize().blur(36.dp),
      )
    }
    Box(
      Modifier.fillMaxSize()
        .background(
          Brush.verticalGradient(
            listOf(accent.copy(alpha = 0.28f), Color.Black.copy(alpha = 0.82f), Color.Black)
          )
        )
    )

    Canvas(Modifier.fillMaxSize()) {
      frameTick.longValue // re-draw every frame without recomposing
      drawVisualizer(state, accent, cx, cy, artPx, unit)
    }

    Box(
      Modifier.offset { IntOffset((cx - artPx / 2f).roundToInt(), (cy - artPx / 2f).roundToInt()) }
        .size(artDp)
        .graphicsLayer {
          frameTick.longValue
          val s = 1f + 0.035f * state.bass + 0.02f * state.beatPulse
          scaleX = s
          scaleY = s
        }
    ) {
      AlbumArtwork(
        artUri = song.albumArtUri,
        title = song.title,
        modifier = Modifier.fillMaxSize(),
        cornerRadius = 18.dp,
        iconSize = 48.dp,
      )
    }

    Column(
      modifier =
        Modifier.align(Alignment.BottomCenter)
          .fillMaxWidth()
          .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (note.isNotBlank()) {
        Text(
          text = note,
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.60f),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = actions,
      )
    }
  }
}

private fun stepViz(s: VizState, now: Long, playing: Boolean, w: Float, h: Float, unit: Float) {
  val dt = if (s.lastNanos == 0L) 0.016f else ((now - s.lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
  s.lastNanos = now
  s.time += dt

  var bassTarget = 0f
  if (playing) {
    val real = SpectrumBus.sample(System.nanoTime(), s.raw)
    if (real != null) {
      s.sinceReal = 0f
      bassTarget = real
    } else if (s.sinceReal < 1.2f) {
      // Brief gap in the audio data (e.g. just after a seek): let the bars settle, don't fake it.
      s.sinceReal += dt
      for (i in s.raw.indices) s.raw[i] *= 0.9f
      bassTarget = 0f
    } else {
      // No audio data (e.g. an unusual audio format): fall back to a gentle rhythmic motion.
      val beat = (0.5f + 0.5f * sin(s.time * 2f * PI.toFloat() * 1.8f)).pow(3f)
      for (i in s.raw.indices) {
        val f = i / s.raw.size.toFloat()
        s.raw[i] =
          ((0.25f + 0.55f * beat) * (1f - 0.6f * f) * (0.7f + 0.3f * sin(s.time * 3f + i * 0.7f)))
            .coerceIn(0f, 1f)
      }
      bassTarget = beat
    }
  } else {
    s.raw.fill(0f)
  }

  val attack = 1f - exp(-dt * 38f)
  val decay = 1f - exp(-dt * 7f)
  for (i in s.levels.indices) {
    val target = s.raw[i]
    s.levels[i] += (target - s.levels[i]) * (if (target > s.levels[i]) attack else decay)
  }
  s.bass += (bassTarget - s.bass) * (if (bassTarget > s.bass) attack else decay)

  // Beat: a sudden rise in bass energy throws a burst of particles and pulses the art.
  s.sinceBeat += dt
  s.sinceAmbient += dt
  val rise = bassTarget - s.prevBassRaw
  if (playing && bassTarget > 0.55f && rise > 0.12f && s.sinceBeat > 0.18f) {
    s.beatPulse = 1f
    s.sinceBeat = 0f
    s.spawn(w, h, unit, 4)
  }
  s.prevBassRaw = bassTarget
  s.beatPulse = max(0f, s.beatPulse - dt * 3.5f)
  if (playing && s.sinceAmbient > 0.30f) {
    s.sinceAmbient = 0f
    s.spawn(w, h, unit, 1)
  }

  for (i in 0 until MAX_PARTICLES) {
    if (s.life[i] > 0f) {
      s.px[i] += s.vx[i] * dt
      s.py[i] += s.vy[i] * dt
      s.life[i] -= dt
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVisualizer(
  s: VizState,
  accent: Color,
  cx: Float,
  cy: Float,
  art: Float,
  unit: Float,
) {
  // Glow behind the art, breathing with the bass.
  val glowR = art * (0.85f + 0.5f * s.bass) * 1.4f
  drawCircle(
    brush =
      Brush.radialGradient(
        colors = listOf(accent.copy(alpha = 0.40f + 0.30f * s.bass), Color.Transparent),
        center = Offset(cx, cy),
        radius = glowR,
      ),
    radius = glowR,
    center = Offset(cx, cy),
  )

  // Mirrored audio bars on both sides of the art; bass in the middle, treble toward the edges.
  val gap = 10f * unit
  val maxLen = max(8f * unit, (size.width - art) / 2f - gap - 6f * unit)
  val rowH = art * 1.05f / BAR_ROWS
  val top = cy - art * 0.525f
  val mid = (BAR_ROWS - 1) / 2f
  for (r in 0 until BAR_ROWS) {
    val d = abs(r - mid) / mid
    val band = (d * (s.levels.size - 1) * 0.85f).toInt().coerceIn(0, s.levels.size - 1)
    val level = s.levels[band]
    val len = max(maxLen * 0.04f, level * maxLen * (1f - 0.35f * d))
    val y = top + r * rowH + rowH * 0.19f
    val bh = rowH * 0.62f
    val color = lerp(accent.copy(alpha = 0.50f + 0.40f * level), Color.White, level * 0.35f)
    val radius = CornerRadius(bh / 2f, bh / 2f)
    drawRoundRect(color, Offset(cx - art / 2f - gap - len, y), Size(len, bh), radius)
    drawRoundRect(color, Offset(cx + art / 2f + gap, y), Size(len, bh), radius)
  }

  // Floating glowing particles.
  for (i in 0 until MAX_PARTICLES) {
    val life = s.life[i]
    if (life <= 0f) continue
    val t = (life / s.maxLife[i]).coerceIn(0f, 1f)
    val fade = min(1f, t * 3f) * min(1f, (1f - t) * 6f + 0.2f)
    val sz = s.size[i] * (1f + 0.6f * s.beatPulse)
    val c = lerp(accent, Color.White, 0.55f)
    drawRect(c.copy(alpha = 0.16f * fade), Offset(s.px[i] - sz, s.py[i] - sz), Size(sz * 2f, sz * 2f))
    drawRect(c.copy(alpha = 0.85f * fade), Offset(s.px[i] - sz / 2f, s.py[i] - sz / 2f), Size(sz, sz))
  }
}

/** A bright, saturated color taken from the album art, or [fallback] when there is none. */
@Composable
private fun rememberArtAccent(uri: Uri?, fallback: Color): Color {
  val context = LocalContext.current
  var accent by remember(uri, fallback) { mutableStateOf(fallback) }
  LaunchedEffect(uri, fallback) {
    if (uri == null) return@LaunchedEffect
    val computed =
      withContext(Dispatchers.IO) {
        runCatching {
            val request =
              ImageRequest.Builder(context).data(uri).allowHardware(false).size(48).build()
            val result = context.imageLoader.execute(request)
            (result as? SuccessResult)?.drawable?.toBitmap()?.let { vividAccent(it) }
          }
          .getOrNull()
      }
    if (computed != null) accent = computed
  }
  return accent
}

private fun vividAccent(bmp: Bitmap): Color? {
  val w = bmp.width
  val h = bmp.height
  if (w == 0 || h == 0) return null
  val hsv = FloatArray(3)
  var sx = 0.0
  var sy = 0.0
  var sat = 0.0
  var weightSum = 0.0
  val stepX = max(1, w / 24)
  val stepY = max(1, h / 24)
  var y = 0
  while (y < h) {
    var x = 0
    while (x < w) {
      android.graphics.Color.colorToHSV(bmp.getPixel(x, y), hsv)
      val weight = (hsv[1] * hsv[2]).toDouble()
      if (weight > 0.02) {
        val rad = Math.toRadians(hsv[0].toDouble())
        sx += cos(rad) * weight
        sy += sin(rad) * weight
        sat += hsv[1] * weight
        weightSum += weight
      }
      x += stepX
    }
    y += stepY
  }
  if (weightSum < 3.0) return null // grayscale artwork: keep the app accent
  var hue = Math.toDegrees(atan2(sy, sx)).toFloat()
  if (hue < 0f) hue += 360f
  val saturation = (sat / weightSum).toFloat().coerceIn(0.6f, 1f)
  return Color.hsv(hue, saturation, 1f)
}
