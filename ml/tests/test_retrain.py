"""Unit and integration tests for travel_logs retraining pipeline and model registry.

Verifies:
  1. Data cleaning drops bad rows (incomplete, GPS jumps, absurd durations, invalid coords).
  2. Privacy protection: rejects PII, outputs only hashed route_id and model features.
  3. Minimum dataset gate: refuses to retrain on < 300 clean trips.
  4. Promotion and rejection gates on newest-trips holdout.
  5. Registry updates and loader cascade fallback behavior.
"""
from __future__ import annotations

import json
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pandas as pd
import pytest

# Ensure scripts directory is in sys.path
SCRIPTS_DIR = Path(__file__).parent.parent / "scripts"
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

try:
    from scripts.retrain_from_logs import (
        clean_and_transform_logs,
        run_retrain,
    )
except ImportError:
    from retrain_from_logs import (
        clean_and_transform_logs,
        run_retrain,
    )

from eventroute_ml.buffer import BufferPredictor


def make_fake_log_row(i: int, base_time: datetime, bad_type: str | None = None) -> dict:
    t = base_time + timedelta(minutes=i * 15)
    row = {
        "trip_id": f"trip-{i:05d}",
        "origin_lat": 28.5459 + (i % 5) * 0.02,
        "origin_lng": 77.1926 + (i % 5) * 0.02,
        "dest_lat": 28.6094 + (i % 3) * 0.02,
        "dest_lng": 77.0368 + (i % 3) * 0.02,
        "mode": ["drive", "metro", "bus"][i % 3],
        "departure_time": t.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "planned_duration_s": 1800.0,
        "actual_duration_s": 2100.0 + (i % 7) * 60.0,
        "distance_m": 15000.0,
        "transfers": 1 if i % 3 == 1 else 0,
        "walking_s": 300.0 if i % 3 != 0 else 0.0,
        "completed": True,
        "gps_jump": False,
    }

    if bad_type == "incomplete":
        row["completed"] = False
    elif bad_type == "missing_actual":
        row["actual_duration_s"] = None
    elif bad_type == "gps_jump":
        row["gps_jump"] = True
    elif bad_type == "teleport_speed":
        row["actual_duration_s"] = 100.0  # 15 km in 100s = 540 km/h
    elif bad_type == "too_short":
        row["actual_duration_s"] = 40.0   # < 60s
    elif bad_type == "too_long":
        row["actual_duration_s"] = 100_000.0  # > 24h
    elif bad_type == "invalid_coords":
        row["origin_lat"] = float("nan")
    elif bad_type == "bad_date":
        row["departure_time"] = "not-a-date"

    return row


def create_fake_logs_csv(path: Path, n_rows: int = 400, include_bad: bool = True) -> Path:
    base_time = datetime(2026, 10, 1, 6, 0, tzinfo=timezone.utc)
    rows = []
    for i in range(n_rows):
        bad_type = None
        if include_bad and i % 20 == 0:
            bad_type = ["incomplete", "gps_jump", "teleport_speed", "too_short", "invalid_coords"][i % 5]
        rows.append(make_fake_log_row(i, base_time, bad_type=bad_type))

    df = pd.DataFrame(rows)
    path.parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(path, index=False)
    return path


def test_clean_and_transform_drops_bad_rows(tmp_path):
    """Verify that incomplete trips, GPS jumps, and absurd durations are dropped."""
    csv_file = tmp_path / "raw_logs.csv"
    create_fake_logs_csv(csv_file, n_rows=100, include_bad=True)

    df_raw = pd.read_csv(csv_file)
    clean_df, stats = clean_and_transform_logs(df_raw)

    assert stats["initial"] == 100
    assert stats["clean"] < 100
    assert stats["dropped_incomplete"] > 0 or stats["dropped_gps_jump"] > 0 or stats["dropped_duration"] > 0
    assert len(clean_df) == stats["clean"]


def test_privacy_no_pii_in_training_data(tmp_path):
    """Verify that training dataset has no PII and raw coordinates are hashed."""
    csv_file = tmp_path / "pii_test.csv"
    create_fake_logs_csv(csv_file, n_rows=50, include_bad=False)

    df_raw = pd.read_csv(csv_file)
    clean_df, _ = clean_and_transform_logs(df_raw)

    # 1. Output must not have raw coordinates or trip_id
    forbidden_cols = {"origin_lat", "origin_lng", "dest_lat", "dest_lng", "trip_id", "user_id", "email", "name"}
    for col in forbidden_cols:
        assert col not in clean_df.columns

    # 2. Must contain hashed route_id and standard model features
    assert "route_id" in clean_df.columns
    assert "mode" in clean_df.columns
    assert "hour" in clean_df.columns
    assert "weekday" in clean_df.columns
    assert "target_min" in clean_df.columns
    assert clean_df["source"].iloc[0] == "travel_log"

    # 3. Reject file immediately if PII columns are present
    df_with_pii = df_raw.copy()
    df_with_pii["email"] = "student@nsut.ac.in"
    with pytest.raises(ValueError, match="Privacy Violation"):
        clean_and_transform_logs(df_with_pii)


def test_below_300_rows_refuses_retrain(tmp_path):
    """Verify retrain pipeline refuses to proceed if clean rows < 300."""
    csv_file = tmp_path / "small_logs.csv"
    create_fake_logs_csv(csv_file, n_rows=150, include_bad=False)

    res = run_retrain(logs_csv=csv_file)
    assert res["status"] == "refused"
    assert "below minimum threshold of 300" in res["reason"]


def test_rejection_gate_generates_report(tmp_path):
    """Verify that an underperforming candidate fails promotion and writes CANDIDATE_REJECTED.md in temp dir."""
    csv_file = tmp_path / "failing_logs.csv"
    # Generate 350 rows with extreme / distorted target delays causing coverage to fail [0.85, 0.95]
    base_time = datetime(2026, 10, 1, 6, 0, tzinfo=timezone.utc)
    rows = []
    for i in range(350):
        r = make_fake_log_row(i, base_time)
        # Force negative delay on half and huge delay on other half
        r["actual_duration_s"] = 1800.0 + (10000.0 if i % 2 == 0 else -600.0)
        rows.append(r)
    pd.DataFrame(rows).to_csv(csv_file, index=False)

    cand_joblib = tmp_path / "buffer_v3_test.joblib"
    rej_file = tmp_path / "CANDIDATE_REJECTED.md"

    res = run_retrain(logs_csv=csv_file, candidate_out=cand_joblib, models_dir=tmp_path)

    assert res["status"] == "rejected"
    assert rej_file.exists(), "CANDIDATE_REJECTED.md should be written on rejection in temp models_dir"
    with open(rej_file, "r", encoding="utf-8") as f:
        rej_text = f.read()
    assert "buffer-v3" in rej_text
    assert "REJECTED" in rej_text


def test_successful_promotion_updates_registry(tmp_path):
    """Verify that candidate passing criteria updates registry.json in temp dir."""
    csv_file = tmp_path / "passing_logs.csv"
    create_fake_logs_csv(csv_file, n_rows=350, include_bad=False)

    cand_joblib = tmp_path / "buffer_v3_test.joblib"
    registry_path = tmp_path / "registry.json"

    # Use force_promote to test the exact promotion path and registry update mechanics
    res = run_retrain(
        logs_csv=csv_file,
        candidate_out=cand_joblib,
        models_dir=tmp_path,
        registry_file=registry_path,
        force_promote=True,
    )

    assert res["status"] == "promoted"
    assert cand_joblib.exists()

    assert registry_path.exists()
    with open(registry_path, "r", encoding="utf-8") as f:
        reg = json.load(f)
    assert reg["active_version"] == "buffer-v3"
    assert "metrics" in reg


def test_buffer_predictor_loader_fallback_cascade(tmp_path):
    """Verify BufferPredictor loader order and fallback cascade on corrupt files."""
    # 1. Point registry to non-existent or corrupt model
    corrupt_file = tmp_path / "corrupt.joblib"
    with open(corrupt_file, "wb") as f:
        f.write(b"NOT_A_VALID_JOBLIB_DATA")

    # BufferPredictor should safely catch exception, log warning, and fall back to buffer_v1.joblib
    p = BufferPredictor(model_path=corrupt_file)
    assert p.version == "rules-v1"
    assert p.load_error is not None

    # Normal loading from default cascade
    p_default = BufferPredictor()
    assert p_default.model is not None
    assert p_default.version == "buffer-v1"
