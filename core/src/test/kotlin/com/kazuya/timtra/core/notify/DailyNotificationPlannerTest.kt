package com.kazuya.timtra.core.notify

import com.kazuya.timtra.core.Fixtures
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.journey.JourneyPlanner
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.BusDirection
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyNotificationPlannerTest {
    private val bus = Fixtures.busTimetable()
    private val daily = DailyNotificationPlanner(JourneyPlanner(bus, Fixtures.jr, CommuteSettings()), bus, Fixtures.jr)

    @Test
    fun `weekday plans both bounds`() {
        val plan = daily.plan(Fixtures.monday)
        assertNull(plan.suppressReason)
        assertEquals(setOf(Bound.OUTBOUND, Bound.INBOUND), plan.journeys.keys)
        // 往路 4 件 + 復路 1 件（宝木発のみ）
        assertEquals(5, plan.notifications.size)
        assertEquals(plan.notifications.sortedBy { it.fireAt }, plan.notifications, "時刻順")
        assertEquals(Fixtures.monday.atTime(LocalTime.of(6, 45)), plan.notifications.first().fireAt)
        // 最後は「まもなく宝木発」（18:20 発の 3 分前）
        assertEquals(Fixtures.monday.atTime(LocalTime.of(18, 17)), plan.notifications.last().fireAt)
    }

    @Test
    fun `suppressed when disabled or day off`() {
        assertEquals(SuppressReason.DISABLED, daily.plan(Fixtures.monday, enabled = false).suppressReason)
        assertEquals(SuppressReason.DAY_OFF, daily.plan(Fixtures.monday, dayOff = Fixtures.monday).suppressReason)
        assertNull(daily.plan(Fixtures.monday, dayOff = Fixtures.monday.plusDays(1)).suppressReason, "別の日の休みは影響しない")
        assertTrue(daily.plan(Fixtures.monday, enabled = false).notifications.isEmpty())
    }

    @Test
    fun `suppressed on weekends, holidays and year end`() {
        assertEquals(SuppressReason.NOT_WORKDAY, daily.plan(Fixtures.saturday).suppressReason)
        assertEquals(SuppressReason.NOT_WORKDAY, daily.plan(Fixtures.sunday).suppressReason)
        assertEquals(SuppressReason.NOT_WORKDAY, daily.plan(Fixtures.respectForAgedDay).suppressReason, "祝日")
        assertEquals(SuppressReason.NOT_WORKDAY, daily.plan(Fixtures.newYearsEve).suppressReason, "JR overrides の年末年始")
        assertEquals(SuppressReason.NOT_WORKDAY, daily.plan(LocalDate.of(2027, 1, 1)).suppressReason)
        assertNull(daily.plan(LocalDate.of(2026, 12, 29)).suppressReason, "12/29 は平日扱い")
    }

    @Test
    fun `suppressed on bus suspension day from calendar_dates`() {
        assertEquals(SuppressReason.NO_BUS_SERVICE, daily.plan(Fixtures.tuesdayNoService).suppressReason)
    }

    @Test
    fun `missing journey for one bound still plans the other`() {
        val onlyOutbound = Fixtures.busTimetable(Fixtures.trips.filter { it.direction == BusDirection.TO_STATION })
        val d = DailyNotificationPlanner(JourneyPlanner(onlyOutbound, Fixtures.jr, CommuteSettings()), onlyOutbound, Fixtures.jr)
        val plan = d.plan(Fixtures.monday)
        assertNull(plan.suppressReason)
        assertEquals(setOf(Bound.OUTBOUND), plan.journeys.keys)
        assertEquals(4, plan.notifications.size)
    }

    @Test
    fun `no journey at all`() {
        val emptyJr = Fixtures.jr.copy(legs = Fixtures.jr.legs.map { it.copy(services = emptyList()) })
        val d = DailyNotificationPlanner(JourneyPlanner(bus, emptyJr, CommuteSettings()), bus, emptyJr)
        assertEquals(SuppressReason.NO_JOURNEY, d.plan(Fixtures.monday).suppressReason)
    }

    @Test
    fun `re-planning during the day keeps only future notifications`() {
        val plan = daily.plan(Fixtures.monday, now = Fixtures.monday.atTime(LocalTime.of(7, 0)))
        assertNull(plan.suppressReason)
        // 07:00 以降: 往路の「まもなく発車」「次は鳥取駅」+ 復路の「まもなく宝木発」
        assertEquals(3, plan.notifications.size)
        assertTrue(plan.notifications.all { it.fireAt.isAfter(Fixtures.monday.atTime(LocalTime.of(7, 0))) })
    }
}
