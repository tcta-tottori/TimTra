package com.kazuya.timtra.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PhotoImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.TimeRange
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.kazuya.timtra.core.TimTraConstants
import com.kazuya.timtra.wear.MainActivity
import com.kazuya.timtra.wear.R
import java.time.LocalDate

/**
 * ウォッチフェイスの日付コンプリケーション（絵）の共通部分。
 * [DateArt] が描く「9/ + 曜日 + 大きな日にち」を枠いっぱいに出す。
 *
 * **画像の型しか宣言していない。** SHORT_TEXT も宣言すると、枠が両方を受け付ける場合に
 * 文字盤側が文字を選んでしまい、絵が出ない（小さな「9/水 16」になる）ため。
 * 画像を受け付けない枠には [DateTextComplicationService]（TimTra 日付（文字））を使う。
 *
 * 絵なので日が変わったら描き直す必要がある。[TimeRange] をその日の 1 日に限ることで、
 * 日付が変わった時点で文字盤側から取り直してもらう（保険として manifest でも定期更新する）。
 *
 * 曜日のあり / なしと日本語 / 英語は、[style] 違いの提供元として別々に登録する。
 * 文字盤側に設定画面を足さずに選べるようにするため。
 */
abstract class DateImageComplicationService(
    private val style: DateStyle,
) : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        data(type, LocalDate.now(TimTraConstants.ZONE), limitToToday = false)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        data(request.complicationType, LocalDate.now(TimTraConstants.ZONE), limitToToday = true)

    private fun data(
        type: ComplicationType,
        date: LocalDate,
        limitToToday: Boolean,
    ): ComplicationData? {
        val description = PlainComplicationText.Builder(getString(R.string.date_complication_description)).build()
        val valid = if (limitToToday) justToday(date) else TimeRange.ALWAYS
        return when (type) {
            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData
                    .Builder(SmallImage.Builder(art(date), SmallImageType.PHOTO).build(), description)
                    .setValidTimeRange(valid)
                    .setTapAction(openApp())
                    .build()
            ComplicationType.PHOTO_IMAGE ->
                PhotoImageComplicationData
                    .Builder(art(date), description)
                    .setValidTimeRange(valid)
                    .setTapAction(openApp())
                    .build()
            else -> null
        }
    }

    private fun art(date: LocalDate): Icon = Icon.createWithBitmap(DateArt.render(this, date, style))

    /** その日のあいだだけ有効にする。日付が変わればデータが切れるので取り直しが走る。 */
    private fun justToday(date: LocalDate): TimeRange =
        TimeRange.between(
            date.atStartOfDay(TimTraConstants.ZONE).toInstant(),
            date.plusDays(1).atStartOfDay(TimTraConstants.ZONE).toInstant(),
        )

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
