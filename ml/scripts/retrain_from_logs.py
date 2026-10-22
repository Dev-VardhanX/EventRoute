"""Offline retraining pipeline for EventRoute delay-risk buffer using travel_logs.

Ingests exported travel_logs CSV, applies data quality and privacy filters,
hashes origin/destination into anonymized route_id, splits by time (newest 20% holdout),
trains candidate buffer_v3, and evaluates the promotion rule against the current model.

Promotion Rule:
  1. Holdout coverage must be between 0.85 and 0.95.
  2. Holdout pinball loss must not be worse than current model by > 2%.
If passed -> updates models/registry.json, writes buffer_v3.joblib & metadata.
If failed -> writes models/CANDIDATE_REJECTED.md, keeps current model.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import logging
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingRegressor

# Ensure package root is in sys.path
ML_ROOT = Path(__file__).parent.parent
if str(ML_ROOT) not in sys.path:
    sys.path.insert(0, str(ML_ROOT))

from eventroute_ml.buffer import BufferPredictor, clamp
from eventroute_ml.config import MODES, QUANTILE
from eventroute_ml.features import FEATURE_NAMES, TripFeatures, row
from eventroute_ml.rules import local_hour_weekday

log = logging.getLogger("eventroute_ml.retrain")
logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")

SALT = "eventroute_secure_retrain_salt_2026"
MIN_CLEAN_TRIPS = 300


def pinball_loss(y_true: np.ndarray, y_pred: np.ndarray, q: float = QUANTILE) -> float:
    diff = y_true - y_pred
    return float(np.mean(np.maximum(q * diff, (q - 1.0) * diff)))


def hash_route(lat1: float, lng1: float, lat2: float, lng2: float) -> str:
    # Salted hash rounded to ~1 km grid (2 decimals) to anonymize exact locations
    s = f"{SALT}_{round(lat1, 2)}_{round(lng1, 2)}_{round(lat2, 2)}_{round(lng2, 2)}"
    return hashlib.sha256(s.encode("utf-8")).hexdigest()[:16]


def clean_and_transform_logs(df: pd.DataFrame) -> tuple[pd.DataFrame, dict[str, int]]:
    """Apply data-quality and privacy rules to raw travel logs."""
    stats = {"initial": len(df), "dropped_incomplete": 0, "dropped_gps_jump": 0, "dropped_duration": 0, "dropped_invalid": 0}

    # 1. Privacy guard: verify no PII columns exist
    pii_cols = {"user_id", "email", "name", "phone", "student_id", "roll_no"}
    found_pii = [c for c in pii_cols if c in df.columns]
    if found_pii:
        raise ValueError(f"Privacy Violation: Raw logs contain PII columns: {found_pii}. Refusing to process.")

    # 2. Check required columns
    required = ["origin_lat", "origin_lng", "dest_lat", "dest_lng", "mode", "departure_time",
                "planned_duration_s", "actual_duration_s", "distance_m"]
    missing = [c for c in required if c not in df.columns]
    if missing:
        raise ValueError(f"Missing required columns in travel logs: {missing}")

    clean_rows = []
    for r in df.itertuples(index=False):
        # Rule 1: Drop incomplete or abandoned trips
        completed = getattr(r, "completed", True)
        if completed in (False, "false", "False", 0, "0"):
            stats["dropped_incomplete"] += 1
            continue

        actual_s = getattr(r, "actual_duration_s", None)
        planned_s = getattr(r, "planned_duration_s", None)
        dist_m = getattr(r, "distance_m", None)

        if actual_s is None or pd.isna(actual_s) or planned_s is None or pd.isna(planned_s) or pd.isna(dist_m):
            stats["dropped_incomplete"] += 1
            continue

        try:
            actual_s = float(actual_s)
            planned_s = float(planned_s)
            dist_m = float(dist_m)
        except (ValueError, TypeError):
            stats["dropped_invalid"] += 1
            continue

        # Rule 2: Drop GPS jumps and impossible speeds
        gps_jump = getattr(r, "gps_jump", False)
        if gps_jump in (True, "true", "True", 1, "1"):
            stats["dropped_gps_jump"] += 1
            continue

        mode = str(getattr(r, "mode", "drive") or "drive").strip().lower()
        if mode not in MODES:
            mode = "drive"

        speed_kmh = (dist_m / 1000.0) / (actual_s / 3600.0) if actual_s > 0 else 0.0
        max_speed = 35.0 if mode in ("walk", "bike") else 180.0
        if speed_kmh > max_speed:
            stats["dropped_gps_jump"] += 1
            continue

        # Rule 3: Drop absurd durations (< 60s or > 24h, or planned <= 0)
        if actual_s < 60.0 or actual_s > 86400.0 or planned_s <= 0.0:
            stats["dropped_duration"] += 1
            continue

        # Parse departure time
        dep_str = str(getattr(r, "departure_time", "") or "")
        try:
            dep_dt = datetime.fromisoformat(dep_str.replace("Z", "+00:00"))
            if dep_dt.tzinfo is None:
                dep_dt = dep_dt.replace(tzinfo=timezone.utc)
        except Exception:
            stats["dropped_invalid"] += 1
            continue

        # Coordinate check
        try:
            lat1, lng1 = float(r.origin_lat), float(r.origin_lng)
            lat2, lng2 = float(r.dest_lat), float(r.dest_lng)
            if not (np.isfinite(lat1) and np.isfinite(lng1) and np.isfinite(lat2) and np.isfinite(lng2)):
                stats["dropped_invalid"] += 1
                continue
        except (ValueError, TypeError):
            stats["dropped_invalid"] += 1
            continue

        # Hash coordinates into route_id
        route_id = hash_route(lat1, lng1, lat2, lng2)

        # Compute model features
        hour, weekday = local_hour_weekday(dep_dt, tz="Asia/Kolkata")
        transfers = max(0, int(getattr(r, "transfers", 0) or 0))

        walking_s = getattr(r, "walking_s", 0.0)
        try:
            walking_s = float(walking_s) if walking_s is not None and pd.notna(walking_s) else 0.0
        except (ValueError, TypeError):
            walking_s = 0.0

        walking_share = min(1.0, max(0.0, walking_s / actual_s)) if actual_s > 0 else 0.0
        target_min = (actual_s - planned_s) / 60.0

        clean_rows.append({
            "route_id": route_id,
            "mode": mode,
            "hour": hour,
            "weekday": weekday,
            "distance_m": dist_m,
            "transfers": transfers,
            "walking_share": walking_share,
            "target_min": target_min,
            "source": "travel_log",
            "departure_time_dt": dep_dt,
        })

    clean_df = pd.DataFrame(clean_rows)
    stats["clean"] = len(clean_df)
    return clean_df, stats


def build_feature_matrix(df: pd.DataFrame) -> np.ndarray:
    feats = [
        TripFeatures(
            mode=r.mode,
            hour=int(r.hour),
            weekday=int(r.weekday),
            distance_m=float(r.distance_m),
            transfers=int(r.transfers),
            walking_share=float(r.walking_share),
        )
        for r in df.itertuples(index=False)
    ]
    return np.asarray([row(f) for f in feats], dtype=float)


def run_retrain(
    logs_csv: str | Path,
    candidate_out: str | Path | None = None,
    models_dir: str | Path | None = None,
    registry_file: str | Path | None = None,
    force_promote: bool = False,
) -> dict[str, Any]:
    logs_path = Path(logs_csv)
    if not logs_path.exists():
        raise FileNotFoundError(f"Logs file does not exist: {logs_path}")

    log.info("Loading travel logs from %s", logs_path)
    df_raw = pd.read_csv(logs_path)
    clean_df, stats = clean_and_transform_logs(df_raw)

    log.info(
        "Cleaned logs: %d clean / %d initial rows (dropped: incomplete=%d, gps_jump=%d, duration=%d, invalid=%d)",
        stats["clean"], stats["initial"], stats["dropped_incomplete"], stats["dropped_gps_jump"],
        stats["dropped_duration"], stats["dropped_invalid"]
    )

    if stats["clean"] < MIN_CLEAN_TRIPS:
        msg = (
            f"Refusing to retrain: Dataset contains {stats['clean']} clean trips, below minimum threshold "
            f"of {MIN_CLEAN_TRIPS}. Sparse sample sizes lack statistical power for 90th percentile quantile "
            f"estimation and risk severe model overfitting or calibration collapse."
        )
        log.error(msg)
        return {"status": "refused", "reason": msg, "clean_rows": stats["clean"]}

    # Time-ordered split: sort by departure_time_dt
    clean_df = clean_df.sort_values("departure_time_dt").reset_index(drop=True)
    n_total = len(clean_df)
    n_holdout = max(1, int(0.20 * n_total))
    train_df = clean_df.iloc[:-n_holdout].copy()
    holdout_df = clean_df.iloc[-n_holdout:].copy()

    log.info("Split: %d training trips, %d newest-trips holdout trips", len(train_df), len(holdout_df))

    # Build X and y
    X_train = build_feature_matrix(train_df)
    y_train = train_df["target_min"].to_numpy(dtype=float)
    X_holdout = build_feature_matrix(holdout_df)
    y_holdout = holdout_df["target_min"].to_numpy(dtype=float)

    # Train candidate model (buffer-v3)
    log.info("Training candidate model buffer-v3 (HistGradientBoostingRegressor, quantile=%.2f)...", QUANTILE)
    candidate = HistGradientBoostingRegressor(
        loss="quantile",
        quantile=QUANTILE,
        max_iter=150,
        min_samples_leaf=15,
        random_state=42,
    )
    candidate.fit(X_train, y_train)

    # Evaluate candidate on newest-trips holdout
    cand_preds = np.asarray([clamp(float(p)) for p in candidate.predict(X_holdout)], dtype=float)
    cand_coverage = float(np.mean(cand_preds >= y_holdout))
    cand_pinball = pinball_loss(y_holdout, cand_preds)
    cand_mean_buffer = float(np.mean(cand_preds))

    # Evaluate current active model on same holdout
    current_predictor = BufferPredictor()
    current_preds = np.asarray([
        float(current_predictor.predict(
            mode=r.mode, hour=r.hour, weekday=r.weekday, distance_m=r.distance_m,
            transfers=r.transfers, walking_share=r.walking_share
        )["minutes"])
        for r in holdout_df.itertuples(index=False)
    ], dtype=float)
    current_coverage = float(np.mean(current_preds >= y_holdout))
    current_pinball = pinball_loss(y_holdout, current_preds)
    current_mean_buffer = float(np.mean(current_preds))

    log.info(
        "Evaluation on Holdout (n=%d):\n"
        "  Candidate (buffer-v3): Coverage=%.4f (target 0.85-0.95), Pinball=%.4f, Mean Buffer=%.2f min\n"
        "  Current (%s):    Coverage=%.4f, Pinball=%.4f, Mean Buffer=%.2f min",
        n_holdout, cand_coverage, cand_pinball, cand_mean_buffer,
        current_predictor.version, current_coverage, current_pinball, current_mean_buffer,
    )

    # Check Promotion Rule:
    # 1. Candidate coverage between 0.85 and 0.95
    # 2. Candidate pinball loss not worse than current by > 2% (i.e. <= current_pinball * 1.02)
    cov_ok = 0.85 <= cand_coverage <= 0.95
    pinball_ok = cand_pinball <= (current_pinball * 1.02)
    promoted = (cov_ok and pinball_ok) or force_promote

    if models_dir is not None:
        models_dir_path = Path(models_dir)
    elif candidate_out is not None:
        models_dir_path = Path(candidate_out).parent
    else:
        models_dir_path = ML_ROOT / "models"
    models_dir_path.mkdir(parents=True, exist_ok=True)

    v3_joblib = Path(candidate_out) if candidate_out else (models_dir_path / "buffer_v3.joblib")
    v3_meta = v3_joblib.with_suffix(".metadata.json")
    registry_target = Path(registry_file) if registry_file else (models_dir_path / "registry.json")

    result = {
        "status": "promoted" if promoted else "rejected",
        "candidate_version": "buffer-v3",
        "current_version": current_predictor.version,
        "clean_rows": stats["clean"],
        "train_rows": len(train_df),
        "holdout_rows": len(holdout_df),
        "candidate_metrics": {
            "coverage": round(cand_coverage, 4),
            "pinball_loss": round(cand_pinball, 4),
            "mean_buffer_min": round(cand_mean_buffer, 2),
        },
        "current_metrics": {
            "coverage": round(current_coverage, 4),
            "pinball_loss": round(current_pinball, 4),
            "mean_buffer_min": round(current_mean_buffer, 2),
        },
        "criteria": {
            "coverage_between_85_95": cov_ok,
            "pinball_within_2_percent": pinball_ok,
        },
    }

    if promoted:
        # Save bundle
        bundle = {
            "model": candidate,
            "version": "buffer-v3",
            "feature_names": FEATURE_NAMES,
            "trained_at": datetime.now(timezone.utc).isoformat(),
        }
        joblib.dump(bundle, v3_joblib)
        with open(v3_meta, "w", encoding="utf-8") as f:
            json.dump(result, f, indent=2)

        # Update registry.json
        prev_ver = current_predictor.version
        try:
            rel_model_path = str(v3_joblib.relative_to(ML_ROOT)).replace("\\", "/")
        except ValueError:
            rel_model_path = str(v3_joblib).replace("\\", "/")

        reg_payload = {
            "active_version": "buffer-v3",
            "active_model_path": rel_model_path,
            "previous_version": prev_ver,
            "updated_at": datetime.now(timezone.utc).isoformat(),
            "metrics": result["candidate_metrics"],
        }
        with open(registry_target, "w", encoding="utf-8") as f:
            json.dump(reg_payload, f, indent=2)

        log.info("PROMOTED: Candidate buffer-v3 passed criteria and is now active in registry.json")
    else:
        # Document rejection in CANDIDATE_REJECTED.md
        rej_file = models_dir_path / "CANDIDATE_REJECTED.md"
        rej_content = [
            "# Candidate Model buffer-v3 Rejection Report",
            "",
            f"**Evaluation Timestamp:** `{datetime.now(timezone.utc).isoformat()}`  ",
            "**Candidate Version:** `buffer-v3`  ",
            f"**Current Active Model:** `{current_predictor.version}`  ",
            "",
            "## Promotion Criteria & Results",
            "",
            "| Rule | Requirement | Candidate Value | Current Value | Status |",
            "| :--- | :--- | :---: | :---: | :---: |",
            f"| **1. Coverage Window** | $0.85 \\le \\text{{Coverage}} \\le 0.95$ | **{cand_coverage:.4f}** | {current_coverage:.4f} | {'PASS' if cov_ok else 'FAIL'} |",
            f"| **2. Pinball Loss Gate** | $\\le {current_pinball * 1.02:.4f}$ (+2% max) | **{cand_pinball:.4f}** | {current_pinball:.4f} | {'PASS' if pinball_ok else 'FAIL'} |",
            "",
            "## Holdout Metrics Breakdown",
            "",
            f"- **Clean Training Rows:** {len(train_df)}",
            f"- **Newest-Trips Holdout Rows:** {len(holdout_df)}",
            f"- **Candidate Mean Buffer:** {cand_mean_buffer:.2f} min",
            f"- **Current Mean Buffer:** {current_mean_buffer:.2f} min",
            "",
            "## Decision",
            "",
            "**REJECTED.** The candidate model failed to satisfy the required promotion gate.",
            "The current active model remains active in `models/registry.json`. No existing models were overwritten.",
        ]
        with open(rej_file, "w", encoding="utf-8") as f:
            f.write("\n".join(rej_content))

        log.warning("REJECTED: Candidate buffer-v3 failed promotion criteria. Wrote %s", rej_file)

    return result


def main():
    parser = argparse.ArgumentParser(description="Retrain EventRoute buffer model from travel logs.")
    parser.add_argument("--logs", required=True, help="Path to exported travel_logs CSV")
    parser.add_argument("--candidate-out", default=None, help="Optional output path for candidate model")
    parser.add_argument("--force-promote", action="store_true", default=False, help="Force promotion for testing")
    args = parser.parse_args()

    res = run_retrain(
        logs_csv=args.logs,
        candidate_out=args.candidate_out,
        force_promote=args.force_promote,
    )
    if res["status"] == "refused":
        sys.exit(2)
    elif res["status"] == "rejected":
        sys.exit(1)
    else:
        sys.exit(0)


if __name__ == "__main__":
    main()
