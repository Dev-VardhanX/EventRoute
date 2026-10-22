import uuid
from datetime import datetime
from pydantic import BaseModel, ConfigDict, Field

from app.models.enums import EventMode, EventStatus, ParticipationType


class CreateEventRequest(BaseModel):
    title: str = Field(..., min_length=1, max_length=255)
    description: str | None = None
    category: str = Field(..., min_length=1, max_length=100)
    tags: list[str] = []
    banner_url: str | None = None
    mode: EventMode = EventMode.OFFLINE
    start_at: datetime
    end_at: datetime | None = None
    check_in_at: datetime
    registration_deadline: datetime
    timezone: str = "Asia/Kolkata"
    venue_name: str | None = None
    venue_lat: float | None = None
    venue_lng: float | None = None
    participation_type: ParticipationType = ParticipationType.SOLO
    team_min: int = 1
    team_max: int = 1
    seat_limit: int | None = None
    fee_amount: float = 0.0
    eligibility: str | None = None
    college_id: uuid.UUID | None = None


class UpdateEventRequest(BaseModel):
    title: str | None = None
    description: str | None = None
    category: str | None = None
    tags: list[str] | None = None
    banner_url: str | None = None
    mode: EventMode | None = None
    start_at: datetime | None = None
    end_at: datetime | None = None
    check_in_at: datetime | None = None
    registration_deadline: datetime | None = None
    timezone: str | None = None
    venue_name: str | None = None
    venue_lat: float | None = None
    venue_lng: float | None = None
    participation_type: ParticipationType | None = None
    team_min: int | None = None
    team_max: int | None = None
    seat_limit: int | None = None
    fee_amount: float | None = None
    eligibility: str | None = None
    college_id: uuid.UUID | None = None


class EventResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    organizer_id: uuid.UUID
    college_id: uuid.UUID | None = None
    title: str
    description: str | None = None
    category: str
    tags: list[str] = []
    banner_url: str | None = None
    mode: EventMode
    start_at: datetime
    end_at: datetime | None = None
    check_in_at: datetime
    registration_deadline: datetime
    timezone: str
    venue_name: str | None = None
    venue_lat: float | None = None
    venue_lng: float | None = None
    participation_type: ParticipationType
    team_min: int
    team_max: int
    seat_limit: int | None = None
    fee_amount: float
    eligibility: str | None = None
    status: EventStatus
    is_demo: bool
    created_at: datetime

    registered_count: int = 0
    seats_left: int | None = None
    distance_km: float | None = None


class EventListResponse(BaseModel):
    items: list[EventResponse]
    total: int
    page: int
    page_size: int


class TravelEstimatesOrigin(BaseModel):
    lat: float
    lng: float


class TravelEstimatesRequest(BaseModel):
    origin: TravelEstimatesOrigin | None = None
    lat: float | None = None
    lng: float | None = None
    event_ids: list[uuid.UUID]


class EventTravelEstimate(BaseModel):
    event_id: uuid.UUID
    distance_km: float
    duration_min: int
    source: str = "estimate"


class TravelEstimatesResponse(BaseModel):
    items: list[EventTravelEstimate]
