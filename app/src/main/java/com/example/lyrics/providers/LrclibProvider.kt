package com.example.lyrics.providers

import com.example.lyrics.LrclibApi
import com.example.lyrics.LyricMatcher
import com.example.lyrics.toRemote
import com.example.model.LyricQuery
import com.example.model.LyricSource
import com.example.model.RemoteLyricTrack
import retrofit2.Response

/** LRCLIB (https://lrclib.net/docs): the primary source, used first. */
class LrclibProvider(private val api: LrclibApi) : LyricsProvider {
  override val source = LyricSource.LRCLIB

  override suspend fun lookup(
    query: LyricQuery,
    accept: (List<RemoteLyricTrack>) -> Boolean,
  ): ProviderResult = guarded {
    val title = LyricMatcher.normalizeForQuery(query.title).ifEmpty { query.title }
    val artist = query.artist.takeUnless { it.isUnknownTag() }.orEmpty()
    val album = query.album.takeUnless { it.isUnknownTag() }
    val found = LinkedHashMap<Long, RemoteLyricTrack>()

    fun add(tracks: List<RemoteLyricTrack>) = tracks.forEach { found.putIfAbsent(it.id, it) }

    // 1. Exact lookup when the tags are complete.
    if (artist.isNotEmpty()) {
      val reply = api.getLyrics(title, artist, album, query.durationSec.takeIf { it > 0 })
      failureOf(reply)?.let { return@guarded it }
      if (reply.isSuccessful) reply.body()?.let { add(listOf(it.toRemote())) }
      if (accept(found.values.toList())) return@guarded ProviderResult.Success(found.values.toList())
    }

    // 2. Search by title (and artist when known).
    val search =
      if (artist.isNotEmpty()) api.searchLyrics(trackName = title, artistName = artist)
      else api.searchLyrics(query = title)
    failureOf(search)?.let { return@guarded it }
    if (search.isSuccessful) add(search.body().orEmpty().map { it.toRemote() })
    if (accept(found.values.toList())) return@guarded ProviderResult.Success(found.values.toList())

    // 3. Free-text search, which tolerates tags that are spelled differently from the catalogue.
    if (found.isEmpty() && artist.isNotEmpty()) {
      val loose = api.searchLyrics(query = "$artist $title")
      failureOf(loose)?.let { return@guarded it }
      if (loose.isSuccessful) add(loose.body().orEmpty().map { it.toRemote() })
    }

    ProviderResult.Success(found.values.toList())
  }

  override suspend fun search(text: String): ProviderResult = guarded {
    val reply = api.searchLyrics(query = text.trim())
    failureOf(reply)?.let { return@guarded it }
    ProviderResult.Success(
      if (reply.isSuccessful) reply.body().orEmpty().map { it.toRemote() } else emptyList()
    )
  }

  private fun failureOf(reply: Response<*>): ProviderResult.Failure? = failureForStatus(reply.code())
}
