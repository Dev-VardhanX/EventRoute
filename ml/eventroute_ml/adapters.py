"""Adapters between backend / mobile JSON formats and EventRoute ML data structures.

This module ensures clean separation: the ML core operates on pure Python dataclasses,
while this layer handles incoming dictionary/JSON payloads, missing fields, format differences,
and produces enriched responses without modifying originals.
"""
from __future__ import annotations

import copy
from collections.abc import Sequence
from datetime import UTC, datetime
from typing import Any

from .buffer import get_predictor
from .config import DEFAULT_TZ
from .features import TripFeatures, route_to_features
from .recommend import EventInfo, UserProfile
from .rules import local_hour_weekday


def _parse_datetime(val: Any) -> datetime | None:
    """Safely parse a datetime object or ISO string. Returns None on failure."""
    if val is None:
        return None
    if isinstance(val, datetime):
        return val
    if isinstance(val, str) and val.strip():
        try:
            return datetime.fromisoformat(val.strip().replace("Z", "+00:00"))
        except Exception:
            return None
    return None


def _clean_route(route: Any) -> dict:
    """Safely sanitize route dictionary fields to prevent type conversion errors."""
    if not isinstance(route, dict):
        return {"id": "", "duration_s": 0.0, "distance_m": 0.0, "transfers": 0, "walking_s": 0.0, "legs": []}

    clean = {"id": str(route.get("id") or "")}

    for key, typ, default in (
        ("duration_s", float, 0.0),
        ("distance_m", float, 0.0),
        ("walking_s", float, 0.0),
        ("transfers", int, 0),
    ):
        v = route.get(key)
        if v is not None:
            try:
                clean[key] = typ(v)
            except (ValueError, TypeError):
                clean[key] = default
        else:
            clean[key] = default

    clean_legs = []
    raw_legs = route.get("legs")
    if isinstance(raw_legs, (list, tuple)):
        for leg in raw_legs:
            if isinstance(leg, dict):
                cl = dict(leg)
                try:
                    cl["duration_s"] = float(leg.get("duration_s", 0) or 0)
                except (ValueError, TypeError):
                    cl["duration_s"] = 0.0
                clean_legs.append(cl)
    clean["legs"] = clean_legs
    return clean


def route_to_risk(route: dict, depart_at: Any = None, tz: str = DEFAULT_TZ) -> dict:
    """Take ONE route in common route format and calculate the delay risk buffer.

    Common route format keys expected (all optional with safe fallbacks):
      id, summary, duration_s, distance_m, transfers, walking_s, legs[], departure_time

    Returns:
      {
        "route_id": str,
        "risk_extra_min": float,
        "source": "model" | "rules",
        "version": str,
        "label": str,
        "features": dict
      }
    """
    clean_r = _clean_route(route)
    route_id = clean_r["id"]

    # Determine departure datetime
    dt = _parse_datetime(depart_at)
    if dt is None and isinstance(route, dict):
        dt = _parse_datetime(route.get("departure_time"))
    if dt is None:
        dt = datetime.now(UTC)

    try:
        hour, weekday = local_hour_weekday(dt, tz or DEFAULT_TZ)
    except Exception:
        hour, weekday = 12, 2

    try:
        feats: TripFeatures = route_to_features(clean_r, hour, weekday)
    except Exception:
        feats = TripFeatures(
            mode="walk",
            hour=hour,
            weekday=weekday,
            distance_m=0.0,
            transfers=0,
            walking_share=1.0,
        )

    pred = get_predictor().predict(
        feats.mode,
        feats.hour,
        feats.weekday,
        feats.distance_m,
        feats.transfers,
        feats.walking_share,
    )

    source = pred.get("source", "rules")
    label = (
        "Delay risk (learned from travel data)"
        if source == "model"
        else "Delay risk (rule table)"
    )

    return {
        "route_id": route_id,
        "risk_extra_min": round(float(pred.get("minutes", 0.0)), 2),
        "source": source,
        "version": pred.get("version", "rules-v1"),
        "label": label,
        "features": {
            "mode": feats.mode,
            "hour": feats.hour,
            "weekday": feats.weekday,
            "distance_m": feats.distance_m,
            "transfers": feats.transfers,
            "walking_share": round(feats.walking_share, 4),
        },
    }


def annotate_routes(
    routes: Sequence[dict], depart_at: Any = None, tz: str = DEFAULT_TZ
) -> list[dict]:
    """Return deep copies of routes with `risk_extra_min` and `risk_extra` added."""
    if not routes:
        return []

    annotated = []
    for r in routes:
        rc = copy.deepcopy(r) if isinstance(r, dict) else {}
        risk = route_to_risk(rc, depart_at=depart_at, tz=tz)
        rc["risk_extra_min"] = risk["risk_extra_min"]
        rc["risk_extra"] = risk
        annotated.append(rc)
    return annotated


def event_from_json(d: dict) -> EventInfo:
    """Map real backend / mobile mock event JSON shape to `EventInfo`.

    Handles missing optional fields without raising.
    """
    d = d if isinstance(d, dict) else {}

    # Extract coordinates from venue_lat/lng, lat/lng, or nested college object
    lat: float | None = None
    for k in ("venue_lat", "lat"):
        if d.get(k) is not None:
            try:
                lat = float(d[k])
                break
            except (ValueError, TypeError):
                pass
    if lat is None and isinstance(d.get("college"), dict):
        try:
            if d["college"].get("lat") is not None:
                lat = float(d["college"]["lat"])
        except (ValueError, TypeError):
            pass

    lng: float | None = None
    for k in ("venue_lng", "lng"):
        if d.get(k) is not None:
            try:
                lng = float(d[k])
                break
            except (ValueError, TypeError):
                pass
    if lng is None and isinstance(d.get("college"), dict):
        try:
            if d["college"].get("lng") is not None:
                lng = float(d["college"]["lng"])
        except (ValueError, TypeError):
            pass

    # Registrations count: direct field or computed from seat_limit - seats_left
    reg_count = 0
    if d.get("registrations_count") is not None:
        try:
            reg_count = max(0, int(d["registrations_count"]))
        except (ValueError, TypeError):
            reg_count = 0
    elif d.get("seat_limit") is not None and d.get("seats_left") is not None:
        try:
            reg_count = max(0, int(d["seat_limit"]) - int(d["seats_left"]))
        except (ValueError, TypeError):
            reg_count = 0

    capacity: int | None = None
    for k in ("seat_limit", "capacity"):
        if d.get(k) is not None:
            try:
                capacity = int(d[k])
                break
            except (ValueError, TypeError):
                pass

    travel_min: float | None = None
    for k in ("travel_estimate_minutes", "travel_minutes"):
        if d.get(k) is not None:
            try:
                travel_min = float(d[k])
                break
            except (ValueError, TypeError):
                pass

    status_str = str(d.get("status", "")).strip().lower()
    cancelled = status_str == "cancelled" or bool(d.get("cancelled", False))
    registered = bool(d.get("registered", False))

    full = False
    if d.get("seats_left") is not None:
        try:
            full = int(d["seats_left"]) <= 0
        except (ValueError, TypeError):
            pass
    if not full and d.get("full") is not None:
        full = bool(d.get("full"))

    raw_tags = d.get("tags") or []
    tags = [str(t) for t in raw_tags] if isinstance(raw_tags, (list, tuple)) else []

    return EventInfo(
        id=str(d.get("id") or ""),
        title=str(d.get("title") or ""),
        category=str(d.get("category") or ""),
        tags=tags,
        lat=lat,
        lng=lng,
        start_at=_parse_datetime(d.get("start_at")),
        registration_deadline=_parse_datetime(d.get("registration_deadline")),
        registrations_count=reg_count,
        capacity=capacity,
        travel_minutes=travel_min,
        cancelled=cancelled,
        registered=registered,
        full=full,
    )


def user_from_json(d: dict, colleges: Sequence[dict] | None = None) -> UserProfile:
    """Map real backend / mobile mock user JSON shape (`me.json`) to `UserProfile`.

    Handles missing optional fields without raising.
    """
    d = d if isinstance(d, dict) else {}

    raw_interests = d.get("interests") or []
    interests = (
        [str(i) for i in raw_interests]
        if isinstance(raw_interests, (list, tuple))
        else []
    )

    lat: float | None = None
    lng: float | None = None

    for k in ("lat", "home_lat"):
        if d.get(k) is not None:
            try:
                lat = float(d[k])
                break
            except (ValueError, TypeError):
                pass

    for k in ("lng", "home_lng"):
        if d.get(k) is not None:
            try:
                lng = float(d[k])
                break
            except (ValueError, TypeError):
                pass

    # If default_start is college and colleges lookup is provided
    if (
        (d.get("default_start") == "college" or lat is None or lng is None)
        and colleges
        and d.get("college_id")
    ):
        for c in colleges:
            if isinstance(c, dict) and c.get("id") == d.get("college_id"):
                if c.get("lat") is not None and c.get("lng") is not None:
                    try:
                        if d.get("default_start") == "college" or lat is None:
                            lat = float(c["lat"])
                            lng = float(c["lng"])
                    except (ValueError, TypeError):
                        pass
                break

    return UserProfile(interests=interests, lat=lat, lng=lng)
