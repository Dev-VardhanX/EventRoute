import uuid
from datetime import datetime
from fastapi import APIRouter, Depends, Query, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.security import get_current_user, get_optional_user, require_role
from app.models.enums import ParticipationType, UserRole
from app.models.event import Event
from app.models.user import User
from app.schemas.event import (
    CreateEventRequest,
    EventListResponse,
    EventResponse,
    TravelEstimatesRequest,
    TravelEstimatesResponse,
    UpdateEventRequest,
)
from app.services.events import (
    build_event_response,
    cancel_event,
    create_event,
    estimate_travel_estimates,
    get_event_detail,
    list_events_query,
    publish_event,
    update_event,
)

router = APIRouter(tags=["events"])


@router.get("/events/mine", response_model=EventListResponse)
def get_my_events(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    current_user: User = Depends(require_role(UserRole.ORGANIZER, UserRole.ADMIN)),
    db: Session = Depends(get_db),
):
    """Retrieve all events created by the authenticated organizer/admin across all statuses."""
    base_query = select(Event).where(Event.organizer_id == current_user.id)
    total = db.scalar(select(func.count(Event.id)).where(Event.organizer_id == current_user.id)) or 0

    events = (
        db.execute(
            base_query.order_by(Event.created_at.desc())
            .offset((page - 1) * page_size)
            .limit(page_size)
        )
        .scalars()
        .all()
    )

    items = [build_event_response(db, ev) for ev in events]
    return EventListResponse(items=items, total=total, page=page, page_size=page_size)


@router.post("/events/travel-estimates", response_model=TravelEstimatesResponse)
def get_travel_estimates(
    req: TravelEstimatesRequest,
    db: Session = Depends(get_db),
):
    """Calculate straight-line distance and rough travel duration estimates (25 km/h)."""
    estimates = estimate_travel_estimates(db, req)
    return TravelEstimatesResponse(items=estimates)


@router.get("/events", response_model=EventListResponse)
def list_events(
    lat: float | None = None,
    lng: float | None = None,
    radius_km: float | None = None,
    category: str | None = None,
    from_time: datetime | None = Query(None, alias="from"),
    to_time: datetime | None = Query(None, alias="to"),
    participation: ParticipationType | None = None,
    q: str | None = None,
    sort: str = Query("start_soon", pattern="^(start_soon|distance|popular)$"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
):
    """List published, upcoming events with geo radius, text search, and pagination."""
    items, total = list_events_query(
        db=db,
        lat=lat,
        lng=lng,
        radius_km=radius_km,
        category=category,
        from_time=from_time,
        to_time=to_time,
        participation=participation,
        q=q,
        sort=sort,
        page=page,
        page_size=page_size,
    )
    return EventListResponse(items=items, total=total, page=page, page_size=page_size)


@router.post("/events", response_model=EventResponse, status_code=status.HTTP_201_CREATED)
def create_new_event(
    req: CreateEventRequest,
    current_user: User = Depends(require_role(UserRole.ORGANIZER, UserRole.ADMIN)),
    db: Session = Depends(get_db),
):
    """Create a draft event (approved organizer or admin only)."""
    event = create_event(db, current_user, req)
    return build_event_response(db, event)


@router.get("/events/{id}", response_model=EventResponse)
def get_event(
    id: uuid.UUID,
    current_user: User | None = Depends(get_optional_user),
    db: Session = Depends(get_db),
):
    """Fetch event details by ID. Draft events are restricted to the owner or admin."""
    return get_event_detail(db, id, current_user)


@router.patch("/events/{id}", response_model=EventResponse)
def patch_event(
    id: uuid.UUID,
    req: UpdateEventRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Update event fields. Only owner organizer or platform admin can update."""
    event = update_event(db, id, current_user, req)
    return build_event_response(db, event)


@router.post("/events/{id}/publish", response_model=EventResponse)
def publish(
    id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Publish a draft event after validating all required rules."""
    event = publish_event(db, id, current_user)
    return build_event_response(db, event)


@router.post("/events/{id}/cancel", response_model=EventResponse)
def cancel(
    id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Cancel an event without hard-deleting the record."""
    event = cancel_event(db, id, current_user)
    return build_event_response(db, event)
