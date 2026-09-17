#!/usr/bin/env python3
"""見本画像から数字・曜日の字を切り出し、寸法表と配置の比率を書き出す。

入力（tools/date_font/）:
  glyph_sheet.png  字見本。1 段目 0-9、2 段目 月火水木金土日、3 段目 MON〜SUN
  reference_jp.png 組み見本「9/金15」。日本語（曜日あり）の配置はここから測る
  reference_en.png 組み見本「9/FRI15」。英語（曜日あり）の配置はここから測る

出力:
  data/src/main/assets/date/*.png                      1 字ずつ（RGBA。グロー込み）
  data/.../data/glyph/Glyphs.kt                        寸法表と配置の比率

「:」は見本に無いので、数字の大きさに合わせてここで作る。
依存: pillow, numpy, scipy。デザインを変えたときだけ実行する。

  python3 tools/date_glyphs.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHEET = os.path.join(ROOT, "tools/date_font/glyph_sheet.png")
REF_JP = os.path.join(ROOT, "tools/date_font/reference_jp.png")
REF_EN = os.path.join(ROOT, "tools/date_font/reference_en.png")
ASSETS = os.path.join(ROOT, "data/src/main/assets/date")
KT = os.path.join(ROOT, "data/src/main/kotlin/com/kazuya/timtra/data/glyph/Glyphs.kt")

INK = 128.0    # これ以上の明るさを字の芯とみなす
FLOOR = 40.0   # これ以下は背景のノイズとして捨てる（切り出しの縁に四角い段差を出さない）
SOLID = 150.0  # これ以上の明るさは芯とみなして不透明にする（見本の圧縮ノイズを均す）
MARGIN = 0.22  # 切り出しに残すグローの余白（基準の高さに対する割合）
# 見本から拾った 2 色（芯 / 外へ広がるグロー）
CORE = np.array([234.0, 242.0, 255.0])
GLOW = np.array([70.0, 130.0, 235.0])
DIGITS = "0123456789"
WEEK_JP = ["jmon", "jtue", "jwed", "jthu", "jfri", "jsat", "jsun"]
WEEK_EN = ["emon", "etue", "ewed", "ethu", "efri", "esat", "esun"]


def load(path):
    a = np.asarray(Image.open(path).convert("RGB")).astype(np.float32)
    return a, 0.299 * a[:, :, 0] + 0.587 * a[:, :, 1] + 0.114 * a[:, :, 2]


def bands(mask, min_gap):
    idx = np.where(mask)[0]
    groups, start, prev = [], idx[0], idx[0]
    for i in idx[1:]:
        if i - prev > min_gap:
            groups.append((int(start), int(prev) + 1))
            start = i
        prev = i
    groups.append((int(start), int(prev) + 1))
    return groups


def ink_box(lum, x0, x1, y0, y1):
    sub = lum[y0:y1, x0:x1] > INK
    ys, xs = np.where(sub.any(axis=1))[0], np.where(sub.any(axis=0))[0]
    return x0 + int(xs.min()), x0 + int(xs.max()) + 1, y0 + int(ys.min()), y0 + int(ys.max()) + 1


def write(t, box, margins, name):
    """芯の濃さ [t]（0〜1）を芯色とグロー色の 2 色に振り直して書き出す。"""
    tone = GLOW + (CORE - GLOW) * t[:, :, None]
    Image.fromarray(np.dstack([tone, t * 255.0]).astype(np.uint8), "RGBA").save(os.path.join(ASSETS, name + ".png"))
    x0, x1, y0, y1 = box
    mx0, my0 = margins
    return dict(file=name, w=int(t.shape[1]), h=int(t.shape[0]),
                ix=int(x0 - mx0), iy=int(y0 - my0), iw=int(x1 - x0), ih=int(y1 - y0), drop=0.0)


def cut(lum, box, margin, name, limits=None, keep=None):
    """芯 [box] にグローの余白を足して切り出す。

    [limits] は切り出してよい範囲 (x0, x1)。隣の字が余白に入り込まないよう挟んでおく。
    [keep] は残す形（画像全体と同じ大きさ）。組み見本のように字が密なときに使う。
    """
    x0, x1, y0, y1 = box
    mx0, my0 = max(0, x0 - margin), max(0, y0 - margin)
    mx1, my1 = min(lum.shape[1], x1 + margin), min(lum.shape[0], y1 + margin)
    if limits is not None:
        mx0, mx1 = max(mx0, limits[0]), min(mx1, limits[1])
    t = np.clip((lum[my0:my1, mx0:mx1] - FLOOR) / (SOLID - FLOOR), 0.0, 1.0)
    if keep is not None:
        t = t * keep[my0:my1, mx0:mx1]
    return write(t, box, (mx0, my0), name)


def soft_mask(lum, box, margin):
    """[box] の中の字だけを残すぼかしマスク。隣の字のグローを持ち込まないために使う。"""
    x0, x1, y0, y1 = box
    core = np.zeros(lum.shape, dtype=np.float32)
    core[y0:y1, x0:x1] = (lum[y0:y1, x0:x1] > INK).astype(np.float32)
    grown = ndimage.gaussian_filter(ndimage.binary_dilation(core > 0, iterations=margin).astype(np.float32), margin / 3.0)
    return np.clip(grown * 1.6, 0.0, 1.0)


def make_colon(cap):
    """「:」を数字の大きさに合わせて作る。見本に無いので丸 2 つで起こす。"""
    radius = cap * 0.105
    low, high = cap * 0.17, cap * 0.60   # ベースラインから丸の中心までの高さ
    margin = int(round(cap * MARGIN))
    ink_w, ink_h = int(round(radius * 2)), int(round(high - low + radius * 2))
    w, h = ink_w + margin * 2, ink_h + margin * 2
    ss = 4  # なめらかにするため 4 倍で描いて縮める
    big = Image.new("L", (w * ss, h * ss), 0)
    draw = ImageDraw.Draw(big)
    for center in (high, low):
        cy = margin + radius + (high - center)
        draw.ellipse(
            [margin * ss, int((cy - radius) * ss), int((margin + radius * 2) * ss), int((cy + radius) * ss)],
            fill=255,
        )
    core = np.asarray(big.resize((w, h), Image.LANCZOS)).astype(np.float32) / 255.0
    glow = ndimage.gaussian_filter(core, cap * 0.055)
    t = np.clip(np.maximum(core, glow * 1.5), 0.0, 1.0)
    return write(t, (margin, margin + ink_w, margin, margin + ink_h), (0, 0), "colon")


def parts_of(lum, min_area=300):
    labels, _ = ndimage.label(lum > INK)
    found = []
    for sl in ndimage.find_objects(labels):
        y, x = sl
        if (x.stop - x.start) * (y.stop - y.start) < min_area:
            continue
        found.append((int(x.start), int(x.stop), int(y.start), int(y.stop)))
    return sorted(found)


def union(boxes):
    return (min(b[0] for b in boxes), max(b[1] for b in boxes),
            min(b[2] for b in boxes), max(b[3] for b in boxes))


def main():
    os.makedirs(ASSETS, exist_ok=True)
    rgb, lum = load(SHEET)
    rows = bands((lum > 40).any(axis=1), 10)
    if len(rows) != 3:
        sys.exit("字見本が 3 段に分かれていない: %s" % (rows,))

    raw, limits = {}, {}
    for (r0, r1), names in zip(rows, (list(DIGITS), WEEK_JP, WEEK_EN)):
        cols = bands((lum[r0:r1] > 40).any(axis=0), 12)
        if len(cols) != len(names):
            sys.exit("字の数が合わない: %d != %d (%s)" % (len(cols), len(names), names[0]))
        raw.update({n: ink_box(lum, c0, c1, r0, r1) for n, (c0, c1) in zip(names, cols)})
        edges = [0] + [(cols[i][1] + cols[i + 1][0]) // 2 for i in range(len(cols) - 1)] + [lum.shape[1]]
        limits.update({n: (edges[i], edges[i + 1]) for i, n in enumerate(names)})

    # 基準の字。数字は「1」、日本語の曜日は「土」、英語の曜日は「SAT」（どれも上下が平ら）
    caps = {"d": raw["1"][3] - raw["1"][2], "j": raw["jsat"][3] - raw["jsat"][2], "e": raw["esat"][3] - raw["esat"][2]}
    bases = {"d": raw["1"][3], "j": raw["jsat"][3], "e": raw["esat"][3]}
    glyphs = {}
    for names, kind, prefix in ((list(DIGITS), "d", "d"), (WEEK_JP, "j", ""), (WEEK_EN, "e", "")):
        for name in names:
            key = prefix + name
            g = cut(lum, raw[name], round(caps[kind] * MARGIN), key, limits[name])
            g["drop"] = (raw[name][3] - bases[kind]) / caps[kind]
            glyphs[key] = g
    glyphs["colon"] = make_colon(caps["d"])
    glyphs["colon"]["drop"] = -(caps["d"] * 0.065) / caps["d"]

    def layout_from(path, week_names, week_kind, cut_slash):
        """組み見本から配置の比率を測る。原点は日にちの字の左下、単位はその高さ。"""
        _, ref = load(path)
        found = parts_of(ref)
        day = found[-2:]                       # いちばん右の 2 つが日にち「15」
        b1, b5 = day
        hd = float(b1[3] - b1[2])
        day_left, day_base = b1[0], b1[3]
        rest = found[:-2]
        top = [b for b in rest if b[2] < b1[2] + hd * 0.3]      # 上の段: 9 と /
        bottom = [b for b in rest if b not in top]              # 下の段: 曜日
        b9, bslash = sorted(top)[0], union(sorted(top)[1:])
        bweek = union(bottom)
        if cut_slash:
            margin = round((bslash[3] - bslash[2]) * MARGIN)
            glyphs["slash"] = cut(ref, bslash, margin, "slash", keep=soft_mask(ref, bslash, margin))
        month_cap = (b9[3] - b9[2]) / (glyphs["d9"]["ih"] / caps["d"])
        week_cap = (bweek[3] - bweek[2]) / (glyphs[week_names[4]]["ih"] / caps[week_kind])
        return dict(
            dayTrack=(b5[0] - b1[1]) / hd,
            monthCap=month_cap / hd,
            monthBaseline=(b9[3] - glyphs["d9"]["drop"] * month_cap - day_base) / hd,
            monthSlashGap=(bslash[0] - b9[1]) / hd,
            slashHeight=(bslash[3] - bslash[2]) / hd,
            slashRight=(bslash[1] - day_left) / hd,
            slashBottom=(bslash[3] - day_base) / hd,
            weekCap=week_cap / hd,
            weekBaseline=(bweek[3] - glyphs[week_names[4]]["drop"] * week_cap - day_base) / hd,
            weekRight=(bweek[1] - day_left) / hd,
        )

    # 「/」は日本語の組み見本から抜く（英語のほうは日にちと近く、抜きにくい）
    jp = layout_from(REF_JP, WEEK_JP, "j", cut_slash=True)
    en = layout_from(REF_EN, WEEK_EN, "e", cut_slash=False)

    def kt(g):
        return ('            Glyph("%s", %d, %d, %d, %d, %d, %d, %.5ff),'
                % (g["file"], g["w"], g["h"], g["ix"], g["iy"], g["iw"], g["ih"], g["drop"]))

    def layout_kt(name, v):
        return ("    val %s: DateLayout =\n        DateLayout(\n" % name) + "".join(
            "            %s = %.5ff,\n" % (k, x) for k, x in v.items()) + "        )"

    lines = [
        "package com.kazuya.timtra.data.glyph",
        "",
        "/**",
        " * 見本画像から切り出した字の寸法表と、日付の配置の比率。",
        " * `tools/date_glyphs.py` が作る。**手で書き換えない。**",
        " *",
        " * 配置の長さはすべて、日にちの字の高さ（基準は「1」）を 1 とした比率。",
        " * 原点は日にちの字の左下（ベースライン）で、y は下向きが正。",
        " */",
        "object Glyphs {",
        "    /** 字見本の中での基準の高さ（px）。切り出した字はこれで割って使う。 */",
        "    const val CAP_DIGIT = %.1ff" % caps["d"],
        "    const val CAP_WEEK_JP = %.1ff" % caps["j"],
        "    const val CAP_WEEK_EN = %.1ff" % caps["e"],
        "",
        "    /** 数字 1 行ぶんの高さ（基準の高さに対する割合）。はみ出すぶんを見込む。 */",
        "    const val DIGIT_LINE = %.5ff" % (max(glyphs["d" + d]["ih"] for d in DIGITS) / caps["d"]),
        "",
        "    /** 0〜9。 */",
        "    val digits: List<Glyph> =",
        "        listOf(",
    ] + [kt(glyphs["d" + d]) for d in DIGITS] + [
        "        )",
        "",
        "    /** 月〜日（`java.time.DayOfWeek` の順）。 */",
        "    val weekdaysJp: List<Glyph> =",
        "        listOf(",
    ] + [kt(glyphs[w]) for w in WEEK_JP] + [
        "        )",
        "",
        "    /** MON〜SUN（`java.time.DayOfWeek` の順）。 */",
        "    val weekdaysEn: List<Glyph> =",
        "        listOf(",
    ] + [kt(glyphs[w]) for w in WEEK_EN] + [
        "        )",
        "",
        "    /** 月と日のあいだの「/」。字見本に無いので組み見本から切り出している。 */",
        "    val slash: Glyph = %s" % kt(glyphs["slash"]).strip().rstrip(","),
        "",
        "    /** 残り時間の「:」。見本に無いので数字の大きさに合わせて起こしている。 */",
        "    val colon: Glyph = %s" % kt(glyphs["colon"]).strip().rstrip(","),
        "",
        "    /** 日本語（曜日あり）の配置。曜日なしもこれを使う。 */",
        layout_kt("jp", jp),
        "",
        "    /** 英語（曜日あり）の配置。 */",
        layout_kt("en", en),
        "}",
        "",
        "/**",
        " * 切り出した 1 字。[file] は `assets/date` の中の PNG。",
        " * 画像には周りのグローも入っているので、並べるときは芯（[ix] [iy] [iw] [ih]）で位置を合わせる。",
        " *",
        " * @param drop 基準の字の下端からのずれ（基準の高さに対する割合）。丸い字の食み出しぶん。",
        " */",
        "data class Glyph(",
        "    val file: String,",
        "    val w: Int,",
        "    val h: Int,",
        "    val ix: Int,",
        "    val iy: Int,",
        "    val iw: Int,",
        "    val ih: Int,",
        "    val drop: Float,",
        ")",
        "",
        "/** 日付の組み方。長さは日にちの字の高さを 1 とした比率。 */",
        "data class DateLayout(",
        "    /** 日にちの字と字のあいだ。 */",
        "    val dayTrack: Float,",
        "    /** 月の数字の高さ。 */",
        "    val monthCap: Float,",
        "    /** 月の数字のベースライン。 */",
        "    val monthBaseline: Float,",
        "    /** 月の数字と「/」のあいだ（負は食い込み）。 */",
        "    val monthSlashGap: Float,",
        "    /** 「/」の高さ。 */",
        "    val slashHeight: Float,",
        "    /** 「/」の右端。 */",
        "    val slashRight: Float,",
        "    /** 「/」の下端。 */",
        "    val slashBottom: Float,",
        "    /** 曜日の字の高さ。 */",
        "    val weekCap: Float,",
        "    /** 曜日の字のベースライン。 */",
        "    val weekBaseline: Float,",
        "    /** 曜日の字の右端。 */",
        "    val weekRight: Float,",
        ")",
        "",
    ]
    os.makedirs(os.path.dirname(KT), exist_ok=True)
    with open(KT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(json.dumps(dict(caps=caps, jp=jp, en=en), indent=1))
    print("字 %d 個を %s へ、寸法表を %s へ" % (len(glyphs), ASSETS, KT))


if __name__ == "__main__":
    main()
