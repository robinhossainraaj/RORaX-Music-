package com.example.lyrics.providers

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A finished HTTP exchange. [body] is null when the server sent none. */
internal class HttpReply(val code: Int, val body: String?) {
  val isSuccessful: Boolean
    get() = code in 200..299
}

/** Thrown inside a provider to stop early and report [failure] to the caller. */
internal class ProviderUnavailable(val failure: ProviderResult.Failure) : Exception()

internal fun failureForStatus(code: Int): ProviderResult.Failure? =
  when {
    code == 429 -> ProviderResult.Failure(retryable = true, rateLimited = true)
    code >= 500 -> ProviderResult.Failure(retryable = true)
    else -> null
  }

/** Runs [block] and turns network problems into a [ProviderResult.Failure] instead of crashing. */
internal inline fun guarded(block: () -> ProviderResult): ProviderResult =
  try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: ProviderUnavailable) {
    e.failure
  } catch (e: IOException) {
    ProviderResult.Failure(retryable = true)
  } catch (e: Exception) {
    ProviderResult.Failure(retryable = false)
  }

/** Performs a GET on a background thread; cancelling the coroutine interrupts the request. */
internal suspend fun OkHttpClient.fetch(
  url: HttpUrl,
  headers: Map<String, String> = emptyMap(),
): HttpReply =
  runInterruptible(Dispatchers.IO) {
    val builder = Request.Builder().url(url)
    headers.forEach { (name, value) -> builder.header(name, value) }
    newCall(builder.build()).execute().use { response ->
      HttpReply(response.code, response.body?.string())
    }
  }

/** True for tag values that mean "we do not know", so they should not be sent to a service. */
internal fun String?.isUnknownTag(): Boolean =
  isNullOrBlank() ||
    equals("Unknown Artist", ignoreCase = true) ||
    equals("Unknown Album", ignoreCase = true) ||
    equals("<unknown>", ignoreCase = true)
