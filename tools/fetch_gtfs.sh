#!/usr/bin/env bash
# 鳥取県オープンデータから日ノ丸自動車の GTFS-JP を取得し、ID を調べ、プリパッケージ DB を作る。
# CI（GitHub Actions）で動かす前提。結果は build/gtfs/ と docs/gtfs_fetch_report.md に残す。
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p tools/gtfs build/gtfs
PORTAL=$(python3 -c "import json;print(json.load(open('tools/gtfs_source.json'))['portal_url'])")

echo "== ポータル取得: $PORTAL"
curl -fsSL -A "Mozilla/5.0 (TimTra fetch)" "$PORTAL" -o build/gtfs/bus.html
python3 tools/find_gtfs_links.py build/gtfs/bus.html "$PORTAL" tools/gtfs_source.json | tee build/gtfs/links.json

# ZIP 候補を順に落とし、agency.txt に事業者名（日ノ丸）が入っているものを採用する。
# ページの表からの推定は取り違えることがあるため、中身で確認する。
ZIP=""
RT=""
for CAND in $(python3 -c "import json;d=json.load(open('build/gtfs/links.json'));print(' '.join(c['url'] for c in d['zip_candidates'][:8]))"); do
  echo "== ZIP 候補を確認: $CAND"
  if ! curl -fsSL -A "Mozilla/5.0 (TimTra fetch)" "$CAND" -o build/gtfs/candidate.zip; then echo "  取得失敗"; continue; fi
  if python3 - "$CAND" <<'PYCHK'
import json, sys, zipfile
cfg = json.load(open("tools/gtfs_source.json", encoding="utf-8"))
ops = cfg["operator_patterns"]
try:
    z = zipfile.ZipFile("build/gtfs/candidate.zip")
    names = [n for n in z.namelist() if n.endswith("agency.txt")]
    text = z.read(names[0]).decode("utf-8-sig") if names else ""
except Exception as e:  # noqa: BLE001
    print("  ZIP として読めない:", e); sys.exit(1)
print("  agency.txt:", " ".join(text.split())[:200])
sys.exit(0 if any(o.lower() in text.lower() for o in ops) else 1)
PYCHK
  then
    ZIP="$CAND"
    mv build/gtfs/candidate.zip tools/gtfs/hinomaru_gtfs.zip
    # 同じ行の GTFS-RT リンク = 採用した ZIP の直後にある ZIP でないリンク
    RT=$(python3 -c "
import json,sys
d=json.load(open('build/gtfs/links.json')); links=d['all_links']; zip=sys.argv[1]
idx=next((i for i,l in enumerate(links) if l['url']==zip), None)
cands=[l['url'] for l in links[idx+1:idx+4] if idx is not None and not l['url'].lower().endswith('.zip') and l['text']]
print(cands[0] if cands else '')" "$ZIP")
    break
  else
    echo "  事業者が一致しないので次の候補へ"
  fi
done

{
  echo "# GTFS 取得レポート（CI 自動生成）"
  echo
  echo "- 取得日時: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- ポータル: $PORTAL"
  echo "- GTFS-JP ZIP（agency.txt で事業者を確認済み）: ${ZIP:-（見つからず）}"
  echo "- GTFS-RT VehiclePosition（同じ行のリンクから推定。要確認）: ${RT:-（見つからず）}"
  echo
  echo "## リンク候補"
  echo
  echo '```json'
  cat build/gtfs/links.json
  echo '```'
} > docs/gtfs_fetch_report.md

if [ -z "$ZIP" ]; then
  echo "日ノ丸自動車の GTFS-JP が見つかりませんでした。docs/gtfs_fetch_report.md の候補を確認してください。"
  exit 2
fi

echo "== 採用: $ZIP"
ls -la tools/gtfs/hinomaru_gtfs.zip
python3 -c "import zipfile;z=zipfile.ZipFile('tools/gtfs/hinomaru_gtfs.zip');print('\n'.join(z.namelist()))"

# RT の URL が推定できたら、中身が protobuf の FeedMessage か軽く確認してレポートに残す
if [ -n "$RT" ]; then
  if curl -fsSL -A "Mozilla/5.0 (TimTra fetch)" "$RT" -o build/gtfs/rt_probe.bin; then
    python3 - <<'PYRT' >> docs/gtfs_fetch_report.md || true
import pathlib
b = pathlib.Path("build/gtfs/rt_probe.bin").read_bytes()
head = b[:200]
looks_pb = len(b) > 0 and b[0] == 0x0A and b"<html" not in head.lower()
print()
print("## GTFS-RT の応答確認")
print()
print(f"- サイズ: {len(b)} bytes")
print(f"- protobuf FeedMessage らしいか: {'はい' if looks_pb else 'いいえ（HTML か別形式）'}")
print(f"- 先頭: {head[:80]!r}")
PYRT
  else
    printf "\n## GTFS-RT の応答確認\n\n- 取得失敗（HTTP エラー）\n" >> docs/gtfs_fetch_report.md
  fi
fi

echo "== discover"
python3 tools/gtfs_import.py discover tools/gtfs/hinomaru_gtfs.zip --config tools/gtfs_config.json \
  --write-config build/gtfs/gtfs_config.suggested.json | tee build/gtfs/discover.txt

{
  echo
  echo "## discover の出力"
  echo
  echo '```'
  cat build/gtfs/discover.txt
  echo '```'
} >> docs/gtfs_fetch_report.md

echo "== build"
if python3 tools/gtfs_import.py build tools/gtfs/hinomaru_gtfs.zip --config tools/gtfs_config.json \
     --out data/src/main/assets/timtra_gtfs.db 2>&1 | tee build/gtfs/build.txt; then
  {
    echo
    echo "## build の出力"
    echo
    echo '```'
    cat build/gtfs/build.txt
    echo '```'
  } >> docs/gtfs_fetch_report.md
  echo "DB を更新しました: data/src/main/assets/timtra_gtfs.db"
else
  {
    echo
    echo "## build は失敗（設定を見直す）"
    echo
    echo '```'
    cat build/gtfs/build.txt
    echo '```'
  } >> docs/gtfs_fetch_report.md
  echo "build に失敗。tools/gtfs_config.json を discover の結果に合わせてください。"
  exit 3
fi
