import uuid
from fastapi import APIRouter, Depends, status
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.security import get_current_user
from app.models.user import User
from app.schemas.team import (
    CreateTeamRequest,
    InviteSummaryResponse,
    JoinTeamRequest,
    TeamMemberStartPointRequest,
    TeamResponse,
)
from app.services.teams import (
    confirm_team,
    create_team,
    get_invite_summary,
    get_team_detail,
    join_team,
    remove_team_member,
    update_member_start_point,
)

router = APIRouter(tags=["teams"])


@router.post(
    "/events/{id}/teams",
    response_model=TeamResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_event_team(
    id: uuid.UUID,
    req: CreateTeamRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Create a new team for an event, registering the creator as team leader."""
    return create_team(db, current_user, id, req)


@router.get("/invite/{code}", response_model=InviteSummaryResponse)
def get_invite(
    code: str,
    db: Session = Depends(get_db),
):
    """Public invite preview returning event and team summary."""
    return get_invite_summary(db, code)


@router.post("/teams/join", response_model=TeamResponse)
def join_event_team(
    req: JoinTeamRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Join an existing team by invite code."""
    return join_team(db, current_user, req)


@router.get("/teams/{id}", response_model=TeamResponse)
def get_team(
    id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Fetch team details, member roster, and size bounds (members, organizer, or admin)."""
    return get_team_detail(db, id, current_user)


@router.post("/teams/{id}/confirm", response_model=TeamResponse)
def confirm_event_team(
    id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Confirm a team within valid size bounds, locking further joins (leader only)."""
    return confirm_team(db, id, current_user)


@router.put("/teams/{id}/me/start-point", response_model=TeamResponse)
def set_member_start_point(
    id: uuid.UUID,
    req: TeamMemberStartPointRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Update authenticated member's origin coordinates for travel planning."""
    return update_member_start_point(db, id, current_user, req)


@router.delete("/teams/{id}/members/{user_id}")
def remove_member(
    id: uuid.UUID,
    user_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Remove member from team (self, leader, or admin)."""
    return remove_team_member(db, id, user_id, current_user)
