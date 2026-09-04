package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.Fixtures
import com.kazuya.timtra.core.model.Bound
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyPlannerTest {
    private val settings = CommuteSettings()
    private val planner = JourneyPlanner(Fixtures.busTimetable(), Fixtures.jr, settings)

    private fun at(
        date: LocalDate,
        time: String,
    ): LocalDateTime = date.atTime(LocalTime.parse(time))

    private fun outbound(
        now: LocalDateTime,
        delays: Map<String, Duration> = emptyMap(),
        boarded: String? = null,
    ) = planner.plan(PlanRequest(now, Bound.OUTBOUND, delays, boarded))

    private fun inbound(
        now: LocalDateTime,
        delays: Map<String, Duration> = emptyMap(),
    ) = planner.plan(PlanRequest(now, Bound.INBOUND, delays))

    // ------------------------------------------------------------ 往路

    @Test
    fun `picks JR first and the latest bus that makes it`() {
        val j = assertNotNull(outbound(at(Fixtures.monday, "06:00")))
        // 06:35 の JR には間に合うバスが無いので 07:45 が targetTrain になる
        assertEquals("S103D", j.train.service.trainId)
        // 07:32 までに鳥取駅に着く便のうち最も遅いのは 07:05 発（07:24 着）。07:20 発は 07:40 着で不可。
        assertEquals("B0705", j.bus.trip.tripId)
        assertEquals(at(Fixtures.monday, "07:05"), j.bus.departureAt)
        assertEquals(at(Fixtures.monday, "07:24"), j.bus.arrivalAt)
        // 余裕 = 07:45 − (07:24 + 乗換 8 分) = 13 分
        assertEquals(Duration.ofMinutes(13), j.transferMargin)
        assertEquals(JourneyStatus.OK, j.status)
        // 家を出る = 07:05 − 徒歩 5 − 準備 5
        assertEquals(at(Fixtures.monday, "06:55"), j.leaveAt)
        // 到着予測 = 08:07 + 10
        assertEquals(at(Fixtures.monday, "08:17"), j.arriveAt)
        assertNull(j.fallback)
        assertEquals(Bound.OUTBOUND, j.bound)
        assertEquals(Duration.ZERO, j.busDelay)
    }

    @Test
    fun `next candidate is the following train with its own latest bus`() {
        val list = planner.candidates(PlanRequest(at(Fixtures.monday, "06:00"), Bound.OUTBOUND), 3)
        assertEquals(listOf("S103D", "S105D", "S107D"), list.map { it.train.service.trainId })
        val second = list[1]
        // 08:12 発 → 07:59 までに着く最も遅い便は 07:35 発（07:56 着）。余裕 = 08:12 − 08:04 = 8 分 → TIGHT
        assertEquals("B0735", second.bus.trip.tripId)
        assertEquals(Duration.ofMinutes(8), second.transferMargin)
        assertEquals(JourneyStatus.TIGHT, second.status)
        // 09:30 発 → 最も遅い便は 08:00 発（08:20 着、5 番のりば）
        assertEquals("B0800", list[2].bus.trip.tripId)
        assertEquals(
            "5",
            list[2]
                .bus.trip.alightStop.platformCode,
        )
    }

    @Test
    fun `buses already departed are not offered`() {
        // 07:30 に起動: 07:45 の JR に乗れるバスはもう無いので 08:12 の JR と 07:35 のバス
        val j = assertNotNull(outbound(at(Fixtures.monday, "07:30")))
        assertEquals("S105D", j.train.service.trainId)
        assertEquals("B0735", j.bus.trip.tripId)
    }

    @Test
    fun `delay turns TIGHT into RISK and offers the previous bus`() {
        val j = assertNotNull(outbound(at(Fixtures.monday, "07:00"), delays = mapOf("B0735" to Duration.ofMinutes(5))))
        // 07:45 の JR: 07:05 発が OK なのでそちらが先
        assertEquals("S103D", j.train.service.trainId)
        val second =
            planner.candidates(
                PlanRequest(at(Fixtures.monday, "07:00"), Bound.OUTBOUND, mapOf("B0735" to Duration.ofMinutes(5))),
                2,
            )[1]
        assertEquals("S105D", second.train.service.trainId)
        assertEquals("B0735", second.bus.trip.tripId)
        assertEquals(Duration.ofMinutes(5), second.busDelay)
        assertTrue(second.hasDelay)
        assertEquals(at(Fixtures.monday, "08:01"), second.busArrivalEstimatedAt)
        // 余裕 = 08:12 − (08:01 + 8) = 3 分 → RISK
        assertEquals(Duration.ofMinutes(3), second.transferMargin)
        assertEquals(JourneyStatus.RISK, second.status)
        val fb = assertNotNull(second.fallback, "1 本前のバスを提示する")
        assertEquals("B0720", fb.bus.trip.tripId)
        assertEquals("S105D", fb.train.service.trainId)
        assertEquals(Duration.ofMinutes(24), fb.transferMargin)
        assertEquals(JourneyStatus.OK, fb.status)
        assertNull(fb.fallback)
    }

    @Test
    fun `delay applies only to the delayed trip`() {
        val j = assertNotNull(outbound(at(Fixtures.monday, "06:00"), delays = mapOf("B0735" to Duration.ofMinutes(30))))
        assertEquals("B0705", j.bus.trip.tripId)
        assertEquals(Duration.ZERO, j.busDelay)
        assertEquals(JourneyStatus.OK, j.status)
    }

    @Test
    fun `on board with small delay is RISK without previous bus`() {
        val j = assertNotNull(outbound(at(Fixtures.monday, "07:10"), delays = mapOf("B0705" to Duration.ofMinutes(10)), boarded = "B0705"))
        assertEquals("S103D", j.train.service.trainId)
        assertEquals("B0705", j.bus.trip.tripId)
        // 07:45 − (07:34 + 8) = 3 分
        assertEquals(Duration.ofMinutes(3), j.transferMargin)
        assertEquals(JourneyStatus.RISK, j.status)
        assertNull(j.fallback, "乗車中なので 1 本前には変更できない")
    }

    @Test
    fun `on board and cannot make it becomes MISSED with next train as fallback`() {
        val j = assertNotNull(outbound(at(Fixtures.monday, "07:10"), delays = mapOf("B0705" to Duration.ofMinutes(15)), boarded = "B0705"))
        assertEquals("S103D", j.train.service.trainId)
        assertEquals(JourneyStatus.MISSED, j.status)
        assertEquals(Duration.ofMinutes(-2), j.transferMargin)
        val fb = assertNotNull(j.fallback)
        assertEquals("S105D", fb.train.service.trainId)
        assertEquals("B0705", fb.bus.trip.tripId, "乗車中の便のまま次の JR に切り替える")
        // 08:12 − (07:39 + 8) = 25 分
        assertEquals(Duration.ofMinutes(25), fb.transferMargin)
        assertEquals(JourneyStatus.OK, fb.status)
    }

    @Test
    fun `after the last train shows tomorrow's first reachable journey`() {
        // 水曜 23:00 に起動（木曜は通常の平日）
        val wednesday = Fixtures.monday.plusDays(2)
        val j = assertNotNull(outbound(at(wednesday, "23:00")))
        assertEquals(wednesday.plusDays(1), j.train.date)
        assertEquals("S103D", j.train.service.trainId)
        assertEquals(at(wednesday.plusDays(1), "06:55"), j.leaveAt)
    }

    @Test
    fun `saturday and holiday timetables`() {
        val sat = assertNotNull(outbound(at(Fixtures.saturday, "06:00")))
        assertEquals("S203D", sat.train.service.trainId)
        assertEquals("B_SA_0800", sat.bus.trip.tripId)
        assertEquals(Duration.ofMinutes(13), sat.transferMargin)

        val sun = assertNotNull(outbound(at(Fixtures.sunday, "06:00")))
        assertEquals("S303D", sun.train.service.trainId)
        assertEquals("B_SH_0830", sun.bus.trip.tripId)
        // 09:05 − (08:49 + 8) = 8 分 → TIGHT
        assertEquals(JourneyStatus.TIGHT, sun.status)

        // 祝日（月曜）は JR もバスも休日ダイヤ
        val holiday = assertNotNull(outbound(at(Fixtures.respectForAgedDay, "06:00")))
        assertEquals("S303D", holiday.train.service.trainId)
        assertEquals("B_SH_0830", holiday.bus.trip.tripId)
    }

    @Test
    fun `year end uses holiday timetable via overrides and calendar_dates`() {
        val j = assertNotNull(outbound(at(Fixtures.newYearsEve, "06:00")))
        assertEquals(Fixtures.newYearsEve, j.train.date)
        assertEquals("S303D", j.train.service.trainId)
        assertEquals("B_SH_0830", j.bus.trip.tripId)
        // 12/29（火）は通常の平日
        val ordinary = assertNotNull(outbound(at(LocalDate.of(2026, 12, 29), "06:00")))
        assertEquals("S103D", ordinary.train.service.trainId)
    }

    @Test
    fun `bus suspension day skips to the next service day`() {
        val j = assertNotNull(outbound(at(Fixtures.tuesdayNoService, "06:00")))
        assertEquals(Fixtures.tuesdayNoService.plusDays(1), j.train.date)
        assertEquals("B0705", j.bus.trip.tripId)
        assertEquals(Fixtures.tuesdayNoService.plusDays(1), j.bus.serviceDate)
    }

    @Test
    fun `returns null when nothing runs within the lookahead`() {
        val empty = JourneyPlanner(Fixtures.busTimetable(emptyList()), Fixtures.jr, settings)
        assertNull(empty.plan(PlanRequest(at(Fixtures.monday, "06:00"), Bound.OUTBOUND)))
        assertNull(empty.plan(PlanRequest(at(Fixtures.monday, "17:00"), Bound.INBOUND)))
    }

    // ------------------------------------------------------------ 境界値

    private fun marginWithBusArriving(arrival: String): Journey {
        // 07:45 発の JR に対して、指定時刻に鳥取駅へ着くバス 1 本だけの時刻表
        val only = listOf(Fixtures.toStation("X", "WD", "06:30:00", arrival))
        val p = JourneyPlanner(Fixtures.busTimetable(only), Fixtures.jr, settings)
        return assertNotNull(p.plan(PlanRequest(at(Fixtures.monday, "06:00"), Bound.OUTBOUND)))
    }

    @Test
    fun `status boundaries at exactly 10 and 5 minutes`() {
        // 07:45 − (07:27 + 8) = 10 分 → OK
        marginWithBusArriving("07:27:00").let {
            assertEquals(Duration.ofMinutes(10), it.transferMargin)
            assertEquals(JourneyStatus.OK, it.status)
        }
        // 07:45 − (07:27:01 + 8) = 9 分 59 秒 → TIGHT
        marginWithBusArriving("07:27:01").let {
            assertEquals(Duration.ofMinutes(10).minusSeconds(1), it.transferMargin)
            assertEquals(JourneyStatus.TIGHT, it.status)
        }
        // 07:45 − (07:32 + 8) = 5 分 → TIGHT（締切ちょうど。選択される）
        marginWithBusArriving("07:32:00").let {
            assertEquals("S103D", it.train.service.trainId)
            assertEquals(Duration.ofMinutes(5), it.transferMargin)
            assertEquals(JourneyStatus.TIGHT, it.status)
        }
        // 07:32:01 着は締切を過ぎるので 07:45 の JR は選ばれず、次の JR になる
        marginWithBusArriving("07:32:01").let {
            assertEquals("S105D", it.train.service.trainId)
        }
    }

    @Test
    fun `classify boundaries`() {
        assertEquals(JourneyStatus.OK, planner.classify(Duration.ofMinutes(10)))
        assertEquals(JourneyStatus.TIGHT, planner.classify(Duration.ofMinutes(10).minusSeconds(1)))
        assertEquals(JourneyStatus.TIGHT, planner.classify(Duration.ofMinutes(5)))
        assertEquals(JourneyStatus.RISK, planner.classify(Duration.ofMinutes(5).minusSeconds(1)))
        assertEquals(JourneyStatus.RISK, planner.classify(Duration.ofMinutes(-3)))
    }

    @Test
    fun `settings change the deadline and margins`() {
        val quick =
            CommuteSettings(
                transferBusToJr = Duration.ofMinutes(3),
                minTransfer = Duration.ofMinutes(2),
                comfortableTransfer = Duration.ofMinutes(6),
            )
        val p = JourneyPlanner(Fixtures.busTimetable(), Fixtures.jr, quick)
        val j = assertNotNull(p.plan(PlanRequest(at(Fixtures.monday, "06:00"), Bound.OUTBOUND)))
        // 締切 07:45 − 5 = 07:40 → 07:20 発（07:40 着）が最も遅い。余裕 = 07:45 − 07:43 = 2 分 → TIGHT
        assertEquals("B0720", j.bus.trip.tripId)
        assertEquals(Duration.ofMinutes(2), j.transferMargin)
        assertEquals(JourneyStatus.TIGHT, j.status)
    }

    // ------------------------------------------------------------ 復路

    @Test
    fun `inbound anchors on JR then takes the first bus after transfer`() {
        val j = assertNotNull(inbound(at(Fixtures.monday, "17:00")))
        assertEquals(Bound.INBOUND, j.bound)
        assertEquals("S110D", j.train.service.trainId) // 17:35 → 17:57
        // 17:57 + 8 = 18:05 以降の最初のバスは 18:30 発
        assertEquals("R1830", j.bus.trip.tripId)
        assertEquals(Duration.ofMinutes(25), j.transferMargin)
        assertEquals(JourneyStatus.OK, j.status)
        // 職場を出る = 17:35 − 10 − 5
        assertEquals(at(Fixtures.monday, "17:20"), j.leaveAt)
        // 帰宅 = 18:47 + 5
        assertEquals(at(Fixtures.monday, "18:52"), j.arriveAt)
    }

    @Test
    fun `inbound RISK offers the next bus`() {
        val j = assertNotNull(inbound(at(Fixtures.monday, "16:30")))
        assertEquals("S108D", j.train.service.trainId) // 16:50 → 17:12
        assertEquals("R1722", j.bus.trip.tripId) // 17:20 以降の最初は 17:22 → 余裕 2 分
        assertEquals(Duration.ofMinutes(2), j.transferMargin)
        assertEquals(JourneyStatus.RISK, j.status)
        val fb = assertNotNull(j.fallback)
        assertEquals("R1745", fb.bus.trip.tripId)
        assertEquals(JourneyStatus.OK, fb.status)
    }

    @Test
    fun `inbound bus delay adds to the margin`() {
        val j = assertNotNull(inbound(at(Fixtures.monday, "16:30"), delays = mapOf("R1722" to Duration.ofMinutes(4))))
        assertEquals("R1722", j.bus.trip.tripId)
        assertEquals(Duration.ofMinutes(6), j.transferMargin)
        assertEquals(JourneyStatus.TIGHT, j.status)
        assertEquals(at(Fixtures.monday, "17:26"), j.busDepartureEstimatedAt)
        assertEquals(at(Fixtures.monday, "17:48"), j.arriveAt) // 17:39 + 4 + 5
    }

    @Test
    fun `inbound uses a bus running past midnight`() {
        val j = assertNotNull(inbound(at(Fixtures.monday, "23:00")))
        assertEquals("S118D", j.train.service.trainId) // 23:10 → 23:32
        assertEquals("R2350", j.bus.trip.tripId) // 23:40 以降の最初は 23:50 発、24:15 着
        assertEquals(Duration.ofMinutes(10), j.transferMargin)
        assertEquals(at(Fixtures.monday.plusDays(1), "00:15"), j.bus.arrivalAt)
        assertEquals(at(Fixtures.monday.plusDays(1), "00:20"), j.arriveAt)
    }

    @Test
    fun `inbound train arriving after midnight connects to previous service day's late bus`() {
        val j = assertNotNull(inbound(at(Fixtures.monday, "23:20")))
        assertEquals("S900D", j.train.service.trainId) // 23:50 → 翌 00:12
        assertEquals(at(Fixtures.monday.plusDays(1), "00:12"), j.train.arrivalAt)
        // 00:20 以降の最初のバスは、月曜のサービス日に属する 24:30 発（火曜 00:30）
        assertEquals("R2430", j.bus.trip.tripId)
        assertEquals(Fixtures.monday, j.bus.serviceDate)
        assertEquals(at(Fixtures.monday.plusDays(1), "00:30"), j.bus.departureAt)
        assertEquals(Duration.ofMinutes(10), j.transferMargin)
    }

    @Test
    fun `inbound after the last bus rolls over to the next day`() {
        val j = assertNotNull(inbound(at(Fixtures.monday, "23:55")))
        assertEquals(Fixtures.monday.plusDays(1), j.train.date)
    }

    // ------------------------------------------------------------ 前夜計算・向き判定

    @Test
    fun `planForDate uses the configured anchors`() {
        val out = assertNotNull(planner.planForDate(Fixtures.monday, Bound.OUTBOUND))
        assertEquals("S103D", out.train.service.trainId)
        assertEquals("B0705", out.bus.trip.tripId)
        val back = assertNotNull(planner.planForDate(Fixtures.monday, Bound.INBOUND))
        // 終業 17:30 + 徒歩 10 = 17:40 以降の JR は 18:20 発。18:50 以降の最初のバスは 20:15
        assertEquals("S112D", back.train.service.trainId)
        assertEquals("R2015", back.bus.trip.tripId)
        // 運休日はその日の案が無い
        assertNull(planner.planForDate(Fixtures.tuesdayNoService, Bound.OUTBOUND))
    }

    @Test
    fun `bound is detected from the time of day`() {
        assertEquals(Bound.OUTBOUND, settings.boundAt(LocalTime.of(3, 0)))
        assertEquals(Bound.OUTBOUND, settings.boundAt(LocalTime.of(7, 30)))
        assertEquals(Bound.OUTBOUND, settings.boundAt(LocalTime.of(11, 59)))
        assertEquals(Bound.INBOUND, settings.boundAt(LocalTime.of(12, 0)))
        assertEquals(Bound.INBOUND, settings.boundAt(LocalTime.of(23, 30)))
        assertEquals(Bound.INBOUND, settings.boundAt(LocalTime.of(0, 30)))
    }

    @Test
    fun `bus trips are validated`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            Fixtures.toStation("bad", "WD", "07:30:00", "07:00:00")
        }
    }
}
