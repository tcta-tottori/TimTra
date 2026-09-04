package com.kazuya.timtra.data.realtime

/**
 * GTFS-RT の取得先。鳥取県オープンデータ（バス情報）が公開する日ノ丸自動車の VehiclePosition の URL を入れる。
 * 空のままなら「未設定」として取得しない。確定したら docs/ids.md にも記録する。
 */
object RealtimeEndpoints {
    const val VEHICLE_POSITIONS_URL: String = ""

    val isConfigured: Boolean get() = VEHICLE_POSITIONS_URL.isNotBlank()
}
