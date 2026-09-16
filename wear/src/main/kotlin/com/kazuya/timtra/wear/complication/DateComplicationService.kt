package com.kazuya.timtra.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.TimeFormatComplicationText
import androidx.wear.watchface.complications.data.TimeRange
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R
import java.time.LocalDate

/**
 * ウォッチフェイスの日付コンプリケーション。
 *
 * - SMALL_IMAGE / PHOTO_IMAGE: [DateArt] が描く「9/ + 曜日 + 大きな日にち」のネオン風の絵。
 *   絵なので日が変わったら描き直す必要がある。[TimeRange] をその日の 1 日に限ることで、
 *   日付が変わった時点で文字盤側から取り直してもらう（保険として manifest でも定期更新する）。
 * - SHORT_TEXT / LONG_TEXT: 絵を置けない枠用。[TimeFormatComplicationText] なので
 *   文字盤側が日付をそのまま描き、取り直しは要らない。
 *
 * 発車標とは無関係だが、同じ文字盤に並べたいので TimTra から提供する。
 */
class DateComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        data(type, LocalDate.now(TimTraConstants.ZONE), limitToToday = false)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        data(request.complicationType, LocalDate.now(TimTraConstants.ZONE), limitToToday = true)

    private fun data(
        type: ComplicationType,
        date: LocalDate,
        limitToToday: Boolean,
    ): ComplicationData? {
        val description = plain(getString(R.string.date_complication_description))
        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData
                    .Builder(TimeFormatComplicationText.Builder(DAY_FORMAT).build(), description)
                    .setTitle(TimeFormatComplicationText.Builder(HEAD_FORMAT).build())
                    .setTapAction(openApp())
                    .build()
            ComplicationType.LONG_TEXT ->
                LongTextComplicationData
                    .Builder(TimeFormatComplicationText.Builder(LONG_FORMAT).build(), description)
                    .setTapAction(openApp())
                    .build()
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData
                    .Builder(SmallImage.Builder(art(date), SmallImageType.PHOTO).build(), description)
                    .setValidTimeRange(if (limitToToday) justToday(date) else TimeRange.ALWAYS)
                    .setTapAction(openApp())
                    .build()
            ComplicationType.PHOTO_IMAGE ->
                PhotoImageComplicationData
                    .Builder(art(date), description)
                    .setValidTimeRange(if (limitToToday) justToday(date) else TimeRange.ALWAYS)
                    .setTapAction(openApp())
                    .build()
            else -> null
        }
    }

    private fun art(date: LocalDate): Icon = Icon.createWithBitmap(DateArt.render(date, resources.configuration.locales[0]))

    /** その日のあいだだけ有効にする。日付が変わればデータが切れるので取り直しが走る。 */
    private fun justToday(date: LocalDate): TimeRange =
        TimeRange.between(
            date.atStartOfDay(TimTraConstants.ZONE).toInstant(),
            date.plusDays(1).atStartOfDay(TimTraConstants.ZONE).toInstant(),
        )

    private fun plain(value: String): ComplicationText = PlainComplicationText.Builder(value).build()

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
