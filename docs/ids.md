# GTFS-JP の対象 ID（日ノ丸自動車 用瀬智頭線）

`tools/gtfs_import.py discover` で判明した ID を記録する。
コード側では `tools/gtfs_config.json`（変換ツール）と、生成された DB の `meta` テーブル
（`route_ids` / `home_stop_ids` / `station_stop_ids`）を通じてアプリに渡す。
Kotlin コードに stop_id / route_id を直接書かないこと。

## 状態: **未確定**

実データの GTFS-JP ZIP は、この開発環境からは取得できなかった
（`odp-pref-tottori.tori-info.co.jp` への接続がネットワークポリシーで拒否される）。
以下の手順を実データで実行し、結果をこのファイルに転記する。

## 確定手順

1. https://odp-pref-tottori.tori-info.co.jp/bus.html から日ノ丸自動車の GTFS-JP ZIP を取得し、
   `tools/gtfs/` に置く（このディレクトリは git 管理外）。
2. 候補を報告させる。

   ```sh
   python3 tools/gtfs_import.py discover tools/gtfs/<ファイル名>.zip
   ```

   - 「路線候補」に用瀬智頭線（系統 91〜95）以外が混ざっていないか確認する。
     混ざる場合は `--route-pattern` を絞るか、確定後に `route_ids` を明示する。
   - 「停留所候補」で `[HOME]` が南吉成 1 件、`[STATION]` が鳥取駅の乗り場ぶんになること。
     「対象路線は使用しない」と出た停留所（例: 鳥取駅南口）は自動的に除外される。
   - 「路線 × direction_id」で、どの direction_id が 南吉成→鳥取駅 / 鳥取駅→南吉成 に
     対応するかを読み取る。**方向判定は direction_id ではなく stop_sequence で行う**ので、
     direction_id は記録のみでよい。
3. 確認できたら設定を書き出し、内容を確認して保存する。

   ```sh
   python3 tools/gtfs_import.py discover tools/gtfs/<ファイル名>.zip --write-config tools/gtfs_config.json
   ```

4. 下の表を埋める。

## 確定した ID

| 項目 | 値 | 備考 |
| --- | --- | --- |
| feed_version | （未確定） | feed_info.txt |
| agency_id | （未確定） | agency.txt |
| route_id（用瀬智頭線） | （未確定） | 系統 91〜95 が別 route_id に分かれている可能性あり |
| direction_id: 南吉成→鳥取駅 | （未確定） | 記録のみ。判定には使わない |
| direction_id: 鳥取駅→南吉成 | （未確定） | 同上 |
| stop_id: 南吉成 | （未確定） | 上下線で stop_id が分かれる可能性あり（両方記録） |
| stop_id: 鳥取駅（乗り場ごと） | （未確定） | 対象路線が使う乗り場のみ |

## 参考: 合成サンプル（tools/testdata/sample_gtfs）

テスト用の架空データ。実データの ID とは無関係。

| 項目 | 値 |
| --- | --- |
| route_id | R_MOCHIGASE_91, R_MOCHIGASE_93 |
| stop_id: 南吉成 | S_MINAMIYOSHINARI |
| stop_id: 鳥取駅 | S_TOTTORI_EKI_1（1番のりば）, S_TOTTORI_EKI_5（5番のりば） |
