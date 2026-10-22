import time
from collections import defaultdict
from datetime import datetime
from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.errors import AppError
from app.services.routing.models import RouteQueryResponse
from app.services.routing.service import get_route

router = APIRouter(tags=["routing"])

# In-memory IP rate limit: 30 requests per 60 seconds
_ip_route_requests: dict[str, list[float]] = defaultdict(list)
IP_RATE_WINDOW_S = 60.0
IP_RATE_LIMIT_MAX = 30


def check_ip_rate_limit(request: Request) -> None:
    client_ip = (
        request.headers.get("x-forwarded-for")
        or (request.client.host if request.client else "unknown")
    )
    if "," in client_ip:
        client_ip = client_ip.split(",")[0].strip()

    now = time.time()
    timestamps = [t for t in _ip_route_requests[client_ip] if now - t < IP_RATE_WINDOW_S]
    _ip_route_requests[client_ip] = timestamps

    if len(timestamps) >= IP_RATE_LIMIT_MAX:
        raise AppError(
            "RATE_LIMITED", "Too many routing requests. Please wait a moment before trying again."
        )

    _ip_route_requests[client_ip].append(now)


@router.get("/route", response_model=RouteQueryResponse)
def compute_route(
    request: Request,
    origin_lat: float = Query(..., description="Origin latitude (-90 to 90)"),
    origin_lng: float = Query(..., description="Origin longitude (-180 to 180)"),
    dest_lat: float = Query(..., description="Destination latitude (-90 to 90)"),
    dest_lng: float = Query(..., description="Destination longitude (-180 to 180)"),
    mode: str = Query("transit", description="Travel mode (transit | walk | bike | drive)"),
    arrive_by: datetime | None = Query(None, description="Desired arrival time (ISO UTC)"),
    db: Session = Depends(get_db),
):
    """Compute routes between two coordinates with caching, retries, and rate limiting."""
    check_ip_rate_limit(request)

    # Validate coordinate ranges
    if not (-90.0 <= origin_lat <= 90.0) or not (-90.0 <= dest_lat <= 90.0):
        raise AppError("VALIDATION_ERROR", "Latitude must be between -90 and 90 degrees")
    if not (-180.0 <= origin_lng <= 180.0) or not (-180.0 <= dest_lng <= 180.0):
        raise AppError("VALIDATION_ERROR", "Longitude must be between -180 and 180 degrees")

    origin = (origin_lat, origin_lng)
    dest = (dest_lat, dest_lng)

    routes, provider, cached = get_route(
        db=db,
        origin=origin,
        destination=dest,
        mode=mode,
        arrive_by=arrive_by,
    )

    return RouteQueryResponse(routes=routes, provider=provider, cached=cached)
