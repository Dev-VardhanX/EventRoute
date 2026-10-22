import uuid
from datetime import datetime
from pydantic import BaseModel, ConfigDict

from app.models.enums import EventMode, EventStatus, RegistrationStatus, TeamStatus


class RegisterSoloRequest(BaseModel):
    acknowledge_clash: bool = False


class EventSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    title: str
    start_at: datetime
    end_at: datetime | None = None
    mode: EventMode
    venue_name: str | None = None
    banner_url: str | None = None
    status: EventStatus


class TeamSummary(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    status: TeamStatus
    invite_code: str
    member_count: int = 1


class RegistrationResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    event_id: uuid.UUID
    user_id: uuid.UUID
    team_id: uuid.UUID | None = None
    status: RegistrationStatus
    ticket_token: str | None = None
    journey_plan: dict | None = None
    created_at: datetime
    event: EventSummary | None = None
    team: TeamSummary | None = None


class RegistrationListResponse(BaseModel):
    items: list[RegistrationResponse]
    total: int
    page: int
    page_size: int


class CheckInRequest(BaseModel):
    ticket_token: str


class CheckInResponse(BaseModel):
    status: str = "checked_in"
    registration_id: uuid.UUID
    user_id: uuid.UUID
    user_name: str | None = None
    event_id: uuid.UUID


class OrganizerRegistrationItem(BaseModel):
    id: uuid.UUID
    user_id: uuid.UUID
    user_name: str
    user_email: str
    team_id: uuid.UUID | None = None
    team_name: str | None = None
    status: RegistrationStatus
    ticket_token: str | None = None
    created_at: datetime


class OrganizerRegistrationListResponse(BaseModel):
    items: list[OrganizerRegistrationItem]
    total: int
    page: int
    page_size: int
