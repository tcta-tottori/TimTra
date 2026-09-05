# GTFS 取得レポート（CI 自動生成）

- 取得日時: 2026-09-05T23:12:01Z
- ポータル: https://odp-pref-tottori.tori-info.co.jp/bus.html
- GTFS-JP ZIP: https://odp-pref-tottori.tori-info.co.jp/bus_data/2.zip
- GTFS-RT VehiclePosition: （見つからず）

## リンク候補

```json
{
  "page_url": "https://odp-pref-tottori.tori-info.co.jp/bus.html",
  "link_count": 53,
  "gtfs_zip": "https://odp-pref-tottori.tori-info.co.jp/bus_data/2.zip",
  "rt_vehicle_positions": null,
  "zip_candidates": [
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/2.zip",
      "text": "データ",
      "score": 4
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/3.zip",
      "text": "データ",
      "score": 4
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/1.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/4.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/5.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/6.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/7.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/8.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/9.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/11.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/12.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/13.zip",
      "text": "データ",
      "score": 2
    },
    {
      "url": "https://odp-pref-tottori.tori-info.co.jp/bus_data/14.zip",
      "text": "データ",
      "score": 2
    }
  ],
  "rt_candidates": []
}
```

## discover の出力

```
# GTFS discover: hinomaru_gtfs.zip
feed_publisher_name=ジョルダン株式会社 feed_version=2.0 feed_start_date=20260801 feed_end_date=20270131
agency: 7270001000651 日ノ丸自動車

## 路線候補 (routes.txt)
  route_id=R310100111 short='' long='用瀬智頭線' desc='' parent=''

## 停留所候補 (stops.txt, 名前一致)
  [STATION] stop_id=S310100000100100 name='鳥取駅' platform='０' parent='' lat=35.494789790591 lon=134.225516142651 -> 対象路線は使用しない
  [STATION] stop_id=S310100000100200 name='鳥取駅' platform='５' parent='' lat=35.4951282684346 lon=134.22430096586 -> 対象路線は使用しない
  [STATION] stop_id=S310100000100300 name='鳥取駅' platform='９' parent='' lat=35.4949633608037 lon=134.224405128253 -> R310100111×47
  [STATION] stop_id=S310100000100400 name='鳥取駅' platform='４' parent='' lat=35.495084874727 lon=134.224509281425 -> 対象路線は使用しない
  [STATION] stop_id=S310100000100500 name='鳥取駅' platform='８' parent='' lat=35.4949112870566 lon=134.224578725066 -> 対象路線は使用しない
  [STATION] stop_id=S310100000100600 name='鳥取駅' platform='' parent='' lat=35.4949199815792 lon=134.225481419499 -> R310100111×45
  [STATION] stop_id=S000300221500100 name='鳥取駅' platform='' parent='' lat=35.4949112916958 lon=134.224856477284 -> 対象路線は使用しない
  [HOME] stop_id=S310100077700100 name='南吉成' platform='' parent='' lat=35.4801735184003 lon=134.218538070166 -> R310100111×45
  [HOME] stop_id=S310100077700200 name='南吉成' platform='' parent='' lat=35.4792361336758 lon=134.218295065861 -> R310100111×46

## 路線 × direction_id と、南吉成/鳥取駅の順序 (trips.txt + stop_times.txt)
  route_id=R310100111 direction_id=0: trips=46 南吉成→鳥取駅=0 鳥取駅→南吉成=46 両方に停車しない=0
      headsign: 用瀬×26, 智頭駅前×19, 栃原×1
  route_id=R310100111 direction_id=1: trips=45 南吉成→鳥取駅=45 鳥取駅→南吉成=0 両方に停車しない=0
      headsign: 鳥取駅×45

## tools/gtfs_config.json の候補（確認のうえ保存）
{
  "route": {
    "route_ids": [
      "R310100111"
    ],
    "patterns": [
      "用瀬智頭線"
    ]
  },
  "home_stop": {
    "stop_ids": [
      "S310100077700100",
      "S310100077700200"
    ],
    "patterns": [
      "南吉成"
    ]
  },
  "station_stop": {
    "stop_ids": [
      "S310100000100300",
      "S310100000100600"
    ],
    "patterns": [
      "鳥取駅"
    ]
  },
  "keep_trips": "either"
}

設定候補を書き出しました: build/gtfs/gtfs_config.suggested.json
```

## build の出力

```
警告: 名前は一致するが対象路線が使わないため除外: S000300221500100 鳥取駅
警告: 名前は一致するが対象路線が使わないため除外: S310100000100100 鳥取駅
警告: 名前は一致するが対象路線が使わないため除外: S310100000100200 鳥取駅
警告: 名前は一致するが対象路線が使わないため除外: S310100000100400 鳥取駅
警告: 名前は一致するが対象路線が使わないため除外: S310100000100500 鳥取駅
書き出し: data/src/main/assets/timtra_gtfs.db  routes=1 stops=169 trips=91 stop_times=4182 calendar=3 calendar_dates=22 commute_legs=91
route_ids=['R310100111'] home_stop_ids=['S310100077700100', 'S310100077700200'] station_stop_ids=['S310100000100300', 'S310100000100600'] legs: 南吉成→鳥取駅=45 鳥取駅→南吉成=46
```
