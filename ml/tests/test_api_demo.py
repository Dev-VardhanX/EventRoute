"""Tests for api_demo.py FastAPI demo harness using TestClient."""
from __future__ import annotations

import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from api_demo import app

FIXTURES_DIR = Path(__file__).parent / "fixtures"
client = TestClient(app)


@pytest.fixture
def fixtures():
    with open(FIXTURES_DIR / "events.json", "r", encoding="utf-8") as f:
        events = json.load(f)
    with open(FIXTURES_DIR / "journey.json", "r", encoding="utf-8") as f:
        journey = json.load(f)
    with open(FIXTURES_DIR / "me.json", "r", encoding="utf-8") as f:
        me = json.load(f)
    return {"events": events, "journey": journey, "me": me}


def test_health_endpoint():
    """Verify GET /ml/health returns model status and sklearn version."""
    res = client.get("/ml/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ok"
    assert "model_loaded" in data
    assert "version" in data
    assert "source" in data
    assert "sklearn_version" in data
    assert data["source"] in ("model", "rules")


def test_buffer_endpoint_valid():
    """Verify POST /ml/buffer calculates delay risk for valid parameters."""
    payload = {
        "mode": "metro",
        "depart_at": "2026-10-24T09:00:00+05:30",
        "distance_m": 23500.0,
        "transfers": 1,
        "walking_share": 0.25,
        "tz": "Asia/Kolkata",
    }
    res = client.post("/ml/buffer", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert "minutes" in data
    assert "risk_extra_min" in data
    assert 0.0 <= data["minutes"] <= 20.0
    assert data["source"] in ("model", "rules")
    assert "label" in data


def test_buffer_endpoint_validation_error():
    """Verify POST /ml/buffer returns contract VALIDATION_ERROR on invalid input."""
    # Negative distance
    bad_payload = {
        "mode": "bus",
        "depart_at": "2026-10-24T09:00:00+05:30",
        "distance_m": -100.0,
        "transfers": 0,
        "walking_share": 0.1,
    }
    res = client.post("/ml/buffer", json=bad_payload)
    assert res.status_code == 422
    data = res.json()
    assert data["code"] == "VALIDATION_ERROR"
    assert "message" in data

    # walking_share > 1.0
    bad_payload2 = {
        "mode": "bus",
        "depart_at": "2026-10-24T09:00:00+05:30",
        "distance_m": 5000.0,
        "transfers": 0,
        "walking_share": 1.5,
    }
    res2 = client.post("/ml/buffer", json=bad_payload2)
    assert res2.status_code == 422
    data2 = res2.json()
    assert data2["code"] == "VALIDATION_ERROR"


def test_buffer_route_endpoint(fixtures):
    """Verify POST /ml/buffer/route handles both nested and top-level route payloads."""
    route = fixtures["journey"]["routes"][0]

    # Nested route format
    res_nested = client.post(
        "/ml/buffer/route",
        json={"route": route, "depart_at": "2026-10-26T09:00:00+05:30"},
    )
    assert res_nested.status_code == 200
    data_nested = res_nested.json()
    assert data_nested["route_id"] == route["id"]
    assert 0.0 <= data_nested["risk_extra_min"] <= 20.0
    assert "features" in data_nested

    # Top-level route format
    top_payload = dict(route)
    top_payload["depart_at"] = "2026-10-26T09:00:00+05:30"
    res_top = client.post("/ml/buffer/route", json=top_payload)
    assert res_top.status_code == 200
    data_top = res_top.json()
    assert data_top["route_id"] == route["id"]
    assert 0.0 <= data_top["risk_extra_min"] <= 20.0


def test_recommend_endpoint(fixtures):
    """Verify POST /ml/recommend scores and ranks candidate events."""
    me = fixtures["me"]
    events = fixtures["events"]["items"]

    payload = {
        "user": me,
        "events": events,
        "now": "2026-10-23T10:00:00Z",
        "limit": 5,
    }
    res = client.post("/ml/recommend", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert "items" in data
    assert "total" in data
    assert len(data["items"]) <= 5
    assert data["total"] == len(data["items"])

    for item in data["items"]:
        assert "event_id" in item
        assert "score" in item
        assert "reason" in item
        assert "components" in item
        assert 0.0 <= item["score"] <= 1.0


def test_recommend_endpoint_empty():
    """Verify POST /ml/recommend with empty events list."""
    res = client.post("/ml/recommend", json={"events": []})
    assert res.status_code == 200
    data = res.json()
    assert data["items"] == []
    assert data["total"] == 0
