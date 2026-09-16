package com.kazuya.timtra.wear.complication

/**
 * 日付コンプリケーションの字の寸法表と配置の比率。
 * `tools/date_glyphs.py` が見本画像から作る。**手で書き換えない。**
 *
 * 長さはすべて、日にちの字の高さ（基準は「1」）を 1 とした比率。
 * 原点は日にちの字の左下（ベースライン）で、y は下向きが正。
 */
internal object DateGlyphs {
    /** 字見本の中での基準の高さ（px）。切り出した字はこれで割って使う。 */
    const val CAP_DIGIT = 270.0f
    const val CAP_WEEK = 128.0f

    /** 日にちの字と字のあいだ。 */
    const val DAY_TRACK = 0.07360f

    /** 月の数字の高さ。 */
    const val MONTH_CAP = 0.41570f

    /** 月の数字のベースライン。 */
    const val MONTH_BASELINE = -0.73350f

    /** 月の数字と「/」のあいだ（負は食い込み）。 */
    const val MONTH_SLASH_GAP = -0.04569f

    /** 「/」の高さ。 */
    const val SLASH_HEIGHT = 0.38579f

    /** 「/」の右端。 */
    const val SLASH_RIGHT = 0.04569f

    /** 「/」の下端。 */
    const val SLASH_BOTTOM = -0.70812f

    /** 曜日の字の高さ。 */
    const val WEEK_CAP = 0.38687f

    /** 曜日の字のベースライン。 */
    const val WEEK_BASELINE = -0.23955f

    /** 曜日の字の右端。 */
    const val WEEK_RIGHT = -0.01015f

    /** 0〜9。 */
    val digits: List<Glyph> =
        listOf(
            Glyph("d0", 209, 387, 57, 59, 134, 269, -0.00370f),
            Glyph("d1", 137, 388, 18, 59, 93, 270, 0.00000f),
            Glyph("d2", 179, 384, 26, 59, 137, 266, -0.01481f),
            Glyph("d3", 163, 387, 16, 59, 131, 269, -0.00370f),
            Glyph("d4", 188, 387, 17, 59, 155, 269, -0.00370f),
            Glyph("d5", 174, 385, 17, 59, 137, 267, 0.00000f),
            Glyph("d6", 169, 387, 20, 59, 132, 269, -0.00370f),
            Glyph("d7", 174, 385, 17, 59, 141, 267, 0.00000f),
            Glyph("d8", 172, 391, 15, 59, 141, 273, 0.00370f),
            Glyph("d9", 209, 390, 17, 59, 141, 272, 0.00000f),
        )

    /** 月〜日（`java.time.DayOfWeek` の順）。 */
    val weekdays: List<Glyph> =
        listOf(
            Glyph("mon", 170, 185, 28, 28, 114, 129, 0.03125f),
            Glyph("tue", 187, 189, 28, 28, 131, 133, 0.03125f),
            Glyph("wed", 189, 188, 28, 28, 133, 132, 0.02344f),
            Glyph("thu", 189, 191, 28, 28, 133, 135, 0.03906f),
            Glyph("fri", 189, 187, 28, 28, 133, 131, 0.01562f),
            Glyph("sat", 190, 184, 28, 28, 134, 128, 0.00000f),
            Glyph("sun", 164, 181, 28, 28, 108, 125, 0.00781f),
        )

    /** 月と日のあいだの「/」。字見本に無いので組み見本から切り出している。 */
    val slash: Glyph = Glyph("slash", 156, 218, 33, 33, 90, 152, 0.00000f)
}

/**
 * 切り出した 1 字。[file] は `assets/date/<file>.png`。
 * 画像には周りのグローも入っているので、並べるときは芯（[ix] [iy] [iw] [ih]）で位置を合わせる。
 *
 * @param drop 基準の字の下端からのずれ（基準の高さに対する割合）。丸い字の食み出しぶん。
 */
internal data class Glyph(
    val file: String,
    val w: Int,
    val h: Int,
    val ix: Int,
    val iy: Int,
    val iw: Int,
    val ih: Int,
    val drop: Float,
)
