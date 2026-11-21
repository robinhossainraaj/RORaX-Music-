package com.example

import android.app.Application
import com.example.data.db.RoraxDatabase
import com.example.data.repo.AiAssistantRepository
import com.example.data.repo.LocalMusicRepository
import com.example.data.repo.LyricsRepository
import com.example.data.repo.PlaceholderAiAssistantRepository
import com.example.data.repo.UserPreferencesRepository
import com.example.lyrics.LrclibClientFactory
import com.example.player.PlaybackController

class RoraxApplication : Application() {

  val database: RoraxDatabase by lazy { RoraxDatabase.build(this) }

  val preferencesRepository: UserPreferencesRepository by lazy {
    UserPreferencesRepository(this)
  }

  val localMusicRepository: LocalMusicRepository by lazy {
    LocalMusicRepository(this, database.libraryInteractionDao())
  }

  private val lrclibDeps by lazy { LrclibClientFactory.create() }

  val lyricsRepository: LyricsRepository by lazy {
    LyricsRepository(
      context = this,
      lyricsDao = database.lyricsDao(),
      downloadTaskDao = database.downloadTaskDao(),
      lrclibApi = lrclibDeps.first,
      moshi = lrclibDeps.second,
    )
  }

  val aiAssistantRepository: AiAssistantRepository by lazy {
    PlaceholderAiAssistantRepository()
  }

  val playbackController: PlaybackController by lazy {
    PlaybackController(
      context = this,
      localMusicRepository = localMusicRepository,
      preferencesRepository = preferencesRepository,
    )
  }
}
