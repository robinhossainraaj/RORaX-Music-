package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    LyricsEntity::class,
    FavoriteEntity::class,
    RecentPlayEntity::class,
    PlaylistEntity::class,
    PlaylistSongCrossRef::class,
    DownloadTaskEntity::class,
  ],
  version = 1,
  exportSchema = false,
)
abstract class RoraxDatabase : RoomDatabase() {
  abstract fun lyricsDao(): LyricsDao

  abstract fun libraryInteractionDao(): LibraryInteractionDao

  abstract fun downloadTaskDao(): DownloadTaskDao

  companion object {
    fun build(context: Context): RoraxDatabase {
      return Room.databaseBuilder(
          context.applicationContext,
          RoraxDatabase::class.java,
          "rorax_music.db",
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }
  }
}
