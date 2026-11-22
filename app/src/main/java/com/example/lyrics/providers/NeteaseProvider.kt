package com.example.lyrics.providers

import com.example.lyrics.LrcParser
import com.example.lyrics.LyricMatcher
import com.example.model.LyricQuery
import com.example.model.LyricSource
import com.example.model.RemoteLyricTrack
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * NetEase Cloud Music. A very large catalogue that includes many Asian and South Asian releases.
 * Its web endpoints are public but not formally documented, so every response is parsed
 * defensively and any surprise is reported as "no result" rather than an error.
 */
class NeteaseProvider(private val client: OkHttpClient) : LyricsProvider {
  override val source = LyricSource.NETEASE

  override suspend fun lookup(
    query: LyricQuery,
    accept: (List<RemoteLyricTrack>) -> Boolean,
  ): ProviderResult = guarded {
    val title = LyricMatcher.normalizeForQuery(query.title).ifEmpty { query.title }
    val artist = query.artist.takeUnless { it.isUnknownTag() }.orEmpty()
    val keywords = listOf("$title $artist".trim(), title).distinct()

    val collected = LinkedHashMap<Long, RemoteLyricTrack>()
    for (keyword in keywords) {
      val ranked =
        searchSongs(keyword)
          .sortedByDescending { LyricMatcher.scoreMetadata(query, it).first }
          .take(MAX_LYRIC_FETCHES)
      for (hit in ranked) {
        fetchLyrics(hit)?.let { collected.putIfAbsent(it.id, it) }
        delay(REQUEST_GAP_MS)
      }
      if (accept(collected.values.toList())) break
    }
    ProviderResult.Success(collected.values.toList())
  }

  override suspend fun search(text: String): ProviderResult = guarded {
    val tracks = mutableListOf<RemoteLyricTrack>()
    for (hit in searchSongs(text.trim()).take(MANUAL_RESULTS)) {
      fetchLyrics(hit)?.let { tracks.add(it) }
      delay(REQUEST_GAP_MS)
    }
    ProviderResult.Success(tracks)
  }

  private suspend fun searchSongs(keyword: String): List<RemoteLyricTrack> {
    if (keyword.isBlank()) return emptyList()
    val url =
      "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
        .addQueryParameter("csrf_token", "")
        .addQueryParameter("s", keyword)
        .addQueryParameter("type", "1")
        .addQueryParameter("offset", "0")
        .addQueryParameter("limit", "10")
        .build()
    val reply = client.fetch(url, HEADERS)
    failureForStatus(reply.code)?.let { throw ProviderUnavailable(it) }
    if (!reply.isSuccessful) return emptyList()
    return parseSearch(reply.body)
  }

  private suspend fun fetchLyrics(hit: RemoteLyricTrack): RemoteLyricTrack? {
    val url =
      "https://music.163.com/api/song/lyric".toHttpUrl().newBuilder()
        .addQueryParameter("id", hit.id.toString())
        .addQueryParameter("lv", "1")
        .addQueryParameter("kv", "1")
        .addQueryParameter("tv", "-1")
        .build()
    val reply = client.fetch(url, HEADERS)
    failureForStatus(reply.code)?.let { throw ProviderUnavailable(it) }
    if (!reply.isSuccessful) return null
    return parseLyrics(hit, reply.body)
  }

  companion object {
    private const val MAX_LYRIC_FETCHES = 3
    private const val MANUAL_RESULTS = 6
    private const val REQUEST_GAP_MS = 150L
    private val HEADERS =
      mapOf("Referer" to "https://music.163.com/", "Accept" to "application/json")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Reads the `result.songs` list of a search response. Malformed input yields an empty list. */
    internal fun parseSearch(body: String?): List<RemoteLyricTrack> {
      if (body.isNullOrBlank()) return emptyList()
      val songs =
        runCatching {
            json.parseToJsonElement(body).jsonObject["result"]?.jsonObject?.get("songs")?.jsonArray
          }
          .getOrNull() ?: return emptyList()
      return songs.mapNotNull { element ->
        val song = element as? JsonObject ?: return@mapNotNull null
        val id = song["id"].asLong() ?: return@mapNotNull null
        val name = song["name"].asText() ?: return@mapNotNull null
        val artists = (song["artists"] as? JsonArray ?: song["ar"] as? JsonArray).orEmpty()
        val artistNames = artists.mapNotNull { (it as? JsonObject)?.get("name").asText() }
        val album =
          ((song["album"] as? JsonObject) ?: (song["al"] as? JsonObject))?.get("name").asText()
        val durationMs = (song["duration"] ?: song["dt"]).asDouble()
        RemoteLyricTrack(
          provider = LyricSource.NETEASE,
          id = id,
          trackName = name,
          artistName = artistNames.joinToString(", ").ifEmpty { null },
          albumName = album,
          durationSec = durationMs?.let { it / 1000.0 },
        )
      }
    }

    /** Builds a lyric-bearing track from a `song/lyric` response, or null if there is nothing. */
    internal fun parseLyrics(hit: RemoteLyricTrack, body: String?): RemoteLyricTrack? {
      if (body.isNullOrBlank()) return null
      val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
      if ((root["nolyric"] as? JsonPrimitive)?.booleanOrNull == true) {
        return hit.copy(instrumental = true)
      }
      val raw = (root["lrc"] as? JsonObject)?.get("lyric").asText()?.trim().orEmpty()
      if (raw.isEmpty()) return null
      val parsed = LrcParser.parse(raw)
      return if (parsed.hasValidTimestamps) {
        hit.copy(
          syncedLyrics = raw,
          plainLyrics = parsed.lines.joinToString("\n") { it.text },
        )
      } else {
        hit.copy(plainLyrics = raw)
      }
    }

    private fun JsonElement?.asText(): String? =
      (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonElement?.asLong(): Long? = (this as? JsonPrimitive)?.longOrNull

    private fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.doubleOrNull
  }
}
