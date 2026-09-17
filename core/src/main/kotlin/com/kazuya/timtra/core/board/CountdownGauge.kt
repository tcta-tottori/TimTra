package com.kazuya.timtra.core.board

import java.time.Duration
import java.time.LocalDateTime

/**
 * 時計ホームの、乗り物アイコンを囲むリングの残量（0〜1）。
 *
 * 砂時計のように **減っていく** 向きで持つ。普段は満タンのままで、発車の [WINDOW] 前から
 * 減りはじめ、発車時刻でちょうど 0 になる。残り時間の数字と同じものを目で見て分かる形にしただけなので、
 * 設定値には依らない。
 */
object CountdownGauge {
    /** リングが減りはじめる、発車までの時間。 */
    val WINDOW: Duration = Duration.ofMinutes(15)

    /**
     * @param window 減りはじめる時間。既定は [WINDOW]。
     * @return 1 = 満タン（まだ先）、0 = 発車時刻（もしくはそれを過ぎている）。
     */
    fun level(
        now: LocalDateTime,
        departure: LocalDateTime,
        window: Duration = WINDOW,
    ): Float {
        val left = Duration.between(now, departure)
        return when {
            left <= Duration.ZERO -> 0f
            left >= window -> 1f
            else -> left.toMillis().toFloat() / window.toMillis().toFloat()
        }
    }
}
