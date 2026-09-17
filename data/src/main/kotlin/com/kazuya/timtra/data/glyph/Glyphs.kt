package com.kazuya.timtra.data.glyph

/**
 * 見本画像から切り出した字の寸法表と、日付の配置の比率。
 * `tools/date_glyphs.py` が作る。**手で書き換えない。**
 *
 * 配置の長さはすべて、日にちの字の高さ（基準は「1」）を 1 とした比率。
 * 原点は日にちの字の左下（ベースライン）で、y は下向きが正。
 */
object Glyphs {
    /** 字見本の中での基準の高さ（px）。切り出した字はこれで割って使う。 */
    const val CAP_DIGIT = 181.0f
    const val CAP_WEEK_JP = 133.0f
    const val CAP_WEEK_EN = 66.0f

    /** 数字 1 行ぶんの高さ（基準の高さに対する割合）。はみ出すぶんを見込む。 */
    const val DIGIT_LINE = 1.02210f

    /** 0〜9。 */
    val digits: List<Glyph> =
        listOf(
            Glyph("d0", 172, 263, 40, 40, 99, 183, 0.00552f),
            Glyph("d1", 136, 261, 33, 40, 63, 181, 0.00000f),
            Glyph("d2", 175, 261, 39, 40, 102, 181, -0.00552f),
            Glyph("d3", 165, 263, 33, 40, 98, 183, 0.00552f),
            Glyph("d4", 186, 263, 33, 40, 115, 183, 0.00552f),
            Glyph("d5", 178, 263, 37, 40, 104, 183, 0.01105f),
            Glyph("d6", 171, 265, 38, 40, 101, 185, 0.01105f),
            Glyph("d7", 176, 262, 34, 40, 108, 182, 0.00552f),
            Glyph("d8", 166, 264, 33, 40, 100, 184, 0.00552f),
            Glyph("d9", 173, 265, 34, 40, 99, 185, 0.01105f),
        )

    /** 月〜日（`java.time.DayOfWeek` の順）。 */
    val weekdaysJp: List<Glyph> =
        listOf(
            Glyph("jmon", 183, 191, 29, 29, 125, 133, 0.03008f),
            Glyph("jtue", 189, 195, 29, 29, 131, 137, 0.02256f),
            Glyph("jwed", 196, 195, 29, 29, 138, 137, 0.01504f),
            Glyph("jthu", 192, 198, 29, 29, 134, 140, 0.04511f),
            Glyph("jfri", 196, 196, 29, 29, 138, 138, 0.00000f),
            Glyph("jsat", 195, 191, 29, 29, 137, 133, 0.00000f),
            Glyph("jsun", 171, 187, 29, 29, 113, 129, 0.01504f),
        )

    /** MON〜SUN（`java.time.DayOfWeek` の順）。 */
    val weekdaysEn: List<Glyph> =
        listOf(
            Glyph("emon", 176, 96, 15, 15, 146, 66, 0.00000f),
            Glyph("etue", 154, 95, 15, 15, 124, 65, 0.00000f),
            Glyph("ewed", 178, 94, 15, 15, 148, 64, -0.01515f),
            Glyph("ethu", 159, 95, 15, 15, 129, 65, 0.00000f),
            Glyph("efri", 133, 94, 15, 15, 103, 64, -0.01515f),
            Glyph("esat", 153, 96, 15, 15, 123, 66, 0.00000f),
            Glyph("esun", 158, 95, 15, 15, 128, 65, 0.00000f),
        )

    /** 月と日のあいだの「/」。字見本に無いので組み見本から切り出している。 */
    val slash: Glyph = Glyph("slash", 156, 218, 33, 33, 90, 152, 0.00000f)

    /** 残り時間の「:」。見本に無いので数字の大きさに合わせて起こしている。 */
    val colon: Glyph = Glyph("colon", 118, 196, 40, 40, 38, 116, -0.06500f)

    /** 日本語（曜日あり）の配置。曜日なしもこれを使う。 */
    val jp: DateLayout =
        DateLayout(
            dayTrack = 0.07360f,
            monthCap = 0.40973f,
            monthBaseline = -0.73803f,
            monthSlashGap = -0.04569f,
            slashHeight = 0.38579f,
            slashRight = 0.04569f,
            slashBottom = -0.70812f,
            weekCap = 0.38159f,
            weekBaseline = -0.23350f,
            weekRight = -0.01015f,
        )

    /** 英語（曜日あり）の配置。 */
    val en: DateLayout =
        DateLayout(
            dayTrack = 0.08413f,
            monthCap = 0.35043f,
            monthBaseline = -0.65772f,
            monthSlashGap = -0.03365f,
            slashHeight = 0.35817f,
            slashRight = -0.03606f,
            slashBottom = -0.62260f,
            weekCap = 0.27021f,
            weekBaseline = -0.22187f,
            weekRight = -0.03125f,
        )
}

/**
 * 切り出した 1 字。[file] は `assets/date` の中の PNG。
 * 画像には周りのグローも入っているので、並べるときは芯（[ix] [iy] [iw] [ih]）で位置を合わせる。
 *
 * @param drop 基準の字の下端からのずれ（基準の高さに対する割合）。丸い字の食み出しぶん。
 */
data class Glyph(
    val file: String,
    val w: Int,
    val h: Int,
    val ix: Int,
    val iy: Int,
    val iw: Int,
    val ih: Int,
    val drop: Float,
)

/** 日付の組み方。長さは日にちの字の高さを 1 とした比率。 */
data class DateLayout(
    /** 日にちの字と字のあいだ。 */
    val dayTrack: Float,
    /** 月の数字の高さ。 */
    val monthCap: Float,
    /** 月の数字のベースライン。 */
    val monthBaseline: Float,
    /** 月の数字と「/」のあいだ（負は食い込み）。 */
    val monthSlashGap: Float,
    /** 「/」の高さ。 */
    val slashHeight: Float,
    /** 「/」の右端。 */
    val slashRight: Float,
    /** 「/」の下端。 */
    val slashBottom: Float,
    /** 曜日の字の高さ。 */
    val weekCap: Float,
    /** 曜日の字のベースライン。 */
    val weekBaseline: Float,
    /** 曜日の字の右端。 */
    val weekRight: Float,
)
