package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.GeoPoint
import com.kazuya.timtra.core.model.Places
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LeaveDisplayPolicyTest {
    private val settings = CommuteSettings()
    private val monday: LocalDate = LocalDate.of(2026, 9, 7)

    /** 勤務先（気高電機付近の仮の位置。宝木駅の西 1 km）。 */
    private val workplace = GeoPoint(35.5183, 134.0636)

    private fun at(
        hour: Int,
        minute: Int,
    ): LocalDateTime = monday.atTime(hour, minute)

    @Test
    fun `leave home is shown only between 05_30 and 06_50`() {
        assertFalse(LeaveDisplayPolicy.showLeaveHome(at(5, 29), settings))
        assertTrue(LeaveDisplayPolicy.showLeaveHome(at(5, 30), settings))
        assertTrue(LeaveDisplayPolicy.showLeaveHome(at(6, 38), settings))
        assertTrue(LeaveDisplayPolicy.showLeaveHome(at(6, 49), settings))
        assertFalse(LeaveDisplayPolicy.showLeaveHome(at(6, 50), settings))
        assertFalse(LeaveDisplayPolicy.showLeaveHome(at(7, 30), settings))
        assertFalse(LeaveDisplayPolicy.showLeaveHome(at(23, 0), settings))
    }

    @Test
    fun `leave home window can wrap past midnight`() {
        val wrapped = settings.copy(leaveHomeDisplayStart = LocalTime.of(23, 0), leaveHomeDisplayEnd = LocalTime.of(1, 0))
        assertTrue(LeaveDisplayPolicy.showLeaveHome(at(23, 30), wrapped))
        assertTrue(LeaveDisplayPolicy.showLeaveHome(at(0, 30), wrapped))
        assertFalse(LeaveDisplayPolicy.showLeaveHome(at(1, 0), wrapped))
        assertFalse(LeaveDisplayPolicy.showLeaveHome(at(12, 0), wrapped))
    }

    @Test
    fun `leave work starts at 17_00 and lasts until the last train of the day`() {
        assertFalse(LeaveDisplayPolicy.showLeaveWork(at(16, 59), monday, null, workplace, settings))
        assertTrue(LeaveDisplayPolicy.showLeaveWork(at(17, 0), monday, null, workplace, settings))
        assertTrue(LeaveDisplayPolicy.showLeaveWork(at(22, 10), monday, null, workplace, settings))
        // 終電（22:14 宝木発）の後は、案のアンカーが翌日の便になる → 出さない
        assertFalse(LeaveDisplayPolicy.showLeaveWork(at(22, 30), monday.plusDays(1), null, workplace, settings))
    }

    @Test
    fun `leave work is hidden once the user has moved away toward Tottori station`() {
        val atWork = workplace
        val onTheWay = GeoPoint(35.5100, 134.1200) // 宝木と鳥取の中間あたり（勤務先から約 10 km）
        val nearStation = GeoPoint(35.4940, 134.2200)
        val busTerminal = GeoPoint(35.4949, 134.2244)
        assertTrue(LeaveDisplayPolicy.showLeaveWork(at(17, 30), monday, atWork, workplace, settings))
        assertTrue(LeaveDisplayPolicy.showLeaveWork(at(17, 30), monday, Places.HOUGI_STATION, workplace, settings))
        assertTrue(LeaveDisplayPolicy.showLeaveWork(at(17, 30), monday, onTheWay, workplace, settings))
        assertFalse(LeaveDisplayPolicy.showLeaveWork(at(17, 30), monday, nearStation, workplace, settings))
        assertFalse(LeaveDisplayPolicy.showLeaveWork(at(17, 30), monday, busTerminal, workplace, settings))
        // 鳥取駅と同じだけ離れた別方向（出張先など）でも出さない
        val farWest = GeoPoint(35.5183, 134.0636 - 0.25)
        assertFalse(LeaveDisplayPolicy.showLeaveWork(at(17, 30), monday, farWest, workplace, settings))
    }

    @Test
    fun `away threshold never drops below the minimum even if workplace is close to the station`() {
        val nearStationWorkplace = GeoPoint(35.4900, 134.2300)
        val threshold = LeaveDisplayPolicy.awayThresholdMeters(nearStationWorkplace)
        assertTrue(threshold >= LeaveDisplayPolicy.MIN_AWAY_METERS, "$threshold")
        // 通常の勤務先では「鳥取駅までの距離 − 1.5 km」
        val normal = LeaveDisplayPolicy.awayThresholdMeters(workplace)
        val expected = workplace.distanceMetersTo(Places.TOTTORI_STATION) - LeaveDisplayPolicy.NEAR_STATION_METERS
        assertTrue(kotlin.math.abs(normal - expected) < 1.0, "$normal vs $expected")
    }
}
