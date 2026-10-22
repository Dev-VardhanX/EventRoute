"""Tests for eventroute_ml adapters against real mock data fixtures and edge cases."""
from __future__ import annotations

import json
from pathlib import Path

import pytest

from eventroute_ml import (
    annotate_routes,
    event_from_json,
    recommend,
    route_to_risk,
    user_from_json,
)

FIXTURES_DIR = Path(__file__).parent / "fixtures"


@pytest.fixture
def events_data():
    path = FIXTURES_DIR / "events.json"
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


@pytest.fixture
def journey_data():
    path = FIXTURES_DIR / "journey.json"
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


@pytest.fixture
def me_data():
    path = FIXTURES_DIR / "me.json"
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


@pytest.fixture
def colleges_data():
    path = FIXTURES_DIR / "colleges.json"
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def test_events_from_json_and_recommend(events_data, me_data, colleges_data):
    """Load every event from events.json through event_from_json and recommend.

    Assert a non-empty ranked list with valid scores and explanations.
    """
    raw_events = events_data.get("items", [])
    assert len(raw_events) > 0, "events.json items should not be empty"

    events = [event_from_json(e) for e in raw_events]
    assert len(events) == len(raw_events)

    user = user_from_json(me_data, colleges=colleges_data)
    assert user.lat is not None and user.lng is not None
    assert len(user.interests) > 0

    recs = recommend(user=user, events=events)
    assert len(recs) > 0, "recommend should return a non-empty list of recommendations"

    # All recs should have score between 0.0 and 1.0 and non-empty reason
    for r in recs:
        assert 0.0 <= r.score <= 1.0
        assert r.reason != ""
        assert "interest" in r.components
        assert "proximity" in r.components
        assert "popularity" in r.components
        assert "urgency" in r.components

    # Verify sorting
    scores = [r.score for r in recs]
    assert scores == sorted(scores, reverse=True)


def test_journey_routes_risk_buffer(journey_data):
    """Load journey.json and assert every route gets a risk_extra_min in [0, 20].

    Also assert that the metro+walk route gets a different value at 09:00 weekday than at 14:00.
    """
    routes = journey_data.get("routes", [])
    assert len(routes) > 0, "journey.json routes should not be empty"

    for r in routes:
        risk = route_to_risk(r)
        assert 0.0 <= risk["risk_extra_min"] <= 20.0
        assert risk["source"] in ("model", "rules")
        assert risk["version"] != ""
        assert risk["label"] != ""
        assert "mode" in risk["features"]

    # Annotate routes batch test
    annotated = annotate_routes(routes)
    assert len(annotated) == len(routes)
    for orig, ann in zip(routes, annotated):
        assert "risk_extra_min" in ann
        assert ann["id"] == orig["id"]
        assert 0.0 <= ann["risk_extra_min"] <= 20.0

    # Find the metro+walk route
    metro_walk = next((r for r in routes if r.get("id") == "route-fastest"), None)
    assert metro_walk is not None, "route-fastest (metro+walk) should exist in journey.json"

    # Test peak vs off-peak departure on a weekday (2026-10-26 is Monday)
    depart_peak = "2026-10-26T09:00:00+05:30"      # Monday 09:00 (peak)
    depart_offpeak = "2026-10-26T14:00:00+05:30"   # Monday 14:00 (off-peak)

    risk_peak = route_to_risk(metro_walk, depart_at=depart_peak)
    risk_offpeak = route_to_risk(metro_walk, depart_at=depart_offpeak)

    assert 0.0 <= risk_peak["risk_extra_min"] <= 20.0
    assert 0.0 <= risk_offpeak["risk_extra_min"] <= 20.0
    assert risk_peak["risk_extra_min"] != risk_offpeak["risk_extra_min"], (
        f"Expected peak risk ({risk_peak['risk_extra_min']}) to differ from "
        f"off-peak risk ({risk_offpeak['risk_extra_min']}) for metro+walk route"
    )
    assert risk_peak["risk_extra_min"] > risk_offpeak["risk_extra_min"], (
        "Peak delay risk should be higher than off-peak delay risk"
    )


def test_adapters_never_raise_on_missing_fields():
    """Assert adapters gracefully handle empty, missing, or malformed fields."""
    # 1. event_from_json edge cases
    ev_empty = event_from_json({})
    assert ev_empty.id == ""
    assert ev_empty.title == ""
    assert ev_empty.tags == []
    assert ev_empty.lat is None
    assert ev_empty.lng is None
    assert ev_empty.start_at is None
    assert ev_empty.registrations_count == 0

    ev_malformed = event_from_json({
        "id": None,
        "title": 123,
        "venue_lat": "not-a-number",
        "venue_lng": None,
        "start_at": "invalid-timestamp",
        "seat_limit": "bad",
        "seats_left": None,
        "status": None,
    })
    assert ev_malformed.id == ""
    assert ev_malformed.lat is None
    assert ev_malformed.start_at is None
    assert ev_malformed.registrations_count == 0

    # 2. user_from_json edge cases
    u_empty = user_from_json({})
    assert u_empty.interests == []
    assert u_empty.lat is None
    assert u_empty.lng is None

    u_malformed = user_from_json({
        "interests": "not-a-list",
        "home_lat": "bad",
        "home_lng": None,
        "default_start": "college",
        "college_id": "non-existent",
    }, colleges=[{"id": "c1", "lat": 28.5, "lng": 77.2}])
    assert u_malformed.interests == []
    assert u_malformed.lat is None
    assert u_malformed.lng is None

    # 3. route_to_risk edge cases
    r_empty = route_to_risk({})
    assert r_empty["route_id"] == ""
    assert 0.0 <= r_empty["risk_extra_min"] <= 20.0
    assert r_empty["features"]["mode"] == "walk"

    r_malformed = route_to_risk({
        "id": 999,
        "duration_s": "not-a-number",
        "distance_m": -500,
        "transfers": "bad",
        "legs": ["not-a-dict"],
    }, depart_at="invalid-date")
    assert r_malformed["route_id"] == "999"
    assert 0.0 <= r_malformed["risk_extra_min"] <= 20.0

    # 4. annotate_routes edge cases
    assert annotate_routes([]) == []
    assert annotate_routes(None) == []
    annotated_mixed = annotate_routes([{}, {"id": "test", "duration_s": 600}])
    assert len(annotated_mixed) == 2
    assert 0.0 <= annotated_mixed[0]["risk_extra_min"] <= 20.0
    assert 0.0 <= annotated_mixed[1]["risk_extra_min"] <= 20.0
