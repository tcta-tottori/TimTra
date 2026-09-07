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
| ホーム | `ui/home/` | CLAUDE.md 7-1 の順（出発時刻と残り時間 → 地図 → バス → 乗り継ぎ → JR → 到着予測 → 代替案 → 次の候補）。往路/復路の自動判定と手動切替。表示中は 30 秒ごとに再計算、残り時間は 1 秒刻み（秒まで表示）、現在地は 3 秒間隔で追従。主役カードに「歩き / 早歩き / 走る / 間に合わない」の人型アイコン（`PaceAdvisor`）。バス / JR カードをタップすると現在時刻以降の時刻表がポップアップ（`TimetablePeekSheet`） |
| 時刻表 | `ui/timetable/` | 南吉成 / 鳥取駅 / 宝木 のタブ（青い帯の中のピル、バス / JR のアイコン付き）。初期表示は今日で、今日 / 平日 / 土曜 / 日祝 を切り替え可能（日種別指定は該当する直近の日を引く）。時間帯ごとの見出し、次の便のハイライトと「あと n 分」、過ぎた便は薄く、境目に「現在 HH:mm」の赤い線。今日の表示のみ現在時刻の位置へ自動スクロールし、右下の「現在時刻へ」で戻れる。鳥取駅タブは すべて / バスのみ / JRのみ で絞り込める |
| 設定 | `ui/settings/` | 所要時間・閾値（分）、往路/復路の時刻帯、前夜計算の基準時刻、出発時刻の表示時間帯、通知 ON/OFF、今日は休み、既定値に戻す。数値・時刻の行は現在値と編集ボタンだけを置き、編集はポップアップ（分は数値入力 + −/+、時刻は Material3 の TimePicker）で行う |
| このアプリについて | `ui/about/` | 出典表示（CLAUDE.md 14）と同梱データの版 |

- 文言は `res/values/strings.xml` に集約。XML レイアウトは無い（テーマ・アイコンのみ XML）。
- 見た目: アプリアイコンに合わせた配色（`ui/theme/Theme.kt`）。ヘッダー（`TimTraTopBar`）と左ドロワーは
  青のグラデーション（#2E8BF5 → #14307F）で文字は白、本文は白地。カードは角丸 20dp + 薄い縁の `TimTraCard`、
  出発時刻は青グラデーションの `GradientCard`。ドロワーは版・現在時刻・画面一覧（選択中は半透明ピル）・ワードマーク。
  往路/復路の切替は右下の青い FAB。ステータスバーは白アイコン、ナビゲーションバーは黒アイコン（edge-to-edge）。
  Compose の material-icons は使わず、必要なアイコンは `res/drawable/ic_*.xml` に持つ。
- 交通手段のアイコンと色は `ui/common/TransitIcons.kt` に集約する（`TransitMode.BUS` = 橙 + `ic_bus`、`TransitMode.JR` = 青 + `ic_train`、
  `ModeBadge` / `ModeChip` / `CircleIcon` / `InfoPill`）。ホーム・時刻表・地図はすべてこれを使い、画面ごとに色や絵柄を変えない。
  地点（南吉成・鳥取駅・宝木駅・勤務先）のアイコンも同じファイルの `LandmarkKind` 拡張で決める。
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

## 残り時間・急ぎ度・時刻表ポップアップ（ホーム）

- **残り時間**: 主役カードは次の便（往路: バス、復路: JR）の発車までを `MM:SS`（1 時間以上は `H:MM:SS`）で大きく出し、
  `HomeViewModel.now`（1 秒刻み、画面表示中だけ進む）で毎秒更新する。出発時刻を出す時間帯（下記）は出発時刻を大きく、
  その下に同じ形式の残り時間を出す。
- **現在地の追従**: `LocationProvider.updates()`（`LocationManagerCompat.requestLocationUpdates`、GPS + 基地局、3 秒間隔）を
  ホーム表示中だけ購読する（`WhileSubscribed`）。8 m 未満の揺れでは再計算しない。バックグラウンドでは取らない（CLAUDE.md 3-4）。
- **急ぎ度**: core の `journey/PaceAdvisor`。現在地から出発地点（往路: 南吉成、復路: 宝木駅）までの直線距離 × 1.25 を道なりの距離とし、
  発車までの残り時間（改札・ホームまで 1 分を差し引く）と比べて 歩き（80 m/分）/ 早歩き（95 m/分）/ 走る（140 m/分）/ 間に合わない を判定する。
  速度は 2026-09-07 の実測（勤務先 → 宝木駅 約 1.4 km を早歩きで 15 分ちょうど）に合わせた。
  アイコンは `ic_walk`（緑）/ `ic_walk_fast`（橙）/ `ic_run`（赤）/ `ic_warning`（灰）。120 m 以内なら「〜にいます」。
- **時刻表ポップアップ**: バス / JR カードをタップすると `ModalBottomSheet` に、現在時刻から一番近い便（ハイライト + 残り時間）と
  それ以降の便を最大 20 本出す。今日の残りが 5 本未満なら翌日の始発から 5 本を続ける。行部品は時刻表画面と共有
  （`ui/timetable/TimetableRows.kt`、エントリの組み立ては `TimetableCatalog`）。

## 出発時刻を出す時間帯（ホーム）

「家を出る時刻」「職場を出る時刻」は決まった時間帯にだけ主役カードに出す（core の `journey/LeaveDisplayPolicy`、テストあり）。
それ以外の時間は最初の便（往路はバス、復路は JR）の発車時刻と残り時間を主役にし、「家を出る」「職場を出る」の文字は出さない。
「次の候補」「代替案」の要約からも出発時刻を外す。

| 向き | 出す条件 | 既定値 |
| --- | --- | --- |
| 往路（家を出る） | 現在時刻が表示開始以上・表示終了未満 | 05:30〜06:50（6:48 発のバスに乗る前提） |
| 復路（職場を出る） | 表示開始以降、かつ案のアンカー（宝木発の JR 便）が今日のうち（= 終電まで）、かつ現在地が勤務先から「鳥取駅までの距離 − 1.5 km」（下限 3 km）以上離れていない | 17:00 開始 |

時刻はいずれも設定画面「出発時刻の表示」で 5 分刻みに変更できる（DataStore、Wear へも同期）。
位置が取れないときは時刻だけで判定する。Wear のタイルとウィジェットはこの規則を適用していない（従来どおり常に出発時刻を出す）。

## 地図（ホーム中央）

`ui/home/RouteMapCard.kt`。下地は OpenStreetMap の標準ラスタタイル、その上に経路上の地点・現在地・バスの位置を重ねる。
サーバーは持たない（CLAUDE.md 3-5）: タイルは openstreetmap.org から表示中にだけ取得し、端末内にキャッシュする
（`ui/map/MapTileLoader`: メモリ 64 枚 + `cacheDir/osm_tiles` 60 MB、14 日で取り直し、同時 2 本、User-Agent 明示。
OSM タイル利用規約に従う）。通信できないときはキャッシュ済みのタイルだけを使い、1 枚も無ければ方眼の簡易地図になる。
出典「© OpenStreetMap contributors」を地図の右下と About 画面に出す（ODbL / 利用規約で必須）。

- 地点は core の `geo/RouteLandmarks`（南吉成 = GTFS の HOME 停留所、鳥取駅 = GTFS の STATION 停留所（バスターミナル）、
  宝木駅 = `Places.HOUGI_STATION`、勤務先 = 設定で登録した位置。未登録なら勤務先は出さない）。
- 投影は core の `geo/MapProjection`（Web メルカトル = タイルと同じ。`geo/WebMercator` にタイル番号の計算。テストあり）。
  全地点が余白つきで収まる縮尺を選び、左下に縮尺バー（地上距離で 50 m〜50 km のきりのよい値）を出す。
  タイルのズームは「1 タイルが画面上で 256 × density × 0.8 px」になる値を選ぶ（高密度画面で文字が読める大きさ。
  OSM 標準タイルに @2x が無いため多少ぼやける）。地図 1 枚あたり 6〜12 タイル。
- 初期表示は**現在地に近い側**だけを拡大する（`RouteLandmarks.sideFor`）: 最寄りの地点が 4 km 以内なら
  その側（自宅側 = 南吉成・鳥取駅 / 勤務先側 = 宝木駅・勤務先）、4 km 超なら移動中とみなして経路全体、40 km 超（出張先など）や
  位置が無いときは向きで決める（往路 → 自宅側、復路 → 勤務先側）。経路全体（約 14 km）を常に出すと自宅側の 2 点が重なるため。
  右上のボタンで全体表示と切り替える。現在地は近くにいるときだけ画面に収め、遠いときは縁に点を置いて距離だけ示す。
- GTFS-RT の車両位置（`RealtimeState.vehicles`。対象区間を走る便だけ）が取れていれば、バスも橙の丸で描く。
  乗る予定の便は大きく「乗るバス 約 n 分遅れ（推定）」のラベル付きで、焦点から 12 km 以内なら画面に収める
  （画面外なら縁に寄せて「乗るバスは画面外（距離）」）。他の便は小さな丸だけ。右上に取得時刻。取得は従来どおり
  ホーム表示中・バス発車 90 分前〜到着 5 分後だけ（30 秒制限は `FetchThrottle`）。
- 経路線はバス区間を橙、JR 区間を青、勤務先までの徒歩を灰の破線で描く。勤務先が既定位置（気高電機付近）から 200 m 以内なら、
  徒歩区間は `Places.WORKPLACE_TO_HOUGI_WALK`（Google マップの徒歩ルート約 1.4 km を手でなぞった折れ線）で道なりに描き、
  中ほどに「徒歩 n 分」（設定の walkStationToWork、既定 20 分）を出す。現在地は青い点（波紋アニメーション）で、
  最寄りの地点まで破線と距離ラベルを出す。地図の下に各地点までの距離を近い順にチップで並べる。
- 地図をタップ（または見出しの全画面ボタン）で全画面のダイアログになる。ドラッグで移動、ピンチで拡大縮小
  （core の `geo/MapCamera`: 指の中心を固定して拡大、縮尺は 0.25〜20,000 m/px、テストあり）。右下に 拡大 / 縮小 / 現在地へ / 経路全体、
  左上の × で閉じる。見え方は開いている間だけ保持し、閉じると捨てる。カードとの描画は `MapLayer` を共有する。
- 位置は `LocationProvider` がホーム表示中に取った値をそのまま使う（新たな取得はしない。CLAUDE.md 3-4）。
  `Places.TOTTORI_STATION`（JR 鳥取駅舎）は表示ポリシーの距離判定に使い、地図ではバスターミナルの 1 点にまとめる。

## 往路 / 復路の決め方（ホーム）

ホームには「往路」「復路」の文字は出さず、現在位置に近い側の出発時刻を常に表示する。
決め方は core の `journey/BoundResolver`（純 Kotlin、ユニットテストあり）:

1. 右下の FAB で手動切替中ならそれを使う（脚注に「手動で切り替え中」と「自動に戻す」）。
2. 現在地が南吉成から 1.2 km 以内なら往路（家を出る時刻）。鳥取駅までは約 1.7 km あるので区別できる。
3. 現在地が宝木駅（`model/Places.HOUGI_STATION`。35.5147, 134.0819。OSM の駅記号と Google マップの徒歩経路から 2026-09-07 に確定）から
   5 km 以内なら復路（職場を出る時刻）。それ以前の座標は約 5 km 西、次いで約 400 m 北西（国道 9 号との交差点付近）にずれていた。
4. それ以外（鳥取駅にいる、位置が取れない、出張中）は設定の時刻帯で決める。

位置は `app/location/LocationProvider` がホーム画面の表示中にだけ 1 回取り、5 分キャッシュする。
基地局・Wi-Fi 測位（NETWORK_PROVIDER）を優先し、直近 10 分以内の last known location があればそれを使う。
常時取得・バックグラウンド取得はしない（CLAUDE.md 3-4）。位置情報が未許可のときはホームに小さな案内カードを出す。
