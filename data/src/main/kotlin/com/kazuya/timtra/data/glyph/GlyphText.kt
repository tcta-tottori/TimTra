package com.kazuya.timtra.data.glyph

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * 切り出した字（[Glyphs]）で数字を書く。時計とスマホの両方から使う。
 *
 * 扱えるのは **0〜9 と「:」** だけ。残り時間（`MM:SS`）と時刻の表示にしか使わないため。
 * 日付の組み方は字の大きさも位置も別なので、時計側の `DateArt` が受け持つ。
 *
 * 大きさはすべて「数字の高さ（cap）」で指定する。字画像は [Glyphs.CAP_DIGIT] の大きさで
 * 切り出してあるので、`cap / CAP_DIGIT` 倍して描くだけでよい。
 *
 * **送りは等幅**（時計の文字盤と同じ）。数字はどれもいちばん広い字の幅で送り、
 * 字はその枠の中で中央に置く。こうしないと 11:11 と 88:88 で幅が変わり、
 * 残り時間が進むたびに表示や隣のアイコンが動いてしまう。
 */
object GlyphText {
    /** 字と字のあいだ（cap に対する割合）。見本の実測。 */
    const val TRACK = 0.07f

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val cache = HashMap<String, Bitmap>()

    fun glyphOf(char: Char): Glyph? =
        when (char) {
            in '0'..'9' -> Glyphs.digits[char - '0']
            ':' -> Glyphs.colon
            else -> null
        }

    /** 1 字ぶんの送り幅（px）。数字は等幅、「:」だけ狭い。 */
    fun advance(
        char: Char,
        cap: Float,
    ): Float = if (char == ':') Glyphs.COLON_ADVANCE * cap else Glyphs.DIGIT_ADVANCE * cap

    /** [text] を [cap] の大きさで書いたときの横幅（px）。字が何であっても同じ幅になる。 */
    fun width(
        text: String,
        cap: Float,
    ): Float {
        var width = 0f
        var count = 0
        text.forEach { char ->
            if (glyphOf(char) == null) return@forEach
            width += advance(char, cap)
            count++
        }
        return width + TRACK * cap * (count - 1).coerceAtLeast(0)
    }

    /** 1 行ぶんの高さ（px）。丸い字が下へ食み出すぶんを見込む。 */
    fun height(cap: Float): Float = cap * Glyphs.DIGIT_LINE

    /**
     * [left] から右へ、ベースラインを [baseline] にそろえて書く。
     * 字画像にはグローも入っているので、囲みは少し広めに取る。
     */
    fun draw(
        context: Context,
        canvas: Canvas,
        text: String,
        cap: Float,
        left: Float,
        baseline: Float,
    ) {
        val scale = cap / Glyphs.CAP_DIGIT
        var x = left
        text.forEach { char ->
            val glyph = glyphOf(char) ?: return@forEach
            val cell = advance(char, cap)
            // 等幅の枠の中で字を中央にそろえる
            val inkLeft = x + (cell - glyph.iw * scale) / 2f
            val bottom = baseline + glyph.drop * cap
            val dstLeft = inkLeft - glyph.ix * scale
            val dstTop = bottom - glyph.ih * scale - glyph.iy * scale
            canvas.drawBitmap(
                bitmap(context, glyph.file),
                null,
                RectF(dstLeft, dstTop, dstLeft + glyph.w * scale, dstTop + glyph.h * scale),
                paint,
            )
            x += cell + TRACK * cap
        }
    }

    /** 字画像。数は知れているので開いたら持っておく。 */
    @Synchronized
    fun bitmap(
        context: Context,
        file: String,
    ): Bitmap =
        cache.getOrPut(file) {
            context.assets.open("date/$file.png").use { checkNotNull(BitmapFactory.decodeStream(it)) { "字が読めない: $file" } }
        }
}
