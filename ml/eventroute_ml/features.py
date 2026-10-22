"""Feature engineering shared by training and inference. One source of truth."""
from __future__ import annotations

import math
from collections.abc import Iterable, Sequence
from dataclasses import dataclass

import numpy as np

from .config import MODES
from .rules import is_peak

FEATURE_NAMES: list[str] = (
    [f"mode_{m}" for m in MODES]
    + ["hour", "weekday", "is_peak", "distance_km", "transfers", "walking_share"]
)


@dataclass(frozen=True)
class TripFeatures:
    mode: str
    hour: int
    weekday: int
    distance_m: float
    transfers: int = 0
    walking_share: float = 0.0


def sanitize(f: TripFeatures) -> TripFeatures:
    """Make any input safe. Raises ValueError only for impossible values (NaN/inf)."""
    for name in ("distance_m", "walking_share", "hour", "weekday", "transfers"):
        v = getattr(f, name)
        if isinstance(v, float) and (math.isnan(v) or math.isinf(v)):
            raise ValueError(f"{name} is not a finite number")
    if f.distance_m is None:
        raise ValueError("distance_m is not a finite number")
    try:
        h = int(f.hour) % 24
    except (TypeError, ValueError):
        h = 12
    try:
        w = int(f.weekday) % 7
    except (TypeError, ValueError):
        w = 2
    try:
        d = max(0.0, float(f.distance_m))
    except (TypeError, ValueError):
        raise ValueError("distance_m is not a finite number")
    try:
        t = max(0, int(f.transfers or 0))
    except (TypeError, ValueError):
        t = 0
    try:
        ws = min(1.0, max(0.0, float(0.0 if f.walking_share is None else f.walking_share)))
    except (TypeError, ValueError):
        raise ValueError("walking_share is not a finite number")
    return TripFeatures(
        mode=(f.mode or "").strip().lower(),
        hour=h,
        weekday=w,
        distance_m=d,
        transfers=t,
        walking_share=ws,
    )


def row(f: TripFeatures) -> list[float]:
    f = sanitize(f)
    onehot = [1.0 if f.mode == m else 0.0 for m in MODES]
    return onehot + [
        float(f.hour), float(f.weekday), 1.0 if is_peak(f.hour, f.weekday) else 0.0,
        f.distance_m / 1000.0, float(f.transfers), f.walking_share,
    ]


def matrix(rows: Iterable[TripFeatures]) -> np.ndarray:
    return np.asarray([row(r) for r in rows], dtype=float)


def matrix_from_frame(df) -> np.ndarray:
    """Build X from a DataFrame with columns mode, hour, weekday, distance_m, transfers, walking_share."""
    feats = [
        TripFeatures(r.mode, int(r.hour), int(r.weekday), float(r.distance_m),
                     int(r.transfers), float(r.walking_share))
        for r in df.itertuples(index=False)
    ]
    return matrix(feats)


def dominant_mode(legs: Sequence[dict]) -> str:
    """Pick the route-level mode from common-route-format legs: longest NON-walk leg by
    duration; 'walk' only if the whole route is walking."""
    valid_legs = [l for l in (legs or []) if isinstance(l, dict)]
    non_walk = [l for l in valid_legs if str(l.get("mode", "")).strip().lower() != "walk"]
    pool = non_walk or valid_legs
    if not pool:
        return "walk"
    best = max(pool, key=lambda l: float(l.get("duration_s", 0) or 0))
    return str(best.get("mode", "walk")).strip().lower()


def route_to_features(route: dict, hour: int, weekday: int) -> TripFeatures:
    """Convert one route in the common route format into model features."""
    route = route or {}
    legs = [l for l in (route.get("legs", []) or []) if isinstance(l, dict)]
    total = float(route.get("duration_s") or sum(float(l.get("duration_s", 0) or 0) for l in legs) or 0)
    walk_s = float(route.get("walking_s") or sum(
        float(l.get("duration_s", 0) or 0) for l in legs if str(l.get("mode", "")).strip().lower() == "walk"))
    return TripFeatures(
        mode=dominant_mode(legs),
        hour=hour, weekday=weekday,
        distance_m=max(0.0, float(route.get("distance_m") or 0)),
        transfers=max(0, int(route.get("transfers") or 0)),
        walking_share=min(1.0, max(0.0, (walk_s / total))) if total > 0 else 0.0,
    )
