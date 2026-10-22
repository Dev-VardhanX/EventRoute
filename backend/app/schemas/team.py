import uuid
from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import JourneyStatus, TeamStatus
from app.schemas.registration import EventSummary


class CreateTeamRequest(BaseModel):
    name: str = Field(..., min_length=2, max_length=100)
    acknowledge_clash: bool = False


class JoinTeamRequest(BaseModel):
    invite_code: str = Field(..., min_length=4, max_length=32)
    acknowledge_clash: bool = False


class TeamMemberResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    team_id: uuid.UUID
    user_id: uuid.UUID
    user_name: str
    user_email: str
    start_lat: float | None = None
    start_lng: float | None = None
    start_source: str | None = None
    travel_pref: str | None = None
    journey_status: JourneyStatus
    created_at: datetime


class TeamResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    event_id: uuid.UUID
    name: str
    leader_id: uuid.UUID
    invite_code: str
    invite_link: str | None = None
    status: TeamStatus
    plan_version: int = 1
    planned_arrival_at: datetime | None = None
    members: list[TeamMemberResponse] = []
    member_count: int = 0
    team_min: int = 1
    team_max: int = 1
    spots_left: int = 0
    created_at: datetime


class InviteSummaryResponse(BaseModel):
    event: EventSummary
    team_id: uuid.UUID
    team_name: str
    invite_code: str
    status: TeamStatus
    member_count: int
    spots_left: int


class TeamMemberStartPointRequest(BaseModel):
    lat: float
    lng: float
    source: str | None = "custom"
    travel_pref: str | None = None
