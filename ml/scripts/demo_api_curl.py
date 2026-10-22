"""Script to query the running demo API or TestClient with real mock data and print exact responses."""
from __future__ import annotations

import json
from pathlib import Path

from fastapi.testclient import TestClient

from api_demo import app

FIXTURES_DIR = Path(__file__).parent.parent / "tests" / "fixtures"

client = TestClient(app)

print("=" * 70)
print("1. GET /ml/health")
res1 = client.get("/ml/health")
print(f"Status: {res1.status_code}")
print(json.dumps(res1.json(), indent=2))

print("\n" + "=" * 70)
print("2. POST /ml/buffer (Direct feature payload)")
payload_buffer = {
    "mode": "metro",
    "depart_at": "2026-10-26T09:00:00+05:30",
    "distance_m": 23500.0,
    "transfers": 1,
    "walking_share": 0.28,
    "tz": "Asia/Kolkata"
}
print("Request:")
print(json.dumps(payload_buffer, indent=2))
res2 = client.post("/ml/buffer", json=payload_buffer)
print(f"Status: {res2.status_code}")
print("Response:")
print(json.dumps(res2.json(), indent=2))

print("\n" + "=" * 70)
print("3. POST /ml/buffer/route (Route in common format from journey.json)")
with open(FIXTURES_DIR / "journey.json", "r", encoding="utf-8") as f:
    journey_data = json.load(f)
route = journey_data["routes"][0]  # route-fastest (Metro + Walk)
payload_route = {
    "route": route,
    "depart_at": "2026-10-26T09:00:00+05:30",
    "tz": "Asia/Kolkata"
}
print(f"Request (Route ID: {route.get('id')}, Summary: {route.get('summary')}):")
res3 = client.post("/ml/buffer/route", json=payload_route)
print(f"Status: {res3.status_code}")
print("Response:")
print(json.dumps(res3.json(), indent=2))

print("\n" + "=" * 70)
print("4. POST /ml/recommend (Personalized event recommendation from me.json & events.json)")
with open(FIXTURES_DIR / "me.json", "r", encoding="utf-8") as f:
    me_data = json.load(f)
with open(FIXTURES_DIR / "events.json", "r", encoding="utf-8") as f:
    events_data = json.load(f)

payload_rec = {
    "user": me_data,
    "events": events_data["items"][:3],  # First 3 events for clean readable snippet
    "now": "2026-10-23T10:00:00Z",
    "limit": 3
}
print(f"Request (User: {me_data.get('name')}, Interests: {me_data.get('interests')}, 3 events):")
res4 = client.post("/ml/recommend", json=payload_rec)
print(f"Status: {res4.status_code}")
print("Response:")
print(json.dumps(res4.json(), indent=2))

print("\n" + "=" * 70)
print("5. POST /ml/buffer (Validation Error contract demonstration)")
bad_payload = {
    "mode": "bus",
    "depart_at": "2026-10-24T09:00:00+05:30",
    "distance_m": -150.0,
    "transfers": -1,
    "walking_share": 1.8
}
print("Request (Bad fields: negative distance, negative transfers, walking_share > 1.0):")
print(json.dumps(bad_payload, indent=2))
res5 = client.post("/ml/buffer", json=bad_payload)
print(f"Status: {res5.status_code}")
print("Response:")
print(json.dumps(res5.json(), indent=2))
print("=" * 70)
