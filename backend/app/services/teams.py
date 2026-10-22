import logging
import secrets
import uuid
from datetime import datetime, timezone

from sqlalchemy import func, select
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
from app.schemas.registration import EventSummary
from app.schemas.team import (
    CreateTeamRequest,
    InviteSummaryResponse,
    JoinTeamRequest,
    TeamMemberResponse,
    TeamMemberStartPointRequest,
    TeamResponse,
)
from app.services.notifications import queue_notification, queue_team_notification
from app.services.registration import check_time_clash, generate_ticket_token, is_eligible

logger = logging.getLogger("eventroute.teams")
settings = get_settings()

INVITE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"


def on_team_confirmed(team: Team) -> None:
    """Stub hook called when team confirms size. Generates team travel plan."""
    logger.info("HOOK: on_team_confirmed -> Team %s confirmed. Generating team travel plan.", team.id)


def on_team_changed(team: Team) -> None:
    """Stub hook called when team members or start points change after confirmation."""
    logger.info(
        "HOOK: on_team_changed -> Team %s changed. Incrementing plan version to %s.",
        team.id,
        team.plan_version,
    )


def generate_invite_code(db: Session) -> str:
    """Generate an unambiguous 8-character unique invite code."""
    for _ in range(10):
        code = "".join(secrets.choice(INVITE_ALPHABET) for _ in range(8))
        existing = db.execute(select(Team).where(Team.invite_code == code)).scalar_one_or_none()
        if not existing:
            return code
    # Fallback to UUID prefix if collision
    return uuid.uuid4().hex[:8].upper()


def build_team_response(db: Session, team: Team, event: Event | None = None) -> TeamResponse:
    """Construct complete TeamResponse with member details and bounds."""
    if not event:
        event = db.get(Event, team.event_id)

    members = (
        db.execute(
            select(TeamMember, User)
            .join(User, TeamMember.user_id == User.id)
            .where(TeamMember.team_id == team.id)
            .order_by(TeamMember.created_at.asc())
        )
        .all()
    )

    member_responses: list[TeamMemberResponse] = []
    for tm, u in members:
        member_responses.append(
            TeamMemberResponse(
                id=tm.id,
                team_id=tm.team_id,
                user_id=tm.user_id,
                user_name=u.name,
                user_email=u.email,
                start_lat=tm.start_lat,
                start_lng=tm.start_lng,
                start_source=tm.start_source,
                travel_pref=tm.travel_pref,
                journey_status=tm.journey_status,
                created_at=tm.created_at,
            )
        )

    t_min = event.team_min if event else 1
    t_max = event.team_max if event else 1
    spots_left = max(0, t_max - len(member_responses))
    invite_link = f"{settings.app_base_url}/invite/{team.invite_code}"

    return TeamResponse(
        id=team.id,
        event_id=team.event_id,
        name=team.name,
        leader_id=team.leader_id,
        invite_code=team.invite_code,
        invite_link=invite_link,
        status=team.status,
        plan_version=team.plan_version,
        planned_arrival_at=team.planned_arrival_at,
        members=member_responses,
        member_count=len(member_responses),
        team_min=t_min,
        team_max=t_max,
        spots_left=spots_left,
        created_at=team.created_at,
    )


def create_team(
    db: Session, user: User, event_id: uuid.UUID, req: CreateTeamRequest
) -> TeamResponse:
    """Create a new team, register leader, and produce unique invite link."""
    now = datetime.now(timezone.utc)

    # 1. Lock event row
    event = (
        db.execute(select(Event).where(Event.id == event_id).with_for_update())
        .scalar_one_or_none()
    )
    if not event:
        raise AppError("NOT_FOUND", "Event not found")
    if event.status != EventStatus.PUBLISHED:
        raise AppError("EVENT_CLOSED", "Event is not open for registration")

    # 2. Check participation type allows teams
    if event.participation_type not in (ParticipationType.TEAM, ParticipationType.EITHER):
        raise AppError("VALIDATION_ERROR", "Event does not allow team participation")

    # 3. Before registration deadline
    if now >= event.registration_deadline:
        raise AppError("EVENT_CLOSED", "Registration deadline has passed")

    # 4. Seats check
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

    # 6. Not already registered
    existing_reg = db.execute(
        select(Registration).where(
            Registration.event_id == event.id, Registration.user_id == user.id
        )
    ).scalar_one_or_none()
    if existing_reg and existing_reg.status != RegistrationStatus.CANCELLED:
        raise AppError("ALREADY_REGISTERED", "You are already registered for this event")

    # 7. Time clash
    check_time_clash(db, user, event, req.acknowledge_clash)

    # 8. Create Team
    invite_code = generate_invite_code(db)
    team = Team(
        id=uuid.uuid4(),
        event_id=event.id,
        name=req.name.strip(),
        leader_id=user.id,
        invite_code=invite_code,
        status=TeamStatus.FORMING,
        plan_version=1,
    )
    db.add(team)
    db.flush()

    # 9. Create Leader TeamMember
    member = TeamMember(
        id=uuid.uuid4(),
        team_id=team.id,
        user_id=user.id,
        start_lat=user.home_lat,
        start_lng=user.home_lng,
        start_source=user.default_start.value,
    )
    db.add(member)

    # 10. Create or reactivate leader Registration
    if existing_reg:
        token = generate_ticket_token(user.id, event.id, existing_reg.id)
        existing_reg.status = RegistrationStatus.REGISTERED
        existing_reg.team_id = team.id
        existing_reg.ticket_token = token
    else:
        new_reg_id = uuid.uuid4()
        token = generate_ticket_token(user.id, event.id, new_reg_id)
        reg = Registration(
            id=new_reg_id,
            event_id=event.id,
            user_id=user.id,
            team_id=team.id,
            status=RegistrationStatus.REGISTERED,
            ticket_token=token,
        )
        db.add(reg)

    db.commit()
    db.refresh(team)

    queue_notification(user, "team_created", event, {"team_id": str(team.id)})

    return build_team_response(db, team, event)


def get_invite_summary(db: Session, code: str) -> InviteSummaryResponse:
    """Fetch public invite preview. Raises INVITE_INVALID if team is closed or confirmed."""
    team = db.execute(select(Team).where(Team.invite_code == code.strip())).scalar_one_or_none()
    if not team or team.status in (TeamStatus.CANCELLED, TeamStatus.CONFIRMED):
        raise AppError(
            "INVITE_INVALID", "Invite code is invalid or team is no longer accepting members"
        )

    event = db.get(Event, team.event_id)
    if (
        not event
        or event.status != EventStatus.PUBLISHED
        or datetime.now(timezone.utc) >= event.registration_deadline
    ):
        raise AppError("INVITE_INVALID", "Event registration has closed")

    member_count = (
        db.scalar(select(func.count(TeamMember.id)).where(TeamMember.team_id == team.id)) or 0
    )
    spots_left = max(0, event.team_max - member_count)

    ev_summary = EventSummary(
        id=event.id,
        title=event.title,
        start_at=event.start_at,
        end_at=event.end_at,
        mode=event.mode,
        venue_name=event.venue_name,
        banner_url=event.banner_url,
        status=event.status,
    )

    return InviteSummaryResponse(
        event=ev_summary,
        team_id=team.id,
        team_name=team.name,
        invite_code=team.invite_code,
        status=team.status,
        member_count=member_count,
        spots_left=spots_left,
    )


def join_team(db: Session, user: User, req: JoinTeamRequest) -> TeamResponse:
    """Join an existing team by invite code with atomic capacity checks."""
    now = datetime.now(timezone.utc)

    # 1. Lock team row
    team = (
        db.execute(
            select(Team)
            .where(Team.invite_code == req.invite_code.strip())
            .with_for_update()
        )
        .scalar_one_or_none()
    )
    if not team or team.status != TeamStatus.FORMING:
        raise AppError(
            "INVITE_INVALID", "Invite code is invalid or team is no longer accepting members"
        )

    # 2. Lock event row
    event = (
        db.execute(select(Event).where(Event.id == team.event_id).with_for_update())
        .scalar_one_or_none()
    )
    if not event or event.status != EventStatus.PUBLISHED or now >= event.registration_deadline:
        raise AppError("EVENT_CLOSED", "Event registration has closed")

    # 3. Check team max capacity
    current_members = (
        db.scalar(select(func.count(TeamMember.id)).where(TeamMember.team_id == team.id)) or 0
    )
    if current_members >= event.team_max:
        raise AppError("TEAM_FULL", "Team is already full")

    # 4. Check event seat limit
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

    # 5. Check eligibility
    if not is_eligible(user, event):
        raise AppError("NOT_ELIGIBLE", "You are not eligible for this event")

    # 6. Check already registered
    existing_reg = db.execute(
        select(Registration).where(
            Registration.event_id == event.id, Registration.user_id == user.id
        )
    ).scalar_one_or_none()
    if existing_reg and existing_reg.status != RegistrationStatus.CANCELLED:
        raise AppError("ALREADY_REGISTERED", "You are already registered for this event")

    # 7. Time clash
    check_time_clash(db, user, event, req.acknowledge_clash)

    # 8. Add TeamMember
    member = TeamMember(
        id=uuid.uuid4(),
        team_id=team.id,
        user_id=user.id,
        start_lat=user.home_lat,
        start_lng=user.home_lng,
        start_source=user.default_start.value,
    )
    db.add(member)

    # 9. Add or reactivate Registration
    if existing_reg:
        token = generate_ticket_token(user.id, event.id, existing_reg.id)
        existing_reg.status = RegistrationStatus.REGISTERED
        existing_reg.team_id = team.id
        existing_reg.ticket_token = token
    else:
        new_reg_id = uuid.uuid4()
        token = generate_ticket_token(user.id, event.id, new_reg_id)
        reg = Registration(
            id=new_reg_id,
            event_id=event.id,
            user_id=user.id,
            team_id=team.id,
            status=RegistrationStatus.REGISTERED,
            ticket_token=token,
        )
        db.add(reg)

    db.commit()
    db.refresh(team)

    queue_team_notification(
        team, "team_member_joined", event, exclude_user_id=user.id
    )

    return build_team_response(db, team, event)


def get_team_detail(db: Session, team_id: uuid.UUID, user: User) -> TeamResponse:
    """Get team details. Restricted to members, event organizer, or admin."""
    team = db.get(Team, team_id)
    if not team:
        raise AppError("NOT_FOUND", "Team not found")

    event = db.get(Event, team.event_id)

    # Permissions: member or organizer or admin
    is_member = (
        db.scalar(
            select(func.count(TeamMember.id)).where(
                TeamMember.team_id == team.id, TeamMember.user_id == user.id
            )
        )
        or 0
    ) > 0
    is_organizer = event and event.organizer_id == user.id
    is_admin = user.role == UserRole.ADMIN

    if not (is_member or is_organizer or is_admin):
        raise AppError("FORBIDDEN", "Not authorized to view this team")

    return build_team_response(db, team, event)


def confirm_team(db: Session, team_id: uuid.UUID, user: User) -> TeamResponse:
    """Lock team members and transition status to CONFIRMED (leader only)."""
    team = db.get(Team, team_id)
    if not team:
        raise AppError("NOT_FOUND", "Team not found")

    if team.leader_id != user.id and user.role != UserRole.ADMIN:
        raise AppError("FORBIDDEN", "Only the team leader can confirm the team")

    if team.status == TeamStatus.CONFIRMED:
        return build_team_response(db, team)

    if team.status == TeamStatus.CANCELLED:
        raise AppError("VALIDATION_ERROR", "Cannot confirm a cancelled team")

    event = db.get(Event, team.event_id)
    if not event:
        raise AppError("NOT_FOUND", "Event not found")

    member_count = (
        db.scalar(select(func.count(TeamMember.id)).where(TeamMember.team_id == team.id)) or 0
    )

    if member_count < event.team_min:
        raise AppError(
            "TEAM_TOO_SMALL", f"Team needs at least {event.team_min} members to confirm"
        )
    if member_count > event.team_max:
        raise AppError(
            "TEAM_FULL", f"Team cannot exceed {event.team_max} members"
        )

    team.status = TeamStatus.CONFIRMED
    db.commit()
    db.refresh(team)

    on_team_confirmed(team)
    queue_team_notification(team, "team_confirmed", event)

    return build_team_response(db, team, event)


def update_member_start_point(
    db: Session,
    team_id: uuid.UUID,
    user: User,
    req: TeamMemberStartPointRequest,
) -> TeamResponse:
    """Update a member's origin coordinates and trigger re-plan if confirmed."""
    team = db.get(Team, team_id)
    if not team:
        raise AppError("NOT_FOUND", "Team not found")

    member = db.execute(
        select(TeamMember).where(TeamMember.team_id == team.id, TeamMember.user_id == user.id)
    ).scalar_one_or_none()

    if not member:
        raise AppError("FORBIDDEN", "You are not a member of this team")

    member.start_lat = req.lat
    member.start_lng = req.lng
    if req.source:
        member.start_source = req.source
    if req.travel_pref:
        member.travel_pref = req.travel_pref

    if team.status == TeamStatus.CONFIRMED:
        team.plan_version += 1
        on_team_changed(team)

    db.commit()
    db.refresh(team)

    return build_team_response(db, team)


def remove_team_member(
    db: Session,
    team_id: uuid.UUID,
    target_user_id: uuid.UUID,
    current_user: User,
) -> dict:
    """Remove member from team. Handles leader handover, minimum size checks, and seat release."""
    team = db.get(Team, team_id)
    if not team:
        raise AppError("NOT_FOUND", "Team not found")

    # Permission check: target removing self, leader removing member, or admin
    is_self = current_user.id == target_user_id
    is_leader = current_user.id == team.leader_id
    is_admin = current_user.role == UserRole.ADMIN

    if not (is_self or is_leader or is_admin):
        raise AppError("FORBIDDEN", "Not authorized to remove this member")

    member = db.execute(
        select(TeamMember).where(
            TeamMember.team_id == team.id, TeamMember.user_id == target_user_id
        )
    ).scalar_one_or_none()
    if not member:
        raise AppError("NOT_FOUND", "Member not found in team")

    # Cancel registration and unbind team
    reg = db.execute(
        select(Registration).where(
            Registration.event_id == team.event_id, Registration.user_id == target_user_id
        )
    ).scalar_one_or_none()
    if reg:
        reg.status = RegistrationStatus.CANCELLED
        reg.team_id = None

    db.delete(member)
    db.flush()

    # Query remaining members ordered by join time
    remaining = (
        db.execute(
            select(TeamMember)
            .where(TeamMember.team_id == team.id)
            .order_by(TeamMember.created_at.asc())
        )
        .scalars()
        .all()
    )

    event = db.get(Event, team.event_id)

    if not remaining:
        team.status = TeamStatus.CANCELLED
    else:
        # Pass leadership if leader was removed
        if team.leader_id == target_user_id:
            team.leader_id = remaining[0].user_id

        # If confirmed team drops below team_min, downgrade to FORMING
        if team.status == TeamStatus.CONFIRMED and event:
            if len(remaining) < event.team_min:
                team.status = TeamStatus.FORMING
                queue_team_notification(team, "team_dropped_below_min", event)
            else:
                team.plan_version += 1
                on_team_changed(team)

    db.commit()
    return {"status": "removed"}
