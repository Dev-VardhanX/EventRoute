import logging
import uuid
from datetime import datetime, timedelta, timezone

import jwt
from sqlalchemy import func, or_, select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.errors import AppError
from app.models.enums import (
    EventStatus,
    ParticipationType,
    RegistrationStatus,
    TeamStatus,
    UserRole,
)
from app.models.event import Event
from app.models.registration import Registration
from app.models.team import Team
from app.models.team_member import TeamMember
from app.models.user import User
from app.schemas.registration import (
    EventSummary,
    RegisterSoloRequest,
    RegistrationResponse,
    TeamSummary,
)
from app.services.notifications import queue_notification

logger = logging.getLogger("eventroute.registration")
settings = get_settings()


def generate_ticket_token(user_id: uuid.UUID, event_id: uuid.UUID, reg_id: uuid.UUID) -> str:
    """Generate signed JWT ticket token for attendee check-in."""
    payload = {
        "sub": str(user_id),
        "event_id": str(event_id),
        "reg_id": str(reg_id),
        "type": "ticket",
        "iat": datetime.now(timezone.utc),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def verify_ticket_token(token: str, expected_event_id: uuid.UUID) -> dict:
    """Validate ticket token signature, type, and matching event."""
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
    except jwt.PyJWTError:
        raise AppError("VALIDATION_ERROR", "Invalid or tampered ticket token")

    if payload.get("type") != "ticket":
        raise AppError("VALIDATION_ERROR", "Token is not a ticket token")

    if payload.get("event_id") != str(expected_event_id):
        raise AppError("VALIDATION_ERROR", "Ticket token does not match this event")

    return payload


def is_eligible(user: User, event: Event) -> bool:
    """Determine user eligibility against event college/domain restrictions."""
    if not event.eligibility or not event.eligibility.strip():
        return True

    el = event.eligibility.strip().lower()
    if el in ("all", "open", "open for all", "public") or el.startswith("open"):
        return True

    # Check for college match
    if "college" in el or "internal" in el:
        if event.college_id and user.college_id != event.college_id:
            return False

    # Check for email domain match (e.g. "@iitd.ac.in" or "iitd.ac.in")
    if "@" in el:
        domain = el.split("@")[-1].strip()
        if not user.email.lower().endswith(f"@{domain}"):
            return False

    return True


def check_time_clash(
    db: Session, user: User, event: Event, acknowledge_clash: bool = False
) -> None:
    """Check for active registrations in overlapping event time windows."""
    # Find all active registrations for this user
    active_regs = (
        db.execute(
            select(Registration, Event)
            .join(Event, Registration.event_id == Event.id)
            .where(
                Registration.user_id == user.id,
                Registration.status != RegistrationStatus.CANCELLED,
                Event.id != event.id,
                Event.status == EventStatus.PUBLISHED,
            )
        )
        .all()
    )

    ev_start = event.start_at
    ev_end = event.end_at or (event.start_at + timedelta(hours=3))

    for _, other_ev in active_regs:
        other_start = other_ev.start_at
        other_end = other_ev.end_at or (other_ev.start_at + timedelta(hours=3))

        # Check interval overlap
        if max(ev_start, other_start) < min(ev_end, other_end):
            if not acknowledge_clash:
                raise AppError(
                    "TIME_CLASH",
                    f"Time clash with another registered event: {other_ev.title}",
                )


def register_solo(
    db: Session, user: User, event_id: uuid.UUID, req: RegisterSoloRequest
) -> RegistrationResponse:
    """Solo event registration workflow with atomic seat locking and validation."""
    now = datetime.now(timezone.utc)

    # 1. Event exists & published (lock row for concurrency control)
    event = (
        db.execute(select(Event).where(Event.id == event_id).with_for_update())
        .scalar_one_or_none()
    )
    if not event:
        raise AppError("NOT_FOUND", "Event not found")
    if event.status != EventStatus.PUBLISHED:
        raise AppError("EVENT_CLOSED", "Event is not open for registration")

    # 2. Before registration deadline
    if now >= event.registration_deadline:
        raise AppError("EVENT_CLOSED", "Registration deadline has passed")

    # 3. Participation type != team-only
    if event.participation_type == ParticipationType.TEAM:
        raise AppError("VALIDATION_ERROR", "team event, create or join a team")

    # 4. Seats left using registered count vs seat_limit
    if event.seat_limit is not None:
        reg_count = (
            db.scalar(
                select(func.count(Registration.id)).where(
                    Registration.event_id == event.id,
                    Registration.status != RegistrationStatus.CANCELLED,
                )
            )
            or 0
        )
        if reg_count >= event.seat_limit:
            raise AppError("EVENT_FULL", "Event is full")

    # 5. Eligibility
    if not is_eligible(user, event):
        raise AppError("NOT_ELIGIBLE", "You are not eligible for this event")

    # 6. Not already registered (cancelled may re-register)
    existing_reg = db.execute(
        select(Registration).where(
            Registration.event_id == event.id, Registration.user_id == user.id
        )
    ).scalar_one_or_none()

    if existing_reg and existing_reg.status != RegistrationStatus.CANCELLED:
        raise AppError("ALREADY_REGISTERED", "You are already registered for this event")

    # 7. Time clash check
    check_time_clash(db, user, event, req.acknowledge_clash)

    # 8. Create or reactivate registration
    if existing_reg:
        token = generate_ticket_token(user.id, event.id, existing_reg.id)
        existing_reg.status = RegistrationStatus.REGISTERED
        existing_reg.team_id = None
        existing_reg.ticket_token = token
        reg = existing_reg
    else:
        new_id = uuid.uuid4()
        token = generate_ticket_token(user.id, event.id, new_id)
        reg = Registration(
            id=new_id,
            event_id=event.id,
            user_id=user.id,
            team_id=None,
            status=RegistrationStatus.REGISTERED,
            ticket_token=token,
        )
        db.add(reg)

    db.commit()
    db.refresh(reg)

    queue_notification(user, "registration_confirmed", event)

    return build_registration_response(db, reg, event)


def cancel_registration(db: Session, user: User, reg_id: uuid.UUID) -> None:
    """Cancel registration. If part of a team, route through team membership rules."""
    reg = db.get(Registration, reg_id)
    if not reg:
        raise AppError("NOT_FOUND", "Registration not found")

    if reg.user_id != user.id and user.role != UserRole.ADMIN:
        raise AppError("FORBIDDEN", "Not authorized to cancel this registration")

    if reg.status == RegistrationStatus.CANCELLED:
        return

    if reg.team_id is not None:
        from app.services.teams import remove_team_member

        remove_team_member(db, reg.team_id, user.id, user)
    else:
        reg.status = RegistrationStatus.CANCELLED
        db.commit()


def build_registration_response(
    db: Session, reg: Registration, event: Event | None = None
) -> RegistrationResponse:
    """Build response model including event and team summaries."""
    if not event:
        event = db.get(Event, reg.event_id)

    ev_summary = (
        EventSummary(
            id=event.id,
            title=event.title,
            start_at=event.start_at,
            end_at=event.end_at,
            mode=event.mode,
            venue_name=event.venue_name,
            banner_url=event.banner_url,
            status=event.status,
        )
        if event
        else None
    )

    team_summary = None
    if reg.team_id:
        team = db.get(Team, reg.team_id)
        if team:
            m_count = (
                db.scalar(
                    select(func.count(TeamMember.id)).where(TeamMember.team_id == team.id)
                )
                or 1
            )
            team_summary = TeamSummary(
                id=team.id,
                name=team.name,
                status=team.status,
                invite_code=team.invite_code,
                member_count=m_count,
            )

    return RegistrationResponse(
        id=reg.id,
        event_id=reg.event_id,
        user_id=reg.user_id,
        team_id=reg.team_id,
        status=reg.status,
        ticket_token=reg.ticket_token,
        journey_plan=None,
        created_at=reg.created_at,
        event=ev_summary,
        team=team_summary,
    )


def list_my_registrations(
    db: Session,
    user: User,
    filter_type: str | None = None,
    page: int = 1,
    page_size: int = 20,
) -> tuple[list[RegistrationResponse], int]:
    """List authenticated user's registrations with optional upcoming/past filter."""
    now = datetime.now(timezone.utc)
    base_query = (
        select(Registration, Event)
        .join(Event, Registration.event_id == Event.id)
        .where(Registration.user_id == user.id)
    )

    if filter_type == "upcoming":
        base_query = base_query.where(
            or_(
                Event.end_at >= now,
                (Event.end_at.is_(None) & (Event.start_at >= now)),
            )
        )
    elif filter_type == "past":
        base_query = base_query.where(
            or_(
                Event.end_at < now,
                (Event.end_at.is_(None) & (Event.start_at < now)),
            )
        )

    count_query = select(func.count()).select_from(base_query.subquery())
    total = db.scalar(count_query) or 0

    rows = (
        db.execute(
            base_query.order_by(Event.start_at.asc())
            .offset((page - 1) * page_size)
            .limit(page_size)
        )
        .all()
    )

    items = [build_registration_response(db, reg, ev) for reg, ev in rows]
    return items, total
