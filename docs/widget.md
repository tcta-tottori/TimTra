# ホーム画面ウィジェット（app/widget、Glance）

CLAUDE.md 12-7。家を出る時刻、更新時点の残り時間、ステータス色、バス/JR の発時刻を 2x2 で表示する。タップでアプリを開く。

## 更新のタイミング（常駐・ポーリングなし）

| きっかけ | 実装 |
| --- | --- |
| 30 分ごと | `commute_widget_info.xml` の `updatePeriodMillis`（システムの最小間隔） |
| 通知が鳴った時刻（出発 10 分前・出発・発車 3 分前・鳥取駅到着 2 分前） | `NotificationAlarmReceiver` → `CommuteWidgetUpdater.updateAll` |
| 前夜の再計算・アプリ起動・設定変更 | `NotificationScheduler.replan()` の最後 |
| ウィジェットの「更新」タップ | `RefreshWidgetAction` |

ウィジェットは自動でカウントダウンできないため、残り時間は「hh:mm 更新」の時点の値として表示する。
出発前後は通知の時刻に合わせて描き直されるので、実用上はそこが最新になる。

## 実装メモ

- Glance 1.2.0（`glance-appwidget`, `glance-material3`）。`GlanceAppWidget.provideGlance` で Hilt の EntryPoint から
  `WidgetSnapshotProvider` を取り出し、core の計算結果を描画する。
- リアルタイム情報（GTFS-RT）はウィジェットでは使わない（バックグラウンドで取得しないため）。
- 未検証: この環境では Android の依存を解決できないため、Glance の API（`cornerRadius`, `GlanceTheme.colors.widgetBackground`,
  `actionRunCallback`）は実機で確認する。
