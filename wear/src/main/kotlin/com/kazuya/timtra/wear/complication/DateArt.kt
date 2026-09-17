package com.kazuya.timtra.wear.complication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.kazuya.timtra.data.glyph.Glyph
import com.kazuya.timtra.data.glyph.GlyphText
import com.kazuya.timtra.data.glyph.Glyphs
import java.time.LocalDate
import kotlin.math.hypot

/** 日付の出し方。文字盤の枠ごとに好きなものを選べるよう、提供元をこの数だけ分けてある。 */
enum class DateStyle {
    /** 9/ + 金 + 15 */
    WEEKDAY_JP,

    /** 9/ + FRI + 15 */
    WEEKDAY_EN,

    /** 9/ + 15（曜日なし） */
    PLAIN,
}

/**
 * ウォッチフェイスの日付コンプリケーションに出す絵。
 *
 * 字はシステムのフォントではなく、提供された見本画像から切り出したものを並べる
 * （`assets/date` の PNG、寸法表は [Glyphs]、切り出しは `tools/date_glyphs.py`）。
 * 画像にはグローまで焼き込んであるので、ここでは並べて拡大するだけでよい。
 *
 * 配置も見本どおり。左に月の数字と「/」、その下に曜日、右に大きな日にち。
 * 組み上がりは横長なので、丸い枠に収まるよう **対角の長さ** を枠の [FILL_DIAGONAL] に合わせる。
 */
object DateArt {
    /** 既定の一辺（px）。コンプリケーションの枠に合わせて文字盤側が縮める。 */
    const val SIZE_PX = 384

    /** 組み上がりの対角を、枠の一辺に対してどこまで広げるか。丸い枠に内接する値。 */
    private const val FILL_DIAGONAL = 0.90f

    /** 右下が開いていて、「/」を食い込ませても潰れない数字。 */
    private const val OPEN_TAIL = "479"

    fun render(
        context: Context,
        date: LocalDate,
        style: DateStyle,
        size: Int = SIZE_PX,
    ): Bitmap {
        val placed = layout(date, style)
        val box = RectF(placed.first().ink)
        placed.forEach { box.union(it.ink) }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = size * FILL_DIAGONAL / hypot(box.width(), box.height())
        canvas.translate(size / 2f, size / 2f)
        canvas.scale(scale, scale)
        canvas.translate(-box.centerX(), -box.centerY())
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        placed.forEach { draw(context, canvas, it, paint) }
        return bitmap
    }

    /**
     * 字の芯の置き場所を決める。長さの単位は日にちの字の高さ、
     * 原点は日にちの字の左下（[Glyphs] と同じ取り方）。
     */
    private fun layout(
        date: LocalDate,
        style: DateStyle,
    ): List<Placed> {
        val form = if (style == DateStyle.WEEKDAY_EN) Glyphs.en else Glyphs.jp
        val placed = mutableListOf<Placed>()
        // 日にち: 原点から右へ
        val day = date.dayOfMonth.toString()
        var x = 0f
        day.forEach { digit ->
            val glyph = Glyphs.digits[digit - '0']
            val width = glyph.iw / Glyphs.CAP_DIGIT
            val bottom = glyph.drop
            placed += Placed(glyph, RectF(x, bottom - glyph.ih / Glyphs.CAP_DIGIT, x + width, bottom))
            x += width + form.dayTrack
        }
        // 「/」: 高さは見本の実測。見本は 15 で、細い「1」に少しだけ重ねてある。
        // 3 や 8 のような丸い数字に重ねると潰れるので、先頭が 1 のときだけ重ねる
        val slash = Glyphs.slash
        val slashWidth = form.slashHeight * slash.iw / slash.ih
        val slashRight = if (day.first() == '1') form.slashRight else form.slashRight - form.dayTrack
        val slashLeft = slashRight - slashWidth
        placed += Placed(slash, RectF(slashLeft, form.slashBottom - form.slashHeight, slashRight, form.slashBottom))
        // 月: 「/」の左へ右詰め（2 桁なら左へ伸びる）。
        // 食い込みは見本の「9」に合わせた値。下が開いている数字だけに使い、ほかは付けるだけにする
        val month = date.monthValue.toString()
        var right = slashLeft - if (month.last() in OPEN_TAIL) form.monthSlashGap else 0f
        month.reversed().forEach { digit ->
            val glyph = Glyphs.digits[digit - '0']
            val width = glyph.iw / Glyphs.CAP_DIGIT * form.monthCap
            val bottom = form.monthBaseline + glyph.drop * form.monthCap
            placed += Placed(glyph, RectF(right - width, bottom - glyph.ih / Glyphs.CAP_DIGIT * form.monthCap, right, bottom))
            right -= width + form.dayTrack * form.monthCap
        }
        // 曜日: 日にちの字に被らないよう右端でそろえる
        weekday(date, style)?.let { glyph ->
            val cap = if (style == DateStyle.WEEKDAY_EN) Glyphs.CAP_WEEK_EN else Glyphs.CAP_WEEK_JP
            val weekRight = slashRight + form.weekRight - form.slashRight
            val bottom = form.weekBaseline + glyph.drop * form.weekCap
            placed +=
                Placed(
                    glyph,
                    RectF(
                        weekRight - glyph.iw / cap * form.weekCap,
                        bottom - glyph.ih / cap * form.weekCap,
                        weekRight,
                        bottom,
                    ),
                )
        }
        return placed
    }

    private fun weekday(
        date: LocalDate,
        style: DateStyle,
    ): Glyph? =
        when (style) {
            DateStyle.WEEKDAY_JP -> Glyphs.weekdaysJp[date.dayOfWeek.value - 1]
            DateStyle.WEEKDAY_EN -> Glyphs.weekdaysEn[date.dayOfWeek.value - 1]
            DateStyle.PLAIN -> null
        }

    /** 画像の芯が [Placed.ink] に重なるよう、グローの余白ごと拡大して描く。 */
    private fun draw(
        context: Context,
        canvas: Canvas,
        placed: Placed,
        paint: Paint,
    ) {
        val glyph = placed.glyph
        val scale = placed.ink.width() / glyph.iw
        val left = placed.ink.left - glyph.ix * scale
        val top = placed.ink.top - glyph.iy * scale
        val target = RectF(left, top, left + glyph.w * scale, top + glyph.h * scale)
        canvas.drawBitmap(GlyphText.bitmap(context, glyph.file), null, target, paint)
    }

    private class Placed(
        val glyph: Glyph,
        val ink: RectF,
    )
}
