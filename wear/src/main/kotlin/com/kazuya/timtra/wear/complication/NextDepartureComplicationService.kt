package com.kazuya.timtra.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R
import com.kazuya.timtra.wear.board.WearBoardProvider
import com.kazuya.timtra.wear.location.WearLocationProvider
import com.kazuya.timtra.wear.ui.hhmm
import com.kazuya.timtra.wear.ui.nameRes
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * コンプリケーション（ショートカット）: 次の便の発車時刻そのもの。
 * 「あと何分か」ではなく「何時何分発か」を文字盤に置きたいとき用。
 * 残り時間が要るときは [LeaveCountdownComplicationService] を選ぶ。
 */
@AndroidEntryPoint
class NextDepartureComplicationService : SuspendingComplicationDataSourceService() {
    @Inject
    lateinit var provider: WearBoardProvider

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val description = plain(getString(R.string.complication_departure_description))
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData
                    .Builder(text = plain(PREVIEW_TIME), contentDescription = description)
                    .setTitle(plain(PREVIEW_HEADSIGN))
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData
                    .Builder(
                        text = plain(getString(R.string.complication_long, PREVIEW_TIME, PREVIEW_HEADSIGN)),
                        contentDescription = description,
                    ).setTitle(plain(getString(R.string.place_home_stop)))
                    .build()
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val snapshot = provider.snapshot(limit = 1, locationTimeoutMillis = WearLocationProvider.TILE_TIMEOUT_MILLIS)
        val next = snapshot.next?.takeIf { !it.at.isBefore(snapshot.now) }
        val description = plain(getString(R.string.complication_departure_description))
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData
                    .Builder(
                        text = plain(next?.at?.hhmm() ?: getString(R.string.complication_none)),
                        contentDescription = description,
                    ).setTitle(plain(next?.headsign ?: getString(R.string.complication_title)))
                    .setTapAction(openApp())
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData
                    .Builder(
                        text =
                            plain(
                                next?.let { getString(R.string.complication_long, it.at.hhmm(), it.headsign) }
                                    ?: getString(R.string.board_empty),
                            ),
                        contentDescription = description,
                    ).setTitle(plain(getString(snapshot.place.nameRes())))
                    .setTapAction(openApp())
                    .build()
            else -> null
        }
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
        const val PREVIEW_TIME = "20:53"
        const val PREVIEW_HEADSIGN = "鳥取駅"
    }
}
