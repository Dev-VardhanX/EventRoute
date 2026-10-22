from collections import defaultdict
from datetime import datetime, timezone

from app.core.config import get_settings

settings = get_settings()

# In-memory daily counter keyed by YYYY-MM-DD
_daily_paid_calls: dict[str, int] = defaultdict(int)


def _get_today_key() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%d")


def record_paid_call() -> int:
    """Increment and return today's paid API call count."""
    key = _get_today_key()
    _daily_paid_calls[key] += 1
    return _daily_paid_calls[key]


def get_today_paid_calls() -> int:
    """Return the total paid API calls recorded today."""
    key = _get_today_key()
    return _daily_paid_calls[key]


def is_paid_limit_exceeded() -> bool:
    """Check if today's paid calls have met or exceeded daily configured quota."""
    return get_today_paid_calls() >= settings.daily_paid_route_limit


def reset_paid_calls() -> None:
    """Reset daily counter (intended for testing and development)."""
    _daily_paid_calls.clear()
