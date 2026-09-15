package com.kazuya.timtra.wear.board

import com.kazuya.timtra.core.board.BoardPlace
import com.kazuya.timtra.core.board.Departure
import com.kazuya.timtra.core.board.DepartureBoard
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.DayType
import com.kazuya.timtra.data.di.AppClock
import com.kazuya.timtra.data.repository.DepartureBoardRepository
import com.kazuya.timtra.data.repository.SettingsRepository
import com.kazuya.timtra.wear.location.WearLocationProvider
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** 地点をどう決めたか。画面の脚注に出す。 */
enum class PlaceBasis {
    /** 現在地から最寄りを選んだ */
    NEAR_HERE,

    /** ユーザーが手で選んだ */
    MANUAL,

    /** 位置が取れず、手動選択も無いので時刻帯（往路 / 復路）で決めた */
    TIME_OF_DAY,
}

/** どの地点を出すか決めた結果。 */
data class PlaceResolution(
    val place: BoardPlace,
    val basis: PlaceBasis,
    /** 現在地からの距離（メートル）。位置が取れていなければ null。 */
    val distanceMeters: Double?,
)

/** 地点と現在地からの距離。選択画面の並び順に使う。 */
data class PlaceDistance(
    val place: BoardPlace,
    val distanceMeters: Double?,
)

/** 発車標 1 枚分。タイルと時計アプリが共通で使う。 */
data class BoardSnapshot(
    val now: LocalDateTime,
    val place: BoardPlace,
    val basis: PlaceBasis,
    /** [now] 以降の便。先頭が次の 1 本。空なら先読み日数内に運行が無い。 */
    val departures: List<Departure>,
) {
    val next: Departure? get() = departures.firstOrNull()

    /** 次の 1 本より後の便。 */
    val later: List<Departure> get() = departures.drop(1)
}

/** 時刻表画面の日種別の切り替え。時計は幅が無いので 3 つだけにする。 */
enum class DaySelection(
    /** 該当する JR の日種別。空は「今日」（種別で探さずその日を出す）。 */
    val dayTypes: Set<DayType>,
) {
    TODAY(emptySet()),
    WEEKDAY(setOf(DayType.WEEKDAY)),

    /** 土日祝。JR は土曜も日祝と同じダイヤ、バスは土曜と日祝で別なので、実在する直近の 1 日を引く。 */
    WEEKEND(setOf(DayType.SATURDAY, DayType.HOLIDAY)),
}

/** 時刻表画面 1 枚分（ある地点の、ある 1 日の全便）。 */
data class DayBoard(
    val place: BoardPlace,
    val selection: DaySelection,
    val date: LocalDate,
    val now: LocalDateTime,
    /** 始発から終電まで。 */
    val departures: List<Departure>,
) {
    val isToday: Boolean get() = selection == DaySelection.TODAY

    /** 今日の表示での「次の便」。今日でない、または終電を過ぎていれば -1。 */
    val nextIndex: Int get() = if (isToday) departures.indexOfFirst { !it.at.isBefore(now) } else -1
}

/**
 * 時計の発車標を組み立てる。
 *
 * 地点の決め方は [PlaceBasis] の順:
 * 1. 手動で選んでいればそれ。
 * 2. 選んでいなければ現在地から最寄りの地点。
 * 3. 位置が取れない・通勤圏外なら時刻帯で決めた向きの出発地。
 *
 * 位置は「どの停留所にいるか」を決めるためだけに 1 回取る。常駐も追跡もしない（CLAUDE.md 3-4）。
 */
@Singleton
class WearBoardProvider
    @Inject
    constructor(
        private val repository: DepartureBoardRepository,
        private val settings: SettingsRepository,
        private val location: WearLocationProvider,
        private val clock: AppClock,
    ) {
        val locationPermitted: Boolean get() = location.hasPermission

        /** どの地点を出すか決める。位置の取得はここでだけ行う。 */
        suspend fun resolvePlace(locationTimeoutMillis: Long = WearLocationProvider.REQUEST_TIMEOUT_MILLIS): PlaceResolution {
            val manual = manualPlace()
            if (manual != null) return PlaceResolution(manual, PlaceBasis.MANUAL, null)
            val here = location.current(timeoutMillis = locationTimeoutMillis)
            val bound = boundNow()
            val nearest = here?.let { repository.nearest(it, bound) }
            if (nearest != null) {
                val distance = repository.locations()[nearest]?.let { here.distanceMetersTo(it) }
                return PlaceResolution(nearest, PlaceBasis.NEAR_HERE, distance)
            }
            return PlaceResolution(defaultFor(bound), PlaceBasis.TIME_OF_DAY, null)
        }

        /** [place] の発車標。 */
        suspend fun board(
            place: BoardPlace,
            basis: PlaceBasis = PlaceBasis.MANUAL,
            limit: Int = DepartureBoard.DEFAULT_LIMIT,
        ): BoardSnapshot {
            val now = clock.now()
            return BoardSnapshot(
                now = now,
                place = place,
                basis = basis,
                departures = repository.upcoming(place, now, limit),
            )
        }

        /** 地点を決めてそのまま発車標まで作る（タイル用）。 */
        suspend fun snapshot(
            limit: Int = DepartureBoard.DEFAULT_LIMIT,
            locationTimeoutMillis: Long = WearLocationProvider.REQUEST_TIMEOUT_MILLIS,
        ): BoardSnapshot {
            val resolution = resolvePlace(locationTimeoutMillis)
            return board(resolution.place, resolution.basis, limit)
        }

        /** 時刻表画面 1 枚分。 */
        suspend fun dayBoard(
            place: BoardPlace,
            selection: DaySelection,
        ): DayBoard {
            val now = clock.now()
            val date = repository.resolveDate(now.toLocalDate(), selection.dayTypes)
            return DayBoard(
                place = place,
                selection = selection,
                date = date,
                now = now,
                departures = repository.onDate(place, date),
            )
        }

        /** 選択画面に並べる地点。現在地が取れていれば近い順、取れなければ経路の順（enum の順）。 */
        suspend fun nearby(): List<PlaceDistance> {
            val locations = repository.locations()
            // 直前に取った位置がキャッシュにあれば使う。無ければ取り直さず距離なしで並べる
            val here = if (locationPermitted) location.cached() else null
            val list =
                BoardPlace.entries.map { place ->
                    PlaceDistance(place, here?.let { h -> locations[place]?.let(h::distanceMetersTo) })
                }
            return if (here == null) list else list.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }
        }

        /** 手動で地点を選ぶ。null で「現在地から自動」に戻す。 */
        suspend fun selectPlace(place: BoardPlace?) {
            settings.setBoardPlaceName(place?.name)
        }

        suspend fun manualPlace(): BoardPlace? =
            settings.boardPlaceName.first()?.let { name -> BoardPlace.entries.firstOrNull { it.name == name } }

        private suspend fun boundNow(): Bound = settings.current().commute.boundAt(clock.now().toLocalTime())

        private companion object {
            /** 位置も手動選択も無いときの地点。往路は家の最寄り、復路は勤務先の最寄り。 */
            fun defaultFor(bound: Bound): BoardPlace =
                when (bound) {
                    Bound.OUTBOUND -> BoardPlace.HOME_STOP
                    Bound.INBOUND -> BoardPlace.HOUGI_JR
                }
        }
    }
