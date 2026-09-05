# GTFS-RT 連携（core/realtime + data/realtime）

CLAUDE.md 4-2 の実装メモ。鳥取県オープンデータの VehiclePosition から、対象便の遅延を推定する。

## 守っていること

- **30 秒に 1 回まで**: `core/realtime/FetchThrottle` が成功・失敗を問わず「試行」の間隔を数える。`GtfsRtClient` に内蔵。
- **フォアグラウンドのみ**: 取得はホーム画面の ViewModel の 30 秒ティッカーから呼ぶ。ティッカーは画面が購読されている間だけ動く
  （`SharingStarted.WhileSubscribed`）。バックグラウンドでは一切取得しない。
- **必要な時間帯のみ**: 選ばれたバスの発車 90 分前から到着 5 分後までしか取得しない。
- **推定であることの明示**: UI の文言はすべて「（推定）」付き。

## 遅延推定（`DelayEstimator`）

TripUpdate が無いので、車両位置と stop_times 上の予定位置を比べる。

1. `current_stop_sequence` があればそれを使う。停車中（STOPPED_AT）ならその停留所の発時刻、走行中なら直前の停留所との間に投影して補間。
2. 無ければ、停留所を結ぶ各線分に車両位置を投影し、最も近い線分の位置で予定時刻を線形補間する（正距円筒近似）。
   800 m 以上離れていればその便の車両とはみなさない。
3. 遅延 = 車両のタイムスタンプ（無ければ取得時刻）− その地点の予定時刻。3 分より古い観測は捨てる。
4. 早発（負）は乗り継ぎ計算には足さない（`delayForPlanning` は 0 に丸める）。

例（CLAUDE.md）: 予定ではすでに南吉成を出ているはずだが、車両はまだ 2 停留所手前 → その地点の予定時刻との差が遅延。

境界はテストで固めている（`DelayEstimatorTest`, `GtfsRtClientTest`, `FetchThrottleTest`）。

## 流れ

1. `RealtimeRepository.refresh()`（data）が `GtfsRtClient` で取得し、対象便（commute_legs の trip_id）の車両だけ `DelayEstimator` にかける。
   停車時刻と座標は `GtfsDao.tripStopTimes()`（stop_times + stops）。深夜便はサービス日を前日にする。
2. 結果は `RealtimeState`（status / delays / estimates / fetchedAt）として StateFlow で公開する。
3. `HomeViewModel` は `PlanRequest.delays` に `delays` を渡して再計算する（手順 3: 遅延を加算して再判定）。
   遅延で RISK になれば「1 本前のバス」の代替案が付く（docs/journey.md）。
4. ホーム画面のバスカードに「約 N 分遅れ（推定）」「到着予測 hh:mm（推定）」と取得状態を表示する。

## 取得先と形式

- URL: `https://odp-pref-tottori.tori-info.co.jp/bus_data/2_rt.json`（docs/ids.md）。
- 形式: GTFS-RT FeedMessage の **JSON 表現**（snake_case、enum は数値、`current_stop_sequence` は文字列）。
  `GtfsRtParser` は先頭が `{` なら protobuf-java-util の `JsonFormat` で読み、それ以外は protobuf バイナリとして読む。
- 実配信には `current_status` が無いので、推定は「直前区間への投影」（走行中扱い）になる。`stop_id` は次停留所。
- 県内の日ノ丸全車両が 1 本のフィードに入る（約 16 KB）。対象便（commute_legs の trip_id）だけを推定にかける。
- ライブラリ: CLAUDE.md は `com.google.transit:gtfs-realtime-bindings` を指定しているが、Maven Central の同 ID は
  0.0.4（protobuf 2.6）で止まっているため、後継の `org.mobilitydata:gtfs-realtime-bindings:0.2.0`
  （同じ `com.google.transit.realtime` パッケージ、protobuf-java 4 系）を使う。
- HTTP は OkHttp（`data/realtime/OkHttpFeedFetcher`）。core は `FeedFetcher` インターフェースだけを持つ。

## 未検証の点

data / app はこの環境でコンパイルできていない。特に OkHttp 5 系の `response.body.bytes()`（4 系では null 許容）と、
protobuf-java 4 系の Android での動作（R8 で `com.google.protobuf` の keep が必要になることがある）を実機で確認する。
