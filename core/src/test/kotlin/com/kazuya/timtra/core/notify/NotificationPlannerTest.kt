package com.kazuya.timtra.core.notify

import com.kazuya.timtra.core.Fixtures
import com.kazuya.timtra.core.journey.CommuteSettings
import com.kazuya.timtra.core.journey.JourneyPlanner
import com.kazuya.timtra.core.model.Bound
import java.time.Duration
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NotificationPlannerTest {
    private val planner = JourneyPlanner(Fixtures.busTimetable(), Fixtures.jr, CommuteSettings())
    private val outbound = assertNotNull(planner.planForDate(Fixtures.monday, Bound.OUTBOUND))
    private val inbound = assertNotNull(planner.planForDate(Fixtures.monday, Bound.INBOUND))

    private fun t(time: String) = Fixtures.monday.atTime(LocalTime.parse(time))

    @Test
    fun `outbound notifications follow CLAUDE_md 8`() {
        // 家を出る 06:55、バス 07:05 発 07:24 着
        val list = NotificationPlanner.plan(outbound)
        assertEquals(
            listOf(
                NotificationKind.LEAVE_SOON to t("06:45"),
                NotificationKind.LEAVE_NOW to t("06:55"),
                NotificationKind.FIRST_LEG_DEPARTING to t("07:02"),
                NotificationKind.APPROACHING_TRANSFER to t("07:22"),
            ),
            list.map { it.kind to it.fireAt },
        )
        assertEquals(listOf(100, 101, 102, 103), list.map { it.id })
        assertTrue(list.all { it.bound == Bound.OUTBOUND && it.journey === outbound })
    }

    @Test
    fun `inbound notifications anchor on the JR departure and arrival`() {
        // 職場を出る 18:05、JR 18:20 発 18:42 着、バス 20:15 発
        val list = NotificationPlanner.plan(inbound)
        assertEquals(
            listOf(
                NotificationKind.LEAVE_SOON to t("17:55"),
                NotificationKind.LEAVE_NOW to t("18:05"),
                NotificationKind.FIRST_LEG_DEPARTING to t("18:17"),
                NotificationKind.APPROACHING_TRANSFER to t("18:40"),
            ),
            list.map { it.kind to it.fireAt },
        )
        assertEquals(listOf(200, 201, 202, 203), list.map { it.id })
    }

    @Test
    fun `custom timing`() {
        val timing = NotificationTiming(Duration.ofMinutes(15), Duration.ofMinutes(5), Duration.ZERO)
        val list = NotificationPlanner.plan(outbound, timing)
        assertEquals(listOf(t("06:40"), t("06:55"), t("07:00"), t("07:24")), list.map { it.fireAt })
    }

    @Test
    fun `notifications already due are dropped when re-planning during the day`() {
        val list = NotificationPlanner.plan(outbound, notBefore = t("06:55"))
        assertEquals(listOf(NotificationKind.FIRST_LEG_DEPARTING, NotificationKind.APPROACHING_TRANSFER), list.map { it.kind })
    }

    @Test
    fun `ids are unique across bounds and kinds`() {
        assertEquals(8, PlannedNotification.ALL_IDS.toSet().size)
        assertEquals(203, PlannedNotification.idOf(Bound.INBOUND, NotificationKind.APPROACHING_TRANSFER))
    }
}
