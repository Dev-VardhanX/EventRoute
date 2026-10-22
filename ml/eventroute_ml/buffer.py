"""Inference entry point used by the backend. NEVER raises: any problem falls back to rules.

    from eventroute_ml import risk_extra_minutes, risk_extra_detail
    risk_extra_minutes("metro", depart_at, 23500, transfers=1, walking_share=0.2)   # -> 6.3
"""
from __future__ import annotations

import json
import logging
import os
import threading
from datetime import datetime
from pathlib import Path

import joblib
import numpy as np

from .config import DEFAULT_TZ, ML_ROOT, MODEL_VERSION
from .features import FEATURE_NAMES, TripFeatures, row
from .rules import clamp, local_hour_weekday, rule_risk_extra

log = logging.getLogger("eventroute_ml")


class BufferPredictor:
    def __init__(self, model_path: Path | None = None):
        self.model = None
        self.version = "rules-v1"
        self.load_error: str | None = None
        self.loaded_path: Path | None = None

        explicit = model_path or os.environ.get("EVENTROUTE_ML_MODEL")
        candidates: list[Path] = []
        if explicit:
            candidates.append(Path(explicit))
        else:
            # 1. Check registry.json
            reg_path = ML_ROOT / "models" / "registry.json"
            if reg_path.exists():
                try:
                    with open(reg_path, "r", encoding="utf-8") as f:
                        reg_data = json.load(f)
                    active_p = reg_data.get("active_model_path")
                    if active_p:
                        p_act = Path(active_p)
                        if not p_act.is_absolute():
                            p_act = ML_ROOT / p_act
                        candidates.append(p_act)
                except Exception as e:
                    log.warning("Failed to parse registry.json (%s)", e)

            # 2. Versioned candidates in priority order
            for v_name in ("buffer_v3.joblib", "buffer_v2.joblib", "buffer_v1.joblib"):
                p_cand = ML_ROOT / "models" / v_name
                if p_cand not in candidates:
                    candidates.append(p_cand)

        for p in candidates:
            if not p.exists():
                continue
            try:
                bundle = joblib.load(p)
                if list(bundle.get("feature_names", [])) != list(FEATURE_NAMES):
                    raise ValueError(f"feature list in {p.name} does not match this code version")
                self.model = bundle["model"]
                self.version = str(bundle.get("version", MODEL_VERSION))
                self.loaded_path = p
                self.load_error = None
                return
            except Exception as e:
                err_msg = f"{type(e).__name__}: {e}"
                log.warning("ML model failed to load from %s (%s); trying fallback", p, err_msg)
                if self.load_error is None:
                    self.load_error = err_msg

        if self.model is None and self.load_error is None:
            self.load_error = "No model file found"

    def predict(self, mode: str, hour: int, weekday: int, distance_m: float,
                transfers: int = 0, walking_share: float = 0.0) -> dict:
        try:
            f = TripFeatures(mode, hour, weekday, distance_m, transfers, walking_share)
            if self.model is None:
                raise RuntimeError("no model loaded")
            x = np.asarray([row(f)], dtype=float)
            minutes = clamp(float(self.model.predict(x)[0]))
            if np.isnan(minutes):
                raise ValueError("model returned NaN")
            return {"minutes": round(minutes, 2), "source": "model", "version": self.version}
        except Exception:
            try:
                h = 12 if hour is None else int(hour)
                w = 2 if weekday is None else int(weekday)
                t = 0 if transfers is None else int(transfers)
                minutes = rule_risk_extra(mode, h, w, t)
            except Exception:
                minutes = 8.0                      # absolute last resort, inside 0..20
            return {"minutes": round(clamp(minutes), 2), "source": "rules", "version": "rules-v1"}


_predictor: BufferPredictor | None = None
_lock = threading.Lock()


def get_predictor() -> BufferPredictor:
    global _predictor
    if _predictor is None:
        with _lock:
            if _predictor is None:
                _predictor = BufferPredictor()
    return _predictor


def reload_model(model_path: Path | None = None) -> BufferPredictor:
    global _predictor
    with _lock:
        _predictor = BufferPredictor(model_path)
    return _predictor


def risk_extra_detail(mode: str, depart_at: datetime | str, distance_m: float, transfers: int = 0,
                      walking_share: float = 0.0, tz: str = DEFAULT_TZ) -> dict:
    """Returns {minutes, source: 'model'|'rules', version, label} for the 'How we calculated this' screen."""
    try:
        if isinstance(depart_at, str):
            depart_at = datetime.fromisoformat(depart_at.replace("Z", "+00:00"))
        hour, weekday = local_hour_weekday(depart_at, tz or DEFAULT_TZ)
    except Exception:
        hour, weekday = 12, 2
    d = get_predictor().predict(mode, hour, weekday, distance_m, transfers, walking_share)
    d["label"] = "Delay risk (learned from travel data)" if d["source"] == "model" else "Delay risk (rule table)"
    return d


def risk_extra_minutes(mode: str, depart_at: datetime | str, distance_m: float, transfers: int = 0,
                       walking_share: float = 0.0, tz: str = DEFAULT_TZ) -> float:
    return float(risk_extra_detail(mode, depart_at, distance_m, transfers, walking_share, tz)["minutes"])
