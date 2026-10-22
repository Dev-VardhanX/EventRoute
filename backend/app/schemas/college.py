import uuid
from pydantic import BaseModel, ConfigDict


class CollegeResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    name: str
    city: str
    lat: float
    lng: float
    email_domain: str | None = None
    verified: bool
    is_demo: bool


class CollegeListResponse(BaseModel):
    items: list[CollegeResponse]
    total: int
    page: int
    page_size: int
