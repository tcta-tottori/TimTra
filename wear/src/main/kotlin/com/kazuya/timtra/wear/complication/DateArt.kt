package com.kazuya.timtra.wear.complication

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * ウォッチフェイスの日付コンプリケーションに出す絵（提供されたデザイン）。
 *
 * 左に「9/」と曜日を 2 段、右に大きな日にち。文字は白に近い色で、外へ青いグローを出す。
 * 文字盤に溶けるよう地は透明のままにする。
 *
 * 文字の実寸を [Paint.getTextBounds] で測り、組み上がり全体を **枠いっぱい** に拡大してから描く。
 * 組み上がりは横長（およそ 1.8:1）なので、丸い枠に内接するよう横幅を [FILL] に収めている。
 *
 * グローは [BlurMaskFilter]（ソフトウェア描画が要る）なので、必ず Bitmap の [Canvas] に描く。
 */
object DateArt {
    /** 既定の一辺（px）。コンプリケーションの枠に合わせて文字盤側が縮める。 */
    const val SIZE_PX = 384

    private val monthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M/")

    fun render(
        date: LocalDate,
        locale: Locale = Locale.getDefault(),
        size: Int = SIZE_PX,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val day = date.dayOfMonth.toString()
        val month = monthFormat.format(date)
        val weekday = DateTimeFormatter.ofPattern("E", locale).format(date).uppercase(locale)

        val dayPaint = textPaint(size * DAY_TEXT)
        val smallPaint = textPaint(size * SMALL_TEXT)
        val dayBox = bounds(dayPaint, day)
        val monthBox = bounds(smallPaint, month)
        val weekBox = bounds(smallPaint, weekday)

        // 日にちはインクの左端を 0、ベースラインを 0 に置く。左の 2 段はその左に並べる
        val columnLeft = -(size * GAP + max(monthBox.width(), weekBox.width()))
        // 「9/」は日にちの上端に、曜日は下端にそろえる
        val monthBaseline = (dayBox.top - monthBox.top).toFloat()
        val weekBaseline = (dayBox.bottom - weekBox.bottom).toFloat()
        val right = dayBox.width().toFloat()

        val fill = size * FILL
        val scale = min(fill / (right - columnLeft), fill / (dayBox.bottom - dayBox.top))
        canvas.translate(size / 2f, size / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-(columnLeft + right) / 2f, -(dayBox.top + dayBox.bottom) / 2f)

        drawGlowing(canvas, day, -dayBox.left.toFloat(), 0f, dayPaint)
        drawGlowing(canvas, month, columnLeft - monthBox.left, monthBaseline, smallPaint)
        drawGlowing(canvas, weekday, columnLeft - weekBox.left, weekBaseline, smallPaint)
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

    private fun bounds(
        paint: Paint,
        text: String,
    ): Rect = Rect().also { paint.getTextBounds(text, 0, text.length, it) }

    private fun textPaint(textSize: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            textAlign = Paint.Align.LEFT
            color = INK
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }

    // 組み上がりの比率だけを決める値（最後に枠へ合わせて拡大するので、絶対の大きさは効かない）
    private const val DAY_TEXT = 0.55f
    private const val SMALL_TEXT = 0.20f
    private const val GAP = 0.04f

    /** 枠の一辺に対して、組み上がりの横幅をどこまで広げるか。丸い枠に内接する値。 */
    private const val FILL = 0.80f

    /** グローの広がり（その文字の大きさに対する割合）。 */
    private const val GLOW_RADIUS = 0.06f

    /** 文字の色。真っ白より少し青に振ると文字盤で浮かない。 */
    private const val INK = 0xFFEAF2FF.toInt()

    /** 外へ広がるグローの色。 */
    private const val GLOW = 0xFF5B9BFF.toInt()
}
