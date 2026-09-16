package com.kazuya.timtra.wear.complication

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ウォッチフェイスの日付コンプリケーションに出す絵（2026-09-15 提供のデザイン）。
 *
 * 左上に「9/」、その下に曜日、右に大きな日にち。文字は白に近い色で、外へ青いグローを出す。
 * 文字盤に溶けるよう地は透明のままにする。
 *
 * グローは [BlurMaskFilter]（ソフトウェア描画が要る）なので、必ず Bitmap の [Canvas] に描く。
 */
object DateArt {
    /** 既定の一辺（px）。コンプリケーションの枠に合わせて文字盤側が縮める。 */
    const val SIZE_PX = 288

    private val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M/")

    fun render(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
        size: Int = SIZE_PX,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = size.toFloat()
        val weekday = DateTimeFormatter.ofPattern("E", locale).format(date).uppercase(locale)

        val day = textPaint(scale * DAY_TEXT, Paint.Align.RIGHT)
        val small = textPaint(scale * SMALL_TEXT, Paint.Align.LEFT)
        drawGlowing(canvas, date.dayOfMonth.toString(), scale * DAY_X, scale * DAY_BASELINE, day)
        drawGlowing(canvas, monthFormat.format(date), scale * SMALL_X, scale * MONTH_BASELINE, small)
        drawGlowing(canvas, weekday, scale * SMALL_X, scale * WEEKDAY_BASELINE, small)
        return bitmap
    }

    /** 先にぼかした青を敷き、その上に白い文字を重ねてネオンのように見せる。 */
    private fun drawGlowing(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        paint: Paint,
    ) {
        val glow =
            Paint(paint).apply {
                color = GLOW
                maskFilter = BlurMaskFilter(paint.textSize * GLOW_RADIUS, BlurMaskFilter.Blur.NORMAL)
            }
        canvas.drawText(text, x, y, glow)
        canvas.drawText(text, x, y, paint)
    }

    private fun textPaint(
        textSize: Float,
        align: Paint.Align,
    ): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            this.textAlign = align
            color = INK
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

    // 一辺に対する割合。デザインの比率をそのまま持ってきている
    private const val DAY_TEXT = 0.60f
    private const val SMALL_TEXT = 0.21f
    private const val DAY_X = 0.97f
    private const val DAY_BASELINE = 0.80f
    private const val SMALL_X = 0.05f
    private const val MONTH_BASELINE = 0.47f
    private const val WEEKDAY_BASELINE = 0.79f
    private const val GLOW_RADIUS = 0.10f

    /** 文字の色。真っ白より少し青に振ると文字盤で浮かない。 */
    private const val INK = 0xFFEAF2FF.toInt()

    /** 外へ広がるグローの色。 */
    private const val GLOW = 0xFF5B9BFF.toInt()
}
