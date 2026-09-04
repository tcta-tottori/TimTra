package com.kazuya.timtra.core.realtime

import java.time.Duration
import java.time.Instant

/** HTTP 取得の差し替え口。core は HTTP ライブラリに依存しない（data 側で OkHttp 実装）。 */
fun interface FeedFetcher {
    /** URL の内容をそのまま返す。失敗は例外。 */
    suspend fun fetch(url: String): ByteArray
}

sealed interface FetchResult {
    /** 直近の取得結果（あれば）。Throttled / Failure でも前回の値を表示に使える。 */
    val lastFeed: VehiclePositionFeed?

    data class Success(
        val feed: VehiclePositionFeed,
    ) : FetchResult {
        override val lastFeed: VehiclePositionFeed get() = feed
    }

    /** 30 秒制限により取得しなかった。 */
    data class Throttled(
        val retryAfter: Duration,
        override val lastFeed: VehiclePositionFeed?,
    ) : FetchResult

    data class Failure(
        val error: Throwable,
        override val lastFeed: VehiclePositionFeed?,
    ) : FetchResult
}

/**
 * GTFS-RT VehiclePosition クライアント。30 秒スロットリング内蔵（CLAUDE.md 4-2）。
 * 呼び出しは画面がフォアグラウンドにある間だけにすること（呼び出し側の責務）。
 */
class GtfsRtClient(
    private val url: String,
    private val fetcher: FeedFetcher,
    private val throttle: FetchThrottle = FetchThrottle(),
    private val clock: () -> Instant = Instant::now,
) {
    @Volatile
    private var last: VehiclePositionFeed? = null

    val lastFeed: VehiclePositionFeed? get() = last

    suspend fun fetchVehiclePositions(): FetchResult {
        val now = clock()
        if (!throttle.tryAcquire(now)) return FetchResult.Throttled(throttle.timeUntilAllowed(now), last)
        return try {
            val bytes = fetcher.fetch(url)
            val feed = GtfsRtParser.parseVehiclePositions(bytes, now)
            last = feed
            FetchResult.Success(feed)
        } catch (e: Exception) {
            FetchResult.Failure(e, last)
        }
    }
}
