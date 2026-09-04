package com.kazuya.timtra.core.data

import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.BusTrip
import com.kazuya.timtra.core.model.ServiceCalendar
import java.time.LocalDate

/**
 * バス時刻表（南吉成⇔鳥取駅の区間だけ）。プリパッケージ DB を読み込んでメモリに持つ。
 * 対象便は数十本しかないので全件保持でよい。スマホ・Wear のどちらでも同じ計算結果になるよう、
 * Android 側は DB の行をこのクラスに詰め替えるだけにする。
 */
class BusTimetable(
    trips: Collection<BusTrip>,
    val calendar: ServiceCalendar,
    /** プリパッケージ DB の meta テーブル（feed_version など）。 */
    val meta: Map<String, String> = emptyMap(),
) {
    val trips: List<BusTrip> = trips.sortedWith(compareBy({ it.direction }, { it.departure }, { it.tripId }))

    private val byDirection: Map<BusDirection, List<BusTrip>> = this.trips.groupBy { it.direction }

    /** その日（サービス日）に運行する便を発時刻順で返す。深夜便は翌日の時刻になることに注意。 */
    fun tripsOn(
        serviceDate: LocalDate,
        direction: BusDirection,
    ): List<BusTrip> {
        val active = calendar.activeServiceIds(serviceDate)
        return byDirection[direction].orEmpty().filter { it.serviceId in active }
    }

    /** その日に対象区間の便が 1 本でもあるか。運休日の通知抑止に使う。 */
    fun hasServiceOn(serviceDate: LocalDate): Boolean = BusDirection.entries.any { tripsOn(serviceDate, it).isNotEmpty() }

    val feedVersion: String? get() = meta[META_FEED_VERSION]

    companion object {
        // meta テーブルのキー（tools/gtfs_import.py と一致させる）
        const val META_SCHEMA_VERSION = "schema_version"
        const val META_FEED_VERSION = "feed_version"
        const val META_FEED_PUBLISHER_NAME = "feed_publisher_name"
        const val META_FEED_START_DATE = "feed_start_date"
        const val META_FEED_END_DATE = "feed_end_date"
        const val META_AGENCY_NAMES = "agency_names"
        const val META_ROUTE_IDS = "route_ids"
        const val META_HOME_STOP_IDS = "home_stop_ids"
        const val META_STATION_STOP_IDS = "station_stop_ids"

        /** core が前提とする DB スキーマ版。docs/db_schema.md / gtfs_import.py の SCHEMA_VERSION と一致させる。 */
        const val SUPPORTED_SCHEMA_VERSION = 1
    }
}
