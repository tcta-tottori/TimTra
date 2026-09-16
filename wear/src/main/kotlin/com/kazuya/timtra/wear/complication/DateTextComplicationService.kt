package com.kazuya.timtra.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeFormatComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R

/**
 * ウォッチフェイスの日付コンプリケーション（文字）。
 * 画像を受け付けない枠のための控え。絵で出したいときは [DateComplicationService] を選ぶ。
 *
 * [TimeFormatComplicationText] なので文字盤側が日付を描く。こちらから取り直す必要はない。
 */
class DateTextComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? = data(request.complicationType)

    private fun data(type: ComplicationType): ComplicationData? {
        val description = PlainComplicationText.Builder(getString(R.string.date_complication_description)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData
                    .Builder(format(DAY_FORMAT), description)
                    .setTitle(format(HEAD_FORMAT))
                    .setTapAction(openApp())
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData
                    .Builder(format(LONG_FORMAT), description)
                    .setTapAction(openApp())
                    .build()
            else -> null
        }
    }

    private fun format(pattern: String): ComplicationText = TimeFormatComplicationText.Builder(pattern).build()

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        /** SimpleDateFormat のパターン（文字盤側が解釈する）。 */
        const val DAY_FORMAT = "d"
        const val HEAD_FORMAT = "M/E"
        const val LONG_FORMAT = "M/d(E)"
    }
}
