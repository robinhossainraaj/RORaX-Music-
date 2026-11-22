package com.example.lyrics

import com.example.BuildConfig
import com.example.model.LyricSource
import com.example.model.RemoteLyricTrack
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class LrclibTrackDto(
  val id: Long,
  val trackName: String? = null,
  val artistName: String? = null,
  val albumName: String? = null,
  val duration: Double? = null,
  val instrumental: Boolean? = null,
  val plainLyrics: String? = null,
  val syncedLyrics: String? = null,
)

fun LrclibTrackDto.toRemote(): RemoteLyricTrack =
  RemoteLyricTrack(
    provider = LyricSource.LRCLIB,
    id = id,
    trackName = trackName,
    artistName = artistName,
    albumName = albumName,
    durationSec = duration,
    instrumental = instrumental == true,
    plainLyrics = plainLyrics,
    syncedLyrics = syncedLyrics,
  )

interface LrclibApi {
  @GET("api/get")
  suspend fun getLyrics(
    @Query("track_name") trackName: String,
    @Query("artist_name") artistName: String,
    @Query("album_name") albumName: String? = null,
    @Query("duration") durationSec: Int? = null,
  ): Response<LrclibTrackDto>

  @GET("api/search")
  suspend fun searchLyrics(
    @Query("q") query: String? = null,
    @Query("track_name") trackName: String? = null,
    @Query("artist_name") artistName: String? = null,
    @Query("album_name") albumName: String? = null,
  ): Response<List<LrclibTrackDto>>
}

/** Shared networking objects used by every lyric source. */
class LyricsNetwork(
  val lrclibApi: LrclibApi,
  val httpClient: OkHttpClient,
  val moshi: Moshi,
)

object LrclibClientFactory {
  private const val BASE_URL = "https://lrclib.net/"
  private const val USER_AGENT =
    "RORaX Music/${BuildConfig.VERSION_NAME} (https://github.com/robinhossainraaj/RORaX-Music-)"

  fun create(): LyricsNetwork {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val okHttp =
      OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .addInterceptor { chain ->
          val request =
            chain
              .request()
              .newBuilder()
              .header("User-Agent", USER_AGENT)
              .header("Accept", "application/json")
              .build()
          chain.proceed(request)
        }
        .apply {
          // Request logging contains song titles, so it is only enabled in debug builds.
          if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
          }
        }
        .build()

    val retrofit =
      Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    return LyricsNetwork(
      lrclibApi = retrofit.create(LrclibApi::class.java),
      httpClient = okHttp,
      moshi = moshi,
    )
  }
}
