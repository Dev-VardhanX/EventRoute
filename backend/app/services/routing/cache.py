import logging
from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.route_cache import RouteCache
from app.services.routing.models import Route

logger = logging.getLogger("eventroute.routing.cache")

TTL_MAP = {
    "transit": 10 * 60,       # 10 minutes
    "walk": 24 * 60 * 60,     # 24 hours
    "bike": 24 * 60 * 60,     # 24 hours
    "drive": 5 * 60,          # 5 minutes
    "discovery": 6 * 60 * 60, # 6 hours
}


def round_time_to_5min(dt: datetime | None) -> str:
    """Round timestamp down to nearest 5-minute bucket for cache aggregation."""
    if not dt:
        return "none"
    dt_utc = dt.astimezone(timezone.utc)
    rounded_minute = (dt_utc.minute // 5) * 5
    rounded = dt_utc.replace(minute=rounded_minute, second=0, microsecond=0)
    return rounded.strftime("%Y%m%d%H%M")


def make_cache_key(
    provider: str,
    mode: str,
    origin: tuple[float, float],
    destination: tuple[float, float],
    arrive_by: datetime | None = None,
) -> str:
    """Generate canonical cache key with 4 decimal coordinate precision and 5 min time rounding."""
    time_part = round_time_to_5min(arrive_by)
    return (
        f"{provider.lower()}:{mode.lower()}:"
        f"{origin[0]:.4f},{origin[1]:.4f}:"
        f"{destination[0]:.4f},{destination[1]:.4f}:{time_part}"
    )


def get_ttl_seconds(mode: str) -> int:
    return TTL_MAP.get(mode.lower(), 10 * 60)


def get_cached_route(db: Session, cache_key: str) -> list[Route] | None:
    """Retrieve non-expired route entry from route_cache table."""
    now = datetime.now(timezone.utc)
    entry = (
        db.execute(
            select(RouteCache).where(
                RouteCache.cache_key == cache_key, RouteCache.expires_at > now
            )
        )
        .scalars()
        .first()
    )
    if not entry:
        return None

    try:
        raw_routes = entry.response_json
        if isinstance(raw_routes, list):
            return [Route.model_validate(r) for r in raw_routes]
        return None
    except Exception as exc:
        logger.warning("Failed to deserialize cached route for key %s: %s", cache_key, exc)
        return None


def set_cached_route(
    db: Session, cache_key: str, routes: list[Route], ttl_seconds: int
) -> None:
    """Persist routes into route_cache with explicit expiration timestamp."""
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(seconds=ttl_seconds)

    serialized = [r.model_dump(mode="json", by_alias=True) for r in routes]

    entry = db.execute(
        select(RouteCache).where(RouteCache.cache_key == cache_key)
    ).scalars().first()

    if entry:
        entry.response_json = serialized
        entry.expires_at = expires_at
    else:
        entry = RouteCache(
            cache_key=cache_key,
            response_json=serialized,
            expires_at=expires_at,
        )
        db.add(entry)

    db.commit()
