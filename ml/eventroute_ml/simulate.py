"""SYNTHETIC trip generator. Every row it produces is labelled source="synthetic".

This is NOT real data. The delay model below is a documented set of assumptions chosen so
the pipeline can be built and tested before any real trips exist. Say so in the repo and the
pitch. Replace/extend with real_api rows (routes API) and travel_log rows (live trips).

target_min = (actual_duration - planned_duration) in minutes. It can be slightly negative.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from .config import MODES
from .rules import is_peak

# Assumed mean extra time as a fraction of planned time, off-peak and added during peak
BASE_FRAC = {"drive": 0.10, "bus": 0.12, "metro": 0.04, "train": 0.05, "tram": 0.08, "bike": 0.04, "walk": 0.02}
PEAK_FRAC = {"drive": 0.25, "bus": 0.15, "metro": 0.05, "train": 0.05, "tram": 0.08, "bike": 0.05, "walk": 0.0}
# Spread of the multiplicative lognormal noise (heavier tail = riskier mode)
SIGMA = {"drive": 0.55, "bus": 0.50, "metro": 0.30, "train": 0.35, "tram": 0.40, "bike": 0.30, "walk": 0.20}
# Typical speeds (km/h) used to derive planned time from distance
SPEED_KMH = {"drive": 28.0, "bus": 18.0, "metro": 32.0, "train": 40.0, "tram": 16.0, "bike": 14.0, "walk": 4.8}
# (min_km, max_km) per mode
DIST_KM = {"drive": (2, 30), "bus": (3, 25), "metro": (4, 35), "train": (10, 60), "tram": (1, 10), "bike": (1, 8), "walk": (0.3, 2.0)}
MODE_WEIGHTS = {"drive": 0.22, "bus": 0.22, "metro": 0.28, "train": 0.06, "tram": 0.04, "bike": 0.06, "walk": 0.12}
TRANSFER_MISS_P = 0.15      # chance one transfer costs extra wait (higher at peak)


def simulate(n_routes: int = 2500, trips_per_route: int = 12, seed: int = 7) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    modes = list(MODES)
    p = np.array([MODE_WEIGHTS[m] for m in modes], dtype=float)
    p /= p.sum()

    rows = []
    for rid in range(n_routes):
        mode = str(rng.choice(modes, p=p))
        lo, hi = DIST_KM[mode]
        dist_km = float(rng.uniform(lo, hi))
        transfers = int(rng.choice([0, 1, 2], p=[0.5, 0.35, 0.15])) if mode in ("bus", "metro", "train", "tram") else 0
        if mode == "walk":
            walk_share = 1.0
        elif mode in ("bus", "metro", "train", "tram"):
            walk_share = float(rng.beta(2, 8))
        else:
            walk_share = 0.0
        planned_s = dist_km / SPEED_KMH[mode] * 3600.0 + transfers * 300.0
        planned_min = planned_s / 60.0

        for _ in range(trips_per_route):
            hour = int(rng.integers(5, 23))
            weekday = int(rng.integers(0, 7))
            peak = is_peak(hour, weekday)
            mean_frac = BASE_FRAC[mode] + (PEAK_FRAC[mode] if peak else 0.0)
            sigma = SIGMA[mode] * (1.3 if peak else 1.0)
            noise = float(rng.lognormal(mean=-sigma ** 2 / 2.0, sigma=sigma))   # mean 1, right-skewed
            delay = planned_min * mean_frac * noise
            miss_p = TRANSFER_MISS_P + (0.10 if peak else 0.0)
            for _t in range(transfers):
                if rng.random() < miss_p:
                    delay += float(rng.uniform(3, 9))
            delay += float(rng.normal(0.0, 0.4))
            rows.append({
                "route_id": f"syn-{rid}", "mode": mode, "hour": hour, "weekday": weekday,
                "distance_m": dist_km * 1000.0, "transfers": transfers, "walking_share": walk_share,
                "planned_s": planned_s, "actual_s": planned_s + delay * 60.0,
                "target_min": delay, "source": "synthetic",
            })
    return pd.DataFrame(rows)
