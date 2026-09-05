package com.kazuya.timtra.data.realtime

import com.kazuya.timtra.core.realtime.FeedFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** GTFS-RT の protobuf を OkHttp で取得する。 */
@Singleton
class OkHttpFeedFetcher
    @Inject
    constructor() : FeedFetcher {
        private val client =
            OkHttpClient
                .Builder()
                .connectTimeout(TIMEOUT)
                .readTimeout(TIMEOUT)
                .build()

        override suspend fun fetch(url: String): ByteArray =
            withContext(Dispatchers.IO) {
                val request =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json, application/x-protobuf, application/octet-stream")
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    response.body.bytes()
                }
            }

        private companion object {
            val TIMEOUT: Duration = Duration.ofSeconds(10)
            const val USER_AGENT = "TimTra/0.1 (personal commute app)"
        }
    }
