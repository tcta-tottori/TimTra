package com.kazuya.timtra.core.notify

import com.kazuya.timtra.core.Fixtures
import com.kazuya.timtra.core.data.JrTimetableParser
import com.kazuya.timtra.core.model.JrLegIds
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainReminderPlannerTest {
    private val jr = JrTimetableParser.parse(Fixtures.realJrTimetableFile.readText())
    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun `three stages per train departing at or after 17_00`() {
        val reminders = TrainReminderPlanner.plan(monday, jr, TrainReminderSettings())
        val trains = jr.servicesOn(monday, JrLegIds.HOUGI_TO_TOTTORI).filter { !it.departure.isBefore(LocalTime.of(17, 0)) }
        assertTrue(trains.isNotEmpty())
        assertEquals(trains.size * ReminderStage.entries.size, reminders.size)
        // 16:59 発は対象外、18:10 発は対象
        assertTrue(reminders.none { it.train.departure == LocalTime.of(16, 59) })
        val first = reminders.filter { it.train.departure == LocalTime.of(18, 10) }
        assertEquals(
            listOf(LocalTime.of(17, 40), LocalTime.of(17, 50), LocalTime.of(17, 55)),
            first.map { it.fireAt.toLocalTime() },
        )
        assertEquals(listOf(ReminderStage.EARLY, ReminderStage.WALK, ReminderStage.FAST_WALK), first.map { it.stage })
        // 同じ電車は同じ通知 ID、requestCode は段階ごとに別
        assertEquals(1, first.map { it.notificationId }.distinct().size)
        assertEquals(3, first.map { it.requestCode }.distinct().size)
        assertTrue(reminders.map { it.requestCode }.all { it in TrainReminderPlanner.ALL_REQUEST_CODES })
        assertTrue(reminders == reminders.sortedWith(compareBy({ it.departureAt }, { it.fireAt })))
    }

    @Test
    fun `notBefore drops reminders already in the past and disabled yields nothing`() {
        val now = monday.atTime(17, 52)
        val reminders = TrainReminderPlanner.plan(monday, jr, TrainReminderSettings(), notBefore = now)
        assertTrue(reminders.none { it.fireAt.isBefore(now) })
        assertEquals(ReminderStage.FAST_WALK, reminders.first().stage)
        assertTrue(TrainReminderPlanner.plan(monday, jr, TrainReminderSettings(enabled = false)).isEmpty())
    }

    @Test
    fun `window start is configurable`() {
        val reminders = TrainReminderPlanner.plan(monday, jr, TrainReminderSettings(windowStart = LocalTime.of(20, 0)))
        assertTrue(reminders.all { !it.train.departure.isBefore(LocalTime.of(20, 0)) })
    }
}
