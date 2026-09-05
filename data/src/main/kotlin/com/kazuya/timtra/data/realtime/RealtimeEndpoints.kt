package com.kazuya.timtra.data.realtime

/**
 * GTFS-RT の取得先。鳥取県オープンデータ（バス情報）の日ノ丸自動車 VehiclePosition。
 * protobuf の JSON 表現で配信される（core の GtfsRtParser が判別して読む）。docs/ids.md 参照。
 * 取得は 30 秒に 1 回まで（提供元の条件。FetchThrottle が保証）。
 */
object RealtimeEndpoints {
    const val VEHICLE_POSITIONS_URL: String = "https://odp-pref-tottori.tori-info.co.jp/bus_data/2_rt.json"

    val isConfigured: Boolean get() = VEHICLE_POSITIONS_URL.isNotBlank()
}
