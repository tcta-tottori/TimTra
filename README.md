# TimTra

日ノ丸バス（用瀬智頭線）と JR 山陰本線を乗り継ぐ通勤のための、個人用 Android / Wear OS アプリ。
仕様と設計原則は [CLAUDE.md](CLAUDE.md) を参照。

## 進捗（CLAUDE.md 12. 開発順序）

- [x] 1. `tools/gtfs_import.py` — GTFS-JP から対象路線・停留所を抽出して DB 化（[tools/README.md](tools/README.md)）
- [x] 2. core/journey 乗り継ぎ計算 + ユニットテスト（[docs/journey.md](docs/journey.md)）
- [ ] 3. スマホ ホーム画面
- [ ] 4. 通知スケジューラ
- [ ] 5. Wear OS タイル + コンプリケーション
- [ ] 6. GTFS-RT 連携
- [ ] 7. ホーム画面ウィジェット

## ビルド・テスト

```sh
./gradlew :core:test        # 乗り継ぎ計算のユニットテスト（JDK 17 以上、python3 が必要）
./gradlew :core:ktlintCheck # コードスタイル
python3 -m unittest discover -s tools/tests -v
```

app / wear モジュールは手順 3 以降で追加する。

## ドキュメント

- [docs/ids.md](docs/ids.md) — GTFS の route_id / stop_id（確定手順と記録）
- [docs/db_schema.md](docs/db_schema.md) — プリパッケージ DB のスキーマ（Room エンティティと 1:1）
- [docs/journey.md](docs/journey.md) — 乗り継ぎ計算エンジンの仕様と設計判断

## 出典

- バス時刻・車両位置情報: 鳥取県オープンデータ（日ノ丸自動車）
- JR 時刻: JR 西日本 駅時刻表より手動転記
