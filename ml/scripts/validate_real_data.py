"""Validate real driving-delay data collected from Google Routes API.

Checks schema, filters out invalid rows, reports per-hour and per-pair statistics,
and verifies that peak hours exhibit higher delay risk than off-peak hours.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

import numpy as np
import pandas as pd

REQUIRED_COLUMNS = [
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


def is_peak_slot(hour: int, weekday: int) -> bool:
    """Weekday 08:00-10:59 and 17:00-20:59."""
    return int(weekday) < 5 and (8 <= int(hour) < 11 or 17 <= int(hour) < 21)


def validate_real_data(csv_path: str | Path) -> bool:
    path = Path(csv_path)
    if not path.exists():
        print(f"Error: Data file does not exist: {path}")
        return False

    try:
        df = pd.read_csv(path)
    except Exception as e:
        print(f"Error reading CSV {path}: {e}")
        return False

    print(f"Loaded {len(df)} rows from {path}")

    # 1. Schema Check
    missing_cols = [c for c in REQUIRED_COLUMNS if c not in df.columns]
    if missing_cols:
        print(f"Schema Error: Missing columns: {missing_cols}")
        return False
    print("Schema Check: PASSED (all required columns present)")

    # 2. Filter Failures and Negative Targets
    initial_count = len(df)
    valid_df = df.dropna(subset=["route_id", "hour", "weekday", "target_min"]).copy()
    valid_df["target_min"] = pd.to_numeric(valid_df["target_min"], errors="coerce")
    valid_df = valid_df[valid_df["target_min"] >= 0.0]

    dropped = initial_count - len(valid_df)
    if dropped > 0:
        print(f"Dropped {dropped} invalid/negative/NaN target rows ({len(valid_df)} clean rows remaining)")
    else:
        print(f"Data Quality: All {len(valid_df)} rows are valid (non-negative target_min)")

    if len(valid_df) == 0:
        print("Error: No valid rows remaining after filtering.")
        return False

    # 3. Per-Hour Summary (Count, Mean, p90)
    print("\n--- Per-Hour Summary (target_min in minutes) ---")
    print(f"{'Hour':<6} {'Count':<8} {'Mean (min)':<12} {'p90 (min)':<12}")
    print("-" * 40)
    hour_stats = (
        valid_df.groupby("hour")["target_min"]
        .agg(
            count="count",
            mean="mean",
            p90=lambda x: np.percentile(x, 90) if len(x) > 0 else 0.0,
        )
        .sort_index()
    )
    for hr, row in hour_stats.iterrows():
        print(f"{int(hr):02d}:00  {int(row['count']):<8} {row['mean']:<12.2f} {row['p90']:<12.2f}")

    # 4. Per-Pair Summary (Count, Mean, p90)
    print("\n--- Per-Pair Summary (target_min in minutes) ---")
    print(f"{'Route ID':<28} {'Count':<8} {'Mean (min)':<12} {'p90 (min)':<12}")
    print("-" * 62)
    pair_stats = (
        valid_df.groupby("route_id")["target_min"]
        .agg(
            count="count",
            mean="mean",
            p90=lambda x: np.percentile(x, 90) if len(x) > 0 else 0.0,
        )
        .sort_index()
    )
    for rid, row in pair_stats.iterrows():
        print(f"{rid!s:<28} {int(row['count']):<8} {row['mean']:<12.2f} {row['p90']:<12.2f}")

    # 5. Peak vs. Off-Peak Validation Check
    valid_df["is_peak"] = [
        is_peak_slot(r.hour, r.weekday) for r in valid_df.itertuples(index=False)
    ]
    peak_rows = valid_df[valid_df["is_peak"]]
    offpeak_rows = valid_df[~valid_df["is_peak"]]

    mean_peak = float(peak_rows["target_min"].mean()) if len(peak_rows) > 0 else 0.0
    mean_offpeak = float(offpeak_rows["target_min"].mean()) if len(offpeak_rows) > 0 else 0.0

    print("\n--- Peak vs. Off-Peak Delay Analysis ---")
    print(f"Peak slots count:     {len(peak_rows)}, Mean target_min: {mean_peak:.2f} min")
    print(f"Off-peak slots count: {len(offpeak_rows)}, Mean target_min: {mean_offpeak:.2f} min")

    if len(peak_rows) > 0 and len(offpeak_rows) > 0:
        if mean_peak > mean_offpeak:
            print("Validation Check: PASSED (Peak hours have higher average delay risk than off-peak hours).")
        else:
            print("WARNING: Peak hours are NOT on average higher than off-peak hours!")
            print("This suggests a data anomaly, traffic disruption, or insufficient sampling.")

    return True


def main():
    parser = argparse.ArgumentParser(description="Validate collected Google Routes API delay data.")
    parser.add_argument(
        "--csv",
        default="data/real_api.csv",
        help="Path to collected real_api.csv (default: data/real_api.csv)",
    )
    args = parser.parse_args()
    success = validate_real_data(args.csv)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
