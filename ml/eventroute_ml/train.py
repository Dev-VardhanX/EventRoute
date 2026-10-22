"""Train the delay-risk buffer model (quantile gradient boosting, q = 0.9).

    python -m eventroute_ml.train --synthetic                 # synthetic only (labelled)
    python -m eventroute_ml.train --data data/trips.csv      # real/merged data
Outputs models/buffer_v1.joblib and models/buffer_v1.metadata.json.

Splits are GROUPED BY route_id so the holdout contains routes the model never saw
(random row splits leak and overstate quality).
"""
from __future__ import annotations

import argparse
import json
import platform
from datetime import UTC, datetime
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
import sklearn
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.model_selection import GroupKFold, GroupShuffleSplit

from . import metrics
from .config import ML_ROOT, MODEL_VERSION, QUANTILE, RISK_MAX_MIN, RISK_MIN_MIN
from .features import FEATURE_NAMES, matrix_from_frame
from .rules import rule_risk_extra
from .simulate import simulate

REQUIRED_COLUMNS = ["route_id", "mode", "hour", "weekday", "distance_m", "transfers", "walking_share", "target_min", "source"]


def make_model(seed: int) -> GradientBoostingRegressor:
    return GradientBoostingRegressor(
        loss="quantile", alpha=QUANTILE, n_estimators=250, learning_rate=0.06, max_depth=3,
        min_samples_leaf=25, subsample=0.8, random_state=seed,
    )


def load_data(path: str | None, synthetic: bool, seed: int) -> pd.DataFrame:
    frames = []
    if path:
        df = pd.read_csv(path)
        missing = [c for c in REQUIRED_COLUMNS if c not in df.columns]
        if missing:
            raise SystemExit(f"{path} is missing columns: {missing}")
        frames.append(df[REQUIRED_COLUMNS])
    if synthetic or not frames:
        frames.append(simulate(seed=seed)[REQUIRED_COLUMNS])
    out = pd.concat(frames, ignore_index=True)
    out = out.replace([np.inf, -np.inf], np.nan).dropna(subset=REQUIRED_COLUMNS)
    return out


def clipped(pred: np.ndarray) -> np.ndarray:
    return np.clip(pred, RISK_MIN_MIN, RISK_MAX_MIN)


def rule_pred(df: pd.DataFrame) -> np.ndarray:
    return np.array([rule_risk_extra(r.mode, int(r.hour), int(r.weekday), int(r.transfers)) for r in df.itertuples(index=False)])


def evaluate_split(df: pd.DataFrame, seed: int) -> dict:
    X = matrix_from_frame(df)
    y = df["target_min"].to_numpy(float)
    groups = df["route_id"].to_numpy()

    gss = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=seed)
    tr, te = next(gss.split(X, y, groups))
    model = make_model(seed).fit(X[tr], y[tr])
    pred_raw = model.predict(X[te])
    pred = clipped(pred_raw)
    base = rule_pred(df.iloc[te])

    result = {
        "holdout": {
            "model_unclipped": metrics.summarize(y[te], pred_raw, QUANTILE),
            "model_clipped_0_20": metrics.summarize(y[te], pred, QUANTILE),
            "rule_table_baseline": metrics.summarize(y[te], base, QUANTILE),
            "rule_table_scaled_to_same_coverage": metrics.matched_coverage_scale(y[te], base, QUANTILE),
        },
        "by_mode_model_clipped": {},
        "by_source_model_clipped": {},
    }
    te_df = df.iloc[te].reset_index(drop=True)
    for mode, idx in te_df.groupby("mode").groups.items():
        idx = np.asarray(list(idx))
        result["by_mode_model_clipped"][mode] = metrics.summarize(y[te][idx], pred[idx], QUANTILE)
    for src, idx in te_df.groupby("source").groups.items():
        idx = np.asarray(list(idx))
        result["by_source_model_clipped"][src] = metrics.summarize(y[te][idx], pred[idx], QUANTILE)

    # Calibration across trip lengths: the model should stay near 0.90 everywhere, while a flat
    # rule table (scaled to the same overall coverage) over-buffers short trips and misses long ones.
    scale = result["holdout"]["rule_table_scaled_to_same_coverage"]["scale"]
    scaled_rule = np.clip(base * scale, RISK_MIN_MIN, RISK_MAX_MIN)
    result["holdout"]["pinball_q"] = {
        "model_clipped": metrics.pinball(y[te], pred, QUANTILE),
        "rule_scaled": metrics.pinball(y[te], scaled_rule, QUANTILE),
    }
    bucket = pd.cut(te_df["distance_m"] / 1000.0, [0, 3, 8, 15, 1e9], labels=["0-3km", "3-8km", "8-15km", "15km+"], include_lowest=True)
    calib = {}
    for label in bucket.cat.categories:
        idx = np.where(bucket.to_numpy() == label)[0]
        if len(idx) == 0:
            continue
        calib[str(label)] = {
            "n": len(idx),
            "model_coverage": round(metrics.coverage(y[te][idx], pred[idx]), 4),
            "rule_scaled_coverage": round(metrics.coverage(y[te][idx], scaled_rule[idx]), 4),
            "model_mean_buffer": round(float(np.mean(pred[idx])), 3),
            "rule_scaled_mean_buffer": round(float(np.mean(scaled_rule[idx])), 3),
        }
    result["calibration_by_distance"] = calib

    cv_cov = []
    n_groups = int(df["route_id"].nunique())
    n_splits = min(5, n_groups)
    if n_splits >= 2:
        for a, b in GroupKFold(n_splits=n_splits).split(X, y, groups):
            m = make_model(seed).fit(X[a], y[a])
            cv_cov.append(metrics.coverage(y[b], clipped(m.predict(X[b]))))
        result["group_5fold_coverage_clipped"] = {"mean": round(float(np.mean(cv_cov)), 4), "std": round(float(np.std(cv_cov)), 4)}
    else:
        result["group_5fold_coverage_clipped"] = {"mean": 0.0, "std": 0.0}
    return result


def run(data: str | None, synthetic: bool, out_dir: Path, seed: int) -> dict:
    df = load_data(data, synthetic, seed)
    report = evaluate_split(df, seed)
    final = make_model(seed).fit(matrix_from_frame(df), df["target_min"].to_numpy(float))   # refit on all data to ship

    out_dir.mkdir(parents=True, exist_ok=True)
    model_path = out_dir / "buffer_v1.joblib"
    joblib.dump({"model": final, "feature_names": FEATURE_NAMES, "version": MODEL_VERSION}, model_path)
    meta = {
        "version": MODEL_VERSION, "quantile": QUANTILE, "clamp_minutes": [RISK_MIN_MIN, RISK_MAX_MIN],
        "trained_at_utc": datetime.now(UTC).isoformat(timespec="seconds"),
        "python": platform.python_version(), "sklearn": sklearn.__version__,
        "rows": len(df), "routes": int(df["route_id"].nunique()),
        "rows_by_source": {k: int(v) for k, v in df["source"].value_counts().items()},
        "contains_synthetic_data": bool((df["source"] == "synthetic").any()),
        "metrics_note": "Metrics come from a route-grouped 80/20 holdout; the shipped model is refit on all rows.",
        "report": report,
    }
    (out_dir / "buffer_v1.metadata.json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    return meta


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default=None, help="CSV with columns: " + ", ".join(REQUIRED_COLUMNS))
    ap.add_argument("--synthetic", action="store_true", help="add labelled synthetic rows")
    ap.add_argument("--out", default=str(ML_ROOT / "models"))
    ap.add_argument("--seed", type=int, default=7)
    a = ap.parse_args()
    meta = run(a.data, a.synthetic, Path(a.out), a.seed)
    h = meta["report"]["holdout"]
    print(json.dumps({"rows": meta["rows"], "synthetic": meta["contains_synthetic_data"],
                      "model": h["model_clipped_0_20"], "rule_baseline": h["rule_table_baseline"],
                      "rule_scaled_to_same_coverage": h["rule_table_scaled_to_same_coverage"],
                      "cv": meta["report"]["group_5fold_coverage_clipped"]}, indent=2))


if __name__ == "__main__":
    main()
