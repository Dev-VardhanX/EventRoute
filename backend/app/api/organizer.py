import csv
import io
import uuid
from fastapi import APIRouter, Depends, Query, status
from fastapi.responses import StreamingResponse
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.errors import AppError
from app.core.security import get_current_user
from app.models.enums import RegistrationStatus, UserRole
from app.models.event import Event
from app.models.registration import Registration
from app.models.team import Team
from app.models.user import User
from app.schemas.registration import (
    CheckInRequest,
    CheckInResponse,
    OrganizerRegistrationItem,
    OrganizerRegistrationListResponse,
)
from app.services.registration import verify_ticket_token

router = APIRouter(tags=["organizer"])


def _check_organizer_ownership(db: Session, event_id: uuid.UUID, user: User) -> Event:
    """Verify event exists and authenticated user is owner organizer or platform admin."""
    event = db.get(Event, event_id)
    if not event:
        raise AppError("NOT_FOUND", "Event not found")
    if event.organizer_id != user.id and user.role != UserRole.ADMIN:
        raise AppError("FORBIDDEN", "Not authorized to manage this event")
    return event


def sanitize_csv_cell(value: str | None) -> str:
    """Escape spreadsheet formula triggers (=, +, -, @) to prevent CSV injection."""
    if value is None:
        return ""
    val_str = str(value)
    if val_str and val_str[0] in ("=", "+", "-", "@"):
        return f"'{val_str}"
    return val_str


@router.get(
    "/events/{id}/registrations",
    response_model=OrganizerRegistrationListResponse,
)
def list_event_registrations(
    id: uuid.UUID,
    reg_status: RegistrationStatus | None = Query(None, alias="status"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """List event attendees with name, email, team name, and status (owner or admin only)."""
    _check_organizer_ownership(db, id, current_user)

    base_query = (
        select(Registration, User, Team)
        .join(User, Registration.user_id == User.id)
        .outerjoin(Team, Registration.team_id == Team.id)
        .where(Registration.event_id == id)
    )

    if reg_status is not None:
        base_query = base_query.where(Registration.status == reg_status)

    total = db.scalar(select(func.count()).select_from(base_query.subquery())) or 0

    rows = (
        db.execute(
            base_query.order_by(Registration.created_at.asc())
            .offset((page - 1) * page_size)
            .limit(page_size)
        )
        .all()
    )

    items: list[OrganizerRegistrationItem] = []
    for reg, u, t in rows:
        items.append(
            OrganizerRegistrationItem(
                id=reg.id,
                user_id=u.id,
                user_name=u.name,
                user_email=u.email,
                team_id=t.id if t else None,
                team_name=t.name if t else None,
                status=reg.status,
                ticket_token=reg.ticket_token,
                created_at=reg.created_at,
            )
        )

    return OrganizerRegistrationListResponse(items=items, total=total, page=page, page_size=page_size)


@router.get("/events/{id}/registrations/export")
def export_event_registrations(
    id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Export registration list to CSV with safe formula cell escaping."""
    _check_organizer_ownership(db, id, current_user)

    rows = (
        db.execute(
            select(Registration, User, Team)
            .join(User, Registration.user_id == User.id)
            .outerjoin(Team, Registration.team_id == Team.id)
            .where(Registration.event_id == id)
            .order_by(Registration.created_at.asc())
        )
        .all()
    )

    output = io.StringIO()
    writer = csv.writer(output)
    writer.writerow([
        "Registration ID",
        "Student Name",
        "Student Email",
        "Team Name",
        "Status",
        "Registered At",
    ])

    for reg, u, t in rows:
        team_name = t.name if t else "Solo"
        writer.writerow([
            sanitize_csv_cell(str(reg.id)),
            sanitize_csv_cell(u.name),
            sanitize_csv_cell(u.email),
            sanitize_csv_cell(team_name),
            sanitize_csv_cell(reg.status.value),
            sanitize_csv_cell(reg.created_at.isoformat()),
        ])

    output.seek(0)
    return StreamingResponse(
        iter([output.getvalue()]),
        media_type="text/csv",
        headers={"Content-Disposition": f"attachment; filename=registrations_{id}.csv"},
    )


@router.post("/events/{id}/check-in", response_model=CheckInResponse)
def check_in_attendee(
    id: uuid.UUID,
    req: CheckInRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Validate ticket token signature and check in attendee."""
    _check_organizer_ownership(db, id, current_user)

    # Validate signature and event id matching
    payload = verify_ticket_token(req.ticket_token, expected_event_id=id)

    reg_id_str = payload.get("reg_id")
    reg_id = uuid.UUID(reg_id_str) if reg_id_str else None

    reg = None
    if reg_id:
        reg = db.get(Registration, reg_id)
    if not reg:
        reg = db.execute(
            select(Registration).where(
                Registration.event_id == id, Registration.ticket_token == req.ticket_token
            )
        ).scalar_one_or_none()

    if not reg:
        raise AppError("NOT_FOUND", "Registration record not found")

    if reg.status == RegistrationStatus.CHECKED_IN:
        raise AppError("VALIDATION_ERROR", "Registration already checked in")

    if reg.status == RegistrationStatus.CANCELLED:
        raise AppError("VALIDATION_ERROR", "Registration has been cancelled")

    reg.status = RegistrationStatus.CHECKED_IN
    db.commit()
    db.refresh(reg)

    user = db.get(User, reg.user_id)
    return CheckInResponse(
        status="checked_in",
        registration_id=reg.id,
        user_id=reg.user_id,
        user_name=user.name if user else None,
        event_id=id,
    )
