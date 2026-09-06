# 通知スケジューラ（app/notify + core/notify）

CLAUDE.md 8 の実装メモ。常駐サービス・ポーリングは使わず、前夜に翌日分を計算して AlarmManager に予約する。

## 流れ

1. `NotificationScheduler.ensureScheduled()`（アプリ起動時）
   - 毎晩 23:00 頃に走る WorkManager の周期ジョブ `NightlyPlanWorker` を登録する（重複登録しない）。
   - 併せて今すぐ 1 回 `replan()` を走らせる。
2. `NightlyPlanWorker` → `NotificationScheduler.replan(now)`
   - `core/notify/DailyNotificationPlanner` で **今日の残り**（現在時刻より後の分だけ）と **明日** の通知を計算する。
   - 既存の予約（最大 16 件）をすべて取り消してから、`AlarmManager.setExactAndAllowWhileIdle()` で予約し直す。
   - 文面は予約時に確定させ、`NotificationAlarmReceiver` は受け取った文面を表示するだけにする。
   - 結果の要約（件数・抑止理由・正確なアラームで予約できたか）を DataStore に保存し、設定画面に表示する。
3. `RescheduleReceiver`: 再起動・アプリ更新・時刻/タイムゾーン変更・正確なアラーム許可の変更で `replan` を再実行する。
4. 設定変更（所要時間・通知タイミング・通知 ON/OFF・今日は休み）のたびに `replan` を再実行する。

## 通知の内容（往路）

| 種類 | 時刻 | 文面 |
| --- | --- | --- |
| LEAVE_SOON | 家を出る時刻 − 10 分 | あと10分で出発。7:05のバスです |
| LEAVE_NOW | 家を出る時刻 | 出発時刻です |
| FIRST_LEG_DEPARTING | バス発車 − 3 分 | まもなく南吉成発。乗り遅れ注意 |
| APPROACHING_TRANSFER | 鳥取駅着予定 − 2 分 | 次は鳥取駅。JRは07:33発 |

復路は「職場を出る時刻」「JR 宝木発」「JR 鳥取駅着」を基準に同じ 4 種類。
分数はすべて設定画面から変更できる（`NotificationTiming`）。

通知 ID / AlarmManager の requestCode は `PlannedNotification.idOf(bound, kind)`（往路 100〜103、復路 200〜203）に、
今日 = +0、明日 = +1000 を足したもの。

## 通知を出さない条件（`SuppressReason`）

| 理由 | 判定 |
| --- | --- |
| DISABLED | 設定で通知 OFF |
| DAY_OFF | 「今日は休み」（その日のみ。翌日に自動解除） |
| NOT_WORKDAY | JR の日種別が平日でない（土日・祝日・JSON overrides の年末年始） |
| NO_BUS_SERVICE | calendar_dates.txt でその日のバスが全便運休 |
| NO_JOURNEY | 時刻表上、案が作れない |

通知は平日のみという前提。土曜出勤などに対応するときは `DailyNotificationPlanner` の NOT_WORKDAY 判定に設定を足す。

## 権限の導線（初回起動時）

ホーム画面の上部に、次の 3 つがすべて整うまでカードを表示する（`PermissionsCard`）。

- 通知の許可（Android 13+ は実行時許可。拒否されたらアプリの通知設定へ）
- 正確な時刻の通知 `SCHEDULE_EXACT_ALARM`（Android 14+ は既定で拒否。許可画面へ誘導。未許可の間は `setAndAllowWhileIdle` で不正確に予約し、設定画面に注意を出す）
- バッテリー最適化の除外（`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`）

## テスト

- `core/notify` は JVM テストで境界を固めている（`NotificationPlannerTest` / `DailyNotificationPlannerTest`）。
- app 側は実機で確認する: 設定画面の「テスト通知を送る」でチャンネルと許可を確認し、「今すぐ再計算」で予約状況を見る。
  `adb shell dumpsys alarm | grep com.kazuya.timtra` で予約を確認できる。

## 勤務先の「次の電車」リマインダー

勤務先（気高電機）にいるときだけ、宝木発（鳥取方面）の次の電車について発車前に段階的に知らせる。

| 何分前 | 段階 | アイコン | 文言 |
|---|---|---|---|
| 30 分 | WALK | 歩く（`ic_walk`） | 歩いて向かえば間に合います |
| 20 分 | FAST_WALK | 早歩き（`ic_walk_fast`） | 早歩きで向かってください |
| 15 分 | DASH | 走る（`ic_run`） | 急いで。走らないと間に合いません |

- 対象は設定「この時刻以降の電車が対象」（既定 17:00）以降に宝木を出る電車。列挙は core の
  `notify/TrainReminderPlanner`（純 Kotlin、テストあり）。同じ電車の 3 段階は同じ通知 ID で差し替える。
- 予約は通勤通知と同じく前夜と再計算時に AlarmManager へ入れる（`app/notify/TrainReminderScheduler`）。常駐しない。
- **鳴る瞬間の判定**: `NotificationAlarmReceiver` が位置を 1 回だけ取り（最大 6 秒）、勤務先から 600 m 以内なら表示する。
  離れていれば表示せず、その日を `trainReminderOffDate` に記録して残りの予約を取り消す
  （「気高電機から離れた時点でその日の通知機能はオフ」）。位置が取れないときも表示しない。
- ホーム画面が前景で位置を見たときも、対象時間帯に勤務先から離れていれば同じように止める。
  ホームに「本日のリマインダーは終了」のバナーが出て、「再開」で戻せる。
- 勤務先の位置は設定の「現在地を勤務先に登録」で端末に保存する。未登録の間は `Places.WORKPLACE_DEFAULT`（宝木駅）で代用。
- 必要な権限: 位置情報（前景）に加えて **「常に許可」（ACCESS_BACKGROUND_LOCATION）**。
  Android 10 以降、通知が鳴る瞬間の BroadcastReceiver はバックグラウンドなので、これが無いと位置が取れずリマインダーは出ない。
  設定画面に状態と導線を置いてある。
- 通知チャンネルは `train_reminder`（IMPORTANCE_DEFAULT）。通勤通知（`commute`）とは別に音量を調整できる。
