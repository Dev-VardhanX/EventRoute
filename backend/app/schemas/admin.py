import uuid
from datetime import datetime
from pydantic import BaseModel, ConfigDict

from app.models.enums import OrganizerStatus
from app.schemas.user import UserResponse


class OrganizerProfileResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    user_id: uuid.UUID
    organization_name: str
    college_id: uuid.UUID | None = None
    status: OrganizerStatus
    reviewed_by: uuid.UUID | None = None
    created_at: datetime
    user: UserResponse | None = None


class OrganizerListResponse(BaseModel):
    items: list[OrganizerProfileResponse]
    total: int
    page: int
    page_size: int
