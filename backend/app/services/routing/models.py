from datetime import datetime
from typing import Any
from pydantic import BaseModel, ConfigDict, Field


class TransitInfo(BaseModel):
    line_name: str | None = None
    short_name: str | None = None
    vehicle_type: str | None = None
    headsign: str | None = None
    stops_count: int = 0
    agency: str | None = None
    color: str | None = None


class Step(BaseModel):
    instruction: str
    distance_m: int
    duration_s: int
    mode: str


class Leg(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    mode: str  # walk | bus | metro | train | tram | bike | drive
    from_loc: str = Field(..., alias="from")
    to_loc: str = Field(..., alias="to")
    start_at: datetime | None = None
    end_at: datetime | None = None
    duration_s: int
    distance_m: int
    polyline: str  # encoded polyline
    steps: list[Step] = []
    transit: TransitInfo | None = None


class Route(BaseModel):
    id: str
    summary: str
    duration_s: int
    distance_m: int
    departure_at: datetime
    arrival_at: datetime
    fare: dict[str, Any] | None = None
    transfers: int = 0
    walking_s: int = 0
    legs: list[Leg] = []


class RouteQueryResponse(BaseModel):
    routes: list[Route]
    provider: str
    cached: bool = False
