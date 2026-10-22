import logging
from datetime import datetime
from typing import Any

from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.errors import AppError
from app.services.routing.base import (
    NoRouteFound,
    ProviderError,
    ProviderTimeout,
    RouteProvider,
)
from app.services.routing.cache import (
    get_cached_route,
    get_ttl_seconds,
    make_cache_key,
    set_cached_route,
)
from app.services.routing.fake import FakeProvider
from app.services.routing.google import GoogleRouteProvider
from app.services.routing.guard import is_paid_limit_exceeded, record_paid_call
from app.services.routing.models import Route
from app.services.routing.ors import ORSRouteProvider

logger = logging.getLogger("eventroute.routing.service")
settings = get_settings()

VALID_MODES = {"transit", "walk", "bike", "drive"}


def _resolve_provider(
    provider_name: str,
    google_provider: RouteProvider | None = None,
    ors_provider: RouteProvider | None = None,
    fake_provider: RouteProvider | None = None,
) -> RouteProvider:
    """Instantiate or reuse providers, falling back to FakeProvider if keys are missing."""
    if provider_name == "google":
        if google_provider is not None:
            return google_provider
        if settings.google_maps_server_key:
            return GoogleRouteProvider(api_key=settings.google_maps_server_key)
        return fake_provider or FakeProvider()

    elif provider_name == "ors":
        if ors_provider is not None:
            return ors_provider
        if settings.ors_api_key:
            return ORSRouteProvider(api_key=settings.ors_api_key)
        return fake_provider or FakeProvider()

    return fake_provider or FakeProvider()


def _execute_provider_with_retry(
    provider: RouteProvider,
    origin: tuple[float, float],
    destination: tuple[float, float],
    mode: str,
    arrive_by: datetime | None,
    depart_at: datetime | None,
) -> list[Route]:
    """Execute provider call with a single retry on timeout."""
    try:
        return provider.get_routes(
            origin=origin,
            destination=destination,
            mode=mode,
            arrive_by=arrive_by,
            depart_at=depart_at,
        )
    except ProviderTimeout:
        logger.warning(
            "Provider %s timed out. Retrying once...", provider.name
        )
        # 1 Retry on timeout
        return provider.get_routes(
            origin=origin,
            destination=destination,
            mode=mode,
            arrive_by=arrive_by,
            depart_at=depart_at,
        )


def get_route(
    db: Session,
    origin: tuple[float, float],
    destination: tuple[float, float],
    mode: str,
    arrive_by: datetime | None = None,
    depart_at: datetime | None = None,
    google_provider: RouteProvider | None = None,
    ors_provider: RouteProvider | None = None,
    fake_provider: RouteProvider | None = None,
) -> tuple[list[Route], str, bool]:
    """Orchestrate route fetching: cache lookup -> primary -> retry -> fallback -> cost guard."""
    mode = mode.lower().strip()
    if mode not in VALID_MODES:
        raise AppError(
            "VALIDATION_ERROR",
            f"Invalid mode '{mode}'. Allowed modes: {', '.join(sorted(VALID_MODES))}",
        )

    # 1. Determine primary and fallback provider targets based on mode table
    # Transit: Google (primary), None (fallback)
    # Walk/Bike/Drive: ORS (primary), Google (fallback)
    if mode == "transit":
        primary_name = "google"
        fallback_name = None
    else:
        primary_name = "ors"
        fallback_name = "google"

    # 2. Check Cache with primary target
    primary_inst = _resolve_provider(
        primary_name, google_provider, ors_provider, fake_provider
    )
    cache_key = make_cache_key(
        provider=primary_inst.name,
        mode=mode,
        origin=origin,
        destination=destination,
        arrive_by=arrive_by,
    )

    cached_routes = get_cached_route(db, cache_key)
    if cached_routes:
        logger.info("Route cache HIT for key: %s", cache_key)
        return cached_routes, primary_inst.name, True

    # 3. Attempt Primary Provider
    routes: list[Route] | None = None
    provider_used = primary_inst.name

    # Check cost guard if primary is Google
    if primary_inst.name == "google" and is_paid_limit_exceeded():
        logger.warning("Cost guard triggered: daily paid routing limit exceeded.")
        # If fallback available, try fallback; else if transit, raise ROUTE_UNAVAILABLE
        if not fallback_name:
            raise AppError(
                "ROUTE_UNAVAILABLE",
                "Daily paid route quota reached. Try walking or cab.",
            )
        primary_inst = None  # Force fallback

    if primary_inst is not None:
        try:
            routes = _execute_provider_with_retry(
                primary_inst, origin, destination, mode, arrive_by, depart_at
            )
            provider_used = primary_inst.name
            if primary_inst.name == "google":
                record_paid_call()
        except (ProviderTimeout, ProviderError, NoRouteFound) as exc:
            logger.warning("Primary provider %s failed: %s", primary_inst.name, exc)
            routes = None

    # 4. Fallback Provider if primary failed
    if routes is None and fallback_name:
        fallback_inst = _resolve_provider(
            fallback_name, google_provider, ors_provider, fake_provider
        )
        if fallback_inst.name == "google" and is_paid_limit_exceeded():
            logger.warning("Cost guard triggered for fallback provider %s.", fallback_inst.name)
            fallback_inst = None

        if fallback_inst is not None:
            try:
                routes = _execute_provider_with_retry(
                    fallback_inst, origin, destination, mode, arrive_by, depart_at
                )
                provider_used = fallback_inst.name
                if fallback_inst.name == "google":
                    record_paid_call()
            except (ProviderTimeout, ProviderError, NoRouteFound) as exc:
                logger.warning("Fallback provider %s failed: %s", fallback_inst.name, exc)
                routes = None

    # 5. Handle complete failure
    if not routes:
        if mode == "transit":
            raise AppError(
                "ROUTE_UNAVAILABLE",
                "Transit route unavailable. Try walking or cab.",
            )
        raise AppError(
            "ROUTE_UNAVAILABLE",
            f"No {mode} route available between the specified locations.",
        )

    # 6. Cache and return
    ttl_s = get_ttl_seconds(mode)
    set_cached_route(db, cache_key, routes, ttl_s)
    return routes, provider_used, False
