"""tools/gtfs_import.py のテスト。

実行: python3 -m unittest discover -s tools/tests -v
依存は標準ライブラリのみ。fixture は tools/testdata/sample_gtfs（合成データ）。
"""

import io
import json
import os
import shutil
import sqlite3
import sys
import tempfile
import unittest
import zipfile
from contextlib import redirect_stdout

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
sys.path.insert(0, TOOLS)

import gtfs_import as gi  # noqa: E402

SAMPLE_DIR = os.path.join(TOOLS, "testdata", "sample_gtfs")
CONFIG = os.path.join(TOOLS, "gtfs_config.json")


def build_to(tmpdir: str, source: str = SAMPLE_DIR, outs=None, config=CONFIG) -> str:
    out = os.path.join(tmpdir, "timtra_gtfs.db")
    argv = ["build", source, "--config", config, "--out", out]
    for o in outs or []:
        argv += ["--out", o]
    buf = io.StringIO()
    with redirect_stdout(buf):
        rc = gi.main(argv)
    assert rc == 0, buf.getvalue()
    return out


class ParseTimeTest(unittest.TestCase):
    def test_zero_padded_and_not(self):
        self.assertEqual(gi.parse_gtfs_time("07:05:00"), 7 * 3600 + 5 * 60)
        self.assertEqual(gi.parse_gtfs_time("7:05:00"), 7 * 3600 + 5 * 60)

    def test_after_midnight(self):
        # 深夜0時をまたぐ便: 24:15:00 は 86400 を超える値のまま保持する
        self.assertEqual(gi.parse_gtfs_time("24:15:00"), 24 * 3600 + 15 * 60)
        self.assertEqual(gi.parse_gtfs_time("25:00:30"), 25 * 3600 + 30)

    def test_invalid(self):
        for bad in ("7:05", "07:60:00", "abc", ""):
            with self.assertRaises(ValueError):
                gi.parse_gtfs_time(bad)

    def test_format_roundtrip(self):
        self.assertEqual(gi.format_secs(gi.parse_gtfs_time("24:15:00")), "24:15:00")


class DiscoverTest(unittest.TestCase):
    def test_reports_ids_and_directions(self):
        buf = io.StringIO()
        with redirect_stdout(buf):
            rc = gi.main(["discover", SAMPLE_DIR])
        self.assertEqual(rc, 0)
        report = buf.getvalue()
        # 路線
        self.assertIn("route_id=R_MOCHIGASE_91", report)
        self.assertIn("route_id=R_MOCHIGASE_93", report)
        self.assertNotIn("route_id=R_KOYAMA_20", report)
        # 停留所: 鳥取駅は 2 乗り場、鳥取駅南口は名前一致するが対象路線が使わない
        self.assertIn("[HOME] stop_id=S_MINAMIYOSHINARI", report)
        self.assertIn("[STATION] stop_id=S_TOTTORI_EKI_1", report)
        self.assertIn("[STATION] stop_id=S_TOTTORI_EKI_5", report)
        self.assertIn("stop_id=S_TOTTORI_MINAMIGUCHI", report)
        self.assertIn("対象路線は使用しない", report)
        # direction_id ごとの向き
        self.assertIn("route_id=R_MOCHIGASE_91 direction_id=0: trips=3 南吉成→鳥取駅=3 鳥取駅→南吉成=0", report)
        self.assertIn("route_id=R_MOCHIGASE_91 direction_id=1: trips=4 南吉成→鳥取駅=0 鳥取駅→南吉成=3 両方に停車しない=1", report)

    def test_write_config_suggestion(self):
        with tempfile.TemporaryDirectory() as d:
            path = os.path.join(d, "cfg.json")
            with redirect_stdout(io.StringIO()):
                gi.main(["discover", SAMPLE_DIR, "--write-config", path])
            with open(path, encoding="utf-8") as f:
                cfg = json.load(f)
        self.assertEqual(cfg["route"]["route_ids"], ["R_MOCHIGASE_91", "R_MOCHIGASE_93"])
        self.assertEqual(cfg["home_stop"]["stop_ids"], ["S_MINAMIYOSHINARI"])
        self.assertEqual(cfg["station_stop"]["stop_ids"], ["S_TOTTORI_EKI_1", "S_TOTTORI_EKI_5"])


class BuildTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp()
        cls.db = build_to(cls.tmp)
        cls.con = sqlite3.connect(cls.db)
        cls.con.row_factory = sqlite3.Row

    @classmethod
    def tearDownClass(cls):
        cls.con.close()
        shutil.rmtree(cls.tmp)

    def rows(self, sql, *params):
        return [dict(r) for r in self.con.execute(sql, params)]

    def test_only_target_routes_and_stops(self):
        self.assertEqual([r["route_id"] for r in self.rows("SELECT route_id FROM routes ORDER BY 1")],
                         ["R_MOCHIGASE_91", "R_MOCHIGASE_93"])
        stop_ids = {r["stop_id"] for r in self.rows("SELECT stop_id FROM stops")}
        self.assertNotIn("S_TOTTORI_MINAMIGUCHI", stop_ids)
        self.assertNotIn("S_KOYAMA", stop_ids)
        self.assertIn("S_YOSHINARI", stop_ids)  # 対象路線が通る中間停留所は残す（GTFS-RT の位置比較用）
        self.assertFalse(self.rows("SELECT 1 FROM trips WHERE route_id = 'R_KOYAMA_20'"))

    def test_stop_roles(self):
        roles = {r["stop_id"]: r["role"] for r in self.rows("SELECT stop_id, role FROM stops")}
        self.assertEqual(roles["S_MINAMIYOSHINARI"], "HOME")
        self.assertEqual(roles["S_TOTTORI_EKI_1"], "STATION")
        self.assertEqual(roles["S_TOTTORI_EKI_5"], "STATION")
        self.assertIsNone(roles["S_YOSHINARI"])

    def test_commute_legs_to_station_sorted_and_correct(self):
        legs = self.rows("SELECT * FROM commute_legs WHERE direction = 'TO_STATION' ORDER BY board_departure_secs")
        self.assertEqual([l["trip_id"] for l in legs],
                         ["T_91_WD_0705", "T_91_WD_0735", "T_91_WD_0800", "T_93_SA_0800", "T_93_SH_0830"])
        first = legs[0]
        self.assertEqual(first["board_stop_id"], "S_MINAMIYOSHINARI")
        self.assertEqual(first["alight_stop_id"], "S_TOTTORI_EKI_1")
        self.assertEqual(first["board_departure_secs"], 7 * 3600 + 5 * 60)
        self.assertEqual(first["alight_arrival_secs"], 7 * 3600 + 24 * 60)
        self.assertEqual(first["service_id"], "WD")
        # 乗り場が違う便でも駅側として拾えること
        self.assertEqual(legs[2]["alight_stop_id"], "S_TOTTORI_EKI_5")

    def test_commute_legs_from_station_including_midnight(self):
        legs = self.rows("SELECT * FROM commute_legs WHERE direction = 'FROM_STATION' ORDER BY board_departure_secs")
        self.assertEqual([l["trip_id"] for l in legs],
                         ["T_91_WD_1745", "T_93_SA_1800", "T_91_WD_1830", "T_91_WD_2350"])
        late = legs[-1]
        self.assertEqual(late["board_stop_id"], "S_TOTTORI_EKI_5")
        self.assertEqual(late["alight_stop_id"], "S_MINAMIYOSHINARI")
        self.assertEqual(late["board_departure_secs"], 23 * 3600 + 50 * 60)
        self.assertEqual(late["alight_arrival_secs"], 24 * 3600 + 15 * 60)  # 0時をまたぐ

    def test_express_without_home_stop_kept_as_trip_but_not_leg(self):
        # keep_trips=either: 鳥取駅にだけ停車する便は時刻表用に残すが、乗継区間にはならない
        self.assertTrue(self.rows("SELECT 1 FROM trips WHERE trip_id = 'T_91_WD_EXPRESS'"))
        self.assertFalse(self.rows("SELECT 1 FROM commute_legs WHERE trip_id = 'T_91_WD_EXPRESS'"))

    def test_stop_times_preserved(self):
        st = self.rows("SELECT * FROM stop_times WHERE trip_id = 'T_91_WD_2350' ORDER BY stop_sequence")
        self.assertEqual([s["stop_sequence"] for s in st], [1, 2, 3, 4])
        self.assertEqual(st[1]["arrival_secs"], 24 * 3600 + 3 * 60)
        self.assertEqual(st[1]["timepoint"], 0)
        self.assertEqual(st[0]["pickup_type"], 0)

    def test_calendar_and_exceptions(self):
        cal = {r["service_id"]: r for r in self.rows("SELECT * FROM calendar")}
        self.assertEqual(set(cal), {"WD", "SA", "SH"})
        self.assertEqual(cal["WD"]["monday"], 1)
        self.assertEqual(cal["WD"]["saturday"], 0)
        self.assertEqual(cal["WD"]["start_date"], "20260401")
        cd = self.rows("SELECT * FROM calendar_dates WHERE date = '20260504' ORDER BY service_id")
        self.assertEqual([(r["service_id"], r["exception_type"]) for r in cd], [("SH", 1), ("WD", 2)])
        self.assertTrue(self.rows("SELECT 1 FROM calendar_dates WHERE date = '20270101' AND service_id = 'WD' AND exception_type = 2"))

    def test_meta(self):
        meta = {r["key"]: r["value"] for r in self.rows("SELECT * FROM meta")}
        self.assertEqual(meta["schema_version"], str(gi.SCHEMA_VERSION))
        self.assertEqual(meta["feed_version"], "sample-2026-04")
        self.assertEqual(json.loads(meta["route_ids"]), ["R_MOCHIGASE_91", "R_MOCHIGASE_93"])
        self.assertEqual(json.loads(meta["home_stop_ids"]), ["S_MINAMIYOSHINARI"])
        self.assertEqual(json.loads(meta["station_stop_ids"]), ["S_TOTTORI_EKI_1", "S_TOTTORI_EKI_5"])
        self.assertIn("日ノ丸自動車", meta["agency_names"])

    def test_room_compatible_schema(self):
        # Room の createFromAsset() 検証に合わせ、NOT NULL / 主キー / インデックス名を固定する
        cols = {r["name"]: r for r in self.rows("PRAGMA table_info(stop_times)")}
        self.assertEqual(cols["trip_id"]["pk"], 1)
        self.assertEqual(cols["stop_sequence"]["pk"], 2)
        self.assertEqual(cols["arrival_secs"]["type"], "INTEGER")
        self.assertEqual(cols["arrival_secs"]["notnull"], 1)
        self.assertEqual(cols["timepoint"]["notnull"], 0)
        idx = {r["name"] for r in self.rows("SELECT name FROM sqlite_master WHERE type = 'index' AND name NOT LIKE 'sqlite_%'")}
        self.assertEqual(idx, {"index_trips_route_id", "index_trips_service_id",
                               "index_stop_times_stop_id", "index_commute_legs_direction"})
        self.assertFalse(self.rows("SELECT 1 FROM sqlite_master WHERE name = 'room_master_table'"))


class ZipAndConfigTest(unittest.TestCase):
    def _zip_sample(self, path: str, prefix: str = ""):
        with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
            for name in os.listdir(SAMPLE_DIR):
                z.write(os.path.join(SAMPLE_DIR, name), prefix + name)

    def _dump(self, db: str):
        con = sqlite3.connect(db)
        try:
            tables = [r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name != 'meta' ORDER BY 1")]
            return {t: con.execute(f"SELECT * FROM {t} ORDER BY 1, 2").fetchall() for t in tables}
        finally:
            con.close()

    def test_zip_and_dir_produce_same_content(self):
        with tempfile.TemporaryDirectory() as d:
            z = os.path.join(d, "sample.zip")
            self._zip_sample(z)
            db_dir = build_to(os.path.join(d, "a"))
            os.makedirs(os.path.join(d, "b"))
            db_zip = build_to(os.path.join(d, "b"), source=z)
            self.assertEqual(self._dump(db_dir), self._dump(db_zip))

    def test_zip_with_subdirectory(self):
        with tempfile.TemporaryDirectory() as d:
            z = os.path.join(d, "nested.zip")
            self._zip_sample(z, prefix="gtfs/")
            db = build_to(d, source=z)
            self.assertTrue(os.path.exists(db))

    def test_multiple_outputs(self):
        with tempfile.TemporaryDirectory() as d:
            second = os.path.join(d, "wear", "assets", "timtra_gtfs.db")
            first = build_to(d, outs=[second])
            self.assertTrue(os.path.exists(first))
            self.assertTrue(os.path.exists(second))

    def test_explicit_ids_and_keep_both(self):
        with tempfile.TemporaryDirectory() as d:
            cfg = os.path.join(d, "cfg.json")
            with open(cfg, "w", encoding="utf-8") as f:
                json.dump({
                    "route": {"route_ids": ["R_MOCHIGASE_91"]},
                    "home_stop": {"stop_ids": ["S_MINAMIYOSHINARI"]},
                    "station_stop": {"stop_ids": ["S_TOTTORI_EKI_1", "S_TOTTORI_EKI_5"]},
                    "keep_trips": "both",
                }, f)
            db = build_to(d, config=cfg)
            con = sqlite3.connect(db)
            try:
                routes = [r[0] for r in con.execute("SELECT route_id FROM routes")]
                trips = {r[0] for r in con.execute("SELECT trip_id FROM trips")}
                cal = {r[0] for r in con.execute("SELECT service_id FROM calendar")}
            finally:
                con.close()
        self.assertEqual(routes, ["R_MOCHIGASE_91"])
        self.assertNotIn("T_91_WD_EXPRESS", trips)  # both: 南吉成に停車しない便は落ちる
        self.assertNotIn("T_93_SA_0800", trips)
        self.assertEqual(cal, {"WD"})  # 参照されない service_id は落ちる

    def test_no_matching_route_fails_clearly(self):
        with tempfile.TemporaryDirectory() as d:
            cfg = os.path.join(d, "cfg.json")
            with open(cfg, "w", encoding="utf-8") as f:
                json.dump({"route": {"patterns": ["存在しない路線"]}}, f)
            with self.assertRaises(SystemExit):
                with redirect_stdout(io.StringIO()):
                    gi.main(["build", SAMPLE_DIR, "--config", cfg, "--out", os.path.join(d, "x.db")])


if __name__ == "__main__":
    unittest.main()
