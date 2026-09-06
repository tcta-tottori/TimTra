# Android モジュール（data / app）

## モジュール構成

| モジュール | 種類 | 役割 |
| --- | --- | --- |
| `core` | Kotlin/JVM | モデル・乗り継ぎ計算・JSON パーサ。Android 非依存（docs/journey.md） |
| `data` | Android library | Room（プリパッケージ DB）、DataStore（設定）、リポジトリ、Hilt モジュール。app と wear で共有 |
| `app` | Android application | スマホ UI（Jetpack Compose） |
| `wear` | Android application (Wear OS) | タイル・コンプリケーション・簡易 UI・設定同期の受信（docs/wear.md） |

ビルド構成: AGP 9.1.1（Kotlin 内蔵）、Gradle 9.5、Kotlin 2.4.10、KSP 2.3.11、Hilt 2.60.1、
Room 2.8.4、Compose BOM 2026.08.00、compileSdk 37 / minSdk 26。バージョンは `gradle/libs.versions.toml`。

Hilt 2.59 以降の Gradle プラグインは AGP 9 を要求し、Compose 1.12 は compileSdk 37 と AGP 9 を要求するため、
AGP 9 系を採用した。AGP 9 では `org.jetbrains.kotlin.android` を適用しない（`core` だけ `kotlin.jvm`）。

## data

- `db/Entities.kt`: docs/db_schema.md と 1:1 のエンティティ。列名・NOT NULL・主キー順・インデックス名を変えない。
- `db/TimTraDatabase.kt`: `createFromAsset("timtra_gtfs.db")`。端末内ファイル名にスキーマ版を含め、
  assets 差し替え時は `fallbackToDestructiveMigration` で作り直す。
- `repository/BusTimetableRepository`: DB → core `BusTimetable`（初回のみ読み込み、以後キャッシュ）。
- `repository/JrTimetableRepository`: `data/src/main/assets/jr_timetable.json` → core `JrTimetable`（app / wear で共有するため data に置く）。
- `repository/SettingsRepository`: DataStore Preferences。`CommuteSettings` + 通知 ON/OFF + 「今日は休み」（日付で保持、翌日自動解除）。
- `repository/JourneyRepository`: 上記を束ねて `JourneyPlanner` を組み立てる。UI・通知ジョブ・Wear 同期の共通入口。
- `data/src/main/assets/timtra_gtfs.db`: 現在は合成サンプルから生成したもの（tools/README.md）。
- `sync/SyncedSettings`: スマホ → Wear に配る設定の JSON 形。
- `di/ClockModule`: 現在時刻の供給（AppClock）。
- `realtime/`: GTFS-RT の取得（OkHttp）と遅延推定の状態（docs/realtime.md）。

## app

| 画面 | ファイル | 内容 |
| --- | --- | --- |
| ホーム | `ui/home/` | CLAUDE.md 7-1 の順（出発時刻と残り時間 → バス → 乗り継ぎ → JR → 到着予測 → 代替案 → 次の候補）。往路/復路の自動判定と手動切替。表示中は 30 秒ごとに再計算 |
| 時刻表 | `ui/timetable/` | 南吉成 / 鳥取駅 / 宝木 のタブ。初期表示は今日で、今日 / 平日 / 土曜 / 日祝 を切り替え可能（日種別指定は該当する直近の日を引く）。今日の表示のみ現在時刻の位置へ自動スクロール |
| 設定 | `ui/settings/` | 所要時間・閾値（分）、往路/復路の時刻帯、前夜計算の基準時刻、通知 ON/OFF、今日は休み、既定値に戻す |
| このアプリについて | `ui/about/` | 出典表示（CLAUDE.md 14）と同梱データの版 |

- 文言は `res/values/strings.xml` に集約。XML レイアウトは無い（テーマ・アイコンのみ XML）。
- 見た目: アプリアイコンに合わせた配色（`ui/theme/Theme.kt`）。ヘッダー（`TimTraTopBar`）と左ドロワーは
  青のグラデーション（#2E8BF5 → #14307F）で文字は白、本文は白地。カードは角丸 20dp + 薄い縁の `TimTraCard`、
  出発時刻は青グラデーションの `GradientCard`。ドロワーは版・現在時刻・画面一覧（選択中は半透明ピル）・ワードマーク。
  往路/復路の切替は右下の青い FAB。ステータスバーは白アイコン、ナビゲーションバーは黒アイコン（edge-to-edge）。
  Compose の material-icons は使わず、必要なアイコンは `res/drawable/ic_*.xml` に持つ。
- 現在時刻は `data/di/AppClock` 経由で取得する（スマホ・Wear 共通。テストで差し替え可能）。
- アプリアイコンは `docs/assets/icon-512.png`（青グラデーション、バスと電車）を元に、adaptive icon
  （背景色 #1546AD + 中央 85% に縮小した前景 PNG、外側は透過）として `res/mipmap-*/` に生成している。
  ドロワーのヘッダーと About で使う `drawable-nodpi/app_logo.png` も同じ元画像。
- 通知まわりは `notify/`（docs/notify.md）。ウィジェットは `widget/`（docs/widget.md）。
- サンプル時刻表で動いている間はホームに警告バナーを出す（`BusTimetable.isSampleData` / JR version が `sample` で始まる）。

## 未検証の点（重要）

この開発環境からは Google Maven（dl.google.com）に到達できないため、`data` と `app` は
**コンパイル・実機起動を確認できていない**。core のテストと ktlint（app / data のソース含む）だけ通している。
手元で最初に行うこと:

1. `./gradlew :app:assembleDebug` を実行し、依存解決とコンパイルエラーを潰す
   （AGP 9 の新 DSL、Hilt/KSP、`androidx.hilt:hilt-lifecycle-viewmodel-compose` の `hiltViewModel` パッケージなどが要確認箇所）。
2. 起動して Room の `createFromAsset` 検証が通ること（通らなければ docs/db_schema.md とエンティティの差分）。
3. ホームに「サンプル時刻表」の警告が出た状態で、月曜 06:00 相当の表示が docs/journey.md の例と一致すること
   （JR 07:45、バス 07:05 発、余裕 13 分、OK）。
