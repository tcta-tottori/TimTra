package com.kazuya.timtra.wear.ui

import com.kazuya.timtra.core.board.BoardPlace
import com.kazuya.timtra.core.board.DepartureMode
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.board.PlaceBasis

/** 地点の名前（「南吉成 バス停」など）。core は Android 非依存なので、文言はここで対応づける。 */
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

/** 地点をどう決めたかの脚注。 */
fun PlaceBasis.labelRes(): Int =
    when (this) {
        PlaceBasis.NEAR_HERE -> R.string.basis_near_here
        PlaceBasis.MANUAL -> R.string.basis_manual
        PlaceBasis.TIME_OF_DAY -> R.string.basis_time_of_day
    }

/** 地点のアイコン（バス / 電車）。デザインの丸バッジとリストの先頭に使う。 */
fun BoardPlace.iconRes(): Int = mode.iconRes()

fun DepartureMode.iconRes(): Int =
    when (this) {
        DepartureMode.BUS -> R.drawable.ic_bus
        DepartureMode.TRAIN -> R.drawable.ic_train
    }
