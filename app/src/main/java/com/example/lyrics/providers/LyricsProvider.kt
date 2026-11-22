package com.example.lyrics.providers

import com.example.model.LyricQuery
import com.example.model.LyricSource
import com.example.model.RemoteLyricTrack

/** Outcome of asking one lyric source about a song. */
sealed interface ProviderResult {
  /** The source answered. [tracks] may be empty, which simply means it has nothing for this song. */
  data class Success(val tracks: List<RemoteLyricTrack>) : ProviderResult

  /** The source could not be reached or is temporarily overloaded. */
  data class Failure(val retryable: Boolean = true, val rateLimited: Boolean = false) : ProviderResult
}

/**
 * A place lyrics can be fetched from. Every source answers the same two questions, so new
 * sources can be added without touching the matching, caching or UI code.
 */
interface LyricsProvider {
  val source: LyricSource

  /**
   * Looks up lyrics for [query] using as many strategies as it takes. After each strategy
   * [accept] is called with everything found so far; returning true stops the search early.
   */
  suspend fun lookup(
    query: LyricQuery,
    accept: (List<RemoteLyricTrack>) -> Boolean = { false },
  ): ProviderResult

  /** Free-text search used when the person looks for lyrics by hand. */
  suspend fun search(text: String): ProviderResult
}
