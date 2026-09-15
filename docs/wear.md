# Wear OS（wear モジュール）

CLAUDE.md 7-4 の実装メモ。`wear` は `data` に依存し、同梱データ（プリパッケージ DB と JR JSON）で
時計単独でも乗り継ぎを計算する。計算は core なのでスマホと同じ結果になる。

## 構成

| 部品 | ファイル | 内容 |
| --- | --- | --- |
| タイル | `tile/TimetableTileService` | アプリのホームと同じ表示（地点バッジ・行き先・「次の発車まで NN 分」・発時刻）。タップでアプリを開く。更新間隔 60 秒 |
| コンプリケーション | `complication/LeaveCountdownComplicationService` | SHORT_TEXT の「次の発車まで」。`TimeDifferenceComplicationText` で文字盤側がカウントダウンする。10 分ごとに次の便へ切り替え。クラス名は設定済みのコンプリケーションを壊さないため据え置き |
| UI | `ui/WearBoardScreen` | ホーム（1 画面）・時刻表・メニューの 3 画面 |
| 同期受信 | `sync/WearDataListenerService` | スマホからの設定（`/timtra/settings`）を受け取り、時計側の DataStore を置き換え、タイルとコンプリケーションの更新を要求 |
| 発車標 | `board/WearBoardProvider` | 地点を決めて `DepartureBoardRepository` から便を引く。3 画面とタイル・コンプリケーションの共通入口 |
| 位置 | `location/WearLocationProvider` | 現在地を 1 回だけ取る。発車標の地点決めにしか使わない |

時計は **発車標だけ** を扱う。「家 / 職場を出る時刻」（`Journey`）の画面・タイルは持たない
（2026-09-15 に削除）。乗り継ぎの詳細はスマホで見る。

## 画面（`BoardStep`）

### ホーム（1 画面に収める）

スクロールさせず、上から順に:

1. 現在時刻（`TimeText`）
2. 地点バッジ（📍 + 地点名）
3. 行き先・路線
4. 大きな丸アイコン（バス / 電車）と「次の発車まで NN 分」
5. 下部に発時刻（`H:MM 発` + 行き先・路線）

操作:

| 操作 | 行き先 |
| --- | --- |
| 下部の発時刻をタップ / リューズを時計回り | 時刻表 |
| 上から下へスワイプ / リューズを反時計回り | メニュー |

### 時刻表

その地点の **1 日分（始発 → 終電）**。開いたときは「次の便」の位置へ送る。

- 現在時刻より前: グレー
- 次の便: 青く塗る
- それ以降〜終電: 通常

上部に 今日 / 平日 / 土日祝 のバッジを置き、タップで切り替える（下に実際に引いた日付を出す）。
右に `PositionIndicator`（スクロールバー）、リューズでも送れる。末尾に「この地点をホームに固定」。
戻るは **右へスワイプ**（Wear の swipe-to-dismiss が戻るとして届き、`BackHandler` で受ける）。

`土日祝` は土曜と日祝のうち **直近の実在する 1 日** を引く。JR は土曜も日祝と同じダイヤだが、
バスは土曜と日祝で別ダイヤなので「その日の実際の時刻表」を出す（日付を併記しているので取り違えない）。

### メニュー

駅・バス停の一覧。タップでその地点の時刻表を開く。現在地が取れていれば近い順・距離付き。

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
- 右へスワイプが `BackHandler` に届くか（`Theme.DeviceDefault` の swipe-to-dismiss）
- `Scaffold(positionIndicator = { PositionIndicator(scalingLazyListState = …) })`
