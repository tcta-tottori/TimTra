# TimTra

日ノ丸バス（用瀬智頭線）と JR 山陰本線を乗り継ぐ通勤のための、個人用 Android / Wear OS アプリ。
仕様と設計原則は [CLAUDE.md](CLAUDE.md) を参照。

## 進捗（CLAUDE.md 12. 開発順序）

- [x] 1. `tools/gtfs_import.py` — GTFS-JP から対象路線・停留所を抽出して DB 化（[tools/README.md](tools/README.md)）
- [x] 2. core/journey 乗り継ぎ計算 + ユニットテスト（[docs/journey.md](docs/journey.md)）
- [x] 3. スマホ ホーム画面 + 時刻表一覧 + 設定 + このアプリについて（[docs/app.md](docs/app.md)、**実機ビルド未確認**）
- [x] 4. 通知スケジューラ（[docs/notify.md](docs/notify.md)、**実機ビルド未確認**）
- [x] 5. Wear OS タイル + コンプリケーション + Data Layer 同期（[docs/wear.md](docs/wear.md)、**実機ビルド未確認**）
- [x] 6. GTFS-RT 連携（30 秒制限・遅延推定・フォアグラウンドのみ、[docs/realtime.md](docs/realtime.md)、URL 未設定・実機未確認）
- [x] 7. ホーム画面ウィジェット（Glance、[docs/widget.md](docs/widget.md)、実機未確認）

## APK の入手

GitHub Actions が push のたびにビルドし、プレリリース [dev](https://github.com/tcta-tottori/TimTra/releases/tag/dev) に置き換えます。

| ファイル | 端末 |
| --- | --- |
| `timtra-phone-debug.apk` | スマートフォン |
| `timtra-wear-debug.apk` | Wear OS |

- スマホ: リリースページから APK をダウンロードし、「提供元不明のアプリ」を許可してインストールする。
- 時計: スマホに adb でつないでも入らない。`adb pair` / `adb connect` で時計に直接つなぎ、
  `adb install timtra-wear-debug.apk` を実行する（時計の開発者オプションで ADB デバッグと Wi-Fi デバッグを有効にする）。
- 両方とも同じ実行でビルドした debug 署名なので、Wearable Data Layer の同期が成立する。
  片方だけ入れ替えると署名が変わり同期しなくなるので、更新は両方まとめて行う。

実データの取得は `.github/workflows/fetch-gtfs.yml` が行う（`tools/gtfs_source.json` の `fetch_nonce` を増やして push すると再取得）。
結果は `docs/gtfs_fetch_report.md` に残る。

**現在の APK は時刻表がサンプルデータです。** 実ダイヤではないので、通勤には使えません。
実データの入れ方は下の「ドキュメント」を参照。

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
- [docs/realtime.md](docs/realtime.md) — GTFS-RT の取得制限と遅延推定
- [docs/widget.md](docs/widget.md) — ホーム画面ウィジェットと更新タイミング

## 出典

- バス時刻・車両位置情報: 鳥取県オープンデータ（日ノ丸自動車）
- JR 時刻: JR 西日本 駅時刻表より手動転記
