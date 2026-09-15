package com.kazuya.timtra.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.core.board.Departure
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.board.WearBoardProvider
import com.kazuya.timtra.wear.location.WearLocationProvider
import com.kazuya.timtra.wear.ui.hhmm
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * コンプリケーション（ショートカット）: 次の便までの残り時間（CLAUDE.md 7-4）。
 * 発車時刻も一緒に見えるよう、SHORT_TEXT の見出しには発時刻（H:MM）を入れる。
 * 文字盤の枠に合わせて RANGED_VALUE（リング付き）と LONG_TEXT も返す。
 *
 * 地点はホームと同じ決め方（固定した地点 → 現在地の最寄り → 時刻帯）。
 * カウントダウンは TimeDifferenceComplicationText で文字盤側が更新するので、こちらは 10 分ごとの再計算だけでよい。
 *
 * クラス名は文字盤に設定済みのコンプリケーションを壊さないために変えていない。
 */
@AndroidEntryPoint
class LeaveCountdownComplicationService : SuspendingComplicationDataSourceService() {
    @Inject
    lateinit var provider: WearBoardProvider

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val description = plain(getString(R.string.complication_description))
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData
                    .Builder(text = plain(PREVIEW_COUNTDOWN), contentDescription = description)
                    .setTitle(plain(PREVIEW_TIME))
                    .build()
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData
                    .Builder(value = PREVIEW_PROGRESS, min = 0f, max = 1f, contentDescription = description)
                    .setText(plain(PREVIEW_COUNTDOWN))
                    .setTitle(plain(PREVIEW_TIME))
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData
                    .Builder(
                        text = plain(getString(R.string.complication_long, PREVIEW_TIME, PREVIEW_HEADSIGN)),
                        contentDescription = description,
                    ).setTitle(plain(PREVIEW_COUNTDOWN))
                    .build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snapshot = provider.snapshot(limit = 1, locationTimeoutMillis = WearLocationProvider.TILE_TIMEOUT_MILLIS)
        val next = snapshot.next?.takeIf { !it.at.isBefore(snapshot.now) }
        val description = plain(getString(R.string.complication_description))
        val countdown = countdownText(next)
        val departure = plain(next?.at?.hhmm() ?: getString(R.string.complication_none))
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData
                    .Builder(text = countdown, contentDescription = description)
                    .setTitle(departure)
                    .setTapAction(openApp())
                    .build()
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData
                    .Builder(value = snapshot.gauge, min = 0f, max = 1f, contentDescription = description)
                    .setText(countdown)
                    .setTitle(departure)
                    .setTapAction(openApp())
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData
                    .Builder(text = longText(next), contentDescription = description)
                    .setTitle(countdown)
                    .setTapAction(openApp())
                    .build()
            else -> null
        }
    }

    /** 残り時間。文字盤側が毎分書き換えるので、こちらは基準時刻だけ渡す。 */
    private fun countdownText(next: Departure?): ComplicationText =
        if (next == null) {
            plain(getString(R.string.complication_none))
        } else {
            TimeDifferenceComplicationText
                .Builder(
                    TimeDifferenceStyle.SHORT_SINGLE_UNIT,
                    CountDownTimeReference(next.at.atZone(TimTraConstants.ZONE).toInstant()),
                ).setMinimumTimeUnit(TimeUnit.MINUTES)
                .build()
        }

    private fun longText(next: Departure?): ComplicationText =
        if (next == null) {
            plain(getString(R.string.board_empty))
        } else {
            plain(getString(R.string.complication_long, next.at.hhmm(), next.headsign))
        }

    private fun plain(value: String): ComplicationText = PlainComplicationText.Builder(value).build()

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val PREVIEW_COUNTDOWN = "10分"
        const val PREVIEW_TIME = "20:53"
        const val PREVIEW_HEADSIGN = "鳥取駅"
        const val PREVIEW_PROGRESS = 0.83f
    }
}
