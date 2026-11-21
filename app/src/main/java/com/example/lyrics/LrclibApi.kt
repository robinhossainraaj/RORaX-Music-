package com.example.lyrics

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

object LrclibClientFactory {
  private const val BASE_URL = "https://lrclib.net/"
  private const val USER_AGENT = "RORaX Music v1.0 (com.rorax.music)"

  fun create(): Pair<LrclibApi, Moshi> {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }

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
        .addInterceptor(logging)
        .build()

    val retrofit =
      Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    return retrofit.create(LrclibApi::class.java) to moshi
  }
}
