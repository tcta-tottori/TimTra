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
  スキーマ版を上げたときは `fallbackToDestructiveMigration` で作り直す。
  `createFromAsset` が assets から写すのは**端末にコピーが無いときだけ**なので、
  ダイヤ改正で assets の DB だけ差し替えてもスキーマ版が同じなら写し直されず、古いダイヤのまま動いてしまう。
  そこで開く前に「どの版のアプリで写したか」（`versionName` + `lastUpdateTime`）を SharedPreferences に覚えておき、
  違っていれば端末内のコピー（`-wal` / `-shm` 含む）を消して写し直させる。
  つまり**アプリを入れ直す・更新すると必ず最新のダイヤになる**。DB は読み取り専用なので消しても失う情報は無い。
- `repository/BusTimetableRepository`: DB → core `BusTimetable`（初回のみ読み込み、以後キャッシュ）。
- `repository/JrTimetableRepository`: `data/src/main/assets/jr_timetable.json` → core `JrTimetable`（app / wear で共有するため data に置く）。
- `repository/SettingsRepository`: DataStore Preferences。`CommuteSettings` + 通知 ON/OFF + 「今日は休み」（日付で保持、翌日自動解除）。
- `repository/JourneyRepository`: 上記を束ねて `JourneyPlanner` を組み立てる。UI・通知ジョブ・Wear 同期の共通入口。
- `data/src/main/assets/timtra_gtfs.db`: 現在は合成サンプルから生成したもの（tools/README.md）。
- `glyph/Glyphs.kt` `glyph/GlyphText.kt`: 見本画像から切り出した数字（`data/src/main/assets/date`）。
  app と wear の両方が使う。切り出しは `tools/date_glyphs.py`。詳しくは docs/wear.md。
- `sync/SyncedSettings`: スマホ → Wear に配る設定の JSON 形。
- `di/ClockModule`: 現在時刻の供給（AppClock）。
- `realtime/`: GTFS-RT の取得（OkHttp）と遅延推定の状態（docs/realtime.md）。

## app

| 画面 | ファイル | 内容 |
| --- | --- | --- |
| ホーム（近くの発車標） | `ui/home/` | **通勤の予定があるかどうかに関係なく、現在地から最寄りの地点の発車標**（`HomeBoard`）を必ず出す。地点名・行き先・次の便までの残り時間（分:秒）・発時刻・この先の数本。位置が取れない / 通勤圏外なら時刻帯で決める（往路の時間帯 = 南吉成、復路 = 宝木。時計版と同じ既定）。タップでその地点の時刻表。「今日は休み」・帰宅後（`resting`）・運行が見つからないときは**これが主役**（`BoardHero` + この先の数本）で、帰宅後は翌朝の予定（`RestCard`）を地図の下に添える。通勤の表示が出ているときは地図の下に `NearbyBoardCard`（地点名と距離 + 次の数本）として添える |
| ホーム | `ui/home/` | CLAUDE.md 7-1 の順（出発時刻と残り時間 → 地図 → バス → 乗り継ぎ → JR → 到着予測 → 代替案 → 次の候補）。往路/復路の自動判定と手動切替。表示中は 30 秒ごとに再計算、残り時間は 1 秒刻み（秒まで表示）、現在地は 3 秒間隔で追従。主役カードに「歩き / 早歩き / 走る / 間に合わない」の人型アイコン（`PaceAdvisor`）。バス / JR カードをタップすると現在時刻以降の時刻表がポップアップ（`TimetablePeekSheet`）。主役表示（地図の上）をタップすると時刻表ページが開く。バス / JR のピルを押したときだけその区間の駅・バス停のタブ、それ以外を押したときは**現在地の最寄り**のタブ。押せることが分かるように主役カードの下に「タップで時刻表」の合図（`TimetableHint`）を置く。地図の下の地点チップ（南吉成 / 鳥取駅 / 宝木）も押すとその地点の時刻表が開く（自宅・勤務先は時刻表が無いので押せない） |
| 時刻表 | `ui/timetable/` | 南吉成 / 鳥取駅 / 宝木 のタブ（青い帯の中のピル、バス / JR のアイコン付き）。**初回に開くタブは現在地の最寄り**（core の `DepartureBoard.nearest`。位置が取れない / 通勤圏外なら南吉成。鳥取駅は往路 = JR・復路 = バスで絞り込む。手でタブを選んだ後は動かさない）。決まるまで（最大 3 秒）は一覧を出さずに読み込み中にする（先に別の駅を見せてから差し替わると読み違えるため）。ホームでバス / JR のピルを押して来たときだけ、その区間（`TimetableFocus`）が現在地より優先される。初期表示は今日で、今日 / 平日 / 土曜 / 日祝 を切り替え可能（日種別指定は該当する直近の日を引く）。時間帯ごとの見出し、次の便のハイライトと「あと n 分」、過ぎた便は薄く。今日の表示のみ次の便の位置へ自動スクロールする（現在時刻の線は置かない）。右下にはホームへ戻るボタンを置く（`BackFabOverlay`。ホーム右下の設定と同じ位置・同じ大きさ）。鳥取駅タブは すべて / バスのみ / JRのみ で絞り込める。JR は **終電まで**（宝木発 23:14 まで）並べる。`JrService.irregular`（◆特定日のみ運転）を立てた便も一覧には出し、備考をオレンジで添える |
| 設定 | `ui/settings/` | 右下にホームへ戻るボタン。所要時間・閾値（分）、往路/復路の時刻帯、前夜計算の基準時刻、出発時刻の表示時間帯、通知 ON/OFF、今日は休み、既定値に戻す。数値・時刻の行は現在値と編集ボタンだけを置き、編集はポップアップ（分は数値入力 + −/+、時刻は Material3 の TimePicker）で行う |
| このアプリについて | `ui/about/` | 出典表示（CLAUDE.md 14）と同梱データの版 |

- 文言は `res/values/strings.xml` に集約。XML レイアウトは無い（テーマ・アイコンのみ XML）。
- 見た目: **時計版（docs/wear.md）に合わせた黒地・濃紺**（`ui/theme/Theme.kt`）。地は左下の黒から
  右へ向かって濃紺へ持ち上げる線形グラデーション。ヘッダー（`TimTraTopBar`）は青のグラデーション
  （#2E8BF5 → #14307F）で文字は白。カードは角丸 20dp + 薄い縁の `TimTraCard`（地 #0F1B30、縁 #1E3355）。強調の青・ステータス色・交通手段の色は、黒地で読める明るさに振ってある。
- `TimTraTheme` は `LocalContentColor` に白を流す。Surface に包まれていない文字は Compose の既定で黒になり、
  黒地では読めなくなるため。`surfaceVariant` はカードの地と別の値にしてある（同じにすると
  `contentColorFor` がカードの文字色を薄い青灰に寄せてしまう）。
- **左メニューもボタンのメニューも持たない。** 時刻表は**ホームの主役表示をタップして開く**。
  区間のピル（`HeroLegStrip`）はバス / JR それぞれが押せて、押した区間の駅・バス停のタブで開く
  （往路: バス → 南吉成、JR → JR 鳥取駅 / 復路: JR → 宝木、バス → 鳥取駅のバスのりば）。
  ピル以外の場所を押したときは最初の区間（往路 = 南吉成、復路 = 宝木）で開く。
  バス / JR のピルを押したときだけ、どのタブで開くかを `TimetableFocus` に持たせて `timetable?focus=…` で渡す
  （`TimetableViewModel` が `SavedStateHandle` から読み、現在地による自動選択より優先する）。
  ピル以外を押したときは渡さないので、現在地の最寄りのタブで開く。
- 右下のボタンは**設定だけ**。更新は画面に戻ったときと 30 秒ごとに自動で走るので、ボタンは置かない。
  設定は主役ではないので、光も影も付けないグレーの丸ボタン（`ui/common/GlowFab.kt` の `QuietFab`）にする。
  「このアプリについて」は設定の中に集約した。
- **丸ボタンは 1 か所に集約した部品**（`ui/common/GlowFab.kt`）。直径 60dp（光を含めて 84dp）、
  画面の隅から 20dp。ホームの設定（`QuietFab`。グレー・光なし）と、他の画面の「戻る」
  （`GlowFab`。外側の淡い光 + 青のグラデーション + 白い細縁 + 影）は、同じ寸法・同じ余白（`fabInset`）
  なので位置が必ず一致する。戻るは Scaffold の floatingActionButton ではなく画面いっぱいの囲みに重ねて置く
  （`BackFabOverlay`。Scaffold の既定の余白だと 4dp ずれるため）。
  ホーム以外（時刻表・設定・このアプリについて）は、左上の矢印をやめて右下の戻るだけにした。
  往路 / 復路の手動切替はホーム脚注のボタンへ移した。
- **スクロールの見え方**（`ui/common/ScrollEffects.kt`）。
  `Modifier.riseIn()`: 画面に入ってきたものは、何も無い状態から下から浮かび上がる（`TimTraCard` /
  `HeroSection` / 時刻表の行に組み込み済み。窓の中での高さが 0 かどうかで「入ってきた」を判定する）。
  `Modifier.fadeBottomEdge()`: 画面の下端 96dp を薄く落とす（続きがあることも、これで分かる）。
  ホームの縦スクロールと時刻表の一覧にかけている。
- ホームのヘッダーの題は「TimTra」。画面が 1 つに寄ったので、題で画面名を言う必要がなくなった。
- **いちばん上（出発時刻・残り時間）はカードにしない**（`HeroSection`）。画面の地の上に文字だけを置く。
  カードにするのは地図や各区間の時刻など、下に続くもの。
- 時刻表の地点タブも塗りつぶしのピルをやめ、下線と文字の濃さだけで選択を示す。
- 地図の下地は色変換で地に馴染ませる。道が主役の暗いタイルは `TransitColors.darkTileMatrix` で
  地の黒を濃紺に、明るい所（= 道）を水色へ持ち上げる。控えの OSM（白地）は `osmTileMatrix` で反転する。
  浮かせるラベルの地も白から濃紺（`TransitColors.labelFill`）へ。
  システムバーのアイコンは上下とも白（edge-to-edge）。
  Compose の material-icons は使わず、必要なアイコンは `res/drawable/ic_*.xml` に持つ。
- 交通手段のアイコンと色は `ui/common/TransitIcons.kt` に集約する（`TransitMode.BUS` = 橙 + `ic_bus`、`TransitMode.JR` = 青 + `ic_train`、
  `ModeBadge` / `ModeChip` / `CircleIcon` / `InfoPill`）。ホーム・時刻表・地図はすべてこれを使い、画面ごとに色や絵柄を変えない。
  地点（南吉成・鳥取駅・宝木駅・勤務先）のアイコンも同じファイルの `LandmarkKind` 拡張で決める。
- 現在時刻は `data/di/AppClock` 経由で取得する（スマホ・Wear 共通。テストで差し替え可能）。
- **ブランドの絵は `tools/brand_assets.py` が焼く**（元絵は `tools/brand/`。デザインを変えたときだけ実行）。
  - ホームのヘッダーは題名ではなく **文字ロゴ**（`drawable-nodpi/logo_timtra.png`。白の「Tim」＋水色の「Tra」、
    高さ 26dp の `TopBarLogo`）。元絵の輪郭のノイズは焼くときに落とし、2 色で塗り直している。
    ホーム以外の画面は今までどおり題名（`TopBarTitle`）。
  - アプリアイコンは adaptive icon。**地**は青のグラデーション（`drawable/ic_launcher_background.xml`。
    元絵の左上と右下の色をそのまま拾ったベクタ）、**前景**はバス・電車・「TimTra」だけを抜いた PNG で、
    安全圏（108dp のうち中央 72dp）に収めてある。旧式の四角・丸アイコンは元絵を型で抜いて作る。
    スマホと時計で同じ絵を使う（`app` / `wear` の `res/mipmap-*`）。資料用は `docs/assets/icon-512.png`。
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

- **残り時間**: 主役カードは次の便（往路: バス、復路: JR）の発車までを、時計と同じ **`分:秒` の 4 桁**
  （core の `board/Countdown`。99:59 で頭打ち）で大きく出し、`HomeViewModel.now`（1 秒刻み、画面表示中だけ進む）で
  毎秒更新する。右に砂時計と同じ向きのリング（`ui/common/CountdownRing`、core の `CountdownGauge`。
  発車 15 分前から減る）を添える。出発時刻を出す時間帯（下記）は出発時刻を大きく、その下に残り時間を出す。
- **数字の字**: 残り時間・出発時刻・各区間の発着時刻は、システムのフォントではなく見本画像から切り出した字で書く
  （`ui/common/GlyphNumber` → `data` の `glyph/GlyphText`）。時計版の日付・残り時間と同じ字。
  送りは等幅なので、秒が進んでも表示の幅も「:」の位置も隣のリングも動かない。
- **現在地の追従**: `LocationProvider.updates()`（`LocationManagerCompat.requestLocationUpdates`、GPS + 基地局、3 秒間隔）を
  ホーム表示中だけ購読する（`WhileSubscribed`）。8 m 未満の揺れでは再計算しない。バックグラウンドでは取らない（CLAUDE.md 3-4）。
- **急ぎ度**: core の `journey/PaceAdvisor`。現在地から出発地点（往路: 南吉成、復路: 宝木駅）までの直線距離 × 1.25 を道なりの距離とし、
  発車までの残り時間（改札・ホームまで 1 分を差し引く）と比べて 歩き（80 m/分）/ 早歩き（95 m/分）/ 走る（140 m/分）/ 間に合わない を判定する。
  速度は 2026-09-07 の実測（勤務先 → 宝木駅 約 1.4 km を早歩きで 15 分ちょうど）に合わせた。
  アイコンは `ic_walk`（緑）/ `ic_walk_fast`（橙）/ `ic_run`（赤）/ `ic_warning`（灰）。120 m 以内なら「〜にいます」。
- **時刻表ポップアップ**: バス / JR カードをタップすると `ModalBottomSheet` に、現在時刻から一番近い便（ハイライト + 残り時間）と
  それ以降の便を最大 20 本出す。今日の残りが 5 本未満なら翌日の始発から 5 本を続ける。行部品は時刻表画面と共有
  （`ui/timetable/TimetableRows.kt`、エントリの組み立ては `TimetableCatalog`）。

## 復路の段階（宝木駅エリアを離れたらバスを主役に）

core の `journey/InboundPhaseResolver`: 復路で現在地が宝木駅から 2 km を超えて離れていれば `TO_BUS`（乗車中・鳥取駅到着後）。
このときホームは電車の時刻を出さず、`JourneyPlanner.nextBuses`（JR と無関係に、現在時刻以降の鳥取駅発 用瀬・智頭方面の便）の
先頭を主役にする: 発車までの秒カウントダウン、GTFS-RT の推定遅延、鳥取駅（バスターミナル）までの急ぎ度、区間カード、自宅到着予測、
次のバス。地図の「乗るバス」もこの便になる。宝木駅から 2 km 以内（勤務先・駅）や位置が無いときは従来どおり宝木発の電車が主役。

## 出発時刻を出す時間帯（ホーム）

「家を出る時刻」「職場を出る時刻」は決まった時間帯にだけ主役カードに出す（core の `journey/LeaveDisplayPolicy`、テストあり）。
それ以外の時間は最初の便（往路はバス、復路は JR）の発車時刻と残り時間を主役にし、「家を出る」「職場を出る」の文字は出さない。
「次の候補」「代替案」の要約からも出発時刻を外す。

| 向き | 出す条件 | 既定値 |
| --- | --- | --- |
| 往路（家を出る） | 現在時刻が表示開始以上・表示終了未満 | 05:30〜06:50（6:48 発のバスに乗る前提） |
| 復路（職場を出る） | 表示開始以降、かつ案のアンカー（宝木発の JR 便）が今日のうち（= 終電まで）、かつ現在地が勤務先から「鳥取駅までの距離 − 1.5 km」（下限 3 km）以上離れていない | 17:00 開始 |
| 両方 | **出発時刻そのものをまだ過ぎていない**（`LeaveDisplayPolicy.stillAhead`） | — |

最後の条件が無いと、出発時刻を過ぎた後に「17:45 / 発車しました」のように **過ぎた時刻と役に立たない文言** が
主役のまま残ってしまう（2026-09-15 報告）。過ぎたら次の便（バス / JR）の発車までの残り時間に切り替え、
脚注に「職場を出る時刻（17:45）は過ぎています」と出す。

時刻はいずれも設定画面「出発時刻の表示」で 5 分刻みに変更できる（DataStore、Wear へも同期）。
位置が取れないときは時刻だけで判定する。Wear のタイルとウィジェットはこの規則を適用していない（従来どおり常に出発時刻を出す）。

## 休止（夜〜翌朝）

夜、鳥取駅を離れて帰路についたら（または自宅側にいたら）残り時間の表示をやめ、翌朝の表示開始時刻（既定 05:30）から再開する
（core の `journey/RestPolicy`、テストあり）。判定は「朝の時間帯（leaveHomeDisplayStart 以上 inboundWindowStart 未満）の外」かつ
「南吉成から 1.2 km 以内、または復路で鳥取駅から 1 km 超離れて鳥取駅〜南吉成の間にいる」。休止中は主役カードの代わりに
「本日の通勤はおつかれさまでした」と翌朝の往路（バスの発車と家を出る時刻）だけを出し、地図はそのまま残す。

## 地図（ホーム中央）

`ui/home/RouteMapCard.kt`。下地は **建物や店を描かない、道が主役のラスタタイル**
（CARTO Dark のラベル無し。元データは OpenStreetMap）。その上に経路上の地点・現在地・バスの位置を重ねる。
通勤で見たいのは道の形だけなので、建物の輪郭・店のアイコン・地名は下地に持たせず、必要な地点はアプリ側で描く。
サーバーは持たない（CLAUDE.md 3-5）: タイルは表示中にだけ取得し、端末内にキャッシュする
（`ui/map/MapTileLoader`: メモリ 64 枚 + `cacheDir/map_tiles/<種類>` 60 MB、14 日で取り直し、同時 2 本、User-Agent 明示）。
通信できないときはキャッシュ済みのタイルだけを使い、1 枚も無ければ方眼の簡易地図になる。
配信側の都合で取れなくなったとき（返事は来るのに 4xx/5xx）は OSM 標準タイルに切り替える（`MapTileStyle`）。
圏外では切り替えない。色の作り方も種類で変える（暗い下地はそのまま持ち上げ、白地の OSM は反転して暗くする）。
出典「© OpenStreetMap contributors © CARTO」を地図の右下と About 画面に出す（ODbL / 各利用規約で必須）。

- 地点は core の `geo/RouteLandmarks`（自宅 = 設定で登録した位置か `Places.HOME_DEFAULT`、南吉成 = GTFS の HOME 停留所、
  鳥取駅 = GTFS の STATION 停留所（バスターミナル）、宝木駅 = `Places.HOUGI_STATION`、勤務先 = 設定で登録した位置か `Places.WORKPLACE_DEFAULT`）。
  自宅 → 南吉成 は本人が描いた徒歩ルート `Places.HOME_TO_MINAMIYOSHINARI_WALK`（約 240 m）を灰の破線で描き、「徒歩 n 分」を添える
  （自宅が既定位置から 200 m 以内のとき）。
- 投影は core の `geo/MapProjection`（Web メルカトル = タイルと同じ。`geo/WebMercator` にタイル番号の計算。テストあり）。
  全地点が余白つきで収まる縮尺を選び、左下に縮尺バー（地上距離で 50 m〜50 km のきりのよい値）を出す。
  タイルのズームは「1 タイルが画面上で 256 × density × 0.8 px」になる値を選ぶ。
  既定の下地は @2x（512px）で取るので、高密度画面でも道の線がぼやけない。地図 1 枚あたり 6〜12 タイル。
- 初期表示は**現在地に近い側**だけを拡大する（`RouteLandmarks.sideFor`）: 最寄りの地点が 4 km 以内なら
  その側（自宅側 = 南吉成・鳥取駅 / 勤務先側 = 宝木駅・勤務先）、4 km 超なら移動中とみなして経路全体、40 km 超（出張先など）や
  位置が無いときは向きで決める（往路 → 自宅側、復路 → 勤務先側）。経路全体（約 14 km）を常に出すと自宅側の 2 点が重なるため。
  右上のボタンで全体表示と切り替える。現在地は近くにいるときだけ画面に収め、遠いときは縁に点を置いて距離だけ示す。
- GTFS-RT の車両位置（`RealtimeState.vehicles`。対象区間を走る便だけ）が取れていれば、バスも橙の丸で描く。
  乗る予定の便は大きく「乗るバス 約 n 分遅れ（推定）」のラベル付きで、焦点から 12 km 以内なら画面に収める
  （画面外なら縁に寄せて「乗るバスは画面外（距離）」）。他の便は小さな丸だけ。右上に取得時刻。取得は従来どおり
  ホーム表示中・バス発車 90 分前〜到着 5 分後だけ（30 秒制限は `FetchThrottle`）。
- 経路線はバス区間を橙、JR 区間を青（`Places.TOTTORI_TO_HOUGI_RAIL`: 山陰本線の線路をなぞった折れ線。鳥取〜鳥取大学前は
  OSM タイルから、末恒〜宝木は駅位置からの推定）、勤務先までの徒歩を灰の破線で描く。勤務先が既定位置（気高電機付近）から 200 m 以内なら、
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
