package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.FavoriteEntity
import com.example.data.db.LyricsEntity
import com.example.data.db.RoraxDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private lateinit var db: RoraxDatabase

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, RoraxDatabase::class.java).allowMainThreadQueries().build()
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `app identity matches RORaX Music specification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("RORaX Music", context.getString(R.string.app_name))
    assertEquals("com.rorax.music", context.packageName)
  }

  @Test
  fun `clearing lyric cache preserves user favorites`() = runTest {
    db.lyricsDao()
      .upsert(
        LyricsEntity(
          songId = 42L,
          status = "CACHED_SYNCED",
          source = "LRCLIB",
          syncedLyricsLrc = "[00:01.00]Hello offline",
        )
      )
    db.libraryInteractionDao().addFavorite(FavoriteEntity(songId = 42L))

    assertNotNull(db.lyricsDao().getBySongId(42L))
    db.lyricsDao().clearAll()

    assertNull(db.lyricsDao().getBySongId(42L))
    val favs = db.libraryInteractionDao().observeFavoriteIds().first()
    assertEquals(listOf(42L), favs)
  }
}
