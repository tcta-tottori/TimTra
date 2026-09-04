#!/usr/bin/env python3
"""
gtfs_import.py — GTFS-JP から TimTra 用のプリパッケージ DB を生成する。

対象: 日ノ丸自動車 用瀬智頭線（系統 91-95）/ 停留所「南吉成」「鳥取駅」
出典: 鳥取県オープンデータ（バス情報） https://odp-pref-tottori.tori-info.co.jp/bus.html

依存は Python 3.9+ の標準ライブラリのみ（csv / zipfile / sqlite3 / json）。

サブコマンド:

  discover  ZIP の中身を調べ、対象路線の route_id・停留所の stop_id・
            direction_id の割り当てを報告する（4-1「最初にやる作業」）。
            結果を見て tools/gtfs_config.json と docs/ids.md を確定させる。

  build     設定ファイルに従って対象路線・停留所だけを抽出し、
            Room から createFromAsset() で開けるプリパッケージ DB を書き出す。

使い方:

  python3 tools/gtfs_import.py discover tools/gtfs/hinomaru_gtfs.zip
  python3 tools/gtfs_import.py build tools/gtfs/hinomaru_gtfs.zip \
      --config tools/gtfs_config.json \
      --out app/src/main/assets/timtra_gtfs.db \
      --out wear/src/main/assets/timtra_gtfs.db

出力 DB のスキーマは docs/db_schema.md を参照。Room のエンティティ定義と
一致させる必要があるため、スキーマを変更するときは SCHEMA_VERSION を上げ、
docs/db_schema.md と core 側のエンティティを同時に更新すること。
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import io
import json
import os
import re
import sqlite3
import sys
import tempfile
import zipfile
from dataclasses import dataclass, field
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

TOOL_VERSION = "0.1.0"
SCHEMA_VERSION = 1

# commute_legs.direction の値。core 側の定数と一致させること。
DIRECTION_TO_STATION = "TO_STATION"      # 南吉成 → 鳥取駅（往路のバス区間）
DIRECTION_FROM_STATION = "FROM_STATION"  # 鳥取駅 → 南吉成（復路のバス区間）

# stops.role の値
ROLE_HOME = "HOME"        # 南吉成
ROLE_STATION = "STATION"  # 鳥取駅（乗り場ごとに複数あり得る）

DEFAULT_CONFIG: Dict = {
    "route": {"route_ids": [], "patterns": ["用瀬智頭線"]},
    "home_stop": {"stop_ids": [], "patterns": ["南吉成"]},
    "station_stop": {"stop_ids": [], "patterns": ["鳥取駅"]},
    # "both": 南吉成と鳥取駅の両方に停車する便だけ残す
    # "either": どちらか一方に停車する便も残す（時刻表一覧向け）
    "keep_trips": "either",
}


# ---------------------------------------------------------------------------
# GTFS 読み込み
# ---------------------------------------------------------------------------


class GtfsSource:
    """ZIP ファイルまたは展開済みディレクトリから GTFS のテキストを読む。"""

    def __init__(self, path: str):
        self.path = path
        self._zip: Optional[zipfile.ZipFile] = None
        self._prefix = ""
        if os.path.isdir(path):
            self._names = set(os.listdir(path))
        elif zipfile.is_zipfile(path):
            self._zip = zipfile.ZipFile(path)
            names = self._zip.namelist()
            # ZIP 内がサブディレクトリに入っているケースに対応する
            txts = [n for n in names if n.endswith(".txt")]
            if txts and all("/" in n for n in txts):
                common = os.path.commonprefix(txts)
                self._prefix = common[: common.rfind("/") + 1]
            self._names = {n[len(self._prefix):] for n in names if n.startswith(self._prefix)}
        else:
            raise SystemExit(f"GTFS の ZIP かディレクトリを指定してください: {path}")

    def has(self, name: str) -> bool:
        return name in self._names

    def read(self, name: str) -> List[Dict[str, str]]:
        """name (例 'stops.txt') を dict の行リストとして返す。存在しなければ空リスト。"""
        if not self.has(name):
            return []
        if self._zip is not None:
            raw = self._zip.read(self._prefix + name)
        else:
            with open(os.path.join(self.path, name), "rb") as f:
                raw = f.read()
        # GTFS-JP は BOM 付き UTF-8 が多いので utf-8-sig で読む
        text = raw.decode("utf-8-sig")
        reader = csv.DictReader(io.StringIO(text))
        rows: List[Dict[str, str]] = []
        for row in reader:
            rows.append({(k or "").strip(): (v or "").strip() for k, v in row.items()})
        return rows

    def describe(self) -> str:
        return os.path.basename(self.path.rstrip("/"))


def parse_gtfs_time(value: str) -> int:
    """'7:05:00' / '07:05:00' / '24:15:00' を 0 時起点の秒数に変換する。

    GTFS の時刻はサービス日の「正午マイナス12時間」起点なので 24 時以降も正当。
    深夜便は 86400 以上の値になる。core 側はそのまま扱う。
    """
    value = value.strip()
    m = re.fullmatch(r"(\d{1,2}):(\d{2}):(\d{2})", value)
    if not m:
        raise ValueError(f"GTFS の時刻として解釈できません: {value!r}")
    h, mi, s = (int(x) for x in m.groups())
    if mi >= 60 or s >= 60:
        raise ValueError(f"GTFS の時刻として解釈できません: {value!r}")
    return h * 3600 + mi * 60 + s


def format_secs(secs: int) -> str:
    return f"{secs // 3600:02d}:{secs % 3600 // 60:02d}:{secs % 60:02d}"


def _matches(patterns: Sequence[str], *fields: str) -> bool:
    for p in patterns:
        rx = re.compile(p)
        for f in fields:
            if f and rx.search(f):
                return True
    return False


def _to_int(value: str, default: Optional[int] = None) -> Optional[int]:
    value = (value or "").strip()
    if value == "":
        return default
    return int(value)


def _to_float(value: str) -> Optional[float]:
    value = (value or "").strip()
    return float(value) if value else None


# ---------------------------------------------------------------------------
# 抽出ロジック
# ---------------------------------------------------------------------------


@dataclass
class Selection:
    route_ids: List[str]
    home_stop_ids: List[str]
    station_stop_ids: List[str]
    routes: List[Dict[str, str]]
    stops: Dict[str, Dict[str, str]]
    trips: List[Dict[str, str]]
    stop_times: Dict[str, List[Dict[str, str]]]  # trip_id -> stop_sequence 昇順
    warnings: List[str] = field(default_factory=list)


def select_routes(src: GtfsSource, cfg: Dict) -> List[Dict[str, str]]:
    routes = src.read("routes.txt")
    ids = cfg["route"].get("route_ids") or []
    if ids:
        wanted = set(ids)
        return [r for r in routes if r.get("route_id") in wanted]
    patterns = cfg["route"].get("patterns") or []
    return [
        r
        for r in routes
        if _matches(
            patterns,
            r.get("route_short_name", ""),
            r.get("route_long_name", ""),
            r.get("route_desc", ""),
            r.get("jp_parent_route_id", ""),
        )
    ]


def _stops_by_pattern(stops: Iterable[Dict[str, str]], stop_cfg: Dict) -> List[str]:
    ids = stop_cfg.get("stop_ids") or []
    if ids:
        return list(ids)
    patterns = stop_cfg.get("patterns") or []
    return [s["stop_id"] for s in stops if _matches(patterns, s.get("stop_name", ""))]


def select(src: GtfsSource, cfg: Dict) -> Selection:
    """設定に従って対象路線・停留所・便を抽出する。"""
    warnings: List[str] = []
    routes = select_routes(src, cfg)
    if not routes:
        raise SystemExit("対象路線が見つかりません。discover で route_id を確認して設定してください。")
    route_ids = [r["route_id"] for r in routes]
    route_id_set = set(route_ids)

    all_stops = {s["stop_id"]: s for s in src.read("stops.txt")}
    home_candidates = set(_stops_by_pattern(all_stops.values(), cfg["home_stop"]))
    station_candidates = set(_stops_by_pattern(all_stops.values(), cfg["station_stop"]))
    for sid in list(home_candidates | station_candidates):
        if sid not in all_stops:
            warnings.append(f"設定された stop_id が stops.txt にありません: {sid}")

    route_trips = [t for t in src.read("trips.txt") if t.get("route_id") in route_id_set]
    route_trip_ids = {t["trip_id"] for t in route_trips}

    # stop_times は巨大なので 1 パスで対象便の分だけ集める
    stop_times: Dict[str, List[Dict[str, str]]] = {}
    for st in src.read("stop_times.txt"):
        tid = st.get("trip_id", "")
        if tid in route_trip_ids:
            stop_times.setdefault(tid, []).append(st)
    for tid, rows in stop_times.items():
        rows.sort(key=lambda r: int(r["stop_sequence"]))

    # 対象路線の便が実際に使っている停留所だけを「南吉成」「鳥取駅」として採用する。
    # （鳥取駅南口など、名前は似ていても対象路線が使わない停留所を除外するため）
    used_stop_ids = {st["stop_id"] for rows in stop_times.values() for st in rows}
    home_ids = sorted(home_candidates & used_stop_ids)
    station_ids = sorted(station_candidates & used_stop_ids)
    if not home_ids:
        raise SystemExit("対象路線が停車する「自宅側」停留所が見つかりません。discover で確認してください。")
    if not station_ids:
        raise SystemExit("対象路線が停車する「駅側」停留所が見つかりません。discover で確認してください。")
    unused = (home_candidates | station_candidates) - used_stop_ids
    for sid in sorted(unused):
        name = all_stops.get(sid, {}).get("stop_name", "?")
        warnings.append(f"名前は一致するが対象路線が使わないため除外: {sid} {name}")

    keep = cfg.get("keep_trips", "either")
    kept_trips: List[Dict[str, str]] = []
    for t in route_trips:
        rows = stop_times.get(t["trip_id"], [])
        sids = {r["stop_id"] for r in rows}
        has_home = bool(sids & set(home_ids))
        has_station = bool(sids & set(station_ids))
        ok = (has_home and has_station) if keep == "both" else (has_home or has_station)
        if ok:
            kept_trips.append(t)
    kept_ids = {t["trip_id"] for t in kept_trips}
    stop_times = {tid: rows for tid, rows in stop_times.items() if tid in kept_ids}

    kept_stop_ids = {st["stop_id"] for rows in stop_times.values() for st in rows}
    stops = {sid: all_stops[sid] for sid in kept_stop_ids if sid in all_stops}
    missing = kept_stop_ids - set(stops)
    for sid in sorted(missing):
        warnings.append(f"stop_times が参照する stop_id が stops.txt にありません: {sid}")

    return Selection(
        route_ids=route_ids,
        home_stop_ids=home_ids,
        station_stop_ids=station_ids,
        routes=routes,
        stops=stops,
        trips=sorted(kept_trips, key=lambda t: t["trip_id"]),
        stop_times=stop_times,
        warnings=warnings,
    )


@dataclass
class CommuteLeg:
    trip_id: str
    direction: str
    route_id: str
    service_id: str
    board_stop_id: str
    alight_stop_id: str
    board_departure_secs: int
    alight_arrival_secs: int


def derive_commute_legs(sel: Selection) -> List[CommuteLeg]:
    """各便について南吉成⇔鳥取駅の区間（乗車・降車時刻）を求める。

    方向は direction_id ではなく stop_sequence の前後関係で決める。
    direction_id は事業者によって付け方が揺れるため信用しない。
    """
    home = set(sel.home_stop_ids)
    station = set(sel.station_stop_ids)
    legs: List[CommuteLeg] = []
    for t in sel.trips:
        rows = sel.stop_times.get(t["trip_id"], [])
        home_rows = [r for r in rows if r["stop_id"] in home]
        station_rows = [r for r in rows if r["stop_id"] in station]
        if not home_rows or not station_rows:
            continue
        # 循環系統などで複数回停車する場合は、南吉成→鳥取駅なら最初の南吉成と
        # その後の最初の鳥取駅、逆も同様に「最短の区間」を採る。
        first_home, first_station = home_rows[0], station_rows[0]
        if int(first_home["stop_sequence"]) < int(first_station["stop_sequence"]):
            board = first_home
            alight = next(r for r in station_rows if int(r["stop_sequence"]) > int(board["stop_sequence"]))
            direction = DIRECTION_TO_STATION
        else:
            board = first_station
            alight = next(r for r in home_rows if int(r["stop_sequence"]) > int(board["stop_sequence"]))
            direction = DIRECTION_FROM_STATION
        dep = parse_gtfs_time(board.get("departure_time") or board["arrival_time"])
        arr = parse_gtfs_time(alight.get("arrival_time") or alight["departure_time"])
        if arr < dep:
            sel.warnings.append(f"到着が出発より早い便を無視: {t['trip_id']} {format_secs(dep)}→{format_secs(arr)}")
            continue
        legs.append(
            CommuteLeg(
                trip_id=t["trip_id"],
                direction=direction,
                route_id=t["route_id"],
                service_id=t["service_id"],
                board_stop_id=board["stop_id"],
                alight_stop_id=alight["stop_id"],
                board_departure_secs=dep,
                alight_arrival_secs=arr,
            )
        )
    legs.sort(key=lambda l: (l.direction, l.board_departure_secs, l.trip_id))
    return legs


# ---------------------------------------------------------------------------
# discover
# ---------------------------------------------------------------------------


def cmd_discover(args: argparse.Namespace) -> int:
    src = GtfsSource(args.gtfs)
    cfg = load_config(args.config) if args.config else json.loads(json.dumps(DEFAULT_CONFIG))
    if args.route_pattern:
        cfg["route"] = {"route_ids": [], "patterns": args.route_pattern}
    if args.home_pattern:
        cfg["home_stop"] = {"stop_ids": [], "patterns": args.home_pattern}
    if args.station_pattern:
        cfg["station_stop"] = {"stop_ids": [], "patterns": args.station_pattern}

    out = sys.stdout
    print(f"# GTFS discover: {src.describe()}", file=out)
    for fi in src.read("feed_info.txt")[:1]:
        print(
            f"feed_publisher_name={fi.get('feed_publisher_name','')} feed_version={fi.get('feed_version','')} "
            f"feed_start_date={fi.get('feed_start_date','')} feed_end_date={fi.get('feed_end_date','')}",
            file=out,
        )
    for ag in src.read("agency.txt"):
        print(f"agency: {ag.get('agency_id','')} {ag.get('agency_name','')}", file=out)

    # 1) 路線
    print("\n## 路線候補 (routes.txt)", file=out)
    routes = select_routes(src, cfg)
    if not routes:
        print("  該当なし。--route-pattern を見直してください。", file=out)
    for r in routes:
        print(
            f"  route_id={r.get('route_id')} short={r.get('route_short_name','')!r} "
            f"long={r.get('route_long_name','')!r} desc={r.get('route_desc','')!r} "
            f"parent={r.get('jp_parent_route_id','')!r}",
            file=out,
        )
    route_ids = {r["route_id"] for r in routes}

    # 2) 停留所（名前一致）
    all_stops = src.read("stops.txt")
    print("\n## 停留所候補 (stops.txt, 名前一致)", file=out)
    home_pat = cfg["home_stop"].get("patterns") or []
    station_pat = cfg["station_stop"].get("patterns") or []
    named = [s for s in all_stops if _matches(home_pat + station_pat, s.get("stop_name", ""))]
    named_ids = {s["stop_id"] for s in named}

    # 3) stop_times を 1 パスで走査して、対象路線の便と候補停留所の関係を集計
    trips = {t["trip_id"]: t for t in src.read("trips.txt") if t.get("route_id") in route_ids}
    stop_usage: Dict[str, Dict[str, int]] = {}  # stop_id -> route_id -> trips 数
    trip_stop_seq: Dict[str, Dict[str, int]] = {}  # trip_id -> stop_id -> seq (候補停留所のみ)
    for st in src.read("stop_times.txt"):
        tid = st.get("trip_id", "")
        sid = st.get("stop_id", "")
        if tid in trips and sid in named_ids:
            rid = trips[tid]["route_id"]
            stop_usage.setdefault(sid, {}).setdefault(rid, 0)
            stop_usage[sid][rid] += 1
            trip_stop_seq.setdefault(tid, {})[sid] = int(st["stop_sequence"])

    for s in named:
        usage = stop_usage.get(s["stop_id"], {})
        used = ", ".join(f"{rid}×{n}" for rid, n in sorted(usage.items())) or "対象路線は使用しない"
        role = ROLE_HOME if _matches(home_pat, s.get("stop_name", "")) else ROLE_STATION
        print(
            f"  [{role}] stop_id={s['stop_id']} name={s.get('stop_name','')!r} "
            f"platform={s.get('platform_code','')!r} parent={s.get('parent_station','')!r} "
            f"lat={s.get('stop_lat','')} lon={s.get('stop_lon','')} -> {used}",
            file=out,
        )

    # 4) 路線 × direction_id ごとの向き
    print("\n## 路線 × direction_id と、南吉成/鳥取駅の順序 (trips.txt + stop_times.txt)", file=out)
    home_ids = {s["stop_id"] for s in named if _matches(home_pat, s.get("stop_name", ""))}
    station_ids = named_ids - home_ids
    summary: Dict[Tuple[str, str], Dict[str, object]] = {}
    for tid, t in trips.items():
        key = (t["route_id"], t.get("direction_id", ""))
        entry = summary.setdefault(
            key, {"trips": 0, "to_station": 0, "from_station": 0, "neither": 0, "headsigns": {}}
        )
        entry["trips"] += 1  # type: ignore[operator]
        hs = t.get("trip_headsign", "")
        entry["headsigns"][hs] = entry["headsigns"].get(hs, 0) + 1  # type: ignore[index]
        seqs = trip_stop_seq.get(tid, {})
        h = min((seq for sid, seq in seqs.items() if sid in home_ids), default=None)
        s_ = min((seq for sid, seq in seqs.items() if sid in station_ids), default=None)
        if h is not None and s_ is not None:
            entry["to_station" if h < s_ else "from_station"] += 1  # type: ignore[operator]
        else:
            entry["neither"] += 1  # type: ignore[operator]
    for (rid, did), e in sorted(summary.items()):
        hs = ", ".join(f"{k or '(なし)'}×{v}" for k, v in sorted(e["headsigns"].items(), key=lambda kv: -kv[1]))  # type: ignore[union-attr]
        print(
            f"  route_id={rid} direction_id={did or '(なし)'}: trips={e['trips']} "
            f"南吉成→鳥取駅={e['to_station']} 鳥取駅→南吉成={e['from_station']} 両方に停車しない={e['neither']}",
            file=out,
        )
        print(f"      headsign: {hs}", file=out)

    # 5) 設定ファイルの雛形
    suggestion = {
        "route": {"route_ids": sorted(route_ids), "patterns": cfg["route"].get("patterns", [])},
        "home_stop": {
            "stop_ids": sorted(sid for sid in home_ids if stop_usage.get(sid)),
            "patterns": home_pat,
        },
        "station_stop": {
            "stop_ids": sorted(sid for sid in station_ids if stop_usage.get(sid)),
            "patterns": station_pat,
        },
        "keep_trips": cfg.get("keep_trips", "either"),
    }
    print("\n## tools/gtfs_config.json の候補（確認のうえ保存）", file=out)
    print(json.dumps(suggestion, ensure_ascii=False, indent=2), file=out)
    if args.write_config:
        with open(args.write_config, "w", encoding="utf-8") as f:
            json.dump(suggestion, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print(f"\n設定候補を書き出しました: {args.write_config}", file=out)
    return 0


# ---------------------------------------------------------------------------
# build
# ---------------------------------------------------------------------------

# Room の Entity 定義と 1:1 で対応させる。変更時は docs/db_schema.md も更新。
SCHEMA_SQL = """
CREATE TABLE meta (
    key TEXT NOT NULL,
    value TEXT NOT NULL,
    PRIMARY KEY (key)
);
CREATE TABLE stops (
    stop_id TEXT NOT NULL,
    stop_name TEXT NOT NULL,
    stop_lat REAL,
    stop_lon REAL,
    platform_code TEXT,
    parent_station TEXT,
    role TEXT,
    PRIMARY KEY (stop_id)
);
CREATE TABLE routes (
    route_id TEXT NOT NULL,
    agency_id TEXT,
    route_short_name TEXT NOT NULL,
    route_long_name TEXT NOT NULL,
    PRIMARY KEY (route_id)
);
CREATE TABLE trips (
    trip_id TEXT NOT NULL,
    route_id TEXT NOT NULL,
    service_id TEXT NOT NULL,
    direction_id INTEGER,
    trip_headsign TEXT NOT NULL,
    block_id TEXT,
    shape_id TEXT,
    PRIMARY KEY (trip_id)
);
CREATE INDEX index_trips_route_id ON trips (route_id);
CREATE INDEX index_trips_service_id ON trips (service_id);
CREATE TABLE stop_times (
    trip_id TEXT NOT NULL,
    stop_sequence INTEGER NOT NULL,
    stop_id TEXT NOT NULL,
    arrival_secs INTEGER NOT NULL,
    departure_secs INTEGER NOT NULL,
    pickup_type INTEGER NOT NULL,
    drop_off_type INTEGER NOT NULL,
    timepoint INTEGER,
    PRIMARY KEY (trip_id, stop_sequence)
);
CREATE INDEX index_stop_times_stop_id ON stop_times (stop_id);
CREATE TABLE calendar (
    service_id TEXT NOT NULL,
    monday INTEGER NOT NULL,
    tuesday INTEGER NOT NULL,
    wednesday INTEGER NOT NULL,
    thursday INTEGER NOT NULL,
    friday INTEGER NOT NULL,
    saturday INTEGER NOT NULL,
    sunday INTEGER NOT NULL,
    start_date TEXT NOT NULL,
    end_date TEXT NOT NULL,
    PRIMARY KEY (service_id)
);
CREATE TABLE calendar_dates (
    service_id TEXT NOT NULL,
    date TEXT NOT NULL,
    exception_type INTEGER NOT NULL,
    PRIMARY KEY (service_id, date)
);
CREATE TABLE commute_legs (
    trip_id TEXT NOT NULL,
    direction TEXT NOT NULL,
    route_id TEXT NOT NULL,
    service_id TEXT NOT NULL,
    board_stop_id TEXT NOT NULL,
    alight_stop_id TEXT NOT NULL,
    board_departure_secs INTEGER NOT NULL,
    alight_arrival_secs INTEGER NOT NULL,
    PRIMARY KEY (trip_id)
);
CREATE INDEX index_commute_legs_direction ON commute_legs (direction);
"""


def write_db(src: GtfsSource, sel: Selection, legs: List[CommuteLeg], out_path: str) -> Dict[str, int]:
    """抽出結果を SQLite に書き出す。既存ファイルは置き換える。"""
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    tmp_fd, tmp_path = tempfile.mkstemp(prefix="timtra_gtfs_", suffix=".db", dir=os.path.dirname(os.path.abspath(out_path)))
    os.close(tmp_fd)
    os.remove(tmp_path)
    con = sqlite3.connect(tmp_path)
    try:
        con.executescript(SCHEMA_SQL)

        service_ids = {t["service_id"] for t in sel.trips}

        con.executemany(
            "INSERT INTO routes VALUES (?,?,?,?)",
            [
                (r["route_id"], r.get("agency_id") or None, r.get("route_short_name", ""), r.get("route_long_name", ""))
                for r in sel.routes
            ],
        )
        home, station = set(sel.home_stop_ids), set(sel.station_stop_ids)
        con.executemany(
            "INSERT INTO stops VALUES (?,?,?,?,?,?,?)",
            [
                (
                    s["stop_id"],
                    s.get("stop_name", ""),
                    _to_float(s.get("stop_lat", "")),
                    _to_float(s.get("stop_lon", "")),
                    s.get("platform_code") or None,
                    s.get("parent_station") or None,
                    ROLE_HOME if sid in home else ROLE_STATION if sid in station else None,
                )
                for sid, s in sorted(sel.stops.items())
            ],
        )
        con.executemany(
            "INSERT INTO trips VALUES (?,?,?,?,?,?,?)",
            [
                (
                    t["trip_id"],
                    t["route_id"],
                    t["service_id"],
                    _to_int(t.get("direction_id", "")),
                    t.get("trip_headsign", ""),
                    t.get("block_id") or None,
                    t.get("shape_id") or None,
                )
                for t in sel.trips
            ],
        )
        st_rows = []
        for tid, rows in sel.stop_times.items():
            for r in rows:
                arr = r.get("arrival_time") or r.get("departure_time") or ""
                dep = r.get("departure_time") or r.get("arrival_time") or ""
                if not arr or not dep:
                    # GTFS では中間停留所の時刻が空でもよいが、GTFS-JP は原則すべて埋まっている。
                    # 空の場合はこの停留所を落とし、警告する。
                    sel.warnings.append(f"時刻が空の stop_time を除外: trip={tid} seq={r.get('stop_sequence')}")
                    continue
                st_rows.append(
                    (
                        tid,
                        int(r["stop_sequence"]),
                        r["stop_id"],
                        parse_gtfs_time(arr),
                        parse_gtfs_time(dep),
                        _to_int(r.get("pickup_type", ""), 0),
                        _to_int(r.get("drop_off_type", ""), 0),
                        _to_int(r.get("timepoint", "")),
                    )
                )
        con.executemany("INSERT INTO stop_times VALUES (?,?,?,?,?,?,?,?)", st_rows)

        cal_rows = [
            (
                c["service_id"],
                *(int(c.get(d, "0") or 0) for d in ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")),
                c["start_date"],
                c["end_date"],
            )
            for c in src.read("calendar.txt")
            if c.get("service_id") in service_ids
        ]
        con.executemany("INSERT INTO calendar VALUES (?,?,?,?,?,?,?,?,?,?)", cal_rows)
        cd_rows = [
            (c["service_id"], c["date"], int(c["exception_type"]))
            for c in src.read("calendar_dates.txt")
            if c.get("service_id") in service_ids
        ]
        con.executemany("INSERT OR REPLACE INTO calendar_dates VALUES (?,?,?)", cd_rows)
        # calendar.txt に無く calendar_dates.txt だけで定義される service_id も GTFS では正当
        cal_ids = {c[0] for c in cal_rows} | {c[0] for c in cd_rows}
        for sid in sorted(service_ids - cal_ids):
            sel.warnings.append(f"service_id が calendar / calendar_dates に無い: {sid}")

        con.executemany(
            "INSERT INTO commute_legs VALUES (?,?,?,?,?,?,?,?)",
            [
                (
                    l.trip_id, l.direction, l.route_id, l.service_id,
                    l.board_stop_id, l.alight_stop_id, l.board_departure_secs, l.alight_arrival_secs,
                )
                for l in legs
            ],
        )

        feed = (src.read("feed_info.txt") or [{}])[0]
        agencies = src.read("agency.txt")
        meta = {
            "schema_version": str(SCHEMA_VERSION),
            "tool_version": TOOL_VERSION,
            "generated_at": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat(),
            "source_file": src.describe(),
            "feed_publisher_name": feed.get("feed_publisher_name", ""),
            "feed_version": feed.get("feed_version", ""),
            "feed_start_date": feed.get("feed_start_date", ""),
            "feed_end_date": feed.get("feed_end_date", ""),
            "agency_names": "、".join(a.get("agency_name", "") for a in agencies),
            "route_ids": json.dumps(sel.route_ids, ensure_ascii=False),
            "home_stop_ids": json.dumps(sel.home_stop_ids, ensure_ascii=False),
            "station_stop_ids": json.dumps(sel.station_stop_ids, ensure_ascii=False),
        }
        con.executemany("INSERT INTO meta VALUES (?,?)", sorted(meta.items()))
        con.commit()
        # 生成物のサイズを最小化し、ページ配置を決定的にする
        con.execute("VACUUM")
        counts = {
            tbl: con.execute(f"SELECT COUNT(*) FROM {tbl}").fetchone()[0]
            for tbl in ("routes", "stops", "trips", "stop_times", "calendar", "calendar_dates", "commute_legs")
        }
    finally:
        con.close()
    os.replace(tmp_path, out_path)
    return counts


def load_config(path: str) -> Dict:
    with open(path, encoding="utf-8") as f:
        cfg = json.load(f)
    merged = json.loads(json.dumps(DEFAULT_CONFIG))
    for key in ("route", "home_stop", "station_stop"):
        if key in cfg:
            merged[key].update(cfg[key])
    if "keep_trips" in cfg:
        if cfg["keep_trips"] not in ("either", "both"):
            raise SystemExit("keep_trips は 'either' か 'both' を指定してください")
        merged["keep_trips"] = cfg["keep_trips"]
    return merged


def cmd_build(args: argparse.Namespace) -> int:
    src = GtfsSource(args.gtfs)
    cfg = load_config(args.config) if args.config else json.loads(json.dumps(DEFAULT_CONFIG))
    sel = select(src, cfg)
    legs = derive_commute_legs(sel)
    outs = args.out or ["app/src/main/assets/timtra_gtfs.db"]
    for out in outs:
        counts = write_db(src, sel, legs, out)
        print(f"書き出し: {out}  " + " ".join(f"{k}={v}" for k, v in counts.items()))
    to_station = sum(1 for l in legs if l.direction == DIRECTION_TO_STATION)
    print(
        f"route_ids={sel.route_ids} home_stop_ids={sel.home_stop_ids} station_stop_ids={sel.station_stop_ids} "
        f"legs: 南吉成→鳥取駅={to_station} 鳥取駅→南吉成={len(legs) - to_station}"
    )
    for w in sorted(set(sel.warnings)):
        print(f"警告: {w}", file=sys.stderr)
    if not legs:
        print("エラー: 南吉成⇔鳥取駅の区間を持つ便が 1 本もありません。設定を見直してください。", file=sys.stderr)
        return 1
    return 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="gtfs_import.py", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="command", required=True)

    d = sub.add_parser("discover", help="対象路線・停留所の ID を調べて報告する")
    d.add_argument("gtfs", help="GTFS-JP の ZIP ファイルまたは展開ディレクトリ")
    d.add_argument("--config", help="tools/gtfs_config.json（省略時は既定パターン）")
    d.add_argument("--route-pattern", action="append", help="路線名の正規表現（複数可）")
    d.add_argument("--home-pattern", action="append", help="自宅側停留所名の正規表現（複数可）")
    d.add_argument("--station-pattern", action="append", help="駅側停留所名の正規表現（複数可）")
    d.add_argument("--write-config", metavar="PATH", help="設定候補を JSON として書き出す")
    d.set_defaults(func=cmd_discover)

    b = sub.add_parser("build", help="プリパッケージ DB を生成する")
    b.add_argument("gtfs", help="GTFS-JP の ZIP ファイルまたは展開ディレクトリ")
    b.add_argument("--config", help="tools/gtfs_config.json")
    b.add_argument("--out", action="append", help="出力先 .db（複数可。既定 app/src/main/assets/timtra_gtfs.db）")
    b.set_defaults(func=cmd_build)
    return p


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
