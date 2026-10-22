"""Collect REAL driving-delay signal from Google's Routes API (needs a key and billing).

Verified against CURRENT official Google Maps Platform documentation ("Routes API computeRoutes"):
- Endpoint: https://routes.googleapis.com/directions/v2:computeRoutes
- Headers: X-Goog-Api-Key, X-Goog-FieldMask (mandatory), Content-Type: application/json
- FieldMask: routes.duration,routes.staticDuration,routes.distanceMeters
- travelMode: DRIVE, routingPreference: TRAFFIC_AWARE_OPTIMAL, trafficModel: BEST_GUESS | PESSIMISTIC
- departureTime: RFC 3339 UTC formatted string in the future.

For each origin/destination pair and each hour of a future weekday and weekend day it asks for the
route twice (traffic model BEST_GUESS and PESSIMISTIC). The proxy target is:
    target_min = (pessimistic_duration - best_guess_duration) / 60
i.e. how much extra time a cautious traveller should allow. This is a PROXY for the 90th percentile
extra delay, NOT measured lateness. Rows are labelled source="real_api" and only drive mode is covered.

Safety: --max-calls hard limit, resumable CSV (already-done keys are skipped), --dry-run prints the
plan and the call count without touching the network. Never commit the API key; read it from the
ROUTES_API_KEY environment variable.

    ROUTES_API_KEY=... python scripts/sample_routes_api.py --pairs data/od_pairs.csv --out data/real_api.csv --dry-run
"""
from __future__ import annotations

import argparse
import csv
import os
import sys
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
FIELD_MASK = "routes.duration,routes.staticDuration,routes.distanceMeters"
IST = ZoneInfo("Asia/Kolkata")
OUT_COLUMNS = [
    "route_id",
    "mode",
    "hour",
    "weekday",
    "distance_m",
    "transfers",
    "walking_share",
    "target_min",
    "source",
    "best_guess_s",
    "pessimistic_s",
]


def next_weekday(start: datetime, weekday: int) -> datetime:
    d = start + timedelta(days=1)
    while d.weekday() != weekday:
        d += timedelta(days=1)
    return d


def plan(pairs: list[dict], days=(1, 5)) -> list[tuple[dict, int, datetime]]:
    """(pair, weekday, departure datetime in IST) for every hour 6..22 on a future Tue and Sat."""
    now = datetime.now(IST)
    out = []
    for wd in days:
        base = next_weekday(now, wd).replace(minute=0, second=0, microsecond=0)
        for p in pairs:
            for hour in range(6, 23):
                out.append((p, wd, base.replace(hour=hour)))
    return out


def seconds(s: str) -> float:
    return float(str(s).rstrip("s"))


def call(client, key: str, p: dict, when: datetime, model: str) -> dict:
    body = {
        "origin": {
            "location": {
                "latLng": {
                    "latitude": float(p["origin_lat"]),
                    "longitude": float(p["origin_lng"]),
                }
            }
        },
        "destination": {
            "location": {
                "latLng": {
                    "latitude": float(p["dest_lat"]),
                    "longitude": float(p["dest_lng"]),
                }
            }
        },
        "travelMode": "DRIVE",
        "routingPreference": "TRAFFIC_AWARE_OPTIMAL",
        "departureTime": when.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "trafficModel": model,
    }
    headers = {
        "X-Goog-Api-Key": key,
        "X-Goog-FieldMask": FIELD_MASK,
        "Content-Type": "application/json",
    }
    r = client.post(URL, json=body, headers=headers, timeout=20.0)
    r.raise_for_status()
    data = r.json()
    routes = data.get("routes", [])
    if not routes:
        raise ValueError("Routes API returned no routes")
    route = routes[0]
    return {
        "duration_s": seconds(route["duration"]),
        "distance_m": float(route.get("distanceMeters", 0)),
    }


def run_sampling(
    pairs_path: str | Path,
    out_path: str | Path,
    max_calls: int = 1500,
    dry_run: bool = False,
    key: str | None = None,
    client=None,
) -> int:
    with open(pairs_path, "r", encoding="utf-8") as pf:
        pairs = list(csv.DictReader(pf))
    jobs = plan(pairs)
    calls_needed = len(jobs) * 2
    print(
        f"{len(pairs)} pairs -> {len(jobs)} departures -> {calls_needed} API calls (limit {max_calls})"
    )

    if dry_run:
        return 0

    if calls_needed > max_calls:
        print(
            "Refusing: planned calls exceed --max-calls. Use fewer pairs or raise the limit deliberately."
        )
        return 2

    api_key = key or os.environ.get("ROUTES_API_KEY")
    if not api_key:
        print("Set ROUTES_API_KEY in the environment.")
        return 2

    import httpx

    out = Path(out_path)
    done = set()
    if out.exists():
        with open(out, "r", encoding="utf-8") as of:
            done = {
                (r["route_id"], str(r["weekday"]), str(r["hour"]))
                for r in csv.DictReader(of)
            }

    new_file = not out.exists()

    def process_jobs(http_client):
        with open(out, "a", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=OUT_COLUMNS)
            if new_file:
                w.writeheader()
            for p, wd, when in jobs:
                k = (p["route_id"], str(wd), str(when.hour))
                if k in done:
                    continue
                try:
                    best = call(http_client, api_key, p, when, "BEST_GUESS")
                    pess = call(http_client, api_key, p, when, "PESSIMISTIC")
                except Exception as e:
                    print("skip", k, type(e).__name__, e)
                    continue
                w.writerow(
                    {
                        "route_id": p["route_id"],
                        "mode": "drive",
                        "hour": when.hour,
                        "weekday": wd,
                        "distance_m": best["distance_m"],
                        "transfers": 0,
                        "walking_share": 0.0,
                        "target_min": (pess["duration_s"] - best["duration_s"]) / 60.0,
                        "source": "real_api",
                        "best_guess_s": best["duration_s"],
                        "pessimistic_s": pess["duration_s"],
                    }
                )
                f.flush()
                time.sleep(0.01)

    if client is not None:
        process_jobs(client)
    else:
        with httpx.Client() as c:
            process_jobs(c)

    return 0


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--pairs", required=True, help="Path to input od_pairs.csv")
    ap.add_argument("--out", required=True, help="Path to output real_api.csv")
    ap.add_argument("--max-calls", type=int, default=1500)
    ap.add_argument(
        "--dry-run",
        action="store_true",
        default=False,
        help="Print plan and call count without making network calls",
    )
    a = ap.parse_args(argv)

    return run_sampling(
        pairs_path=a.pairs,
        out_path=a.out,
        max_calls=a.max_calls,
        dry_run=a.dry_run,
    )


if __name__ == "__main__":
    sys.exit(main())
