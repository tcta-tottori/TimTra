#!/usr/bin/env python3
"""ポータルの HTML から日ノ丸自動車の GTFS-JP ZIP と GTFS-RT の URL 候補を抜き出す。

使い方: find_gtfs_links.py <html> <page_url> [<gtfs_source.json>]
出力: JSON（gtfs_zip / rt_vehicle_positions / all_candidates）。人が確認できるよう候補は全部出す。
"""
import html
import json
import re
import sys
from urllib.parse import urljoin


def main() -> int:
    path, page_url = sys.argv[1], sys.argv[2]
    cfg = json.load(open(sys.argv[3], encoding="utf-8")) if len(sys.argv) > 3 else {}
    ops = cfg.get("operator_patterns", ["日ノ丸", "hinomaru"])
    static_hints = cfg.get("static_hints", ["gtfs", ".zip"])
    rt_hints = cfg.get("realtime_hints", ["vehicle", ".pb", "gtfs-rt"])
    text = open(path, encoding="utf-8", errors="replace").read()

    # <a ...>text</a> を href とテキスト、前後 200 文字の文脈込みで拾う
    links = []
    for m in re.finditer(r'<a\b[^>]*?href\s*=\s*["\']([^"\']+)["\'][^>]*>(.*?)</a>', text, re.I | re.S):
        href = html.unescape(m.group(1).strip())
        label = re.sub(r"<[^>]+>", "", html.unescape(m.group(2))).strip()
        ctx = re.sub(r"<[^>]+>", " ", text[max(0, m.start() - 300): m.end() + 100])
        links.append({"url": urljoin(page_url, href), "text": label, "context": " ".join(ctx.split())[:300]})

    def has(s, pats):
        return any(p.lower() in s.lower() for p in pats)

    def score(link, hints):
        blob = link["url"] + " " + link["text"] + " " + link["context"]
        s = 0
        if has(link["url"] + " " + link["text"], ops):
            s += 4
        elif has(link["context"], ops):
            s += 2
        if has(link["url"] + " " + link["text"], hints):
            s += 2
        return s

    zips = [l for l in links if l["url"].lower().endswith(".zip") or has(l["url"], ["gtfs"])]
    rts = [l for l in links if has(l["url"] + " " + l["text"], rt_hints) and not l["url"].lower().endswith(".zip")]
    zips.sort(key=lambda l: -score(l, static_hints))
    rts.sort(key=lambda l: -score(l, rt_hints))
    best_zip = next((l["url"] for l in zips if score(l, static_hints) >= 4), None)
    best_rt = next((l["url"] for l in rts if score(l, rt_hints) >= 4), None)
    # 鳥取県のポータルは表形式で、同じ行に「GTFS-JP データ」「GTFS-RT データ」のリンクが並ぶ。
    # RT のリンクは URL にヒントが無いので、選んだ ZIP の直後にある ZIP でないリンクを候補にする。
    if best_rt is None and best_zip is not None:
        idx = next((i for i, l in enumerate(links) if l["url"] == best_zip), None)
        if idx is not None:
            for l in links[idx + 1: idx + 4]:
                if not l["url"].lower().endswith(".zip") and l["text"] and not has(l["url"], ["#", "mailto:"]):
                    best_rt = l["url"]
                    break
    out = {
        "page_url": page_url,
        "link_count": len(links),
        "all_links": [{"url": l["url"], "text": l["text"]} for l in links],
        "gtfs_zip": best_zip,
        "rt_vehicle_positions": best_rt,
        "zip_candidates": [
            {"url": l["url"], "text": l["text"], "score": score(l, static_hints), "context": l["context"][:200]} for l in zips[:20]
        ],
        "rt_candidates": [{"url": l["url"], "text": l["text"], "score": score(l, rt_hints)} for l in rts[:20]],
        # <a> になっていない URL（本文に書かれた GTFS-RT のエンドポイントなど）
        "raw_urls": sorted(set(re.findall(r"https?://[^\s\"'<>]+", html.unescape(text)))),
        # リアルタイム関連の記述行（URL がテキストで書かれている場合の手がかり）
        "realtime_text": [
            " ".join(seg.split())[:300]
            for seg in re.split(r"<(?:p|li|tr|div|br)[^>]*>", text, flags=re.I)
            if has(seg, ["リアルタイム", "GTFS-RT", "GTFSリアルタイム", "VehiclePosition", "車両位置"])
        ][:20],
    }
    print(json.dumps(out, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
