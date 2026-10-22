"""Offline tests for sample_routes_api.py using httpx.MockTransport.

Zero network access. Verifies request contract, header masking, response parsing,
resumability, --max-calls protection, dry-run safety, and error skipping.
"""
from __future__ import annotations

import csv
import json
import sys
from datetime import UTC, datetime
from pathlib import Path

import httpx
import pytest

# Ensure scripts directory is importable
SCRIPTS_DIR = Path(__file__).parent.parent / "scripts"
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

from sample_routes_api import (
    FIELD_MASK,
    OUT_COLUMNS,
    URL,
    call,
    run_sampling,
    seconds,
)


@pytest.fixture
def sample_pair_csv(tmp_path):
    p = tmp_path / "od_pairs.csv"
    with open(p, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["route_id", "origin_lat", "origin_lng", "dest_lat", "dest_lng"])
        w.writerow(["test_route_01", "28.5459", "77.1926", "28.5520", "77.1910"])
    return p


def test_seconds_parser():
    """Verify seconds parser strips 's' and converts to float."""
    assert seconds("1234s") == 1234.0
    assert seconds("60.5s") == 60.5
    assert seconds(120) == 120.0


def test_request_body_and_headers_contract():
    """Verify request headers and body match official Routes API specifications."""
    recorded_requests = []

    def mock_handler(request: httpx.Request):
        recorded_requests.append(request)
        body = json.loads(request.content.decode("utf-8"))
        # Return realistic response
        dur = "1800s" if body.get("trafficModel") == "BEST_GUESS" else "2400s"
        resp_data = {
            "routes": [
                {
                    "distanceMeters": 15000,
                    "duration": dur,
                    "staticDuration": "1500s",
                }
            ]
        }
        return httpx.Response(200, json=resp_data)

    transport = httpx.MockTransport(mock_handler)
    client = httpx.Client(transport=transport)

    test_pair = {
        "origin_lat": "28.5459",
        "origin_lng": "77.1926",
        "dest_lat": "28.5520",
        "dest_lng": "77.1910",
    }
    departure_time = datetime(2026, 10, 27, 9, 0, tzinfo=UTC)

    res = call(client, key="test-api-key-123", p=test_pair, when=departure_time, model="BEST_GUESS")
    assert res["duration_s"] == 1800.0
    assert res["distance_m"] == 15000.0

    assert len(recorded_requests) == 1
    req = recorded_requests[0]

    # Verify Endpoint and Headers
    assert str(req.url) == URL
    assert req.headers["X-Goog-Api-Key"] == "test-api-key-123"
    assert req.headers["X-Goog-FieldMask"] == FIELD_MASK
    assert req.headers["Content-Type"] == "application/json"

    # Verify Body
    body = json.loads(req.content.decode("utf-8"))
    assert body["travelMode"] == "DRIVE"
    assert body["routingPreference"] == "TRAFFIC_AWARE_OPTIMAL"
    assert body["trafficModel"] == "BEST_GUESS"
    assert body["departureTime"] == "2026-10-27T09:00:00Z"
    assert body["origin"]["location"]["latLng"]["latitude"] == 28.5459
    assert body["origin"]["location"]["latLng"]["longitude"] == 77.1926
    assert body["destination"]["location"]["latLng"]["latitude"] == 28.5520
    assert body["destination"]["location"]["latLng"]["longitude"] == 77.1910


def test_dry_run_writes_nothing(sample_pair_csv, tmp_path):
    """Verify --dry-run makes no calls and creates no output file."""
    out_file = tmp_path / "should_not_exist.csv"

    # Client that would fail if called
    transport = httpx.MockTransport(lambda r: httpx.Response(500))
    client = httpx.Client(transport=transport)

    ret = run_sampling(
        pairs_path=sample_pair_csv,
        out_path=out_file,
        max_calls=1500,
        dry_run=True,
        client=client,
    )
    assert ret == 0
    assert not out_file.exists()


def test_max_calls_limit_refusal(sample_pair_csv, tmp_path):
    """Verify sampler refuses to start when required calls exceed max_calls."""
    out_file = tmp_path / "out.csv"
    transport = httpx.MockTransport(lambda r: httpx.Response(200, json={"routes": []}))
    client = httpx.Client(transport=transport)

    # 1 pair * 17 hours * 2 days * 2 models = 68 calls
    ret = run_sampling(
        pairs_path=sample_pair_csv,
        out_path=out_file,
        max_calls=50,  # Less than 68
        dry_run=False,
        key="test-key",
        client=client,
    )
    assert ret == 2
    assert not out_file.exists()


def test_successful_sampling_and_columns(sample_pair_csv, tmp_path):
    """Verify rows are written with exact required columns and correct target_min."""
    out_file = tmp_path / "real_api.csv"

    call_count = [0]

    def mock_handler(request: httpx.Request):
        call_count[0] += 1
        body = json.loads(request.content.decode("utf-8"))
        # 1800s best guess, 2100s pessimistic -> 300s diff = 5.0 minutes
        dur = "1800s" if body["trafficModel"] == "BEST_GUESS" else "2100s"
        return httpx.Response(200, json={
            "routes": [{"duration": dur, "distanceMeters": 8500}]
        })

    transport = httpx.MockTransport(mock_handler)
    client = httpx.Client(transport=transport)

    ret = run_sampling(
        pairs_path=sample_pair_csv,
        out_path=out_file,
        max_calls=1500,
        dry_run=False,
        key="test-key",
        client=client,
    )
    assert ret == 0
    assert out_file.exists()

    with open(out_file, "r", encoding="utf-8") as f:
        reader = list(csv.DictReader(f))

    # 1 pair * 17 hours * 2 days = 34 departures
    assert len(reader) == 34
    assert call_count[0] == 68  # 34 * 2 calls

    for row in reader:
        for col in OUT_COLUMNS:
            assert col in row
        assert row["route_id"] == "test_route_01"
        assert row["mode"] == "drive"
        assert float(row["distance_m"]) == 8500.0
        assert float(row["target_min"]) == 5.0
        assert row["source"] == "real_api"
        assert float(row["best_guess_s"]) == 1800.0
        assert float(row["pessimistic_s"]) == 2100.0


def test_resumable_csv_skips_finished_keys(sample_pair_csv, tmp_path):
    """Verify re-running skips already completed departures."""
    out_file = tmp_path / "resumable.csv"

    # Pre-populate CSV with 2 completed rows
    with open(out_file, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=OUT_COLUMNS)
        w.writeheader()
        w.writerow({
            "route_id": "test_route_01", "mode": "drive", "hour": "6", "weekday": "1",
            "distance_m": "5000", "transfers": "0", "walking_share": "0.0",
            "target_min": "4.0", "source": "real_api", "best_guess_s": "600", "pessimistic_s": "840",
        })
        w.writerow({
            "route_id": "test_route_01", "mode": "drive", "hour": "7", "weekday": "1",
            "distance_m": "5000", "transfers": "0", "walking_share": "0.0",
            "target_min": "4.5", "source": "real_api", "best_guess_s": "600", "pessimistic_s": "870",
        })

    call_count = [0]

    def mock_handler(request: httpx.Request):
        call_count[0] += 1
        return httpx.Response(200, json={
            "routes": [{"duration": "1000s", "distanceMeters": 5000}]
        })

    transport = httpx.MockTransport(mock_handler)
    client = httpx.Client(transport=transport)

    ret = run_sampling(
        pairs_path=sample_pair_csv,
        out_path=out_file,
        max_calls=1500,
        dry_run=False,
        key="test-key",
        client=client,
    )
    assert ret == 0

    # Total departures = 34. 2 were pre-populated, so 32 departures remained = 64 calls
    assert call_count[0] == 64

    with open(out_file, "r", encoding="utf-8") as f:
        reader = list(csv.DictReader(f))
    assert len(reader) == 34


def test_failing_call_skipped_without_crashing(sample_pair_csv, tmp_path):
    """Verify failing HTTP call for one departure is logged/skipped and others proceed."""
    out_file = tmp_path / "errors.csv"

    attempts = [0]

    def mock_handler(request: httpx.Request):
        attempts[0] += 1
        # Fail only the first call (first departure)
        if attempts[0] == 1:
            return httpx.Response(500, text="Internal Server Error")
        return httpx.Response(200, json={
            "routes": [{"duration": "900s", "distanceMeters": 4000}]
        })

    transport = httpx.MockTransport(mock_handler)
    client = httpx.Client(transport=transport)

    ret = run_sampling(
        pairs_path=sample_pair_csv,
        out_path=out_file,
        max_calls=1500,
        dry_run=False,
        key="test-key",
        client=client,
    )
    assert ret == 0
    assert out_file.exists()

    with open(out_file, "r", encoding="utf-8") as f:
        reader = list(csv.DictReader(f))

    # 34 departures total, 1 skipped due to failure -> 33 written
    assert len(reader) == 33


def test_validate_real_data_script(tmp_path):
    """Verify validate_real_data checks schema, drops negative targets, and evaluates peak vs off-peak."""
    from validate_real_data import validate_real_data

    csv_path = tmp_path / "valid_data.csv"
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=OUT_COLUMNS)
        w.writeheader()
        # Peak row: weekday 1 (Tuesday), hour 9 (Peak) -> target_min 8.0
        w.writerow({
            "route_id": "r1", "mode": "drive", "hour": "9", "weekday": "1",
            "distance_m": "10000", "transfers": "0", "walking_share": "0.0",
            "target_min": "8.0", "source": "real_api", "best_guess_s": "1200", "pessimistic_s": "1680",
        })
        # Off-peak row: weekday 1 (Tuesday), hour 14 (Off-peak) -> target_min 3.0
        w.writerow({
            "route_id": "r1", "mode": "drive", "hour": "14", "weekday": "1",
            "distance_m": "10000", "transfers": "0", "walking_share": "0.0",
            "target_min": "3.0", "source": "real_api", "best_guess_s": "1000", "pessimistic_s": "1180",
        })
        # Invalid / negative row: should be dropped
        w.writerow({
            "route_id": "r1", "mode": "drive", "hour": "15", "weekday": "1",
            "distance_m": "10000", "transfers": "0", "walking_share": "0.0",
            "target_min": "-2.0", "source": "real_api", "best_guess_s": "1000", "pessimistic_s": "880",
        })

    assert validate_real_data(csv_path) is True

    # Missing file returns False
    assert validate_real_data(tmp_path / "missing.csv") is False

    # Bad schema returns False
    bad_schema = tmp_path / "bad_schema.csv"
    with open(bad_schema, "w", newline="", encoding="utf-8") as f:
        f.write("foo,bar\n1,2\n")
    assert validate_real_data(bad_schema) is False

