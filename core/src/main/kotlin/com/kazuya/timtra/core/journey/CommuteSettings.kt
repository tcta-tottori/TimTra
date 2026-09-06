package com.kazuya.timtra.core.journey

import com.kazuya.timtra.core.model.Bound
import java.time.Duration
import java.time.LocalTime

/**
 * ユーザー設定値。既定値は CLAUDE.md 6 の仮の値。すべて設定画面から変更できるようにする。
 */
data class CommuteSettings(
    /** 自宅 → 南吉成 の徒歩時間。家を出る時刻の逆算に使う。 */
    val walkHomeToStop: Duration = Duration.ofMinutes(5),
    /** 鳥取駅 バス降車 → JR ホーム の乗換所要時間。復路は JR 降車 → バスターミナルにも同じ値を使う。 */
    val transferBusToJr: Duration = Duration.ofMinutes(8),
    /** 準備時間（バッファ）。通知を早める余裕。 */
    val prepBuffer: Duration = Duration.ofMinutes(5),
    /** 宝木駅 → 勤務先 の所要時間。到着予測に使う。 */
    val walkStationToWork: Duration = Duration.ofMinutes(10),
    /** 最低乗換許容。余裕がこれを下回る接続は RISK。バス便選択の締切にも使う。 */
    val minTransfer: Duration = Duration.ofMinutes(5),
    /** これ以上の余裕があれば OK。未満は TIGHT。 */
    val comfortableTransfer: Duration = Duration.ofMinutes(10),
    /** 往路と判定する時刻帯の開始。これより前は復路（前日の帰宅）扱い。 */
    val outboundWindowStart: LocalTime = LocalTime.of(3, 0),
    /** 復路と判定する時刻帯の開始。 */
    val inboundWindowStart: LocalTime = LocalTime.of(12, 0),
    /** 前夜に翌日分を計算するときの「この時刻以降に家を出る」基準。 */
    val earliestLeaveHome: LocalTime = LocalTime.of(6, 30),
    /** 終業時刻。復路の翌日分計算の基準。 */
    val workEndsAt: LocalTime = LocalTime.of(17, 30),
    /** ホームに「家を出る時刻」を出す時間帯の開始（既定 05:30。6:48 発のバスに乗る前提）。 */
    val leaveHomeDisplayStart: LocalTime = LocalTime.of(5, 30),
    /** 「家を出る時刻」を出す時間帯の終了（この時刻以降は出さない）。 */
    val leaveHomeDisplayEnd: LocalTime = LocalTime.of(6, 50),
    /** 「職場を出る時刻」を出し始める時刻。終わりは終電（[LeaveDisplayPolicy]）。 */
    val leaveWorkDisplayStart: LocalTime = LocalTime.of(17, 0),
) {
    init {
        require(comfortableTransfer >= minTransfer) { "comfortableTransfer は minTransfer 以上にしてください" }
        listOf(walkHomeToStop, transferBusToJr, prepBuffer, walkStationToWork, minTransfer, comfortableTransfer).forEach {
            require(!it.isNegative) { "設定値に負の時間は指定できません" }
        }
    }

    /** 時刻帯から往路 / 復路を自動判定する。手動切替は呼び出し側で上書きする。 */
    fun boundAt(time: LocalTime): Bound =
        if (!time.isBefore(outboundWindowStart) && time.isBefore(inboundWindowStart)) Bound.OUTBOUND else Bound.INBOUND
}
