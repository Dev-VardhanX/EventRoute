import uuid
from datetime import datetime
from pydantic import BaseModel, ConfigDict

from app.models.enums import DefaultStart, UserRole


class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    email: str
    email_verified: bool
    role: UserRole
    college_id: uuid.UUID | None = None
    interests: list[str] = []
    home_lat: float | None = None
    home_lng: float | None = None
    home_label: str | None = None
    default_start: DefaultStart
    is_active: bool
    created_at: datetime


class UpdateMeRequest(BaseModel):
    name: str | None = None
    interests: list[str] | None = None
    college_id: uuid.UUID | None = None
    home_lat: float | None = None
    home_lng: float | None = None
    home_label: str | None = None


class UpdateStartPointRequest(BaseModel):
    default_start: DefaultStart  # college | home | custom
    lat: float | None = None
    lng: float | None = None
    label: str | None = None


class DeviceTokenRequest(BaseModel):
    token: str
    platform: str = "android"
