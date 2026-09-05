# GTFS-JP の対象 ID（日ノ丸自動車 用瀬智頭線）

`tools/gtfs_import.py discover` で判明した ID を記録する。
コード側では `tools/gtfs_config.json`（変換ツール）と、生成された DB の `meta` テーブル
（`route_ids` / `home_stop_ids` / `station_stop_ids`）を通じてアプリに渡す。
Kotlin コードに stop_id / route_id を直接書かないこと。

## 状態: **確定**（2026-09-05、CI の Fetch GTFS で取得）

取得元: https://odp-pref-tottori.tori-info.co.jp/bus.html → `bus_data/2.zip`（日ノ丸自動車、作成: ジョルダン株式会社）。
feed_version 2.0、有効期間 2026-08-01〜2027-01-31。取得の記録は docs/gtfs_fetch_report.md。
再取得は `tools/gtfs_source.json` の `fetch_nonce` を増やして push する。

## 確定した ID

| 項目 | 値 | 備考 |
| --- | --- | --- |
| feed_version | 2.0 | feed_info.txt |
| agency_id | 7270001000651 | 日ノ丸自動車 |
| route_id（用瀬智頭線） | R310100111 | route_short_name は空、route_long_name が「用瀬智頭線」。系統 91〜95 の区別は無い |
| direction_id: 南吉成→鳥取駅 | 1 | headsign「鳥取駅」。記録のみ（判定は stop_sequence） |
| direction_id: 鳥取駅→南吉成 | 0 | headsign「用瀬」「智頭駅前」「栃原」 |
| stop_id: 南吉成（鳥取駅方面） | S310100077700100 | 往路の乗車 |
| stop_id: 南吉成（用瀬方面） | S310100077700200 | 復路の降車 |
| stop_id: 鳥取駅（降車） | S310100000100600 | 往路の降車。乗り場番号なし |
| stop_id: 鳥取駅 9 番のりば | S310100000100300 | 復路の乗車（一部の往路便もここで降車） |
| service_id: 平日 | S000002 | 月〜金 |
| service_id: 土日祝 | S000001 | 土日。祝日・盆は calendar_dates で振替 |
| service_id: 毎日 | S000006 | 少数の便 |

鳥取駅の他の乗り場（0・4・5・8 番、S000300221500100）は用瀬智頭線が使わないため除外している。

## GTFS-RT（VehiclePosition）の取得 URL

| 項目 | 値 | 備考 |
| --- | --- | --- |
| VehiclePosition URL | https://odp-pref-tottori.tori-info.co.jp/bus_data/2_rt.json | ポータルの表で 2.zip と同じ行。**protobuf ではなく JSON 表現**（snake_case）。`data/realtime/RealtimeEndpoints.kt` に設定済み |
| 取得間隔 | 30 秒に 1 回まで | 提供元の明示条件。`FetchThrottle` で保証 |
| trip_id の対応 | 確認済み | RT の trip_id（例 T3101000994）は GTFS-JP の trip_id と同じ体系。stop_sequence も双方 0 起点 |
| 配信の形 | JSON | `current_stop_sequence` は文字列（"16"）、`current_status` は無し、`vehicle.id`・`stop_id`・`timestamp` あり。サンプルは docs/gtfs_fetch_report.md |

## 参考: 合成サンプル（tools/testdata/sample_gtfs）

テスト用の架空データ。実データの ID とは無関係。

| 項目 | 値 |
| --- | --- |
| route_id | R_MOCHIGASE_91, R_MOCHIGASE_93 |
| stop_id: 南吉成 | S_MINAMIYOSHINARI |
| stop_id: 鳥取駅 | S_TOTTORI_EKI_1（1番のりば）, S_TOTTORI_EKI_5（5番のりば） |
