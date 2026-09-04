# 乗り継ぎ計算エンジン（core/journey）

CLAUDE.md 6 の実装メモ。エントリポイントは `JourneyPlanner`（`com.kazuya.timtra.core.journey`）。
純粋 Kotlin/JVM で、Android SDK に依存しない。テストは `./gradlew :core:test`。

## モジュール構成の判断

CLAUDE.md 5 は `core/data` に Room DAO を置くとしているが、原則 3-3（core は Android 非依存）を優先し、
core には **モデル・計算・インメモリの時刻表・JSON パーサ** だけを置く。
Room の実装（プリパッケージ DB を開いて `BusTimetable` に詰め替える）は手順 3 で
app / wear 共有の Android ライブラリモジュール `data` に置く。
DB 行 → `BusTrip` の結合方法は `core/src/test/.../JdbcBusTimetableLoader.kt` がそのまま雛形になる。

## 入力

| 型 | 役割 | 由来 |
| --- | --- | --- |
| `BusTimetable` | 南吉成⇔鳥取駅の便（`BusTrip`）と運行日（`ServiceCalendar`） | プリパッケージ DB（docs/db_schema.md） |
| `JrTimetable` | JR 2 区間の便と日種別 overrides | app/src/main/assets/jr_timetable.json |
| `CommuteSettings` | 徒歩時間・乗換時間・準備時間・閾値・時刻帯 | 設定画面（既定値は CLAUDE.md 6） |
| `PlanRequest` | 現在時刻・往路/復路・GTFS-RT の推定遅延・乗車中の便 | UI / 通知ジョブ |

## 余裕（transferMargin）の定義

CLAUDE.md 6 の例（バス 07:24 着、JR 07:33 発で「余裕 9 分」）と計算手順（着時刻 ≤ 発 −（乗換 8 + 最低 5））は
そのままでは両立しない。**手順の方を正とし**、余裕は次で定義する。

    transferMargin = JR 発時刻 − (バス着時刻 + 推定遅延 + 乗換所要時間 transferBusToJr)

つまり「乗換に必要な歩き時間を差し引いたうえで、なお残る余裕」。閾値はこの値に対して適用する。

| transferMargin | status |
| --- | --- |
| 10 分以上 | OK |
| 5 分以上 10 分未満 | TIGHT |
| 5 分未満（負を含む） | RISK |
| 乗車中で負 | MISSED |

境界は「以上」で上のランクになる（10:00 → OK、9:59 → TIGHT、5:00 → TIGHT、4:59 → RISK）。

## 往路（OUTBOUND）

1. 現在時刻以降の JR 鳥取→宝木 を日付をまたいで列挙する（最大 10 日先読み）。
2. 各 JR 便について、締切 = 発 − transferBusToJr − minTransfer までに鳥取駅へ着く便のうち、
   現在時刻以降に南吉成を出る **最も遅い** バスを選ぶ。無ければ次の JR 便へ。
3. 推定遅延を加えて余裕を再計算し、status を決める。
4. RISK なら `fallback` に「1 本前のバス」（遅延込みでも minTransfer を満たす直前の便）を入れる。
5. `leaveAt` = バス発 − walkHomeToStop − prepBuffer。`arriveAt` = JR 着 + walkStationToWork。

`boardedTripId` を渡すと、その便に乗車中として扱う。間に合わなければ status = MISSED、
`fallback` に同じバスで乗れる次の JR 便の案が入る。

## 復路（INBOUND）

1. 現在時刻以降の JR 宝木→鳥取 を列挙する。
2. JR 着 + transferBusToJr 以降に鳥取駅を出る **最初の** バスを選ぶ。無ければ次の JR 便へ。
3. 余裕 = バス発（遅延込み）− (JR 着 + transferBusToJr)。RISK なら `fallback` は「1 本後のバス」。
4. `leaveAt` = JR 発 − walkStationToWork − prepBuffer（職場を出る時刻）。`arriveAt` = バス着 + walkHomeToStop。

## 日付・時刻の扱い

- バスの時刻は GTFS の秒数（`GtfsTime`）。24:15:00 は 87300 秒のまま持ち、サービス日を与えて絶対時刻にする。
  ある日のバスを探すときは **前日のサービス日** の便も含める（前日の 24:30 発 = 当日 00:30）。
- JR の着時刻が発時刻より早ければ翌日着（23:50 発 00:12 着）。
- 日種別: `overrides` > 日曜・祝日 > 土曜 > 平日。祝日は `JapaneseHolidays`（2022 年以降の祝日法、
  振替休日・国民の休日を含む）。年末年始は祝日ではないので JSON の `overrides` で休日ダイヤを指定する。
- バスの運行日は calendar.txt + calendar_dates.txt（`ServiceCalendar`）。calendar_dates が優先。
- 往路/復路の自動判定は `CommuteSettings.boundAt(time)`（既定: 03:00〜12:00 が往路、それ以外は復路）。

## 前夜の計算（手順 4 で使う）

`planForDate(date, bound)`:
往路は「`earliestLeaveHome`（既定 06:30）以降に家を出る」、復路は「`workEndsAt`（既定 17:30）に職場を出る」を
基準にその日の案を返す。その日に案が作れなければ（運休など）null。
