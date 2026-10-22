import logging
import math
import uuid
from datetime import datetime, timezone
from typing import Any

from sqlalchemy import func, or_, select, update
from sqlalchemy.orm import Session

from app.core.errors import AppError
from app.models.enums import (
    EventMode,
    EventStatus,
    OrganizerStatus,
    ParticipationType,
    RegistrationStatus,
    TeamStatus,
    UserRole,
)
from app.models.event import Event
from app.models.organizer_profile import OrganizerProfile
from app.models.registration import Registration
from app.models.team import Team
from app.models.user import User
from app.schemas.event import (
    CreateEventRequest,
    EventResponse,
    EventTravelEstimate,
    TravelEstimatesRequest,
    UpdateEventRequest,
)

logger = logging.getLogger("eventroute.events")


# --- Stub Hooks for Lifecycle Events ---

def on_event_changed(event: Event) -> None:
    """Stub hook called when critical event fields (start_at, check_in_at, venue) change."""
    logger.info("HOOK: on_event_changed -> Event %s modified. Re-planning triggered.", event.id)


def on_event_cancelled(event: Event) -> None:
    """Stub hook called when an event is cancelled; will close registrations/teams later."""
    logger.info(
        "HOOK: on_event_cancelled -> Event %s cancelled. Closing registrations and teams.", event.id
    )


# --- Haversine Utilities ---

def haversine_distance(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    """Compute straight-line great-circle distance between two coords in kilometers."""
    r = 6371.0  # Earth radius in km
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = (
        math.sin(dlat / 2.0) ** 2
        + math.cos(math.radians(lat1))
        * math.cos(math.radians(lat2))
        * math.sin(dlng / 2.0) ** 2
    )
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return r * c


def sql_haversine(lat: float, lng: float):
    """Generate SQLAlchemy expression computing distance (km) from (lat, lng) to event venue."""
    lat_rad = math.radians(lat)
    lng_rad = math.radians(lng)
    venue_lat_rad = func.radians(Event.venue_lat)
    venue_lng_rad = func.radians(Event.venue_lng)
    dlat = (venue_lat_rad - lat_rad) / 2.0
    dlng = (venue_lng_rad - lng_rad) / 2.0
    a = func.power(func.sin(dlat), 2) + func.cos(lat_rad) * func.cos(
        venue_lat_rad
    ) * func.power(func.sin(dlng), 2)
    clamped_a = func.least(1.0, func.greatest(0.0, a))
    c = 2.0 * func.asin(func.sqrt(clamped_a))
    return 6371.0 * c


# --- Validation Helpers ---

def validate_event_rules(
    mode: EventMode,
    start_at: datetime,
    check_in_at: datetime,
    registration_deadline: datetime,
    participation_type: ParticipationType,
    team_min: int,
    team_max: int,
    venue_lat: float | None,
    venue_lng: float | None,
) -> None:
    """Enforce BACKEND.md constraints on dates, team bounds, and venue coordinates."""
    if team_min > team_max:
        raise AppError("VALIDATION_ERROR", "team_min must be less than or equal to team_max")

    if check_in_at > start_at:
        raise AppError("VALIDATION_ERROR", "check_in_at must be before or equal to start_at")

    if registration_deadline > check_in_at:
        raise AppError(
            "VALIDATION_ERROR", "registration_deadline must be before or equal to check_in_at"
        )

    if participation_type == ParticipationType.SOLO:
        if team_min != 1 or team_max != 1:
            raise AppError("VALIDATION_ERROR", "Solo participation requires team_min=1 and team_max=1")
    elif participation_type in (ParticipationType.TEAM, ParticipationType.EITHER):
        if team_min < 2:
            raise AppError(
                "VALIDATION_ERROR", "team_min must be at least 2 for team or either participation"
            )

    if mode in (EventMode.OFFLINE, EventMode.HYBRID):
        if venue_lat is None or venue_lng is None:
            raise AppError(
                "VALIDATION_ERROR",
                "venue_lat and venue_lng are required for offline or hybrid events",
            )


# --- Event Service Operations ---

def create_event(db: Session, current_user: User, req: CreateEventRequest) -> Event:
    """Create a new event in draft status with role & profile verification."""
    # Enforce role: organizer must have approved profile, or admin
    if current_user.role == UserRole.ORGANIZER:
        profile = db.execute(
            select(OrganizerProfile).where(OrganizerProfile.user_id == current_user.id)
        ).scalar_one_or_none()
        if not profile or profile.status != OrganizerStatus.APPROVED:
            raise AppError(
                "FORBIDDEN", "Only approved organizers or admins can create events"
            )
    elif current_user.role != UserRole.ADMIN:
        raise AppError("FORBIDDEN", "Only organizers or admins can create events")

    # Validate rules
    validate_event_rules(
        mode=req.mode,
        start_at=req.start_at,
        check_in_at=req.check_in_at,
        registration_deadline=req.registration_deadline,
        participation_type=req.participation_type,
        team_min=req.team_min,
        team_max=req.team_max,
        venue_lat=req.venue_lat,
        venue_lng=req.venue_lng,
    )

    event = Event(
        organizer_id=current_user.id,
        college_id=req.college_id,
        title=req.title.strip(),
        description=req.description,
        category=req.category.strip(),
        tags=req.tags,
        banner_url=req.banner_url,
        mode=req.mode,
        start_at=req.start_at,
        end_at=req.end_at,
        check_in_at=req.check_in_at,
        registration_deadline=req.registration_deadline,
        timezone=req.timezone,
        venue_name=req.venue_name,
        venue_lat=req.venue_lat,
        venue_lng=req.venue_lng,
        participation_type=req.participation_type,
        team_min=req.team_min,
        team_max=req.team_max,
        seat_limit=req.seat_limit,
        fee_amount=req.fee_amount,
        eligibility=req.eligibility,
        status=EventStatus.DRAFT,
        is_demo=current_user.is_demo,
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return event


def check_event_ownership(event: Event, current_user: User) -> None:
    """Ensure current user is event owner or platform admin."""
    if current_user.role != UserRole.ADMIN and event.organizer_id != current_user.id:
        raise AppError("FORBIDDEN", "Not authorized to modify this event")


def update_event(db: Session, event_id: uuid.UUID, current_user: User, req: UpdateEventRequest) -> Event:
    """Update event fields with ownership check and re-plan detection."""
    event = db.get(Event, event_id)
    if not event:
        raise AppError("NOT_FOUND", "Event not found")

    check_event_ownership(event, current_user)

    # Check if re-planning trigger fields changed on a published event
    critical_changed = False
    if event.status == EventStatus.PUBLISHED:
        if req.start_at is not None and req.start_at != event.start_at:
            critical_changed = True
        if req.check_in_at is not None and req.check_in_at != event.check_in_at:
            critical_changed = True
        if req.venue_lat is not None and req.venue_lat != event.venue_lat:
            critical_changed = True
        if req.venue_lng is not None and req.venue_lng != event.venue_lng:
            critical_changed = True
        if req.venue_name is not None and req.venue_name != event.venue_name:
            critical_changed = True

    # Compute candidate values for validation
    new_mode = req.mode if req.mode is not None else event.mode
    new_start_at = req.start_at if req.start_at is not None else event.start_at
    new_check_in_at = req.check_in_at if req.check_in_at is not None else event.check_in_at
    new_reg_deadline = (
        req.registration_deadline
        if req.registration_deadline is not None
        else event.registration_deadline
    )
    new_part_type = (
        req.participation_type if req.participation_type is not None else event.participation_type
    )
    new_team_min = req.team_min if req.team_min is not None else event.team_min
    new_team_max = req.team_max if req.team_max is not None else event.team_max
    new_venue_lat = req.venue_lat if req.venue_lat is not None else event.venue_lat
    new_venue_lng = req.venue_lng if req.venue_lng is not None else event.venue_lng

    validate_event_rules(
        mode=new_mode,
        start_at=new_start_at,
        check_in_at=new_check_in_at,
        registration_deadline=new_reg_deadline,
        participation_type=new_part_type,
        team_min=new_team_min,
        team_max=new_team_max,
        venue_lat=new_venue_lat,
        venue_lng=new_venue_lng,
    )

    # Apply updates
    for field, val in req.model_dump(exclude_unset=True).items():
        if val is not None and isinstance(val, str):
            val = val.strip()
        setattr(event, field, val)

    db.commit()
    db.refresh(event)

    if critical_changed:
        on_event_changed(event)

    return event


def publish_event(db: Session, event_id: uuid.UUID, current_user: User) -> Event:
    """Transition event from DRAFT to PUBLISHED with full requirement validation."""
    event = db.get(Event, event_id)
    if not event:
        raise AppError("NOT_FOUND", "Event not found")

    check_event_ownership(event, current_user)

    if event.status == EventStatus.PUBLISHED:
        return event

    if event.status not in (EventStatus.DRAFT, EventStatus.PENDING_REVIEW):
        raise AppError("VALIDATION_ERROR", f"Cannot publish event in status '{event.status.value}'")

    validate_event_rules(
        mode=event.mode,
        start_at=event.start_at,
        check_in_at=event.check_in_at,
        registration_deadline=event.registration_deadline,
        participation_type=event.participation_type,
        team_min=event.team_min,
        team_max=event.team_max,
        venue_lat=event.venue_lat,
        venue_lng=event.venue_lng,
    )

    event.status = EventStatus.PUBLISHED
    db.commit()
    db.refresh(event)
    return event


def cancel_event(db: Session, event_id: uuid.UUID, current_user: User) -> Event:
    """Cancel an event without hard delete, invoking teardown hooks."""
    event = db.get(Event, event_id)
    if not event:
        raise AppError("NOT_FOUND", "Event not found")

    check_event_ownership(event, current_user)

    event.status = EventStatus.CANCELLED

    # Cascade cancellation to active registrations and teams
    db.execute(
        update(Registration)
        .where(
            Registration.event_id == event.id,
            Registration.status != RegistrationStatus.CANCELLED,
        )
        .values(status=RegistrationStatus.CANCELLED)
    )
    db.execute(
        update(Team)
        .where(
            Team.event_id == event.id,
            Team.status != TeamStatus.CANCELLED,
        )
        .values(status=TeamStatus.CANCELLED)
    )

    db.commit()
    db.refresh(event)

    on_event_cancelled(event)
    return event


def build_event_response(
    db: Session, event: Event, distance_km: float | None = None
) -> EventResponse:
    """Build unified EventResponse including registration stats and optional distance."""
    reg_count = (
        db.scalar(
            select(func.count(Registration.id)).where(
                Registration.event_id == event.id,
                Registration.status != RegistrationStatus.CANCELLED,
            )
        )
        or 0
    )
    seats_left = max(0, event.seat_limit - reg_count) if event.seat_limit is not None else None

    return EventResponse(
        id=event.id,
        organizer_id=event.organizer_id,
        college_id=event.college_id,
        title=event.title,
        description=event.description,
        category=event.category,
        tags=event.tags or [],
        banner_url=event.banner_url,
        mode=event.mode,
        start_at=event.start_at,
        end_at=event.end_at,
        check_in_at=event.check_in_at,
        registration_deadline=event.registration_deadline,
        timezone=event.timezone,
        venue_name=event.venue_name,
        venue_lat=event.venue_lat,
        venue_lng=event.venue_lng,
        participation_type=event.participation_type,
        team_min=event.team_min,
        team_max=event.team_max,
        seat_limit=event.seat_limit,
        fee_amount=event.fee_amount,
        eligibility=event.eligibility,
        status=event.status,
        is_demo=event.is_demo,
        created_at=event.created_at,
        registered_count=reg_count,
        seats_left=seats_left,
        distance_km=round(distance_km, 2) if distance_km is not None else None,
    )


def get_event_detail(db: Session, event_id: uuid.UUID, current_user: User | None) -> EventResponse:
    """Get single event by ID. Drafts are restricted to owner or admin."""
    event = db.get(Event, event_id)
    if not event:
        raise AppError("NOT_FOUND", "Event not found")

    if event.status == EventStatus.DRAFT:
        is_owner = current_user and current_user.id == event.organizer_id
        is_admin = current_user and current_user.role == UserRole.ADMIN
        if not (is_owner or is_admin):
            raise AppError("NOT_FOUND", "Event not found")

    return build_event_response(db, event)


def list_events_query(
    db: Session,
    lat: float | None = None,
    lng: float | None = None,
    radius_km: float | None = None,
    category: str | None = None,
    from_time: datetime | None = None,
    to_time: datetime | None = None,
    participation: ParticipationType | None = None,
    q: str | None = None,
    sort: str = "start_soon",
    page: int = 1,
    page_size: int = 20,
) -> tuple[list[EventResponse], int]:
    """Search and filter published, upcoming events with haversine distance in SQL."""
    now = datetime.now(timezone.utc)

    # Base conditions: published and not in the past
    conditions = [
        Event.status == EventStatus.PUBLISHED,
        or_(
            Event.end_at >= now,
            (Event.end_at.is_(None) & (Event.start_at >= now)),
        ),
    ]

    if category and category.strip():
        conditions.append(Event.category.ilike(category.strip()))

    if from_time:
        conditions.append(Event.start_at >= from_time)

    if to_time:
        conditions.append(Event.start_at <= to_time)

    if participation:
        conditions.append(Event.participation_type == participation)

    if q and q.strip():
        term = f"%{q.strip()}%"
        # Match title or tags
        conditions.append(
            or_(
                Event.title.ilike(term),
                Event.category.ilike(term),
                Event.tags.any(q.strip()),
            )
        )

    # Geo calculation
    has_geo = lat is not None and lng is not None
    dist_expr = sql_haversine(lat, lng) if has_geo else None

    if has_geo and radius_km is not None and dist_expr is not None:
        conditions.append(dist_expr <= radius_km)

    # Build base select
    base_query = select(Event).where(*conditions)

    # Total count query
    count_query = select(func.count(Event.id)).where(*conditions)
    total = db.scalar(count_query) or 0

    # Sorting
    if sort == "distance" and has_geo and dist_expr is not None:
        base_query = base_query.order_by(dist_expr.asc(), Event.start_at.asc())
    elif sort == "popular":
        # Order by active registrations count descending
        reg_subquery = (
            select(func.count(Registration.id))
            .where(
                Registration.event_id == Event.id,
                Registration.status != RegistrationStatus.CANCELLED,
            )
            .coroutine(False)
            if hasattr(select(func.count(Registration.id)), "coroutine")
            else select(func.count(Registration.id)).where(
                Registration.event_id == Event.id,
                Registration.status != RegistrationStatus.CANCELLED,
            )
        )
        base_query = base_query.order_by(reg_subquery.scalar_subquery().desc(), Event.start_at.asc())
    else:
        # Default: start_soon
        base_query = base_query.order_by(Event.start_at.asc())

    # Pagination
    events = (
        db.execute(base_query.offset((page - 1) * page_size).limit(page_size))
        .scalars()
        .all()
    )

    items: list[EventResponse] = []
    for ev in events:
        d_km = None
        if has_geo and ev.venue_lat is not None and ev.venue_lng is not None:
            d_km = haversine_distance(lat, lng, ev.venue_lat, ev.venue_lng)  # type: ignore[arg-type]
        items.append(build_event_response(db, ev, distance_km=d_km))

    return items, total


def estimate_travel_estimates(
    db: Session, req: TravelEstimatesRequest
) -> list[EventTravelEstimate]:
    """Calculate travel estimates using 6h cached routing with straight-line fallback."""
    if len(req.event_ids) > 20:
        raise AppError(
            "VALIDATION_ERROR",
            "Maximum 20 event IDs allowed per travel estimate request",
        )

    origin_lat = req.origin.lat if req.origin else req.lat
    origin_lng = req.origin.lng if req.origin else req.lng

    if origin_lat is None or origin_lng is None:
        raise AppError("VALIDATION_ERROR", "Origin coordinates (lat, lng) are required")

    from app.services.routing.cache import get_cached_route, set_cached_route
    from app.services.routing.service import get_route

    events = db.execute(select(Event).where(Event.id.in_(req.event_ids))).scalars().all()
    events_map = {ev.id: ev for ev in events}

    estimates: list[EventTravelEstimate] = []
    DISCOVERY_TTL_S = 6 * 60 * 60  # 6 hours

    for eid in req.event_ids:
        ev = events_map.get(eid)
        if not ev or ev.venue_lat is None or ev.venue_lng is None:
            estimates.append(
                EventTravelEstimate(
                    event_id=eid,
                    distance_km=0.0,
                    duration_min=0,
                    source="estimate",
                )
            )
            continue

        dist_km = haversine_distance(origin_lat, origin_lng, ev.venue_lat, ev.venue_lng)
        # Default walk under 1.5 km, otherwise transit
        mode = "walk" if dist_km < 1.5 else "transit"
        cache_key = (
            f"discovery:{mode}:{origin_lat:.4f},{origin_lng:.4f}:"
            f"{ev.venue_lat:.4f},{ev.venue_lng:.4f}:none"
        )

        # 1. Check discovery cache
        cached_routes = get_cached_route(db, cache_key)
        if cached_routes and len(cached_routes) > 0:
            c_route = cached_routes[0]
            estimates.append(
                EventTravelEstimate(
                    event_id=eid,
                    distance_km=round(c_route.distance_m / 1000.0, 2),
                    duration_min=max(1, round(c_route.duration_s / 60)),
                    source="route",
                )
            )
            continue

        # 2. Try real route computation
        try:
            routes, _, _ = get_route(
                db=db,
                origin=(origin_lat, origin_lng),
                destination=(ev.venue_lat, ev.venue_lng),
                mode=mode,
            )
            if routes and len(routes) > 0:
                best_route = routes[0]
                set_cached_route(db, cache_key, routes, DISCOVERY_TTL_S)
                estimates.append(
                    EventTravelEstimate(
                        event_id=eid,
                        distance_km=round(best_route.distance_m / 1000.0, 2),
                        duration_min=max(1, round(best_route.duration_s / 60)),
                        source="route",
                    )
                )
                continue
        except Exception as exc:
            logger.debug(
                "Real route estimate failed for event %s, falling back to estimate: %s",
                eid,
                exc,
            )

        # 3. Fallback to straight-line estimate
        duration_min = max(1, round((dist_km / 25.0) * 60))
        estimates.append(
            EventTravelEstimate(
                event_id=eid,
                distance_km=round(dist_km, 2),
                duration_min=duration_min,
                source="estimate",
            )
        )

    return estimates
