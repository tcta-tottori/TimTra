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
- 署名鍵はリポジトリの `keystore/debug.keystore` に固定してあるので、以後は上書きインストールで更新できる。
  **鍵を固定する前（2026-09-06 以前）に入れた APK は署名が違うため、一度アンインストールしてから入れ直す。**
- スマホ版と Wear 版は同じ鍵で署名しているので、Wearable Data Layer の同期が成立する。

実データの取得は `.github/workflows/fetch-gtfs.yml` が行う（`tools/gtfs_source.json` の `fetch_nonce` を増やして push すると再取得）。
結果は `docs/gtfs_fetch_report.md` に残る。

**バスは実ダイヤ**（日ノ丸自動車 GTFS-JP、2026-08-01〜2027-01-31、GTFS-RT の遅延推定つき）。
**JR も実ダイヤ**（`data/src/main/assets/jr_timetable.json`、2026-09 時点の駅時刻表から手動転記、宝木に停車しない特急は除外）。
着時刻は所要時間からの推定（下り 22 分 / 上り 24 分）、土曜は日曜祝日と同じダイヤと仮定している。
テスト用のサンプル JR 時刻表は `core/src/test/resources/jr_timetable_sample.json` に分けてある。

勤務先（気高電機）にいるときは、17 時以降の宝木発の次の電車について 30 / 20 / 15 分前にリマインダーが届く
（詳細は `docs/notify.md`）。初回は設定画面で「現在地を勤務先に登録」と位置情報の「常に許可」を済ませておく。

ホームの「家を出る時刻」は朝 5:30〜6:50、「職場を出る時刻」は 17:00〜終電（勤務先付近にいる間）だけ出る。
それ以外の時間は次のバス / 電車の発車時刻が主役になる（`docs/app.md`「出発時刻を出す時間帯」）。
ホーム中央の地図は OpenStreetMap のタイルを下地に、現在地・南吉成・鳥取駅・宝木駅・勤務先とバスの車両位置（GTFS-RT）を重ねる。
タイルは表示中にだけ取得して端末内にキャッシュする（出典: © OpenStreetMap contributors）。

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
