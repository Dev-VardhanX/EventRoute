"""Event recommendation score (a formula, not a trained model), with a human-readable reason.

score = 0.40*interest_match + 0.25*proximity + 0.20*popularity + 0.15*urgency   (all 0..1)
"""
from __future__ import annotations

import math
import re
from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import UTC, datetime

from .config import REC_WEIGHTS

# Words that mean the same thing after canonicalisation (see canon()).
SYNONYM_GROUPS: list[set[str]] = [
    {"hackathon", "hack", "coding", "programming", "codefest"},
    {"robotic", "robot", "robocup", "automation"},
    {"ai", "ml", "aiml", "machinelearning", "artificialintelligence", "deeplearning"},
    {"openmic", "mic", "standup", "poetry", "comedy", "openmicnight"},
    {"fest", "festival", "cultural", "culturalfest"},
    {"workshop", "bootcamp", "training", "masterclass"},
    {"sport", "athletic", "athletics", "tournament"},
]
EFFECTIVE_KMH = 22.0             # city travel speed used only when travel_minutes is unknown
NEUTRAL = 0.5                    # used when a signal is unknown (cold start, no coords...)


def canon(s: str) -> str:
    """'#Hackathons' -> 'hackathon', 'Open Mic' -> 'openmic', 'Robotics' -> 'robotic'."""
    t = re.sub(r"[^a-z0-9]", "", (s or "").lower())
    return t[:-1] if len(t) > 3 and t.endswith("s") else t


def _same(a: str, b: str) -> bool:
    if not a or not b:
        return False
    if a == b:
        return True
    return any(a in g and b in g for g in SYNONYM_GROUPS)


@dataclass
class UserProfile:
    interests: Sequence[str] = ()
    lat: float | None = None
    lng: float | None = None


@dataclass
class EventInfo:
    id: str
    title: str = ""
    category: str = ""
    tags: Sequence[str] = ()
    lat: float | None = None
    lng: float | None = None
    start_at: datetime | None = None
    registration_deadline: datetime | None = None
    registrations_count: int = 0
    capacity: int | None = None
    travel_minutes: float | None = None     # from the routing service when known
    cancelled: bool = False
    registered: bool = False                   # current user already registered
    full: bool = False


@dataclass
class Recommendation:
    event_id: str
    score: float
    reason: str
    components: dict = field(default_factory=dict)


def haversine_km(lat1, lng1, lat2, lng2) -> float:
    r = 6371.0088
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


def _aware(dt: datetime | str | None) -> datetime | None:
    if dt is None:
        return None
    if isinstance(dt, str):
        try:
            dt = datetime.fromisoformat(dt.replace("Z", "+00:00"))
        except Exception:
            return None
    if not isinstance(dt, datetime):
        return None
    return dt if dt.tzinfo else dt.replace(tzinfo=UTC)


def interest_match(user: UserProfile, ev: EventInfo) -> tuple[float, str | None, str]:
    """Returns (value 0..1, matched user interest text or None, kind: 'category'|'tag'|'none'|'unknown')."""
    interests = [i for i in (user.interests or []) if canon(i)]
    if not interests:
        return NEUTRAL, None, "unknown"
    cat = canon(ev.category)
    tag_terms = {canon(t) for t in (ev.tags or [])}
    tag_terms |= {canon(w) for w in re.split(r"\W+", ev.title or "") if w}
    cat_hit = next((i for i in interests if _same(canon(i), cat)), None)
    tag_hit = next((i for i in interests if i != cat_hit and any(_same(canon(i), t) for t in tag_terms)), None)
    value = min(1.0, (0.7 if cat_hit else 0.0) + (0.3 if tag_hit else 0.0))
    if cat_hit:
        return value, cat_hit, "category"
    if tag_hit:
        return value, tag_hit, "tag"
    return 0.0, None, "none"


def proximity(user: UserProfile, ev: EventInfo) -> tuple[float, float | None]:
    minutes = ev.travel_minutes
    if minutes is not None:
        try:
            m_val = float(minutes)
            if math.isnan(m_val) or math.isinf(m_val):
                minutes = None
            else:
                minutes = max(0.0, m_val)
        except (TypeError, ValueError):
            minutes = None

    if minutes is None and None not in (user.lat, user.lng, ev.lat, ev.lng):
        try:
            coords = (float(user.lat), float(user.lng), float(ev.lat), float(ev.lng))
            if all(math.isfinite(c) for c in coords):
                minutes = haversine_km(*coords) / EFFECTIVE_KMH * 60.0
        except (TypeError, ValueError):
            minutes = None

    if minutes is None:
        return NEUTRAL, None
    score = max(0.0, min(1.0, 1.0 - minutes / 90.0))
    return score, float(minutes)


def popularity(ev: EventInfo) -> tuple[float, float]:
    try:
        reg_raw = ev.registrations_count
        reg = 0 if reg_raw is None or (isinstance(reg_raw, float) and math.isnan(reg_raw)) else max(0, int(reg_raw))
    except (TypeError, ValueError):
        reg = 0
    try:
        cap_raw = ev.capacity
        cap = None if cap_raw is None or (isinstance(cap_raw, float) and math.isnan(cap_raw)) else int(cap_raw)
    except (TypeError, ValueError):
        cap = None
    if cap and cap > 0:
        fill = max(0.0, min(1.0, reg / cap))
        return fill, fill
    return min(1.0, math.log1p(reg) / math.log1p(200)), 0.0


def urgency(ev: EventInfo, now: datetime) -> tuple[float, float | None]:
    dl = _aware(ev.registration_deadline)
    if dl is None:
        return 0.0, None
    hours = (dl - _aware(now)).total_seconds() / 3600.0
    if hours <= 0:
        return 0.0, hours
    if hours <= 24:
        return 1.0, hours
    if hours >= 168:
        return 0.0, hours
    return (168.0 - hours) / (168.0 - 24.0), hours


def _reason(comp: dict, w: dict, ev: EventInfo, matched: str | None, kind: str,
            minutes: float | None, fill: float, hours: float | None) -> str:
    cands = []   # (weighted contribution, phrase)
    w_int = w.get("interest", 0.40)
    w_prox = w.get("proximity", 0.25)
    w_pop = w.get("popularity", 0.20)
    w_urg = w.get("urgency", 0.15)

    clean_matched = re.sub(r"^[#\s]+|[#\s!.,;:]+$", "", str(matched or ""))
    tag_display = f"#{clean_matched}" if clean_matched else "interest"

    if kind == "category" and matched:
        cands.append((w_int * comp["interest"], f"Matches your {tag_display} interest"))
    elif kind == "tag" and matched:
        cands.append((w_int * comp["interest"], f"Related to your {tag_display} interest"))

    if minutes is not None and comp["proximity"] >= 0.5:
        cands.append((w_prox * comp["proximity"], f"{int(round(minutes))} min away"))

    if fill >= 0.8:
        cands.append((w_pop * comp["popularity"], "Filling fast"))
    elif ev.registrations_count and comp["popularity"] >= 0.3:
        cands.append((w_pop * comp["popularity"], f"{ev.registrations_count} students going"))

    if hours is not None and hours > 0 and comp["urgency"] > 0:
        if hours <= 24:
            cands.append((w_urg * comp["urgency"], "Registration closes within a day"))
        else:
            cands.append((w_urg * comp["urgency"], f"Closes in {int(math.ceil(hours / 24))} days"))

    cands.sort(key=lambda c: -c[0])
    phrases = [p for _, p in cands[:2]]
    res = " · ".join(phrases) if phrases else "Upcoming event"

    # Enforce maximum 70 characters
    if len(res) > 70:
        if len(phrases) > 1 and len(phrases[0]) <= 70:
            res = phrases[0]
        elif len(res) > 70:
            res = res[:67] + "..."

    return res if res and res.strip() else "Upcoming event"


def score_event(user: UserProfile, ev: EventInfo, now: datetime, weights: dict = REC_WEIGHTS) -> Recommendation:
    iv, matched, kind = interest_match(user, ev)
    pv, minutes = proximity(user, ev)
    popv, fill = popularity(ev)
    uv, hours = urgency(ev, now)
    comp = {"interest": iv, "proximity": pv, "popularity": popv, "urgency": uv}
    total = sum(weights.get(k, 0.0) * comp[k] for k in weights if k in comp)
    return Recommendation(str(ev.id), round(total, 4), _reason(comp, weights, ev, matched, kind, minutes, fill, hours),
                          {k: round(v, 4) for k, v in comp.items()})


def recommend(user: UserProfile, events: Sequence[EventInfo], now: datetime | None = None,
              limit: int | None = None, exclude_registered: bool = True, exclude_full: bool = True,
              weights: dict = REC_WEIGHTS) -> list[Recommendation]:
    """Ranked recommendations. Never raises on a single bad event; it is skipped."""
    now = _aware(now) or datetime.now(UTC)
    scored = []
    seen_ids = set()

    for ev in (events or []):
        try:
            if ev is None:
                continue
            eid = str(getattr(ev, "id", "") or "")
            if not eid or eid in seen_ids:
                continue
            seen_ids.add(eid)

            if ev.cancelled or (exclude_registered and ev.registered) or (exclude_full and ev.full):
                continue
            dl, st = _aware(ev.registration_deadline), _aware(ev.start_at)
            if (dl and dl < now) or (st and st < now):
                continue
            scored.append((score_event(user, ev, now, weights), st))
        except Exception:
            continue

    far = datetime.max.replace(tzinfo=UTC)
    scored.sort(key=lambda t: (-t[0].score, t[1] or far, str(t[0].event_id)))     # stable, deterministic
    recs = [r for r, _ in scored]
    return recs[:limit] if limit else recs
