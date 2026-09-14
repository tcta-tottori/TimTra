package com.kazuya.timtra.core.board

import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.calendar.HolidayCalendar
import com.kazuya.timtra.core.calendar.JapaneseHolidays
import com.kazuya.timtra.core.data.BusTimetable
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.JrLegIds
import com.kazuya.timtra.core.model.JrTimetable
import com.kazuya.timtra.core.model.Places
import java.time.LocalDateTime

/** 発車標に出す便の種別。 */
enum class DepartureMode {
    BUS,
    TRAIN,
}

/**
 * 時刻表を出す地点と、そこで乗る向き（CLAUDE.md 2 の固定経路上の 4 か所）。
 * 「地点」ではなく「地点 + 行き先」の組。同じ鳥取駅でもバスと JR は別の地点として扱う。
 * 表示名は Android 側の strings.xml に置く（CLAUDE.md 10）。
 */
enum class BoardPlace(
    val mode: DepartureMode,
    /** [DepartureMode.BUS] のときの方向。 */
    val busDirection: BusDirection? = null,
    /** [DepartureMode.TRAIN] のときの jr_timetable.json の区間 ID。 */
    val jrLegId: String? = null,
) {
    /** 南吉成 バス停 → 鳥取駅方面 */
    HOME_STOP(DepartureMode.BUS, busDirection = BusDirection.TO_STATION),

    /** 鳥取駅 バスターミナル → 用瀬・智頭方面（自宅方面） */
    STATION_BUS(DepartureMode.BUS, busDirection = BusDirection.FROM_STATION),

    /** JR 鳥取駅 → 宝木方面（下り） */
    TOTTORI_JR(DepartureMode.TRAIN, jrLegId = JrLegIds.TOTTORI_TO_HOUGI),

    /** JR 宝木駅 → 鳥取方面（上り） */
    HOUGI_JR(DepartureMode.TRAIN, jrLegId = JrLegIds.HOUGI_TO_TOTTORI),
    ;

    /** この地点で乗る便が属する通勤の向き。鳥取駅がバスか JR かの選択にも使う。 */
    val bound: Bound
        get() =
            when (this) {
                HOME_STOP, TOTTORI_JR -> Bound.OUTBOUND
                STATION_BUS, HOUGI_JR -> Bound.INBOUND
            }
}

/** 発車標の 1 行。 */
data class Departure(
    /** 発時刻（絶対時刻）。深夜便も日付をまたいで正しく並ぶ。 */
    val at: LocalDateTime,
    val mode: DepartureMode,
    /** 系統番号（バス）または列車番号（JR）。 */
    val code: String,
    /** 行き先。 */
    val headsign: String,
    /** 路線名（「用瀬智頭線」「山陰本線」）。 */
    val line: String,
    /** のりば。無ければ null。 */
    val platform: String?,
    /** 対象区間の相手側に着く時刻（バスなら鳥取駅着、JR なら相手駅着）。 */
    val arrivalAt: LocalDateTime,
)

/**
 * 「いまここから次に出る便」を並べる発車標（時計のタイルと時計アプリで使う）。
 *
 * 乗り継ぎ計算（[com.kazuya.timtra.core.journey.JourneyPlanner]）と違って、
 * 1 区間だけを素直に時刻順で出す。最終便のあとは翌日以降の初便へ自然に続く。
 */
object DepartureBoard {
    /** 既定で並べる本数。タイルに収まる量。 */
    const val DEFAULT_LIMIT = 5

    /**
     * 現在地から最寄りの地点を選ぶときの上限距離。
     * 鳥取駅〜宝木駅は約 14 km なので、その中間（乗車中）でもどちらかが選ばれる長さにする。
     */
    const val NEAR_METERS = 10_000.0

    /** 鳥取駅にある 2 つの地点（バスターミナルと JR 駅舎）。約 150 m しか離れておらず距離では選べない。 */
    private val TOTTORI_PLACES = listOf(BoardPlace.STATION_BUS, BoardPlace.TOTTORI_JR)

    /** [now] 以降に出る便を早い順に最大 [limit] 件。1 本も無ければ空。 */
    fun upcoming(
        place: BoardPlace,
        now: LocalDateTime,
        bus: BusTimetable,
        jr: JrTimetable,
        limit: Int = DEFAULT_LIMIT,
        lookaheadDays: Long = TimTraConstants.MAX_LOOKAHEAD_DAYS,
        holidays: HolidayCalendar = JapaneseHolidays,
    ): List<Departure> {
        require(limit > 0) { "limit は 1 以上にしてください" }
        val all =
            when (place.mode) {
                DepartureMode.BUS -> busDepartures(checkNotNull(place.busDirection), now, bus, lookaheadDays)
                DepartureMode.TRAIN -> trainDepartures(checkNotNull(place.jrLegId), now, jr, lookaheadDays, holidays)
            }
        return all
            .filter { !it.at.isBefore(now) }
            .sortedWith(compareBy({ it.at }, { it.code }))
            .take(limit)
    }

    /** 各地点の位置。バス停は GTFS、JR 駅は [Places]。位置が分からない地点は含めない。 */
    fun locations(bus: BusTimetable): Map<BoardPlace, GeoPoint> =
        buildMap {
            bus.homeStopLocation?.let { put(BoardPlace.HOME_STOP, it) }
            put(BoardPlace.STATION_BUS, bus.stationStopLocation ?: Places.TOTTORI_STATION)
            put(BoardPlace.TOTTORI_JR, Places.TOTTORI_STATION)
            put(BoardPlace.HOUGI_JR, Places.HOUGI_STATION)
        }

    /**
     * 現在地に最も近い地点。どの地点からも [maxMeters] より遠ければ null（＝手動で選んでもらう）。
     *
     * 鳥取駅はバスターミナルと JR 駅舎が約 150 m しか離れておらず距離では選べないので、
     * そこにいるときだけ [bound]（時刻帯や手動切替で決めた向き）で決める:
     * 往路なら JR 下り、復路ならバス（自宅方面）。
     */
    fun nearest(
        here: GeoPoint,
        locations: Map<BoardPlace, GeoPoint>,
        bound: Bound,
        maxMeters: Double = NEAR_METERS,
    ): BoardPlace? {
        val nearest =
            locations.entries
                .map { it.key to here.distanceMetersTo(it.value) }
                .minByOrNull { it.second }
                ?: return null
        if (nearest.second > maxMeters) return null
        val place = nearest.first
        if (place !in TOTTORI_PLACES) return place
        return TOTTORI_PLACES.firstOrNull { it.bound == bound && it in locations } ?: place
    }

    private fun busDepartures(
        direction: BusDirection,
        now: LocalDateTime,
        bus: BusTimetable,
        lookaheadDays: Long,
    ): List<Departure> =
        // 前日のサービス日に属する深夜便（24:15 など）が当日 0 時台に走るので、前日分から見る
        (-1..lookaheadDays).flatMap { offset ->
            val serviceDate = now.toLocalDate().plusDays(offset)
            bus.tripsOn(serviceDate, direction).map { trip ->
                Departure(
                    at = trip.departure.at(serviceDate),
                    mode = DepartureMode.BUS,
                    code = trip.routeDisplayName,
                    headsign = trip.headsign,
                    line = trip.routeLongName.ifBlank { trip.routeDisplayName },
                    platform = trip.boardStop.platformCode,
                    arrivalAt = trip.arrival.at(serviceDate),
                )
            }
        }

    private fun trainDepartures(
        legId: String,
        now: LocalDateTime,
        jr: JrTimetable,
        lookaheadDays: Long,
        holidays: HolidayCalendar,
    ): List<Departure> {
        val leg = jr.leg(legId)
        return (0..lookaheadDays).flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset)
            jr.servicesOn(date, legId, holidays).map { service ->
                Departure(
                    at = date.atTime(service.departure),
                    mode = DepartureMode.TRAIN,
                    code = service.trainId,
                    headsign = leg.to,
                    line = leg.line,
                    platform = service.platform.ifBlank { null },
                    arrivalAt = (if (service.arrivesNextDay) date.plusDays(1) else date).atTime(service.arrival),
                )
            }
        }
    }
}
