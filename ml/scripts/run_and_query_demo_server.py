"""Launch demo API with uvicorn on port 8011, query all endpoints over HTTP using httpx, and print outputs."""
from __future__ import annotations

import json
import subprocess
import sys
import time
from pathlib import Path

import httpx

FIXTURES_DIR = Path(__file__).parent.parent / "tests" / "fixtures"
ML_DIR = Path(__file__).parent.parent
PYTHON_EXE = sys.executable

print(f"Starting uvicorn api_demo:app on port 8011 using {PYTHON_EXE}...")
proc = subprocess.Popen(
    [PYTHON_EXE, "-m", "uvicorn", "api_demo:app", "--host", "127.0.0.1", "--port", "8011", "--log-level", "warning"],
    cwd=str(ML_DIR),
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True,
)

try:
    time.sleep(2.0)
    base_url = "http://127.0.0.1:8011"
    with httpx.Client(base_url=base_url, timeout=10.0) as client:
        print("\n" + "=" * 70)
        print("1. GET /ml/health")
        res1 = client.get("/ml/health")
        print(f"HTTP Status: {res1.status_code}")
        print(json.dumps(res1.json(), indent=2))

        print("\n" + "=" * 70)
        print("2. POST /ml/buffer")
        payload_buffer = {
            "mode": "metro",
            "depart_at": "2026-10-26T09:00:00+05:30",
            "distance_m": 23500.0,
            "transfers": 1,
            "walking_share": 0.28,
            "tz": "Asia/Kolkata"
        }
        res2 = client.post("/ml/buffer", json=payload_buffer)
        print(f"HTTP Status: {res2.status_code}")
        print(json.dumps(res2.json(), indent=2))

        print("\n" + "=" * 70)
        print("3. POST /ml/buffer/route")
        with open(FIXTURES_DIR / "journey.json", "r", encoding="utf-8") as f:
            journey_data = json.load(f)
        route = journey_data["routes"][0]  # route-fastest
        payload_route = {
            "route": route,
            "depart_at": "2026-10-26T09:00:00+05:30",
            "tz": "Asia/Kolkata"
        }
        res3 = client.post("/ml/buffer/route", json=payload_route)
        print(f"HTTP Status: {res3.status_code}")
        print(json.dumps(res3.json(), indent=2))

        print("\n" + "=" * 70)
        print("4. POST /ml/recommend")
        with open(FIXTURES_DIR / "me.json", "r", encoding="utf-8") as f:
            me_data = json.load(f)
        with open(FIXTURES_DIR / "events.json", "r", encoding="utf-8") as f:
            events_data = json.load(f)

        payload_rec = {
            "user": me_data,
            "events": events_data["items"][:3],
            "now": "2026-10-23T10:00:00Z",
            "limit": 3
        }
        res4 = client.post("/ml/recommend", json=payload_rec)
        print(f"HTTP Status: {res4.status_code}")
        print(json.dumps(res4.json(), indent=2))

        print("\n" + "=" * 70)
        print("5. POST /ml/buffer (Validation Error)")
        bad_payload = {
            "mode": "bus",
            "depart_at": "2026-10-24T09:00:00+05:30",
            "distance_m": -150.0,
            "transfers": -1,
            "walking_share": 1.8
        }
        res5 = client.post("/ml/buffer", json=bad_payload)
        print(f"HTTP Status: {res5.status_code}")
        print(json.dumps(res5.json(), indent=2))
        print("=" * 70)

finally:
    proc.terminate()
    try:
        proc.wait(timeout=3.0)
    except Exception:
        proc.kill()
    print("Demo server stopped.")
