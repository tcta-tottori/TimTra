# Wear OS（wear モジュール）

CLAUDE.md 7-4 の実装メモ。`wear` は `data` に依存し、同梱データ（プリパッケージ DB と JR JSON）で
時計単独でも乗り継ぎを計算する。計算は core なのでスマホと同じ結果になる。

## 構成

| 部品 | ファイル | 内容 |
| --- | --- | --- |
| タイル（出発） | `tile/CommuteTileService` | 家（職場）を出る時刻、残り時間、ステータス色のチップ、バス/JR の発着。タップでアプリを開く。残り時間は ProtoLayout の動的式でレンダラーが毎分更新し、非対応レンダラーには静的文字列を出す。更新間隔 60 秒 |
| タイル（時刻表） | `tile/TimetableTileService` | いまいる場所の地点名・行き先、次の発車時刻と残り時間、その後の便の時刻。タップでアプリの発車標を開く。更新間隔 60 秒 |
| コンプリケーション | `complication/LeaveCountdownComplicationService` | SHORT_TEXT の残り時間のみ。`TimeDifferenceComplicationText` で文字盤側がカウントダウンする。10 分ごとに次の便へ切り替え |
| UI（発車標） | `ui/WearBoardScreen` | **アプリの既定画面**。添付デザインに合わせた 4 状態（現在地取得中 / 最寄りの確認 / 発車標 / 駅・バス停の選択）|
| UI（出発） | `ui/WearHomeScreen` | 出発タイル・コンプリケーションのタップ先。出発時刻、バス/JR、余裕、到着予測、往路/復路の手動切替、同期時刻 |
| 同期受信 | `sync/WearDataListenerService` | スマホからの設定（`/timtra/settings`）を受け取り、時計側の DataStore を置き換え、タイルとコンプリケーションの更新を要求 |
| 計算 | `WearJourneyProvider` | `JourneyRepository` を使って現在の案を求める共通入口 |
| 発車標 | `board/WearBoardProvider` | 地点を決めて `DepartureBoardRepository` から便を引く。タイルと発車標画面の共通入口 |
| 位置 | `location/WearLocationProvider` | 現在地を 1 回だけ取る。発車標の地点決めにしか使わない |

## 発車標（時刻表タイル / 発車標画面）

「いま立っている停留所・駅から次に何時に出るか」を出す。乗り継ぎ計算とは別物で、1 区間を素直に時刻順で並べる。
計算は core の `board/DepartureBoard`（純 Kotlin、テストあり）。地点は固定経路上の 4 か所:

| 地点 | 出す時刻表 |
| --- | --- |
| 南吉成 バス停 | 鳥取駅方面のバス |
| 鳥取駅 バスのりば | 用瀬・智頭方面のバス |
| JR 鳥取駅 | 宝木方面（下り） |
| JR 宝木駅 | 鳥取方面（上り） |

地点の決め方は次の順（`PlaceBasis`）:

1. **MANUAL**: 発車標画面で手で選んだ地点。DataStore に残るのでタイルにも効く。
2. **NEAR_HERE**: 位置が取れたら最寄りの地点。`DepartureBoard.NEAR_METERS`（10 km）より遠ければ選ばない。
   鳥取駅はバスのりばと JR 駅舎が約 150 m しか離れておらず距離では選べないので、
   そこにいるときだけ時刻帯の向き（往路 / 復路）で JR かバスかを決める。
3. **TIME_OF_DAY**: 位置が取れない・通勤圏外のとき。往路なら南吉成、復路なら宝木駅。

最終便の後は翌日以降の初便へ自然に続く（先読み `TimTraConstants.MAX_LOOKAHEAD_DAYS`）。
バスは前日のサービス日に属する深夜便（24:15 など）も拾う。

タイルはバックグラウンドで描かれるため、位置が取れないことがある（Android 10 以降の背景位置の制限）。
その場合は上の 2 → 3 に落ちるので、よく使う地点は発車標画面で選んでおくとタイルも固定できる。

### 画面の流れ（`BoardStep`）

添付デザイン（2026-09-15）に合わせている。

| 状態 | 画面 |
| --- | --- |
| LOCATING | 「現在地を取得中…」。輪の中に 📍。キャンセルで選択画面へ |
| CONFIRM | 「現在地から近い駅・バス停」+ 地点名 + 距離 + 青いグラデーションの「この地点で表示」 |
| BOARD | 地点ピル（📍 + 地点名）→ 行き先 → 丸アイコン + 「次の発車まで NN 分」→ 「H:MM 発」→ 行き先と路線 → つぎ / そのつぎ のピル → 以降の便の一覧（先頭は青く塗る） |
| PICKER | 「行き先を選択」。地点をアイコン付きで並べ、現在地が取れていれば距離順・距離付き。選択中は青く塗って ✓ |

- CONFIRM は手動選択が無く位置が取れたときだけ挟む。「この地点で表示」は記憶しない（次に開けばまた現在地から選ぶ）。
- PICKER で選んだ地点は DataStore に残り、以後は MANUAL。「現在地から自動」で解除する。
- 配色は `ui/WearTheme.kt` の `WearColors`。黒地（有機 EL で消灯）に上だけ濃紺、青はスマホ版と同じ値。

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

- ProtoLayout 動的式（`DynamicInstant.durationUntil().toIntMinutes().format()`）と `setLayoutConstraintsForDynamicText`
- `SuspendToFutureAdapter.launchFuture` による `onTileRequest` の非同期化
- `SuspendingComplicationDataSourceService` の `ShortTextComplicationData.Builder(text =, contentDescription =)`
- Wear Compose Material（1.6.2）の `Scaffold` / `ScalingLazyColumn` / `Chip`
- `WearableListenerService` の manifest（`DATA_CHANGED` + `pathPrefix="/timtra/"`）
- 時刻表タイルの `ActionBuilders.AndroidActivity.addKeyToExtraMapping`（発車標を直接開く extra）
- 時計での位置取得（`LocationManagerCompat.getCurrentLocation`）とタイルからの取得可否
