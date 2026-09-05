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

ZIP=$(python3 -c "import json;print(json.load(open('build/gtfs/links.json')).get('gtfs_zip') or '')")
RT=$(python3 -c "import json;print(json.load(open('build/gtfs/links.json')).get('rt_vehicle_positions') or '')")

{
  echo "# GTFS 取得レポート（CI 自動生成）"
  echo
  echo "- 取得日時: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "- ポータル: $PORTAL"
  echo "- GTFS-JP ZIP: ${ZIP:-（見つからず）}"
  echo "- GTFS-RT VehiclePosition: ${RT:-（見つからず）}"
  echo
  echo "## リンク候補"
  echo
  echo '```json'
  cat build/gtfs/links.json
  echo '```'
} > docs/gtfs_fetch_report.md

if [ -z "$ZIP" ]; then
  echo "GTFS-JP の ZIP が見つかりませんでした。docs/gtfs_fetch_report.md の候補を確認してください。"
  exit 2
fi

echo "== ZIP 取得: $ZIP"
curl -fsSL -A "Mozilla/5.0 (TimTra fetch)" "$ZIP" -o tools/gtfs/hinomaru_gtfs.zip
ls -la tools/gtfs/hinomaru_gtfs.zip
python3 -c "import zipfile;z=zipfile.ZipFile('tools/gtfs/hinomaru_gtfs.zip');print('\n'.join(z.namelist()))"

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
