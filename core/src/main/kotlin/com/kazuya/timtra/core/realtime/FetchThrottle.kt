package com.kazuya.timtra.core.realtime

import com.kazuya.timtra.core.TimTraConstants
import java.time.Duration
import java.time.Instant

/**
 * 取得頻度の上限（30 秒に 1 回）。データ提供元の明示条件なので、成功・失敗にかかわらず
 * 「試行」の間隔で数える（CLAUDE.md 4-2）。
 */
class FetchThrottle(
    private val minInterval: Duration = Duration.ofSeconds(TimTraConstants.GTFS_RT_MIN_INTERVAL_SECONDS),
    private val clock: () -> Instant = Instant::now,
) {
    private val lock = Any()
    private var lastAttempt: Instant? = null

    /** 次に取得してよいまでの時間。今すぐ可能なら ZERO。 */
    fun timeUntilAllowed(now: Instant = clock()): Duration =
        synchronized(lock) {
            val last = lastAttempt ?: return Duration.ZERO
            val elapsed = Duration.between(last, now)
            if (elapsed >= minInterval || elapsed.isNegative) Duration.ZERO else minInterval.minus(elapsed)
        }

    /** 取得してよければ試行として記録し true を返す。 */
    fun tryAcquire(now: Instant = clock()): Boolean =
        synchronized(lock) {
            if (timeUntilAllowed(now) > Duration.ZERO) return false
            lastAttempt = now
            true
        }

    /** プロセス再起動をまたいでも間隔を守るため、保存しておいた最終試行時刻を復元する。 */
    fun restoreLastAttempt(at: Instant?) {
        synchronized(lock) { lastAttempt = at }
    }

    fun lastAttempt(): Instant? = synchronized(lock) { lastAttempt }
}
