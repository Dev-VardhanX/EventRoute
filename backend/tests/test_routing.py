import json
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from unittest.mock import MagicMock

import pytest

from app.core.db import SessionLocal
from app.core.errors import AppError
from app.models.route_cache import RouteCache
from app.services.routing.base import ProviderError, ProviderTimeout, RouteProvider
from app.services.routing.cache import (
    get_ttl_seconds,
    make_cache_key,
    round_time_to_5min,
)
from sqlalchemy import delete
from app.services.routing.fake import FakeProvider
from app.services.routing.google import parse_google_response
from app.services.routing.guard import (
    get_today_paid_calls,
    is_paid_limit_exceeded,
    record_paid_call,
    reset_paid_calls,
)
from app.services.routing.ors import parse_ors_response
from app.services.routing.service import get_route

SAMPLES_DIR = Path(__file__).parent / "samples"


@pytest.fixture
def db():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.rollback()
        session.close()


def test_parse_google_transit_sample():
    sample_path = SAMPLES_DIR / "google_transit.json"
    with open(sample_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    routes = parse_google_response(data, mode="transit")
    assert len(routes) == 1
    r = routes[0]
    assert r.duration_s == 2820
    assert r.distance_m == 18200
    assert r.fare == {"currency": "INR", "amount": 40.0}
    assert r.transfers == 1  # 2 transit legs (Yellow & Magenta) -> 1 transfer
    assert r.walking_s == 600  # 270s + 330s
    assert len(r.legs) == 4

    modes = [leg.mode for leg in r.legs]
    assert modes == ["walk", "metro", "metro", "walk"]

    metro1 = r.legs[1]
    assert metro1.transit is not None
    assert metro1.transit.line_name == "Delhi Metro Yellow Line"
    assert metro1.transit.color == "#FFD700"
    assert metro1.transit.stops_count == 9
    assert metro1.from_loc == "Rajiv Chowk"
    assert metro1.to_loc == "Hauz Khas"

    metro2 = r.legs[2]
    assert metro2.transit is not None
    assert metro2.transit.line_name == "Delhi Metro Magenta Line"
    assert metro2.transit.color == "#FF00FF"


def test_parse_ors_sample():
    sample_path = SAMPLES_DIR / "ors_drive.json"
    with open(sample_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    routes = parse_ors_response(data, mode="drive")
    assert len(routes) == 1
    r = routes[0]
    assert r.duration_s == 1560
    assert r.distance_m == 14250
    assert len(r.legs) == 1
    leg = r.legs[0]
    assert leg.mode == "drive"
    assert leg.polyline == "ors_encoded_polyline_geometry"
    assert len(leg.steps) == 3
    assert leg.steps[0].instruction == "Turn right onto Outer Ring Road"


def test_cache_key_rounding_and_ttl():
    # Test 5-minute rounding
    dt1 = datetime(2026, 10, 22, 14, 18, 45, tzinfo=timezone.utc)
    assert round_time_to_5min(dt1) == "202610221415"

    dt2 = datetime(2026, 10, 22, 14, 20, 0, tzinfo=timezone.utc)
    assert round_time_to_5min(dt2) == "202610221420"

    assert round_time_to_5min(None) == "none"

    # Test cache key format with 4 decimals
    key = make_cache_key(
        provider="google",
        mode="transit",
        origin=(28.5459123, 77.1926456),
        destination=(28.6139123, 77.2090456),
        arrive_by=dt1,
    )
    assert key == "google:transit:28.5459,77.1926:28.6139,77.2090:202610221415"

    # Test TTL values
    assert get_ttl_seconds("transit") == 600
    assert get_ttl_seconds("walk") == 86400
    assert get_ttl_seconds("bike") == 86400
    assert get_ttl_seconds("drive") == 300
    assert get_ttl_seconds("discovery") == 21600


def test_cache_hit_and_miss(db):
    db.execute(delete(RouteCache))
    db.commit()

    origin = (28.5459, 77.1926)
    dest = (28.6094, 77.0368)

    # 1. First call -> Cache Miss
    routes1, prov1, cached1 = get_route(
        db=db,
        origin=origin,
        destination=dest,
        mode="walk",
        fake_provider=FakeProvider(),
    )
    assert cached1 is False
    assert len(routes1) >= 1

    # 2. Second call -> Cache Hit
    routes2, prov2, cached2 = get_route(
        db=db,
        origin=origin,
        destination=dest,
        mode="walk",
        fake_provider=FakeProvider(),
    )
    assert cached2 is True
    assert routes2[0].duration_s == routes1[0].duration_s
    assert routes2[0].distance_m == routes1[0].distance_m


def test_fallback_order_with_mocked_providers(db):
    db.execute(delete(RouteCache))
    db.commit()

    origin = (28.5459, 77.1926)
    dest = (28.6139, 77.2090)

    # Mock primary (ORS) to time out
    mock_ors = MagicMock(spec=RouteProvider)
    mock_ors.name = "ors"
    mock_ors.get_routes.side_effect = ProviderTimeout("ORS Timeout")

    # Mock fallback (Google) to succeed
    mock_google = MagicMock(spec=RouteProvider)
    mock_google.name = "google"
    fake_route = FakeProvider().get_routes(origin, dest, "drive")[0]
    mock_google.get_routes.return_value = [fake_route]

    routes, provider, cached = get_route(
        db=db,
        origin=origin,
        destination=dest,
        mode="drive",
        google_provider=mock_google,
        ors_provider=mock_ors,
    )

    # Primary should have been called twice (1 attempt + 1 retry)
    assert mock_ors.get_routes.call_count == 2
    # Fallback should have been called once and succeeded
    assert mock_google.get_routes.call_count == 1
    assert provider == "google"
    assert len(routes) == 1


def test_transit_failure_returns_helpful_message(db):
    db.execute(delete(RouteCache))
    db.commit()

    origin = (28.5459, 77.1926)
    dest = (28.6139, 77.2090)

    # Mock Google to fail for transit (which has NO fallback)
    mock_google = MagicMock(spec=RouteProvider)
    mock_google.name = "google"
    mock_google.get_routes.side_effect = ProviderError("No transit schedule")

    with pytest.raises(AppError) as exc_info:
        get_route(
            db=db,
            origin=origin,
            destination=dest,
            mode="transit",
            google_provider=mock_google,
        )

    err = exc_info.value
    assert err.code == "ROUTE_UNAVAILABLE"
    assert "Try walking or cab" in err.message


def test_cost_guard_blocks_paid_calls(db):
    db.execute(delete(RouteCache))
    db.commit()

    reset_paid_calls()
    assert is_paid_limit_exceeded() is False

    # Simulate hitting limit
    for _ in range(500):
        record_paid_call()
    assert is_paid_limit_exceeded() is True

    origin = (28.5459, 77.1926)
    dest = (28.6139, 77.2090)

    mock_google = MagicMock(spec=RouteProvider)
    mock_google.name = "google"

    # Transit requires Google (paid) with no fallback -> blocked by cost guard
    with pytest.raises(AppError) as exc_info:
        get_route(
            db=db,
            origin=origin,
            destination=dest,
            mode="transit",
            google_provider=mock_google,
        )

    assert exc_info.value.code == "ROUTE_UNAVAILABLE"
    assert "Daily paid route quota reached" in exc_info.value.message

    reset_paid_calls()


def test_route_api_validation_and_rate_limiting(client):
    # 1. Invalid coordinates validation
    res_bad = client.get(
        "/api/v1/route?origin_lat=999&origin_lng=77.1926&dest_lat=28.6139&dest_lng=77.2090"
    )
    assert res_bad.status_code == 422
    assert res_bad.json()["code"] == "VALIDATION_ERROR"

    # 2. Rate limiting (30 requests/min)
    from app.api.route import _ip_route_requests

    _ip_route_requests.clear()

    # Perform 30 requests -> all succeed
    for _ in range(30):
        res = client.get(
            "/api/v1/route?origin_lat=28.5459&origin_lng=77.1926&dest_lat=28.6139&dest_lng=77.2090&mode=walk"
        )
        assert res.status_code == 200

    # 31st request triggers RATE_LIMITED (429)
    res_rate_limited = client.get(
        "/api/v1/route?origin_lat=28.5459&origin_lng=77.1926&dest_lat=28.6139&dest_lng=77.2090&mode=walk"
    )
    assert res_rate_limited.status_code == 429
    assert res_rate_limited.json()["code"] == "RATE_LIMITED"

    _ip_route_requests.clear()


def test_travel_estimates_cap_at_20_and_real_routing(client):
    # Log in as organizer
    org_login = client.post(
        "/api/v1/auth/login",
        json={"email": "organizer@eventroute.dev", "password": "Organizer@123"},
    )
    token = org_login.json()["access_token"]
    headers = {"Authorization": f"Bearer {token}"}

    # 1. Exceeding 20 IDs returns 422 VALIDATION_ERROR
    excess_ids = [str(uuid.uuid4()) for _ in range(21)]
    res_excess = client.post(
        "/api/v1/events/travel-estimates",
        json={
            "origin": {"lat": 28.6139, "lng": 77.2090},
            "event_ids": excess_ids,
        },
    )
    assert res_excess.status_code == 422
    assert res_excess.json()["code"] == "VALIDATION_ERROR"

    # 2. Valid request returns real cached estimates
    # Create an event at Dogra Hall
    now = datetime.now(timezone.utc)
    ev_res = client.post(
        "/api/v1/events",
        headers=headers,
        json={
            "title": "Routing Discovery Test",
            "category": "Workshop",
            "mode": "offline",
            "start_at": (now + timedelta(days=5)).isoformat(),
            "check_in_at": (now + timedelta(days=5)).isoformat(),
            "registration_deadline": (now + timedelta(days=4)).isoformat(),
            "venue_name": "Dogra Hall",
            "venue_lat": 28.5459,
            "venue_lng": 77.1926,
            "participation_type": "solo",
        },
    )
    assert ev_res.status_code == 201
    event_id = ev_res.json()["id"]

    est_res = client.post(
        "/api/v1/events/travel-estimates",
        json={
            "origin": {"lat": 28.6139, "lng": 77.2090},
            "event_ids": [event_id],
        },
    )
    assert est_res.status_code == 200
    est_data = est_res.json()
    assert len(est_data["items"]) == 1
    item = est_data["items"][0]
    assert item["event_id"] == event_id
    assert item["distance_km"] > 0
    assert item["duration_min"] > 0
    assert item["source"] in ("route", "estimate")
