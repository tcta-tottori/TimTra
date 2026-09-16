#!/usr/bin/env python3
"""ウォッチフェイスの日付コンプリケーション用に、見本画像から字を切り出す。

入力（tools/date_font/）:
  glyph_sheet.png  字見本。1 段目に 0-9、2 段目に 月火水木金土日
  reference.png    組み見本（9/金15）。字の大きさと位置関係はここから測る

出力:
  wear/src/main/assets/date/*.png                       1 字ずつ（明るさをアルファにした RGBA）
  wear/src/main/kotlin/.../complication/DateGlyphs.kt   寸法表と配置の比率

依存: pillow, numpy, scipy。ダイヤ改正のような定期作業ではなく、
デザインを変えたときだけ実行する。

  python3 tools/date_glyphs.py
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHEET = os.path.join(ROOT, "tools/date_font/glyph_sheet.png")
REF = os.path.join(ROOT, "tools/date_font/reference.png")
ASSETS = os.path.join(ROOT, "wear/src/main/assets/date")
KT = os.path.join(ROOT, "wear/src/main/kotlin/com/kazuya/timtra/wear/complication/DateGlyphs.kt")

INK = 128.0    # これ以上の明るさを字の芯とみなす
FLOOR = 40.0   # これ以下は背景のノイズとして捨てる（切り出しの縁に四角い段差を出さない）
SOLID = 150.0  # これ以上の明るさは芯とみなして不透明にする（見本の圧縮ノイズを均す）
MARGIN = 0.22  # 切り出しに残すグローの余白（基準の高さに対する割合）
# 見本から拾った 2 色（芯 / 外へ広がるグロー）
CORE = np.array([234.0, 242.0, 255.0])
GLOW = np.array([70.0, 130.0, 235.0])
DIGITS = "0123456789"
WEEKDAYS = ["mon", "tue", "wed", "thu", "fri", "sat", "sun"]


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


def cut(rgb, lum, box, margin, name, limits=None, keep=None):
    """芯 [box] にグローの余白を足して切り出し、明るさをアルファにして書き出す。

    [limits] は切り出してよい範囲 (x0, x1)。隣の字が余白に入り込まないよう挟んでおく。
    [keep] は残す形（bool の配列、画像全体と同じ大きさ）。組み見本のように字が密で
    範囲では切り分けられないときに使う。
    """
    x0, x1, y0, y1 = box
    mx0, my0 = max(0, x0 - margin), max(0, y0 - margin)
    mx1, my1 = min(lum.shape[1], x1 + margin), min(lum.shape[0], y1 + margin)
    if limits is not None:
        mx0, mx1 = max(mx0, limits[0]), min(mx1, limits[1])
    t = np.clip((lum[my0:my1, mx0:mx1] - FLOOR) / (SOLID - FLOOR), 0.0, 1.0)
    if keep is not None:
        t = t * keep[my0:my1, mx0:mx1]
    # 見本の圧縮ノイズを持ち込まないよう、色は芯とグローの 2 色から作り直す
    tone = GLOW + (CORE - GLOW) * t[:, :, None]
    out = np.dstack([tone, t * 255.0]).astype(np.uint8)
    Image.fromarray(out, "RGBA").save(os.path.join(ASSETS, name + ".png"))
    return dict(file=name, w=int(mx1 - mx0), h=int(my1 - my0),
                ix=int(x0 - mx0), iy=int(y0 - my0), iw=int(x1 - x0), ih=int(y1 - y0), drop=0.0)


def soft_mask(lum, box, margin):
    """[box] の中の字だけを残すぼかしマスク。隣の字のグローを持ち込まないために使う。"""
    x0, x1, y0, y1 = box
    core = np.zeros(lum.shape, dtype=np.float32)
    core[y0:y1, x0:x1] = (lum[y0:y1, x0:x1] > INK).astype(np.float32)
    grown = ndimage.gaussian_filter(ndimage.binary_dilation(core > 0, iterations=margin).astype(np.float32), margin / 3.0)
    return np.clip(grown * 1.6, 0.0, 1.0)


def parts_of(lum):
    """明るい塊を左上から順に返す。組み見本の 5 つの部品を拾うのに使う。"""
    labels, _ = ndimage.label(lum > INK)
    found = []
    for sl in ndimage.find_objects(labels):
        y, x = sl
        if (x.stop - x.start) * (y.stop - y.start) < 400:
            continue
        found.append((int(x.start), int(x.stop), int(y.start), int(y.stop)))
    return found


def main():
    os.makedirs(ASSETS, exist_ok=True)
    rgb, lum = load(SHEET)
    rows = bands((lum > 40).any(axis=1), 10)
    if len(rows) != 2:
        sys.exit("字見本が 2 段に分かれていない: %s" % (rows,))

    raw, limits = {}, {}
    for (r0, r1), names in ((rows[0], list(DIGITS)), (rows[1], WEEKDAYS)):
        cols = bands((lum[r0:r1] > 40).any(axis=0), 12)
        if len(cols) != len(names):
            sys.exit("字の数が合わない: %d != %d" % (len(cols), len(names)))
        raw.update({n: ink_box(lum, c0, c1, r0, r1) for n, (c0, c1) in zip(names, cols)})
        # 隣との中間で挟む。ここまでならグローを残しても隣が入り込まない
        edges = [0] + [(cols[i][1] + cols[i + 1][0]) // 2 for i in range(len(cols) - 1)] + [lum.shape[1]]
        limits.update({n: (edges[i], edges[i + 1]) for i, n in enumerate(names)})

    # 数字は「1」、曜日は「土」を基準にする（どちらも上下が平ら）
    cap_digit = raw["1"][3] - raw["1"][2]
    cap_week = raw["sat"][3] - raw["sat"][2]
    glyphs = {}
    for name in DIGITS:
        g = cut(rgb, lum, raw[name], round(cap_digit * MARGIN), "d" + name, limits[name])
        g["drop"] = (raw[name][3] - raw["1"][3]) / cap_digit
        glyphs["d" + name] = g
    for name in WEEKDAYS:
        g = cut(rgb, lum, raw[name], round(cap_week * MARGIN), name, limits[name])
        g["drop"] = (raw[name][3] - raw["sat"][3]) / cap_week
        glyphs[name] = g

    # 組み見本「9/金15」。左から 9 / 金 と 1 5 に分かれる
    rgb2, lum2 = load(REF)
    found = parts_of(lum2)
    if len(found) != 5:
        sys.exit("組み見本の部品が 5 つでない: %d" % len(found))
    by_x = sorted(found)
    b9, bkin = sorted(by_x[:2], key=lambda b: b[2])  # 左端の 2 つ。上が 9、下が 金
    bslash, b1, b5 = by_x[2], by_x[3], by_x[4]
    slash_margin = round((bslash[3] - bslash[2]) * MARGIN)
    glyphs["slash"] = cut(rgb2, lum2, bslash, slash_margin, "slash",
                          keep=soft_mask(lum2, bslash, slash_margin))

    hd = float(b1[3] - b1[2])   # 日にちの字の高さ（基準「1」）
    day_left, day_base = b1[0], b1[3]
    # 見本の「9」「金」の高さを、基準字（1 / 土）に直す
    month_cap = (b9[3] - b9[2]) / (glyphs["d9"]["ih"] / cap_digit)
    week_cap = (bkin[3] - bkin[2]) / (glyphs["fri"]["ih"] / cap_week)
    layout = dict(
        DAY_TRACK=(b5[0] - b1[1]) / hd,
        MONTH_CAP=month_cap / hd,
        MONTH_BASELINE=(b9[3] - glyphs["d9"]["drop"] * month_cap - day_base) / hd,
        MONTH_SLASH_GAP=(bslash[0] - b9[1]) / hd,
        SLASH_HEIGHT=(bslash[3] - bslash[2]) / hd,
        SLASH_RIGHT=(bslash[1] - day_left) / hd,
        SLASH_BOTTOM=(bslash[3] - day_base) / hd,
        WEEK_CAP=week_cap / hd,
        WEEK_BASELINE=(bkin[3] - glyphs["fri"]["drop"] * week_cap - day_base) / hd,
        WEEK_RIGHT=(bkin[1] - day_left) / hd,
    )

    def kt(g):
        return ('            Glyph("%s", %d, %d, %d, %d, %d, %d, %.5ff),'
                % (g["file"], g["w"], g["h"], g["ix"], g["iy"], g["iw"], g["ih"], g["drop"]))

    lines = [
        "package com.kazuya.timtra.wear.complication",
        "",
        "/**",
        " * 日付コンプリケーションの字の寸法表と配置の比率。",
        " * `tools/date_glyphs.py` が見本画像から作る。**手で書き換えない。**",
        " *",
        " * 長さはすべて、日にちの字の高さ（基準は「1」）を 1 とした比率。",
        " * 原点は日にちの字の左下（ベースライン）で、y は下向きが正。",
        " */",
        "internal object DateGlyphs {",
        "    /** 字見本の中での基準の高さ（px）。切り出した字はこれで割って使う。 */",
        "    const val CAP_DIGIT = %.1ff" % cap_digit,
        "    const val CAP_WEEK = %.1ff" % cap_week,
        "",
    ]
    doc = {
        "DAY_TRACK": "日にちの字と字のあいだ",
        "MONTH_CAP": "月の数字の高さ",
        "MONTH_BASELINE": "月の数字のベースライン",
        "MONTH_SLASH_GAP": "月の数字と「/」のあいだ（負は食い込み）",
        "SLASH_HEIGHT": "「/」の高さ",
        "SLASH_RIGHT": "「/」の右端",
        "SLASH_BOTTOM": "「/」の下端",
        "WEEK_CAP": "曜日の字の高さ",
        "WEEK_BASELINE": "曜日の字のベースライン",
        "WEEK_RIGHT": "曜日の字の右端",
    }
    for key, value in layout.items():
        lines += ["    /** %s。 */" % doc[key], "    const val %s = %.5ff" % (key, value), ""]
    lines += [
        "    /** 0〜9。 */",
        "    val digits: List<Glyph> =",
        "        listOf(",
    ] + [kt(glyphs["d" + d]) for d in DIGITS] + [
        "        )",
        "",
        "    /** 月〜日（`java.time.DayOfWeek` の順）。 */",
        "    val weekdays: List<Glyph> =",
        "        listOf(",
    ] + [kt(glyphs[w]) for w in WEEKDAYS] + [
        "        )",
        "",
        "    /** 月と日のあいだの「/」。字見本に無いので組み見本から切り出している。 */",
        "    val slash: Glyph = %s" % kt(glyphs["slash"]).strip().rstrip(","),
        "}",
        "",
        "/**",
        " * 切り出した 1 字。[file] は `assets/date/<file>.png`。",
        " * 画像には周りのグローも入っているので、並べるときは芯（[ix] [iy] [iw] [ih]）で位置を合わせる。",
        " *",
        " * @param drop 基準の字の下端からのずれ（基準の高さに対する割合）。丸い字の食み出しぶん。",
        " */",
        "internal data class Glyph(",
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
    ]
    with open(KT, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print(json.dumps(layout, indent=1))
    print("字 %d 個を %s へ、寸法表を %s へ" % (len(glyphs), ASSETS, KT))


if __name__ == "__main__":
    main()
