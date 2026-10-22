"""Rule-table delay buffer. This ships FIRST and is also the permanent fallback.

All numbers are ASSUMPTIONS (not measured). Tune them with the student survey and real
travel_logs. Minutes of extra buffer by (peak, off-peak).
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from .config import DEFAULT_TZ, RISK_MAX_MIN, RISK_MIN_MIN

# (peak, off_peak) extra minutes
RULE_TABLE: dict[str, tuple[float, float]] = {
    "drive": (15.0, 5.0),
    "bus": (10.0, 5.0),
    "tram": (6.0, 3.0),
    "train": (6.0, 3.0),
    "metro": (4.0, 2.0),
    "bike": (3.0, 2.0),
    "walk": (2.0, 2.0),
}
DEFAULT_RULE = (8.0, 4.0)        # unknown mode: conservative middle value
TRANSFER_EXTRA_MIN = 2.0         # per transfer


def is_peak(hour: int, weekday: int) -> bool:
    """Weekday (Mon=0) 08:00-10:59 and 17:00-20:59 local time."""
    return weekday < 5 and (8 <= hour < 11 or 17 <= hour < 21)


_IST = timezone(timedelta(hours=5, minutes=30))     # India has no DST, so a fixed offset is exact


def _zone(tz: str | None = None):
    tz = tz or DEFAULT_TZ
    try:
        return ZoneInfo(tz)
    except ZoneInfoNotFoundError:
        # Windows without the 'tzdata' package has no tz database. India is a fixed +05:30 offset.
        if tz == "Asia/Kolkata":
            return _IST
        raise


def local_hour_weekday(dt: datetime, tz: str = DEFAULT_TZ) -> tuple[int, int]:
    """Hour and weekday in the event's time zone. Naive datetimes are treated as local."""
    if dt.tzinfo is not None:
        dt = dt.astimezone(_zone(tz))
    return dt.hour, dt.weekday()


def clamp(x: float) -> float:
    return max(RISK_MIN_MIN, min(RISK_MAX_MIN, float(x)))


def rule_risk_extra(mode: str, hour: int, weekday: int, transfers: int = 0) -> float:
    try:
        h = int(hour) % 24
    except (TypeError, ValueError):
        h = 12
    try:
        w = int(weekday) % 7
    except (TypeError, ValueError):
        w = 2
    try:
        t = max(0, int(transfers or 0))
    except (TypeError, ValueError):
        t = 0
    peak_v, off_v = RULE_TABLE.get((mode or "").strip().lower(), DEFAULT_RULE)
    base = peak_v if is_peak(h, w) else off_v
    return clamp(base + TRANSFER_EXTRA_MIN * t)
