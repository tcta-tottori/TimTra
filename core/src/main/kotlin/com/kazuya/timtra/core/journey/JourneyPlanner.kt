package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.calendar.HolidayCalendar
import com.kazuya.timtra.core.calendar.JapaneseHolidays
import com.kazuya.timtra.core.data.BusTimetable
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.BusDirection
import com.kazuya.timtra.core.model.JrTimetable
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** 計算の入力。 */
data class PlanRequest(
    val now: LocalDateTime,
    val bound: Bound,
    /** GTFS-RT から推定したバスの遅延。trip_id → 遅延。無い便は定刻扱い。 */
    val delays: Map<String, Duration> = emptyMap(),
    /** 乗車中のバス便の trip_id。指定すると往路はその便に固定し、間に合わなければ MISSED になる。 */
    val boardedTripId: String? = null,
)

/**
 * 乗り継ぎ計算エンジン（CLAUDE.md 6）。純粋 Kotlin。スマホと Wear で同じ結果になる。
 *
 * 原則:
 * 1. JR をアンカーにする。乗りたい JR 便を先に決め、そこから逆算してバス便を求める。
 * 2. 往路のバス便は「間に合う中で最も遅い便」を選ぶ。
 */
class JourneyPlanner(
    private val bus: BusTimetable,
    private val jr: JrTimetable,
    private val settings: CommuteSettings = CommuteSettings(),
    private val holidays: HolidayCalendar = JapaneseHolidays,
) {
    /** 直近の案。便が 1 本も見つからなければ null（先読み日数内に運行が無い）。 */
    fun plan(request: PlanRequest): Journey? = candidates(request, 1).firstOrNull()

    /** 直近から順に [count] 件の案。ホーム画面の「次の候補（1 本後）」に使う。 */
    fun candidates(
        request: PlanRequest,
        count: Int,
    ): List<Journey> {
        require(count > 0)
        val seq =
            when (request.bound) {
                Bound.OUTBOUND -> outbound(request)
                Bound.INBOUND -> inbound(request)
            }
        return seq.take(count).toList()
    }

    /**
     * 前夜のジョブ用: その日の通勤案。往路は「earliestLeaveHome 以降に家を出る」、
     * 復路は「workEndsAt に職場を出る」を基準にする。バス・JR とも運休なら null。
     */
    fun planForDate(
        date: LocalDate,
        bound: Bound,
    ): Journey? {
        val now =
            when (bound) {
                Bound.OUTBOUND -> date.atTime(settings.earliestLeaveHome).plus(settings.walkHomeToStop).plus(settings.prepBuffer)
                Bound.INBOUND -> date.atTime(settings.workEndsAt).plus(settings.walkStationToWork)
            }
        return plan(PlanRequest(now = now, bound = bound))?.takeIf { it.train.date == date }
    }

    // ---------------------------------------------------------------- 往路

    private fun outbound(request: PlanRequest): Sequence<Journey> =
        sequence {
            var missed: Journey? = null
            for (train in upcomingTrains(request.now, Bound.OUTBOUND)) {
                val journey = outboundFor(train, request) ?: continue
                if (journey.status == JourneyStatus.MISSED) {
                    // 乗車中の便で間に合わない。最初に逃した JR 便の案だけ覚えておき、次の便で再計算する。
                    if (missed == null) missed = journey
                    continue
                }
                val pending = missed
                if (pending != null) {
                    missed = null
                    yield(pending.copy(fallback = journey))
                } else {
                    yield(journey)
                }
            }
        }

    /** この JR 便に乗るための案。乗れるバスが無ければ null。 */
    private fun outboundFor(
        train: ScheduledTrain,
        request: PlanRequest,
    ): Journey? {
        val boarded = request.boardedTripId
        if (boarded != null) {
            val onBoard =
                busesAround(train.departureAt.toLocalDate(), BusDirection.TO_STATION)
                    .firstOrNull {
                        it.trip.tripId == boarded &&
                            !it.departureAt.isAfter(request.now) &&
                            it.departureAt.isAfter(request.now.minusHours(6))
                    }
                    ?: return null
            val margin = marginFor(train, onBoard, request)
            val status = if (margin.isNegative) JourneyStatus.MISSED else classify(margin)
            return journey(train, onBoard, request, margin, status, fallback = null)
        }

        // 手順 2: 発車 − (乗換時間 + 最低乗換許容) までに鳥取駅へ着く便のうち最も遅い便
        val deadline = train.departureAt.minus(settings.transferBusToJr).minus(settings.minTransfer)
        val reachable =
            busesAround(train.departureAt.toLocalDate(), BusDirection.TO_STATION)
                .filter { !it.departureAt.isBefore(request.now) && !it.arrivalAt.isAfter(deadline) }
        val chosen = reachable.lastOrNull() ?: return null

        // 手順 3: 遅延を加味して再判定
        val margin = marginFor(train, chosen, request)
        val status = classify(margin)
        val fallback =
            if (status == JourneyStatus.RISK) {
                // 「1 本前のバスに変更」: 遅延込みでも最低乗換許容を満たす、直前の便
                reachable
                    .filter { it.departureAt.isBefore(chosen.departureAt) }
                    .lastOrNull { marginFor(train, it, request) >= settings.minTransfer }
                    ?.let { alt ->
                        journey(train, alt, request, marginFor(train, alt, request), classify(marginFor(train, alt, request)), null)
                    }
            } else {
                null
            }
        return journey(train, chosen, request, margin, status, fallback)
    }

    private fun marginFor(
        train: ScheduledTrain,
        bus: ScheduledBus,
        request: PlanRequest,
    ): Duration {
        val arrival = bus.arrivalAt.plus(delayOf(bus, request))
        return Duration.between(arrival.plus(settings.transferBusToJr), train.departureAt)
    }

    private fun journey(
        train: ScheduledTrain,
        bus: ScheduledBus,
        request: PlanRequest,
        margin: Duration,
        status: JourneyStatus,
        fallback: Journey?,
    ): Journey =
        Journey(
            bound = Bound.OUTBOUND,
            train = train,
            bus = bus,
            busDelay = delayOf(bus, request),
            // 手順 5: leaveHomeAt = バス発車時刻 − 徒歩 − 準備時間
            leaveAt = bus.departureAt.minus(settings.walkHomeToStop).minus(settings.prepBuffer),
            transferMargin = margin,
            status = status,
            arriveAt = train.arrivalAt.plus(settings.walkStationToWork),
            fallback = fallback,
        )

    // ---------------------------------------------------------------- 復路

    private fun inbound(request: PlanRequest): Sequence<Journey> =
        sequence {
            for (train in upcomingTrains(request.now, Bound.INBOUND)) {
                val journey = inboundFor(train, request) ?: continue
                yield(journey)
            }
        }

    /** この JR 便で帰るときの案。JR 到着後に乗れるバスが無ければ null。 */
    private fun inboundFor(
        train: ScheduledTrain,
        request: PlanRequest,
    ): Journey? {
        val earliestBoarding = train.arrivalAt.plus(settings.transferBusToJr)
        val candidates =
            busesAround(train.arrivalAt.toLocalDate(), BusDirection.FROM_STATION)
                .filter { !it.departureAt.isBefore(earliestBoarding) }
        val chosen = candidates.firstOrNull() ?: return null
        val margin = inboundMargin(chosen, earliestBoarding, request)
        val status = classify(margin)
        val fallback =
            if (status == JourneyStatus.RISK) {
                // 復路の代替案は「1 本後のバス」
                candidates.drop(1).firstOrNull()?.let { alt ->
                    inboundJourney(
                        train,
                        alt,
                        request,
                        inboundMargin(alt, earliestBoarding, request),
                        classify(inboundMargin(alt, earliestBoarding, request)),
                        null,
                    )
                }
            } else {
                null
            }
        return inboundJourney(train, chosen, request, margin, status, fallback)
    }

    private fun inboundMargin(
        bus: ScheduledBus,
        earliestBoarding: LocalDateTime,
        request: PlanRequest,
    ): Duration = Duration.between(earliestBoarding, bus.departureAt.plus(delayOf(bus, request)))

    private fun inboundJourney(
        train: ScheduledTrain,
        bus: ScheduledBus,
        request: PlanRequest,
        margin: Duration,
        status: JourneyStatus,
        fallback: Journey?,
    ): Journey =
        Journey(
            bound = Bound.INBOUND,
            train = train,
            bus = bus,
            busDelay = delayOf(bus, request),
            leaveAt = train.departureAt.minus(settings.walkStationToWork).minus(settings.prepBuffer),
            transferMargin = margin,
            status = status,
            arriveAt = bus.arrivalAt.plus(delayOf(bus, request)).plus(settings.walkHomeToStop),
            fallback = fallback,
        )

    // ---------------------------------------------------------------- 共通

    /** 手順 4: 余裕による分類。境界値は「以上」で上のランクに入る。 */
    internal fun classify(margin: Duration): JourneyStatus =
        when {
            margin >= settings.comfortableTransfer -> JourneyStatus.OK
            margin >= settings.minTransfer -> JourneyStatus.TIGHT
            else -> JourneyStatus.RISK
        }

    private fun delayOf(
        bus: ScheduledBus,
        request: PlanRequest,
    ): Duration = request.delays[bus.trip.tripId] ?: Duration.ZERO

    /** 手順 1: 現在時刻以降の JR 便を日付をまたいで列挙する（最終便の後なら翌日以降の便）。 */
    private fun upcomingTrains(
        from: LocalDateTime,
        bound: Bound,
    ): Sequence<ScheduledTrain> =
        sequence {
            val start = from.toLocalDate()
            for (offset in 0..TimTraConstants.MAX_LOOKAHEAD_DAYS) {
                val date = start.plusDays(offset)
                for (service in jr.servicesOn(date, bound.jrLegId, holidays)) {
                    val train = ScheduledTrain(service, date)
                    if (!train.departureAt.isBefore(from)) yield(train)
                }
            }
        }

    /**
     * その日の前後で走るバス便を絶対時刻順に返す。
     * 前日のサービス日に属する深夜便（24:15 など）が当日 0 時台に走るので、前日分も含める。
     */
    private fun busesAround(
        date: LocalDate,
        direction: BusDirection,
    ): List<ScheduledBus> =
        listOf(date.minusDays(1), date)
            .flatMap { serviceDate -> bus.tripsOn(serviceDate, direction).map { ScheduledBus(it, serviceDate) } }
            .sortedWith(compareBy({ it.departureAt }, { it.trip.tripId }))
}
