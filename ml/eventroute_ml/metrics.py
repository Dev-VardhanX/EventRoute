"""Metrics for a 0.9-quantile buffer. Report these honestly (coverage target ~0.90)."""
from __future__ import annotations

import numpy as np


def coverage(y, pred) -> float:
    """Share of trips where delay <= buffer. A perfect 0.9-quantile gives about 0.90."""
    y, pred = np.asarray(y, float), np.asarray(pred, float)
    if len(y) == 0:
        return 0.0
    return float(np.mean(y <= pred))


def pinball(y, pred, q: float = 0.9) -> float:
    y, pred = np.asarray(y, float), np.asarray(pred, float)
    if len(y) == 0:
        return 0.0
    d = y - pred
    return float(np.mean(np.maximum(q * d, (q - 1) * d)))


def mae(y, pred) -> float:
    """Mean absolute gap between buffer and observed (non-negative) delay."""
    y, pred = np.asarray(y, float), np.asarray(pred, float)
    if len(y) == 0:
        return 0.0
    return float(np.mean(np.abs(np.maximum(y, 0.0) - pred)))


def summarize(y, pred, q: float = 0.9) -> dict:
    y, pred = np.asarray(y, float), np.asarray(pred, float)
    if len(y) == 0:
        return {
            "n": 0,
            "coverage": 0.0,
            "late_rate": 1.0,
            "pinball_q": 0.0,
            "mae_to_observed_min": 0.0,
            "mean_buffer_min": 0.0,
        }
    return {
        "n": len(y),
        "coverage": round(coverage(y, pred), 4),
        "late_rate": round(1.0 - coverage(y, pred), 4),
        "pinball_q": round(pinball(y, pred, q), 4),
        "mae_to_observed_min": round(mae(y, pred), 4),
        "mean_buffer_min": round(float(np.mean(pred)), 4),
    }


def matched_coverage_scale(y, base_pred, target: float = 0.9, lo: float = 0.0, hi: float = 20.0,
                           scales=None) -> dict:
    """Fair baseline comparison: scale a baseline buffer until it reaches the SAME coverage,
    then compare how many minutes it needs. (Comparing raw MAE is misleading when the
    baseline simply under-buffers.)"""
    y, base = np.asarray(y, float), np.asarray(base_pred, float)
    scales = scales if scales is not None else np.arange(0.5, 6.01, 0.05)
    for s in scales:
        pred = np.clip(base * s, lo, hi)
        if coverage(y, pred) >= target:
            return {"scale": round(float(s), 2), "coverage": round(coverage(y, pred), 4),
                    "mean_buffer_min": round(float(np.mean(pred)), 4), "reached_target": True}
    pred = np.clip(base * scales[-1], lo, hi)
    return {"scale": round(float(scales[-1]), 2), "coverage": round(coverage(y, pred), 4),
            "mean_buffer_min": round(float(np.mean(pred)), 4), "reached_target": False}
