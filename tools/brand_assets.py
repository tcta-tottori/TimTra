#!/usr/bin/env python3
"""ロゴ（文字）とアプリアイコンを、渡された絵から各サイズに焼き直す。

入力（tools/brand/）:
  wordmark.png  「TimTra」の文字ロゴ。白の「Tim」＋水色の「Tra」。輪郭のノイズはここで落とす
  icon.png      アプリアイコン（角丸の青い四角にバスと電車と「TimTra」）。白い余白込みでよい

出力:
  app/src/main/res/drawable-nodpi/logo_timtra.png    ホームのヘッダーに出す文字ロゴ
  app|wear/src/main/res/mipmap-*/ic_launcher*.png    ランチャーアイコン（旧式・丸・前景）
  app|wear/src/main/res/drawable/ic_launcher_background.xml  地の青のグラデーション
  docs/assets/icon-512.png                           資料用

アイコンは Android の adaptive icon に合わせて **地（グラデーション）と絵（バス・電車・文字）に分ける**。
絵は安全圏（108dp のうち中央 72dp）に収める。旧式の四角・丸アイコンは元絵をそのまま型で抜く。

デザインを変えたときだけ実行する。依存: pillow, numpy, scipy。

  python3 tools/brand_assets.py
"""
import os

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_WORDMARK = os.path.join(ROOT, "tools/brand/wordmark.png")
SRC_ICON = os.path.join(ROOT, "tools/brand/icon.png")

# ランチャーアイコンの寸法（mdpi を 1 として）
DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
LEGACY_DP = 48      # 旧式アイコンの一辺（dp）
ADAPTIVE_DP = 108   # adaptive icon の一辺（dp）
SAFE_DP = 72        # そのうち必ず見える中央（dp）
CORNER = 0.22       # 旧式アイコンの角丸（一辺に対する割合）
ART_RED = 50        # これより赤が強ければ絵（白・水色）、弱ければ地の青

# 文字ロゴの手当て
WORDMARK_HEIGHT = 320   # 書き出す高さ（px）。ヘッダーは 26dp なので 4 倍でも足りる
SPECK = 600             # これより小さいかたまり・穴はノイズとして均す（元絵の画素数）
CLOSE = 9               # 欠けを埋める半径（元絵の画素）


def disk(radius):
    y, x = np.ogrid[-radius : radius + 1, -radius : radius + 1]
    return x * x + y * y <= radius * radius


def clean_mask(mask):
    """しきい値で拾った形のノイズを落とす。欠けを埋め、粒を消し、小さな穴を塞ぐ。"""
    mask = ndimage.binary_closing(mask, structure=disk(CLOSE))
    mask = ndimage.binary_opening(mask, structure=disk(3))
    labels, count = ndimage.label(mask)
    if count:
        sizes = ndimage.sum(mask, labels, range(1, count + 1))
        mask = np.isin(labels, [i + 1 for i, s in enumerate(sizes) if s >= SPECK])
    holes = ndimage.binary_fill_holes(mask) & ~mask
    labels, count = ndimage.label(holes)
    if count:
        sizes = ndimage.sum(holes, labels, range(1, count + 1))
        small = np.isin(labels, [i + 1 for i, s in enumerate(sizes) if s < SPECK * 8])
        mask = mask | small
    return mask


def wordmark():
    """文字ロゴ。輪郭のノイズを落とし、白と水色の 2 色で塗り直す。"""
    src = np.asarray(Image.open(SRC_WORDMARK).convert("RGBA")).astype(np.float32)
    mask = clean_mask(src[:, :, 3] > 128)
    labels, count = ndimage.label(mask)
    out = np.zeros(src.shape, dtype=np.float32)
    for index in range(1, count + 1):
        part = labels == index
        rgb = src[:, :, :3][part]
        # 元絵の色で白（Tim）と水色（Tra）に振り分け、字ごとに 1 色で塗る
        tone = (255.0, 255.0, 255.0) if rgb[:, 2].mean() - rgb[:, 0].mean() < 60 else (75.0, 207.0, 252.0)
        out[part] = (*tone, 255.0)
    ys, xs = np.where(mask)
    box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    image = Image.fromarray(out.astype(np.uint8), "RGBA").crop(box)
    height = WORDMARK_HEIGHT
    width = round(image.width * height / image.height)
    image = image.resize((width, height), Image.LANCZOS)
    path = os.path.join(ROOT, "app/src/main/res/drawable-nodpi/logo_timtra.png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    image.save(path)
    print("文字ロゴ %d×%d → %s" % (width, height, path))


def icon_square():
    """アイコンの青い四角だけを切り出す（白い余白と影を落とす）。"""
    src = np.asarray(Image.open(SRC_ICON).convert("RGB")).astype(np.float32)
    blue = (src[:, :, 2] > src[:, :, 0] + 40) & (src[:, :, 2] > 80)
    ys, xs = np.where(blue)
    side = min(int(xs.max()) + 1 - int(xs.min()), int(ys.max()) + 1 - int(ys.min()))
    x0, y0 = int(xs.min()), int(ys.min())
    return src[y0 : y0 + side, x0 : x0 + side]


def icon_art(square):
    """地の青を外して、バス・電車・文字だけを残す。

    地の青は赤がほとんど無い（R < 30）。白い絵も水色の「Tra」もそれより赤が強いので、赤で分けられる。
    窓やライトは形の内側なので、穴を埋めてから抜けばそのまま残る。
    外枠（元絵の白い縁）は囲みの端に触れるので、そこだけ落とす。
    """
    art = ndimage.binary_fill_holes(square[:, :, 0] > ART_RED)
    labels, count = ndimage.label(art)
    edge = set(labels[0]) | set(labels[-1]) | set(labels[:, 0]) | set(labels[:, -1])
    sizes = ndimage.sum(art, labels, range(1, count + 1))
    art = np.isin(labels, [i + 1 for i, s in enumerate(sizes) if s >= 50 and (i + 1) not in edge])
    rgba = np.dstack([square, art.astype(np.float32) * 255.0])
    return Image.fromarray(rgba.astype(np.uint8), "RGBA"), art


def rounded(size, radius):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def circle(size):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def gradient_ends(square):
    """地の青の、左上と右下の色。絵と白い縁を外し、対角の両端の帯の中央値から伸ばして求める。"""
    side = square.shape[0]
    ys, xs = np.mgrid[0:side, 0:side]
    along = (ys + xs) / (2.0 * (side - 1))
    field = (square[:, :, 2] > square[:, :, 0] + 40) & (square[:, :, 0] < ART_RED)
    near = field & (along < 0.15)
    far = field & (along > 0.85)
    first, last = np.median(square[near], axis=0), np.median(square[far], axis=0)
    u0, u1 = along[near].mean(), along[far].mean()
    slope = (last - first) / (u1 - u0)
    return np.clip(first - slope * u0, 0, 255), np.clip(first + slope * (1.0 - u0), 0, 255)


def gradient_xml(square):
    """地の青。左上から右下へのグラデーションを元絵から拾って、ベクタに焼く。"""
    start, end = gradient_ends(square)
    hex_of = lambda c: "#FF%02X%02X%02X" % (int(round(c[0])), int(round(c[1])), int(round(c[2])))
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- tools/brand_assets.py が作る。手で書き換えない。 -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    xmlns:aapt="http://schemas.android.com/aapt"\n'
        '    android:width="108dp"\n'
        '    android:height="108dp"\n'
        '    android:viewportWidth="108"\n'
        '    android:viewportHeight="108">\n'
        '    <path android:pathData="M0,0h108v108h-108z">\n'
        '        <aapt:attr name="android:fillColor">\n'
        "            <gradient\n"
        '                android:endX="108"\n'
        '                android:endY="108"\n'
        '                android:startX="0"\n'
        '                android:startY="0"\n'
        '                android:type="linear">\n'
        '                <item android:color="%s" android:offset="0" />\n'
        '                <item android:color="%s" android:offset="1" />\n'
        "            </gradient>\n"
        "        </aapt:attr>\n"
        "    </path>\n"
        "</vector>\n" % (hex_of(start), hex_of(end))
    )


def foreground(art_image, art_mask, size):
    """adaptive icon の前景。絵の囲みが安全圏（中央 72/108）に収まるように置く。"""
    ys, xs = np.where(art_mask)
    box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
    art = art_image.crop(box)
    safe = size * SAFE_DP / ADAPTIVE_DP
    scale = min(safe / art.width, safe / art.height)
    art = art.resize((max(1, round(art.width * scale)), max(1, round(art.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(art, ((size - art.width) // 2, (size - art.height) // 2))
    return canvas


def launcher(square, art_image, art_mask):
    full = Image.fromarray(square.astype(np.uint8), "RGB").convert("RGBA")
    for module in ("app", "wear"):
        base = os.path.join(ROOT, module, "src/main/res")
        path = os.path.join(base, "drawable/ic_launcher_background.xml")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w") as f:
            f.write(gradient_xml(square))
        for name, factor in DENSITIES.items():
            out = os.path.join(base, "mipmap-" + name)
            os.makedirs(out, exist_ok=True)
            legacy = round(LEGACY_DP * factor)
            shaped = full.resize((legacy, legacy), Image.LANCZOS)
            square_icon = shaped.copy()
            square_icon.putalpha(rounded(legacy, round(legacy * CORNER)))
            square_icon.save(os.path.join(out, "ic_launcher.png"))
            round_icon = shaped.copy()
            round_icon.putalpha(circle(legacy))
            round_icon.save(os.path.join(out, "ic_launcher_round.png"))
            foreground(art_image, art_mask, round(ADAPTIVE_DP * factor)).save(
                os.path.join(out, "ic_launcher_foreground.png")
            )
    doc = os.path.join(ROOT, "docs/assets/icon-512.png")
    docs_icon = full.resize((512, 512), Image.LANCZOS)
    docs_icon.putalpha(rounded(512, round(512 * CORNER)))
    docs_icon.save(doc)
    print("アイコン → app / wear の mipmap-* と %s" % doc)


def main():
    wordmark()
    square = icon_square()
    art_image, art_mask = icon_art(square)
    launcher(square, art_image, art_mask)


if __name__ == "__main__":
    main()
