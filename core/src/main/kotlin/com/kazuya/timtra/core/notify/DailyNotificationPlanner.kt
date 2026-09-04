package com.kazuya.timtra.core.notify

import com.kazuya.timtra.core.calendar.HolidayCalendar
import com.kazuya.timtra.core.calendar.JapaneseHolidays
import com.kazuya.timtra.core.data.BusTimetable
import com.kazuya.timtra.core.journey.Journey
import com.kazuya.timtra.core.journey.JourneyPlanner
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.DayType
import com.kazuya.timtra.core.model.JrTimetable
import java.time.LocalDate
import java.time.LocalDateTime

/** 通知を出さない理由。UI で「祝日のため通知なし」のように表示する。 */
enum class SuppressReason {
    /** 設定で通知 OFF */
    DISABLED,

    /** 「今日は休み」 */
    DAY_OFF,

    /** 土日祝・年末年始（JR の日種別が平日でない） */
    NOT_WORKDAY,

    /** calendar_dates によるバス運休日 */
    NO_BUS_SERVICE,

    /** 時刻表上、その日の案が作れない */
    NO_JOURNEY,
}

/** ある日の通知予約の計算結果。 */
data class DailyNotificationPlan(
    val date: LocalDate,
    val notifications: List<PlannedNotification>,
    val suppressReason: SuppressReason?,
    /** 通知の元になった案（往路 / 復路）。 */
    val journeys: Map<Bound, Journey>,
) {
    val isSuppressed: Boolean get() = suppressReason != null
}

/**
 * 前夜のジョブが使う: その日の往路・復路の案を求め、通知に展開する（CLAUDE.md 8）。
 * 祝日・年末年始・運休日・「今日は休み」・通知 OFF のときは空にする。
 */
class DailyNotificationPlanner(
    private val planner: JourneyPlanner,
    private val bus: BusTimetable,
    private val jr: JrTimetable,
    private val holidays: HolidayCalendar = JapaneseHolidays,
) {
    fun plan(
        date: LocalDate,
        timing: NotificationTiming = NotificationTiming(),
        enabled: Boolean = true,
        dayOff: LocalDate? = null,
        /** 当日の再計算では現在時刻を渡し、過ぎた通知を捨てる。 */
        now: LocalDateTime? = null,
    ): DailyNotificationPlan {
        val reason =
            when {
                !enabled -> SuppressReason.DISABLED
                dayOff == date -> SuppressReason.DAY_OFF
                jr.dayTypeOf(date, holidays) != DayType.WEEKDAY -> SuppressReason.NOT_WORKDAY
                !bus.hasServiceOn(date) -> SuppressReason.NO_BUS_SERVICE
                else -> null
            }
        if (reason != null) return DailyNotificationPlan(date, emptyList(), reason, emptyMap())

        val journeys = Bound.entries.mapNotNull { bound -> planner.planForDate(date, bound)?.let { bound to it } }.toMap()
        if (journeys.isEmpty()) return DailyNotificationPlan(date, emptyList(), SuppressReason.NO_JOURNEY, emptyMap())

        val notifications =
            journeys.values
                .flatMap { NotificationPlanner.plan(it, timing, now) }
                .sortedWith(compareBy({ it.fireAt }, { it.id }))
        return DailyNotificationPlan(date, notifications, null, journeys)
    }
}
