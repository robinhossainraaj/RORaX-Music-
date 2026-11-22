package com.example.lyrics.providers

import com.example.lyrics.LrcParser
import com.example.lyrics.LyricMatcher
import com.example.model.LyricQuery
import com.example.model.LyricSource
import com.example.model.RemoteLyricTrack
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

/**
 * Kugou Music. Used as a last fallback. Like NetEase, its endpoints are public but undocumented,
 * so parsing is defensive and surprises are treated as "nothing found".
 */
class KugouProvider(private val client: OkHttpClient) : LyricsProvider {
  override val source = LyricSource.KUGOU

  /** A song found by search, before its lyrics are fetched. */
  internal class Hit(val track: RemoteLyricTrack, val hash: String)

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
          .sortedByDescending { LyricMatcher.scoreMetadata(query, it.track).first }
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

  private suspend fun searchSongs(keyword: String): List<Hit> {
    if (keyword.isBlank()) return emptyList()
    val url =
      "https://mobilecdn.kugou.com/api/v3/search/song".toHttpUrl().newBuilder()
        .addQueryParameter("format", "json")
        .addQueryParameter("keyword", keyword)
        .addQueryParameter("page", "1")
        .addQueryParameter("pagesize", "10")
        .build()
    val reply = client.fetch(url)
    failureForStatus(reply.code)?.let { throw ProviderUnavailable(it) }
    if (!reply.isSuccessful) return emptyList()
    return parseSearch(reply.body)
  }

  private suspend fun fetchLyrics(hit: Hit): RemoteLyricTrack? {
    val durationMs = ((hit.track.durationSec ?: 0.0) * 1000).toLong()
    val searchUrl =
      "https://lyrics.kugou.com/search".toHttpUrl().newBuilder()
        .addQueryParameter("ver", "1")
        .addQueryParameter("man", "yes")
        .addQueryParameter("client", "pc")
        .addQueryParameter("hash", hit.hash)
        .addQueryParameter("duration", durationMs.toString())
        .build()
    val searchReply = client.fetch(searchUrl)
    failureForStatus(searchReply.code)?.let { throw ProviderUnavailable(it) }
    if (!searchReply.isSuccessful) return null
    val (id, accessKey) = parseLyricCandidate(searchReply.body) ?: return null

    val downloadUrl =
      "https://lyrics.kugou.com/download".toHttpUrl().newBuilder()
        .addQueryParameter("ver", "1")
        .addQueryParameter("client", "pc")
        .addQueryParameter("id", id)
        .addQueryParameter("accesskey", accessKey)
        .addQueryParameter("fmt", "lrc")
        .addQueryParameter("charset", "utf8")
        .build()
    val downloadReply = client.fetch(downloadUrl)
    failureForStatus(downloadReply.code)?.let { throw ProviderUnavailable(it) }
    if (!downloadReply.isSuccessful) return null
    return parseLyrics(hit.track, downloadReply.body)
  }

  companion object {
    private const val MAX_LYRIC_FETCHES = 3
    private const val MANUAL_RESULTS = 6
    private const val REQUEST_GAP_MS = 150L
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Reads `data.info` from a search response. Malformed input yields an empty list. */
    internal fun parseSearch(body: String?): List<Hit> {
      if (body.isNullOrBlank()) return emptyList()
      val info =
        runCatching {
            json.parseToJsonElement(body).jsonObject["data"]?.jsonObject?.get("info")?.jsonArray
          }
          .getOrNull() ?: return emptyList()
      return info.mapNotNull { element ->
        val song = element as? JsonObject ?: return@mapNotNull null
        val hash = song["hash"].asText()?.takeIf { it.length >= 15 } ?: return@mapNotNull null
        val name = song["songname"].asText() ?: return@mapNotNull null
        // The hash is hexadecimal, so its first 15 digits make a stable numeric id.
        val id = hash.take(15).toLongOrNull(16) ?: return@mapNotNull null
        Hit(
          track =
            RemoteLyricTrack(
              provider = LyricSource.KUGOU,
              id = id,
              trackName = name,
              artistName = song["singername"].asText(),
              albumName = song["album_name"].asText(),
              durationSec = (song["duration"] as? JsonPrimitive)?.doubleOrNull,
            ),
          hash = hash,
        )
      }
    }

    /** Returns the id and access key of the first lyric candidate in a lyric search response. */
    internal fun parseLyricCandidate(body: String?): Pair<String, String>? {
      if (body.isNullOrBlank()) return null
      val first =
        runCatching {
            json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray?.firstOrNull()
          }
          .getOrNull() as? JsonObject ?: return null
      val id = first["id"].asText() ?: return null
      val key = first["accesskey"].asText() ?: return null
      return id to key
    }

    /** Decodes the base64 `content` field of a lyric download response. */
    @OptIn(ExperimentalEncodingApi::class)
    internal fun parseLyrics(track: RemoteLyricTrack, body: String?): RemoteLyricTrack? {
      if (body.isNullOrBlank()) return null
      val content =
        runCatching { json.parseToJsonElement(body).jsonObject["content"].asText() }.getOrNull()
          ?: return null
      val raw =
        runCatching { Base64.decode(content).toString(Charsets.UTF_8) }
          .getOrNull()
          ?.removePrefix("﻿")
          ?.trim()
          .orEmpty()
      if (raw.isEmpty()) return null
      val parsed = LrcParser.parse(raw)
      return if (parsed.hasValidTimestamps) {
        track.copy(syncedLyrics = raw, plainLyrics = parsed.lines.joinToString("\n") { it.text })
      } else {
        null
      }
    }

    private fun JsonElement?.asText(): String? =
      (this as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
  }
}
