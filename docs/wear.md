# Wear OS（wear モジュール）

CLAUDE.md 7-4 の実装メモ。`wear` は `data` に依存し、同梱データ（プリパッケージ DB と JR JSON）で
時計単独でも乗り継ぎを計算する。計算は core なのでスマホと同じ結果になる。

## 構成

| 部品 | ファイル | 内容 |
| --- | --- | --- |
| タイル | `tile/CommuteTileService` | 家（職場）を出る時刻、残り時間、ステータス色のチップ、バス/JR の発着。タップでアプリを開く。残り時間は ProtoLayout の動的式でレンダラーが毎分更新し、非対応レンダラーには静的文字列を出す。更新間隔 60 秒 |
| コンプリケーション | `complication/LeaveCountdownComplicationService` | SHORT_TEXT の残り時間のみ。`TimeDifferenceComplicationText` で文字盤側がカウントダウンする。10 分ごとに次の便へ切り替え |
| UI | `ui/WearHomeScreen` | タイル・コンプリケーションのタップ先。出発時刻、バス/JR、余裕、到着予測、往路/復路の手動切替、同期時刻 |
| 同期受信 | `sync/WearDataListenerService` | スマホからの設定（`/timtra/settings`）を受け取り、時計側の DataStore を置き換え、タイルとコンプリケーションの更新を要求 |
| 計算 | `WearJourneyProvider` | `JourneyRepository` を使って現在の案を求める共通入口 |

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
