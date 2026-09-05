# tools

## gtfs_import.py

GTFS-JP（日ノ丸自動車）から用瀬智頭線・南吉成・鳥取駅だけを抜き出し、
Room のプリパッケージ DB `timtra_gtfs.db` を生成する。Python 3.9 以上・標準ライブラリのみ。

```sh
# 1. ID の確認（初回・ダイヤ改正時）
python3 tools/gtfs_import.py discover tools/gtfs/hinomaru_gtfs.zip

# 2. 確認できたら設定候補を書き出して内容を確認
python3 tools/gtfs_import.py discover tools/gtfs/hinomaru_gtfs.zip --write-config tools/gtfs_config.json

# 3. DB 生成（data モジュールの assets へ。app / wear はこれを共有する）
python3 tools/gtfs_import.py build tools/gtfs/hinomaru_gtfs.zip \
    --config tools/gtfs_config.json \
    --out data/src/main/assets/timtra_gtfs.db
```

- `tools/gtfs/` に置いた ZIP は git 管理外（.gitignore）。生成した `.db` はコミットする。
- 実行時に ZIP をダウンロードする仕組みは意図的に持たない（CLAUDE.md 4-1）。
- スキーマは `docs/db_schema.md`、確定した ID は `docs/ids.md` を参照。

### テスト

```sh
python3 -m unittest discover -s tools/tests -v
```

`tools/testdata/sample_gtfs/` は合成データ（BOM 付き UTF-8、`24:15:00` の深夜便、
乗り場違いの鳥取駅、対象外路線、calendar_dates の祝日振替を含む）。実データではない。

### 開発用のサンプル DB

実データが手元にない間、アプリ側の動作確認には合成データから生成した DB を使える。

```sh
python3 tools/gtfs_import.py build tools/testdata/sample_gtfs --config tools/testdata/sample_config.json --out data/src/main/assets/timtra_gtfs.db
```

現在コミットされている `data/src/main/assets/timtra_gtfs.db` はこのサンプルから生成したもの。
`meta.feed_publisher_name` が「サンプルデータ（実データではない）」なので、ホーム画面に警告が出る。
