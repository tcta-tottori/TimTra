package com.kazuya.timtra.core.board

import com.kazuya.timtra.core.Fixtures
import com.kazuya.timtra.core.model.Bound
import com.kazuya.timtra.core.model.DayType
import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DepartureBoardTest {
    private val bus = Fixtures.busTimetable()
    private val jr = Fixtures.jr

    private fun at(
        date: java.time.LocalDate,
        time: String,
    ): LocalDateTime = date.atTime(LocalTime.parse(time))

    @Test
    fun `lists the next departures from the home stop`() {
        val board = DepartureBoard.upcoming(BoardPlace.HOME_STOP, at(Fixtures.monday, "07:10"), bus, jr, limit = 3)
        assertEquals(
            listOf(at(Fixtures.monday, "07:20"), at(Fixtures.monday, "07:35"), at(Fixtures.monday, "08:00")),
            board.map { it.at },
        )
        val first = board.first()
        assertEquals(DepartureMode.BUS, first.mode)
        assertEquals("91", first.code)
        assertEquals("鳥取駅", first.headsign)
        assertEquals("91", first.line, "系統名しか無いフィクスチャでは系統番号で代用する")
        assertEquals(at(Fixtures.monday, "07:40"), first.arrivalAt)
    }

    @Test
    fun `the departure exactly now is still listed`() {
        val board = DepartureBoard.upcoming(BoardPlace.HOME_STOP, at(Fixtures.monday, "07:20"), bus, jr, limit = 1)
        assertEquals(at(Fixtures.monday, "07:20"), board.single().at)
    }

    @Test
    fun `after the last bus it rolls over to the next service day`() {
        // 月曜の最終便（24:30 発 = 火曜 00:30）より後。火曜は全面運休なので水曜の初便になる
        val board = DepartureBoard.upcoming(BoardPlace.STATION_BUS, at(Fixtures.monday.plusDays(1), "01:00"), bus, jr, limit = 1)
        val wednesday = Fixtures.monday.plusDays(2)
        assertEquals(at(wednesday, "17:22"), board.single().at)
    }

    @Test
    fun `midnight trips of the previous service day are listed`() {
        // 前日（月曜）のサービス日に属する 24:30 発は火曜 00:30。火曜 0 時台に立っていれば次の便として出る
        val board = DepartureBoard.upcoming(BoardPlace.STATION_BUS, at(Fixtures.monday.plusDays(1), "00:10"), bus, jr, limit = 1)
        assertEquals(at(Fixtures.monday.plusDays(1), "00:30"), board.single().at)
    }

    @Test
    fun `train board uses the jr leg and keeps platform and train number`() {
        val board = DepartureBoard.upcoming(BoardPlace.HOUGI_JR, at(Fixtures.monday, "00:00"), bus, jr, limit = 2)
        val expected = jr.servicesOn(Fixtures.monday, BoardPlace.HOUGI_JR.jrLegId!!).take(2)
        assertEquals(expected.map { Fixtures.monday.atTime(it.departure) }, board.map { it.at })
        assertEquals(expected.map { it.trainId }, board.map { it.code })
        assertTrue(board.all { it.mode == DepartureMode.TRAIN })
        assertTrue(board.all { it.line == "山陰本線" })
        assertTrue(board.all { it.arrivalAt.isAfter(it.at) })
    }

    @Test
    fun `board is sorted and never exceeds the limit`() {
        val board = DepartureBoard.upcoming(BoardPlace.TOTTORI_JR, at(Fixtures.monday, "06:00"), bus, jr, limit = 4)
        assertEquals(4, board.size)
        assertEquals(board.map { it.at }.sorted(), board.map { it.at })
    }

    @Test
    fun `nearest picks the stop you are standing at`() {
        val locations = DepartureBoard.locations(bus)
        // 南吉成はフィクスチャに位置が無いので、鳥取駅と宝木駅だけで確かめる
        assertEquals(
            BoardPlace.HOUGI_JR,
            DepartureBoard.nearest(Places.HOUGI_STATION, locations, Bound.INBOUND),
        )
        assertEquals(
            BoardPlace.HOUGI_JR,
            DepartureBoard.nearest(Places.HOUGI_STATION, locations, Bound.OUTBOUND),
            "宝木駅は 1 方向しかないので向きに関係なく同じ",
        )
    }

    @Test
    fun `at tottori station the bound decides bus or train`() {
        val locations = DepartureBoard.locations(bus)
        assertEquals(BoardPlace.TOTTORI_JR, DepartureBoard.nearest(Places.TOTTORI_STATION, locations, Bound.OUTBOUND))
        assertEquals(BoardPlace.STATION_BUS, DepartureBoard.nearest(Places.TOTTORI_STATION, locations, Bound.INBOUND))
    }

    @Test
    fun `far from the route there is no nearest place`() {
        val locations = DepartureBoard.locations(bus)
        // 大阪あたり。通勤圏外では自動で選ばず、手動選択にフォールバックさせる
        assertNull(DepartureBoard.nearest(GeoPoint(34.7025, 135.4959), locations, Bound.OUTBOUND))
    }

    @Test
    fun `onDate lists the whole service day`() {
        val all = DepartureBoard.onDate(BoardPlace.HOME_STOP, Fixtures.monday, bus, jr)
        // 月曜（平日）の南吉成発はフィクスチャの WD 4 本
        assertEquals(
            listOf(at(Fixtures.monday, "07:05"), at(Fixtures.monday, "07:20"), at(Fixtures.monday, "07:35"), at(Fixtures.monday, "08:00")),
            all.map { it.at },
        )
        // 現在時刻に関係なく、始発から入っている（一覧で過去便をグレーにして出すため）
        assertTrue(all.first().at.toLocalTime() < LocalTime.of(7, 10))
    }

    @Test
    fun `onDate keeps midnight trips on their service day`() {
        val all = DepartureBoard.onDate(BoardPlace.STATION_BUS, Fixtures.monday, bus, jr)
        // 24:30 発は月曜のサービス日に属し、絶対時刻では火曜 00:30
        assertEquals(at(Fixtures.monday.plusDays(1), "00:30"), all.last().at)
    }

    @Test
    fun `resolveDate finds the next day of the wanted type`() {
        // 今日（種別指定なし）はそのまま
        assertEquals(Fixtures.monday, DepartureBoard.resolveDate(Fixtures.monday, emptySet(), jr))
        // 平日は月曜そのもの
        assertEquals(Fixtures.monday, DepartureBoard.resolveDate(Fixtures.monday, setOf(DayType.WEEKDAY), jr))
        // 土日祝は直近の土曜（2026-09-12 の次は 9-19）
        val weekend = DepartureBoard.resolveDate(Fixtures.monday, setOf(DayType.SATURDAY, DayType.HOLIDAY), jr)
        assertTrue(jr.dayTypeOf(weekend) in setOf(DayType.SATURDAY, DayType.HOLIDAY))
        assertTrue(!weekend.isBefore(Fixtures.monday))
    }

    @Test
    fun `every place maps to exactly one direction`() {
        BoardPlace.entries.forEach { place ->
            when (place.mode) {
                DepartureMode.BUS -> assertTrue(place.busDirection != null && place.jrLegId == null, "$place")
                DepartureMode.TRAIN -> assertTrue(place.jrLegId != null && place.busDirection == null, "$place")
            }
        }
    }
}
