# プリパッケージ DB スキーマ（timtra_gtfs.db）

`tools/gtfs_import.py build` が生成し、Room の `createFromAsset()` で開く SQLite DB。
Room は起動時にエンティティ定義と実 DB のスキーマ（列名・型・NOT NULL・主キー順・インデックス名）を
突き合わせるため、**ここに書いたとおりにエンティティを定義する**こと。
変更するときは `SCHEMA_VERSION`（gtfs_import.py）・このファイル・Room の `@Database(version)` を同時に更新する。

- 時刻はすべて「サービス日の 0 時起点の秒数」(INTEGER)。深夜便は 86400 以上になる（GTFS の `24:15:00` → 87300）。
- 日付は GTFS のまま `yyyyMMdd` の TEXT。
- `room_master_table` は含めない（Room が初回オープン時に検証して作成する）。
- インデックス名は Room の既定命名 `index_<table>_<column>` に合わせている。
  エンティティ側で `@Entity(indices = [Index("route_id")])` のように**同じものを宣言する**必要がある。
  宣言が過不足すると Room の検証で失敗する。
- DEFAULT 句は使わない。

## meta

| 列 | 型 | 説明 |
| --- | --- | --- |
| key | TEXT PK | |
| value | TEXT NOT NULL | |

キー: `schema_version`, `tool_version`, `generated_at`(ISO8601 UTC), `source_file`,
`feed_publisher_name`, `feed_version`, `feed_start_date`, `feed_end_date`, `agency_names`,
`route_ids`(JSON 配列), `home_stop_ids`(JSON 配列), `station_stop_ids`(JSON 配列)。

## stops

| 列 | 型 | 説明 |
| --- | --- | --- |
| stop_id | TEXT PK | |
| stop_name | TEXT NOT NULL | |
| stop_lat | REAL | null 可 |
| stop_lon | REAL | null 可 |
| platform_code | TEXT | 乗り場番号。null 可 |
| parent_station | TEXT | null 可 |
| role | TEXT | `HOME`(南吉成) / `STATION`(鳥取駅) / null |

対象路線の便が停車するすべての停留所を含む（GTFS-RT の車両位置と予定位置の比較に使う）。

## routes

| 列 | 型 |
| --- | --- |
| route_id | TEXT PK |
| agency_id | TEXT |
| route_short_name | TEXT NOT NULL |
| route_long_name | TEXT NOT NULL |

## trips

| 列 | 型 |
| --- | --- |
| trip_id | TEXT PK |
| route_id | TEXT NOT NULL |
| service_id | TEXT NOT NULL |
| direction_id | INTEGER（null 可） |
| trip_headsign | TEXT NOT NULL（空文字あり） |
| block_id | TEXT |
| shape_id | TEXT |

インデックス: `index_trips_route_id (route_id)`, `index_trips_service_id (service_id)`

## stop_times

| 列 | 型 |
| --- | --- |
| trip_id | TEXT, PK 1 |
| stop_sequence | INTEGER, PK 2 |
| stop_id | TEXT NOT NULL |
| arrival_secs | INTEGER NOT NULL |
| departure_secs | INTEGER NOT NULL |
| pickup_type | INTEGER NOT NULL |
| drop_off_type | INTEGER NOT NULL |
| timepoint | INTEGER（null 可） |

インデックス: `index_stop_times_stop_id (stop_id)`

## calendar

| 列 | 型 |
| --- | --- |
| service_id | TEXT PK |
| monday … sunday | INTEGER NOT NULL（0/1） |
| start_date | TEXT NOT NULL (yyyyMMdd) |
| end_date | TEXT NOT NULL (yyyyMMdd) |

## calendar_dates

| 列 | 型 |
| --- | --- |
| service_id | TEXT, PK 1 |
| date | TEXT, PK 2 (yyyyMMdd) |
| exception_type | INTEGER NOT NULL（1=追加運行, 2=運休） |

## commute_legs（派生テーブル）

南吉成⇔鳥取駅を両方通る便について、乗車・降車の停留所と時刻をあらかじめ切り出したもの。
乗り継ぎ計算エンジンはまずこのテーブルだけを見ればよい。

| 列 | 型 | 説明 |
| --- | --- | --- |
| trip_id | TEXT PK | |
| direction | TEXT NOT NULL | `TO_STATION`(南吉成→鳥取駅, 往路) / `FROM_STATION`(鳥取駅→南吉成, 復路) |
| route_id | TEXT NOT NULL | |
| service_id | TEXT NOT NULL | 運行日判定は calendar / calendar_dates と突き合わせる |
| board_stop_id | TEXT NOT NULL | 乗車停留所 |
| alight_stop_id | TEXT NOT NULL | 降車停留所（鳥取駅は乗り場ごとに異なる） |
| board_departure_secs | INTEGER NOT NULL | 乗車停留所の発時刻 |
| alight_arrival_secs | INTEGER NOT NULL | 降車停留所の着時刻 |

インデックス: `index_commute_legs_direction (direction)`

方向は `direction_id` ではなく stop_sequence の前後関係から決めている。
