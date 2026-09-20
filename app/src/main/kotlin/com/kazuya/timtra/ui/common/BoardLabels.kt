package com.kazuya.timtra.ui.common

import com.kazuya.timtra.R
import com.kazuya.timtra.core.board.BoardPlace
import com.kazuya.timtra.core.board.DepartureMode
import com.kazuya.timtra.ui.timetable.TimetableFocus

/*
 * 発車標の地点（core の BoardPlace）の見せ方。時計版（wear/ui/BoardLabels.kt）と同じ文言・同じ絵柄にそろえる。
 * 休みの日のホームと、そこから開く時刻表で使う。
 */

/** 地点の名前（「南吉成 バス停」など）。 */
fun BoardPlace.nameRes(): Int =
    when (this) {
        BoardPlace.HOME_STOP -> R.string.place_home_stop
        BoardPlace.STATION_BUS -> R.string.place_station_bus
        BoardPlace.TOTTORI_JR -> R.string.place_tottori_jr
        BoardPlace.HOUGI_JR -> R.string.place_hougi_jr
    }

/** 地点の行き先（「鳥取駅方面」など）。 */
fun BoardPlace.directionRes(): Int =
    when (this) {
        BoardPlace.HOME_STOP -> R.string.place_home_stop_dir
        BoardPlace.STATION_BUS -> R.string.place_station_bus_dir
        BoardPlace.TOTTORI_JR -> R.string.place_tottori_jr_dir
        BoardPlace.HOUGI_JR -> R.string.place_hougi_jr_dir
    }

/** 地点で乗るもの（バス / JR）。色とアイコンはアプリ共通（[TransitMode]）。 */
fun BoardPlace.transitMode(): TransitMode =
    when (mode) {
        DepartureMode.BUS -> TransitMode.BUS
        DepartureMode.TRAIN -> TransitMode.JR
    }

/** タップしたときに開く時刻表のタブ。 */
fun BoardPlace.focus(): TimetableFocus =
    when (this) {
        BoardPlace.HOME_STOP -> TimetableFocus.HOME_STOP
        BoardPlace.STATION_BUS -> TimetableFocus.STATION_BUS
        BoardPlace.TOTTORI_JR -> TimetableFocus.STATION_JR
        BoardPlace.HOUGI_JR -> TimetableFocus.HOUGI
    }
