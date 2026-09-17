# Wear OS（wear モジュール）

CLAUDE.md 7-4 の実装メモ。`wear` は `data` に依存し、同梱データ（プリパッケージ DB と JR JSON）で
時計単独でも乗り継ぎを計算する。計算は core なのでスマホと同じ結果になる。

## 構成

| 部品 | ファイル | 内容 |
| --- | --- | --- |
| タイル（ウィジェット） | `tile/TimetableTileService` | アプリのホームと同じ見た目を ProtoLayout で組む。タップでアプリを開く。更新間隔 60 秒 |
| コンプリケーション | `complication/LeaveCountdownComplicationService` | 次の便までの残り時間。`TimeDifferenceComplicationText` で文字盤側がカウントダウンする。SHORT_TEXT / RANGED_VALUE / LONG_TEXT。10 分ごとに次の便へ切り替え。クラス名は設定済みのコンプリケーションを壊さないため据え置き |
| コンプリケーション | `complication/NextDepartureComplicationService` | 次の便の**発車時刻**（H:MM）。SHORT_TEXT / LONG_TEXT |
| コンプリケーション | `complication/Date*ComplicationService` | ウォッチフェイスの**日付**（絵）。曜日あり日本語 / 英語 / 曜日なしの 3 つ |
| コンプリケーション | `complication/DateTextComplicationService` | 同じ日付を文字で。画像を受け付けない枠のための控え |
| UI | `ui/WearBoardScreen` | ホーム（1 画面）・この先の発車・時刻表・メニューの 4 画面 |
| 同期受信 | `sync/WearDataListenerService` | スマホからの設定（`/timtra/settings`）を受け取り、時計側の DataStore を置き換え、タイルとコンプリケーションの更新を要求 |
| 発車標 | `board/WearBoardProvider` | 地点を決めて `DepartureBoardRepository` から便を引く。各画面とタイル・コンプリケーションの共通入口 |
| 位置 | `location/WearLocationProvider` | 現在地を 1 回だけ取る。発車標の地点決めにしか使わない |

時計は **発車標だけ** を扱う。「家 / 職場を出る時刻」（`Journey`）の画面・タイルは持たない
（2026-09-15 に削除）。乗り継ぎの詳細はスマホで見る。

## 画面（`BoardStep`）

### ホーム（1 画面に収める）

2026-09-15 に提供されたモックをそのまま写している。スクロールさせず、上から順に:

1. 現在時刻（`TimeText`）
2. 📍（青）+ 地点名（大きく太く）
3. 行き先・路線（1 行）
4. 左に「次の便まで」+ 残り時間（`分:秒` の 4 桁。切り出した字で書く）、右にリングで囲んだバス / 電車アイコン
5. 画面下部の発時刻（`行き先 行き` + `H:MM`）

発時刻は角丸カードに乗せない。丸い文字盤では隅が切れて見栄えが悪いので、
画面の横幅いっぱいに敷いた **下からのグロー**（`WearColors.departureGlow`）の上に置く。
下端がいちばん明るく、中央へ向かって透明に消える縦グラデーション。

同じ見た目をタイルでも `ProtoLayout` で組んでいる（`Arc` + `ArcLine` でリング、
`Image` + `ColorFilter` でアイコン）。寸法は `TimetableTileService` の companion にまとめてある。
タイルはグラデーションを敷けないので、下部の地は置かず文字だけにしている。

### リング（サークルバー）

砂時計と同じで **減っていく**。計算は core の `board/CountdownGauge`（純 Kotlin、テストあり）で、
`BoardSnapshot.gauge` に載る。設定値には依らない。

| 残り時間 | リング |
| --- | --- |
| `CountdownGauge.WINDOW`（15 分）より多い | 満タンのまま |
| 15 分前 → 発車時刻 | 1 → 0 へ線形に減る |
| 発車時刻（およびそれ以降） | 0 |

描画は `Canvas` の `drawArc` を 2 本（地と残量）重ねる。

操作:

| 操作 | 行き先 |
| --- | --- |
| 下部の発時刻をタップ / リューズを時計回り | この先の発車 |
| 上から下へスワイプ / リューズを反時計回り | メニュー |

### この先の発車（ホームから開く）

**現在時刻から当日の終電まで**。時刻表（1 日分）ではなく、この先だけを出す。
開いたときは「次の便」の位置へ送る。最下部に **「時刻表を表示する」** を置き、
そこから 今日 / 平日 / 土日祝 を切り替えられる時刻表へ入る。

### 時刻表

その地点の **1 日分（始発 → 終電）**。上部に 今日 / 平日 / 土日祝 のバッジを置き、
タップで切り替える（下に実際に引いた日付を出す）。末尾に「この地点をホームに固定」。

`土日祝` は土曜と日祝のうち **直近の実在する 1 日** を引く。JR は土曜も日祝と同じダイヤだが、
バスは土曜と日祝で別ダイヤなので「その日の実際の時刻表」を出す（日付を併記しているので取り違えない）。

どちらの一覧も行の見え方は同じ:

- 現在時刻より前: グレー
- 次の便: 青く塗る
- それ以降〜終電: 通常

右に `PositionIndicator`（スクロールバー）を出し、リューズでも送れる。

### 残り時間の出し方

ホームの残り時間は時計と同じ **`分:秒` の 4 桁**（core の `board/Countdown`、テストあり）。
字は日付と同じ切り出したもの（`ui/GlyphNumber` → `data` の `GlyphText`）。

送りは **等幅**。数字はどれもいちばん広い字（`Glyphs.DIGIT_ADVANCE`）の幅で送り、字はその枠の中で
中央にそろえる。そうしないと 11:11 と 88:88 で幅が変わり、秒が進むたびに「:」と隣のアイコンが動いてしまう。
日付の絵（`DateArt`）は組み見本どおりの詰め組みなので、こちらは等幅にしない。
秒まで出すので、ホームを開いているあいだは 1 秒ごとに引き直す（リングも同じ刻みで動く）。
便そのものの入れ替えは ViewModel の 10 秒ごとの引き直しで行う。

タイルは文字盤側の更新間隔（60 秒）に縛られて秒を出せないので、`NN 分` のままにしている。

### 一覧からホームへ戻る

`ui/WearBoardScreen` の `Modifier.listNavigation` が受け持つ。

- **端で一度止めて、もう一度送る**: 端まで一気に送った勢いでそのままホームへ飛ばないよう、
  **その操作が端から始まったときだけ** 押し込み量を数える（`EdgeTravel`）。
  操作の切れ目は入力が `GESTURE_GAP_MILLIS`（350 ms）途切れたことで見る
  （リューズを止める / 指を離す）。押し込みが `OVERSCROLL_BACK_PX`（96 px）を超えたら戻る。
  - リューズ: `ScalingLazyListState.scrollBy` の戻り値（実際に送れた量）との差分を使う。
  - 指: `NestedScrollConnection.onPostScroll` の `available`（子が食べ残した量）を使う。
    `NestedScrollSource.UserInput` のときだけ数えるので、慣性スクロールで端に当たっても戻らない。
- **右へスワイプ**（Wear の swipe-to-dismiss が戻るとして届き、`BackHandler` で受ける）でも戻れる。

この操作の説明文言はアプリ内には出さない（画面が狭いので表示を優先する）。

### メニュー

駅・バス停の一覧。タップでその地点の時刻表を開く。現在地が取れていれば近い順・距離付き。

## コンプリケーション（ショートカット）

文字盤に置くものは 2 つ。どちらも地点の決め方はホームと同じ。

| データソース | 出すもの | 対応タイプ |
| --- | --- | --- |
| `LeaveCountdownComplicationService` | 次の便までの残り時間（見出しに発時刻 H:MM） | SHORT_TEXT / RANGED_VALUE / LONG_TEXT |
| `NextDepartureComplicationService` | 次の便の発車時刻 H:MM（見出しに行き先） | SHORT_TEXT / LONG_TEXT |
| `DateComplicationService` | 今日の日付（絵・曜日・日本語） | SMALL_IMAGE / PHOTO_IMAGE |
| `DateEnComplicationService` | 今日の日付（絵・曜日・英語） | SMALL_IMAGE / PHOTO_IMAGE |
| `DatePlainComplicationService` | 今日の日付（絵・曜日なし） | SMALL_IMAGE / PHOTO_IMAGE |
| `DateTextComplicationService` | 今日の日付（文字） | SHORT_TEXT / LONG_TEXT |

残り時間は `TimeDifferenceComplicationText` なので毎分の書き換えは文字盤側が行う。
アプリ側の再計算は `UPDATE_PERIOD_SECONDS`（600 秒）で、次の便へ切り替えるためだけに走る。
RANGED_VALUE の値はホームのリングと同じ `BoardSnapshot.gauge`（`CountdownGauge`）。発車が近いほど減る。

### 日付（`DateComplicationService`）

文字盤の日付の枠に入れるもの。発車標とは無関係だが、同じ文字盤に並べたいので TimTra から出す。

`complication/DateArt` が、切り出した字を並べて描く。左に「9/」と曜日を 2 段、右に大きな日にち。
地は透明。字はシステムのフォントではなく **提供された見本画像から切り出したもの** を使う。

| もの | 置き場所 |
| --- | --- |
| 見本画像（字見本・組み見本 日本語 / 英語） | `tools/date_font/` |
| 切り出し器 | `tools/date_glyphs.py`（pillow / numpy / scipy） |
| 切り出した字 26 個 | `data/src/main/assets/date/`（スマホ版とも共有） |
| 寸法表と配置の比率 | `data/.../glyph/Glyphs.kt`（自動生成。手で書き換えない） |
| 数字を書く道具 | `data/.../glyph/GlyphText.kt`（0〜9 と「:」だけ） |

切り出しでは、明るさをそのままアルファにしたうえで、色を芯（`#EAF2FF`）とグロー（`#4682EB`）の
2 色から作り直している（見本の圧縮ノイズを持ち込まないため）。隣の字が余白に入り込まないよう、
切り出す範囲は隣との中間で挟む。「/」は字見本に無いので組み見本から、形のマスクを掛けて抜いている。

並べ方は `DateArt.layout`。長さの単位は日にちの字の高さ、原点は日にちの字の左下で、
`DateGlyphs` の比率（字間・月の数字の大きさとベースライン・「/」の位置・曜日の大きさと右端）は
すべて組み見本の実測から出している。最後に組み上がり全体の **対角** を枠の 90% に合わせて拡大するので、
丸い枠でも切れずに目いっぱいの大きさになる。

見本は「9/15」なので、細い「1」に「/」を少しだけ重ねてある。3 や 8 のような丸い数字に重ねると
潰れるため、日にちの先頭が 1 のときだけ重ね、ほかは少し離す。月と「/」の食い込みも同じ考えで、
右下が開いている 4 / 7 / 9 のときだけ使う。曜日は日にちに被らないよう右端でそろえる。

配置の比率は **日本語版と英語版で別** に測ってある（`Glyphs.jp` / `Glyphs.en`）。
それぞれの組み見本で月の数字の大きさも「/」の位置も違うため。曜日なしは日本語版の組み方を使う。
文字盤に設定画面を足さずに選べるよう、3 つは別々の提供元として登録している。

ダイヤ改正のような定期作業ではないので、切り出し器はデザインを変えたときだけ実行する。

    python3 tools/date_glyphs.py

**サービスを 2 つに分けている。** 1 つの提供元が SMALL_IMAGE と SHORT_TEXT の両方を宣言すると、
両方を受け付ける枠では文字盤側が文字を選んでしまい、絵が出ない（小さな「9/水 16」になる）。
そのため `DateComplicationService` は画像の型だけを宣言し、文字は
`DateTextComplicationService`（「TimTra 日付（文字）」）に分けた。
文字のほうは `TimeFormatComplicationText` なので文字盤側が日付を描き、取り直しは要らない。

絵のほうは日が変わったら描き直しが要る。`setValidTimeRange` をその日 1 日に限って失効させ、
効かない文字盤のために manifest の `UPDATE_PERIOD_SECONDS`（1800 秒）も併せて置いている。

## 地点の決め方（`PlaceBasis`）

1. **MANUAL**: 時刻表の「この地点をホームに固定」で選んだ地点。DataStore に残るのでタイルにも効く。
2. **NEAR_HERE**: 位置が取れたら最寄りの地点。`DepartureBoard.NEAR_METERS`（10 km）より遠ければ選ばない。
   鳥取駅はバスのりばと JR 駅舎が約 150 m しか離れておらず距離では選べないので、
   そこにいるときだけ時刻帯の向き（往路 / 復路）で JR かバスかを決める。
3. **TIME_OF_DAY**: 位置が取れない・通勤圏外のとき。往路なら南吉成、復路なら宝木駅。

対象は固定経路上の 4 地点（南吉成 / 鳥取駅バスのりば / JR 鳥取駅 / JR 宝木駅）。計算は core の
`board/DepartureBoard`（純 Kotlin、テストあり）。最終便の後は翌日以降の初便へ自然に続き、
バスは前日のサービス日に属する深夜便（24:15 など）も拾う。

タイルはバックグラウンドで描かれるため位置が取れないことがある（Android 10 以降の背景位置の制限）。
その場合は上の 2 → 3 に落ちるので、よく使う地点は時刻表から固定しておくとタイルも安定する。

## スマホ → 時計 の同期（Wearable Data Layer）

- スマホ側 `app/sync/WearSyncPublisher` が、通知の再計算（起動時・設定変更・夜間ジョブ）のたびに
  `data/sync/SyncedSettings`（JSON）を DataItem として送る。
- 同期するのは **設定と「今日は休み」** だけ。計算結果（Journey）は送らない。同梱データと core が共通なので、
  設定が一致していれば時計側の計算はスマホと一致する。時計が未接続でも同梱データで動く。
- Data Layer を使うため、スマホ版と時計版は **同じ applicationId（com.kazuya.timtra）と同じ署名** にする。
  `wear` の `namespace` は `com.kazuya.timtra.wear`（R クラスの衝突回避）。

## 同梱データの配置変更

JR 時刻表 JSON は CLAUDE.md 4-3 で `app/src/main/assets` と指定されているが、Wear と共有するため
`data/src/main/assets/jr_timetable.json` に置いている（ライブラリの assets は app / wear の両方に取り込まれる）。
プリパッケージ DB も同じ場所。

## 未検証の点

この環境では Android 系の依存を解決できないため、`wear` もコンパイル未確認。特に確認が要る箇所:

- `SuspendToFutureAdapter.launchFuture` による `onTileRequest` の非同期化
- `SuspendingComplicationDataSourceService` の `ShortTextComplicationData.Builder(text =, contentDescription =)`
- Wear Compose Material（1.6.2）の `Scaffold` / `ScalingLazyColumn` / `Chip`
- `WearableListenerService` の manifest（`DATA_CHANGED` + `pathPrefix="/timtra/"`）
- 時計での位置取得（`LocationManagerCompat.getCurrentLocation`）とタイルからの取得可否
- リューズ（`Modifier.onRotaryScrollEvent` + `focusable`）と、そこからの `ScalingLazyListState.scrollBy`
- 端での戻り判定（`NestedScrollConnection.onPostScroll` の `available` と `NestedScrollSource.UserInput`）
- 右へスワイプが `BackHandler` に届くか（`Theme.DeviceDefault` の swipe-to-dismiss）
- `Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = …) })`
- タイルの `Arc` / `ArcLine`（リング）と `Image.setColorFilter`、`Background.setCorner`
- コンプリケーションの RANGED_VALUE / LONG_TEXT（`RangedValueComplicationData.Builder(value, min, max, contentDescription)`）
