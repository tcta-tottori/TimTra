package com.kazuya.timtra.core

import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** プロジェクト全体で共有する定数。ハードコードを避けるためここに集約する（CLAUDE.md 10）。 */
object TimTraConstants {
    /** アプリが扱うタイムゾーン。鳥取県内の通勤専用なので固定でよい。 */
    val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")

    /** GTFS の日付表記 (yyyyMMdd)。 */
    val GTFS_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** JR 時刻表 JSON の日付表記 (yyyy-MM-dd)。 */
    val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    /** 便を探すときに先読みする最大日数。年末年始の運休が続いても翌便を見つけられる長さ。 */
    const val MAX_LOOKAHEAD_DAYS: Long = 10

    /** GTFS-RT の取得間隔の下限（秒）。データ提供元の明示条件（CLAUDE.md 4-2）。 */
    const val GTFS_RT_MIN_INTERVAL_SECONDS: Long = 30
}
