#!/usr/bin/env python3
"""scoreJob 로컬 시연용 더미 수집 파일을 만들고, 같은 규칙으로 계산한 예상 점수를 출력한다.

형식은 docs/ingest-spec.md 2장(경로)·5장(레코드), 점수 규칙은 docs/score-spec.md를 따른다.

사용: python3 scripts/make-score-sample.py [--root 로컬 저장소 루트] [--start 2026-11-20] [--days 3] [--seed 7]
      루트를 주지 않으면 UBI_LOCAL_STORAGE_ROOT, 그것도 없으면 레포 옆 ../local-storage를 쓴다.

만드는 것:
  {root}/s3/firehose/processed/year=…/hour=HH/score-sample-*.jsonl.gz   (Swift에도 같은 키로 복사)
  {root}/score-sample-dates.txt   ingestJob에 넘길 targetDate 목록 (UTC)

차량 3대가 하루 2번씩 운행한다(난폭 차량은 KST 01:30 심야 운행 1번 더). microtrip은 2초마다 1건이다.
같은 시드면 같은 파일이 나온다. 이전에 만든 score-sample 파일은 지우고 새로 만든다.
"""

import argparse
import gzip
import hashlib
import json
import os
import random
import shutil
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

# ---- 점수 규칙 (application.yaml의 운영 가중치, test 기준) ----
WEIGHTS = {"overspeed": 120, "accel": 90, "brake": 200, "turn": 100}
NIGHT_RATES = {23: 0.2539, 0: 0.2692, 1: 0.40, 2: 0.384, 3: 0.164}
GROUP_MASKS = {"accel": 0x21, "brake": 0x50, "turn": 0x0E}
OVERSPEED_MASK = 0x80
MANEUVER_GAP_MS = 4000
INTERVAL_S = 2
SEGMENT_M = 50_000      # test 기준 구간 거리
WINDOW_M = 600_000      # test 기준 반영 범위
KST = timezone(timedelta(hours=9))

# ---- 차량별 운전 성향 (주기 하나에서 기동이 시작될 확률) ----
# 100km(약 3,000주기)당 대략: 안전 급가속 1.5·급감속 1·급회전 1.5회, 보통 3·2·3회, 난폭 8·7·6회와 과속 수 km
PROFILES = [
    {"vin": "DUMMY0000000SAFE1", "label": "안전", "accel": 0.0005, "brake": 0.0003, "turn": 0.0005,
     "overspeed": 0.00008, "cruise": 48, "night": False},
    {"vin": "DUMMY000000NORML1", "label": "보통", "accel": 0.0009, "brake": 0.0006, "turn": 0.0009,
     "overspeed": 0.00018, "cruise": 55, "night": False},
    {"vin": "DUMMY0000000AGGR1", "label": "난폭", "accel": 0.0028, "brake": 0.0024, "turn": 0.0021,
     "overspeed": 0.0010, "cruise": 62, "night": True},
]
# 운행 시작 시각 (KST). 09시를 넘기지 않게 해 UTC 날짜가 바뀌지 않도록 한다
DAY_TRIPS_KST = [(13, 0), (18, 30)]
NIGHT_TRIP_KST = (1, 30)


def main():
    repo_root = Path(__file__).resolve().parent.parent
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=os.environ.get("UBI_LOCAL_STORAGE_ROOT", str(repo_root.parent / "local-storage")))
    parser.add_argument("--start", default="2026-11-20", help="첫 날짜 (UTC)")
    parser.add_argument("--days", type=int, default=3)
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()

    rng = random.Random(args.seed)
    root = Path(args.root).resolve()
    start = date.fromisoformat(args.start)
    days = [start + timedelta(days=i) for i in range(args.days)]

    lines_by_hour = {}   # (UTC 날짜, 시) → JSONL 줄 목록
    trips = []           # 예상 점수 계산용
    for profile in PROFILES:
        seq = 0
        for day in days:
            starts = [datetime(day.year, day.month, day.day, h, m, tzinfo=KST) for h, m in DAY_TRIPS_KST]
            if profile["night"]:
                h, m = NIGHT_TRIP_KST
                starts.append(datetime(day.year, day.month, day.day, h, m, tzinfo=KST) + timedelta(days=1))
            for started in starts:
                seq += 1
                tid = int(day.strftime("%y%m%d")) * 100 + seq
                trip = simulate_trip(rng, profile, tid, started)
                trips.append(trip)
                for record in trip["records"]:
                    received = datetime.fromtimestamp(record["ts"] / 1000, tz=timezone.utc)
                    lines_by_hour.setdefault((received.date(), received.hour), []).append(json.dumps(record, separators=(",", ":")))

    write_files(root, lines_by_hour)
    (root / "score-sample-dates.txt").write_text("\n".join(sorted({d.isoformat() for d, _ in lines_by_hour})) + "\n")
    print_expected(trips, root)


def simulate_trip(rng, profile, tid, started):
    """2초마다 microtrip을 만들고, 기동은 1–3주기 이어서 켠다. 끝에 ty=3 요약을 붙인다."""
    distance_km = rng.uniform(22, 34)
    cycles = int(distance_km / profile["cruise"] * 3600 / INTERVAL_S)
    t0 = int(started.timestamp() * 1000)
    active = {}          # 묶음 → 남은 주기 수
    overspeed_left = 0
    records, cycles_out = [], []
    total_m = 0.0
    for i in range(cycles):
        ramp = min(1.0, i / 15, (cycles - i) / 15)
        speed = max(0, round(profile["cruise"] * ramp + rng.uniform(-6, 6)))
        mask = 0
        for group, bits in (("accel", [0x20, 0x01]), ("brake", [0x10, 0x40]), ("turn", [0x02, 0x04, 0x08])):
            if active.get(group, 0) == 0 and rng.random() < profile[group]:
                active[group] = rng.randint(1, 3)
                active[group + "_bit"] = rng.choice(bits)
            if active.get(group, 0) > 0:
                mask |= active[group + "_bit"]
                active[group] -= 1
        if overspeed_left == 0 and rng.random() < profile["overspeed"]:
            overspeed_left = rng.randint(10, 40)
        if overspeed_left > 0:
            speed = rng.randint(108, 130)
            mask |= OVERSPEED_MASK
            overspeed_left -= 1
        clt = t0 + i * INTERVAL_S * 1000
        total_m += speed * INTERVAL_S / 3.6
        if rng.random() < 0.003:
            continue                       # 전송 누락: 이 주기는 오지 않는다
        em = None if rng.random() < 0.002 else mask   # em 누락: 평가하지 않는 주기
        pld = {"tid": tid, "clt": clt, "sp": speed}
        if em is not None:
            pld["em"] = format(em, "X")
        records.append(envelope(profile["vin"], 4, clt + rng.randint(100, 900), f"{tid}-{i}", pld))
        cycles_out.append({"clt": clt, "sp": speed, "em": em})

    ended = t0 + cycles * INTERVAL_S * 1000
    dis = round(total_m)
    driving = sum(1 for c in cycles_out if c["sp"] > 0) * INTERVAL_S
    pld = {"tid": tid, "stt": t0, "edt": ended, "dis": dis, "hsts": max(c["sp"] for c in cycles_out),
           "mesp": round(dis / 1000 / ((ended - t0) / 3_600_000)), "dtvt": driving}
    records.append(envelope(profile["vin"], 3, ended + 1000, f"{tid}-trip", pld))
    return {"vin": profile["vin"], "tid": tid, "ended": ended, "dis": dis, "cycles": cycles_out, "records": records}


def envelope(vin, ty, ts, key, pld):
    return {"vehicleId": vin, "ts": ts, "ty": ty, "schemaVersion": 1,
            "idempotencyKey": hashlib.sha256(f"score-sample-{vin}-{key}".encode()).hexdigest(), "pld": pld}


def write_files(root, lines_by_hour):
    for store in ("s3", "swift"):
        for old in (root / store).glob("firehose/processed/**/score-sample-*.jsonl.gz"):
            old.unlink()
    for (day, hour), lines in sorted(lines_by_hour.items()):
        key = f"firehose/processed/year={day:%Y}/month={day:%m}/day={day:%d}/hour={hour:02d}/score-sample-{day:%Y%m%d}{hour:02d}.jsonl.gz"
        s3_path = root / "s3" / key
        s3_path.parent.mkdir(parents=True, exist_ok=True)
        with gzip.GzipFile(s3_path, "wb", mtime=0) as out:
            out.write(("\n".join(lines) + "\n").encode())
        swift_path = root / "swift" / key
        swift_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(s3_path, swift_path)


# ---- 예상 점수: docs/score-spec.md 규칙을 그대로 따라 계산한다 ----

def trip_tally(trip):
    tally = {"distance_m": trip["dis"], "overspeed": 0.0, "accel": 0, "brake": 0, "turn": 0,
             "n_overspeed": 0.0, "n_accel": 0.0, "n_brake": 0.0, "n_turn": 0.0}
    previous = None
    for cycle in sorted(trip["cycles"], key=lambda c: c["clt"]):
        if cycle["em"] is None:
            previous = cycle
            continue
        rate = NIGHT_RATES.get(datetime.fromtimestamp(cycle["clt"] / 1000, tz=KST).hour, 0.0)
        if cycle["em"] & OVERSPEED_MASK:
            km = cycle["sp"] * INTERVAL_S / 3600
            tally["overspeed"] += km
            tally["n_overspeed"] += rate * km
        for group, group_mask in GROUP_MASKS.items():
            if not cycle["em"] & group_mask:
                continue
            continued = (previous is not None and previous["em"] is not None and previous["em"] & group_mask
                         and cycle["clt"] - previous["clt"] <= MANEUVER_GAP_MS)
            if not continued:
                tally[group] += 1
                tally["n_" + group] += rate
        previous = cycle
    return tally


def add(a, b):
    return {k: a[k] + b[k] for k in a}


def penalty(tally):
    km = tally["distance_m"] / 1000
    return sum(WEIGHTS[k] * (tally[k] + tally["n_" + k]) for k in WEIGHTS) / km


def print_expected(trips, root):
    print(f"더미 수집 파일을 만들었습니다: {root}")
    print(f"ingestJob targetDate 목록: {(root / 'score-sample-dates.txt').read_text().split()}")
    print()
    print("예상 결과 (test 기준: 구간 50km, 반영 범위 600km / 모든 날짜를 적재한 뒤 scoreJob을 한 번 돌렸을 때)")
    print(f"{'차량':<18} {'성향':<4} {'trip':>4} {'주행km':>7} {'과속km':>6} {'급가속':>5} {'급감속':>5} {'급회전':>5} {'구간':>4} {'예상 점수':>8}")
    for profile in PROFILES:
        vin_trips = sorted((t for t in trips if t["vin"] == profile["vin"]), key=lambda t: (t["ended"], t["tid"]))
        tallies = [trip_tally(t) for t in vin_trips]
        total = tallies[0]
        for t in tallies[1:]:
            total = add(total, t)
        segments, current = [], None
        for t in tallies:
            current = t if current is None else add(current, t)
            if current["distance_m"] >= SEGMENT_M:
                segments.append(current)
                current = None
        window, accumulated = [], 0
        for segment in reversed(segments):
            if accumulated >= WINDOW_M:
                break
            window.append(segment)
            accumulated += segment["distance_m"]
        if window:
            pooled = window[0]
            for s in window[1:]:
                pooled = add(pooled, s)
            score = f"{max(0.0, min(100.0, 100 - penalty(pooled))):.2f}"
        else:
            score = "미산출"
        print(f"{profile['vin']:<18} {profile['label']:<4} {len(vin_trips):>4} {total['distance_m'] / 1000:>7.1f} "
              f"{total['overspeed']:>6.2f} {total['accel']:>5} {total['brake']:>5} {total['turn']:>5} {len(segments):>4} {score:>8}")


if __name__ == "__main__":
    main()
