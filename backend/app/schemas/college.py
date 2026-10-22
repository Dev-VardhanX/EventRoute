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


class CreateCollegeRequest(BaseModel):
    name: str
    city: str
    lat: float
    lng: float
    email_domain: str | None = None
    verified: bool = False
    is_demo: bool = False


class UpdateCollegeRequest(BaseModel):
    name: str | None = None
    city: str | None = None
    lat: float | None = None
    lng: float | None = None
    email_domain: str | None = None
    verified: bool | None = None
    is_demo: bool | None = None
