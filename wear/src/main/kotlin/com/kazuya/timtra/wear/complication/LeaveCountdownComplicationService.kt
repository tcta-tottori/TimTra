package com.kazuya.timtra.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.board.WearBoardProvider
import com.kazuya.timtra.wear.location.WearLocationProvider
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * コンプリケーション: 次の発車までの残り時間のみ（SHORT_TEXT、CLAUDE.md 7-4）。
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
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData
            .Builder(
                text = PlainComplicationText.Builder(PREVIEW_TEXT).build(),
                contentDescription = PlainComplicationText.Builder(getString(R.string.complication_description)).build(),
            ).setTitle(PlainComplicationText.Builder(getString(R.string.complication_title)).build())
            .build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val snapshot = provider.snapshot(limit = 1, locationTimeoutMillis = WearLocationProvider.TILE_TIMEOUT_MILLIS)
        val next = snapshot.next
        val description = PlainComplicationText.Builder(getString(R.string.complication_description)).build()
        val title = PlainComplicationText.Builder(getString(R.string.complication_title)).build()
        val text =
            if (next == null || next.at.isBefore(snapshot.now)) {
                PlainComplicationText.Builder(getString(R.string.complication_none)).build()
            } else {
                TimeDifferenceComplicationText
                    .Builder(
                        TimeDifferenceStyle.SHORT_SINGLE_UNIT,
                        CountDownTimeReference(next.at.atZone(TimTraConstants.ZONE).toInstant()),
                    ).setMinimumTimeUnit(TimeUnit.MINUTES)
                    .build()
            }
        return ShortTextComplicationData
            .Builder(text = text, contentDescription = description)
            .setTitle(title)
            .setTapAction(openApp())
            .build()
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val PREVIEW_TEXT = "12分"
    }
}
