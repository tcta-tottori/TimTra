# TimTra

日ノ丸バス（用瀬智頭線）と JR 山陰本線を乗り継ぐ通勤のための、個人用 Android / Wear OS アプリ。
仕様と設計原則は [CLAUDE.md](CLAUDE.md) を参照。

## 進捗（CLAUDE.md 12. 開発順序）

- [x] 1. `tools/gtfs_import.py` — GTFS-JP から対象路線・停留所を抽出して DB 化（[tools/README.md](tools/README.md)）
- [x] 2. core/journey 乗り継ぎ計算 + ユニットテスト（[docs/journey.md](docs/journey.md)）
- [x] 3. スマホ ホーム画面 + 時刻表一覧 + 設定 + このアプリについて（[docs/app.md](docs/app.md)、**実機ビルド未確認**）
- [x] 4. 通知スケジューラ（[docs/notify.md](docs/notify.md)、**実機ビルド未確認**）
- [x] 5. Wear OS タイル + コンプリケーション + Data Layer 同期（[docs/wear.md](docs/wear.md)、**実機ビルド未確認**）
- [ ] 6. GTFS-RT 連携
- [ ] 7. ホーム画面ウィジェット

## ビルド・テスト

```sh
./gradlew :core:test        # 乗り継ぎ計算のユニットテスト（JDK 17 以上、python3 が必要）
./gradlew ktlintCheck       # コードスタイル
./gradlew :app:assembleDebug :wear:assembleDebug
python3 -m unittest discover -s tools/tests -v
```

Android Studio で開く場合は AGP 9.1 / Gradle 9.5 / compileSdk 37 に対応した版（2026 年以降のもの）を使う。
モジュール構成と、開発環境の制約で未検証の点は [docs/app.md](docs/app.md) を参照。

## ドキュメント

- [docs/ids.md](docs/ids.md) — GTFS の route_id / stop_id（確定手順と記録）
- [docs/db_schema.md](docs/db_schema.md) — プリパッケージ DB のスキーマ（Room エンティティと 1:1）
- [docs/journey.md](docs/journey.md) — 乗り継ぎ計算エンジンの仕様と設計判断
- [docs/app.md](docs/app.md) — Android モジュール構成（data / app）と画面
- [docs/notify.md](docs/notify.md) — 通知スケジューラ（WorkManager + AlarmManager）と権限導線
- [docs/wear.md](docs/wear.md) — Wear OS タイル・コンプリケーション・設定同期

## 出典

- バス時刻・車両位置情報: 鳥取県オープンデータ（日ノ丸自動車）
- JR 時刻: JR 西日本 駅時刻表より手動転記
