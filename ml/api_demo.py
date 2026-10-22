"""DEMO / TEST SERVER ONLY — NOT THE PRODUCTION BACKEND.

This FastAPI application is a lightweight local demonstration and test harness for
the EventRoute ML package. The production backend is maintained separately by the
backend team in `backend/app`.

Do NOT deploy this server to production. Use it to:
  1. Verify ML buffer inference and recommendation scoring interactively.
  2. Validate payload contracts against mock Android/Web data.
  3. Run offline and automated API tests without databases or external services.
"""
from __future__ import annotations

from datetime import datetime
from typing import Any

import sklearn
from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

from eventroute_ml.adapters import (
    event_from_json,
    route_to_risk,
    user_from_json,
)
from eventroute_ml.buffer import get_predictor, risk_extra_detail
from eventroute_ml.config import DEFAULT_TZ
from eventroute_ml.recommend import recommend

app = FastAPI(
    title="EventRoute ML Demo API",
    description="Demo / Test harness for EventRoute delay risk buffer and recommendation algorithms.",
    version="1.0.0",
)


# ---------------------------------------------------------------------------
# Contract Error Handling: { "code", "message", "details"? }
# ---------------------------------------------------------------------------
@app.exception_handler(RequestValidationError)
async def validation_exception_handler(
    request: Request, exc: RequestValidationError
):
    errors = exc.errors()
    msg = "; ".join(
        f"{' -> '.join(str(loc) for loc in err.get('loc', []))}: {err.get('msg')}"
        for err in errors
    )
    return JSONResponse(
        status_code=422,
        content={
            "code": "VALIDATION_ERROR",
            "message": f"Invalid request payload: {msg}",
            "details": errors,
        },
    )


@app.exception_handler(HTTPException)
async def http_exception_handler(request: Request, exc: HTTPException):
    code = getattr(
        exc, "code", "VALIDATION_ERROR" if exc.status_code == 422 else "ERROR"
    )
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": code, "message": str(exc.detail)},
    )


# ---------------------------------------------------------------------------
# Pydantic Schemas
# ---------------------------------------------------------------------------
class BufferRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    mode: str = Field(..., description="Travel mode (e.g. metro, bus, drive, walk, bike)")
    depart_at: datetime | str = Field(
        ..., description="Planned departure time in ISO-8601 format"
    )
    distance_m: float = Field(..., ge=0.0, description="Trip distance in meters")
    transfers: int = Field(0, ge=0, description="Number of transit transfers")
    walking_share: float = Field(
        0.0, ge=0.0, le=1.0, description="Fraction of duration spent walking (0.0 to 1.0)"
    )
    tz: str = Field(DEFAULT_TZ, description="Local timezone string")


class RouteBufferRequest(BaseModel):
    model_config = ConfigDict(extra="allow")

    route: dict[str, Any] | None = Field(
        None, description="Route dictionary in common route format"
    )
    depart_at: datetime | str | None = Field(
        None, description="Planned departure time in ISO-8601 format"
    )
    tz: str = Field(DEFAULT_TZ, description="Local timezone string")


class RecommendRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    user: dict[str, Any] | list[dict[str, Any]] | None = Field(
        None, description="User profile dict or list with 1 user dict"
    )
    events: list[dict[str, Any]] = Field(
        default_factory=list, description="List of candidate events"
    )
    now: datetime | str | None = Field(
        None, description="Current evaluation timestamp in ISO-8601 format"
    )
    limit: int | None = Field(
        10, ge=1, le=100, description="Max recommendations to return"
    )


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@app.get("/ml/health")
def get_health():
    """Health status and model metadata for diagnostics."""
    predictor = get_predictor()
    model_loaded = predictor.model is not None
    source = "model" if model_loaded else "rules"
    return {
        "status": "ok",
        "model_loaded": model_loaded,
        "version": predictor.version,
        "source": source,
        "sklearn_version": sklearn.__version__,
    }


@app.post("/ml/buffer")
def post_buffer(req: BufferRequest):
    """Calculate delay risk extra buffer from raw features."""
    detail = risk_extra_detail(
        mode=req.mode,
        depart_at=req.depart_at,
        distance_m=req.distance_m,
        transfers=req.transfers,
        walking_share=req.walking_share,
        tz=req.tz,
    )
    detail["risk_extra_min"] = detail["minutes"]
    return detail


@app.post("/ml/buffer/route")
def post_buffer_route(req: RouteBufferRequest):
    """Calculate delay risk extra buffer from a route in common route format.

    Supports either:
      1. {"route": {<common route format>}, "depart_at": "..."}
      2. Direct route dictionary payload with optional top-level "depart_at"
    """
    if req.route is not None:
        route_data = req.route
    else:
        # Extra fields from top-level request body
        dump = req.model_dump(exclude={"route", "depart_at", "tz"})
        route_data = dump if dump else {}

    depart_at = req.depart_at or route_data.get("departure_time") or route_data.get("depart_at")
    risk = route_to_risk(route_data, depart_at=depart_at, tz=req.tz)
    return risk


@app.post("/ml/recommend")
def post_recommend(req: RecommendRequest):
    """Compute personalized recommendations from user profile and candidate events."""
    user_payload: dict[str, Any] = {}
    if isinstance(req.user, list) and req.user:
        user_payload = req.user[0] if isinstance(req.user[0], dict) else {}
    elif isinstance(req.user, dict):
        user_payload = req.user

    user_profile = user_from_json(user_payload)
    event_infos = [event_from_json(e) for e in req.events if isinstance(e, dict)]

    now_dt = None
    if isinstance(req.now, datetime):
        now_dt = req.now
    elif isinstance(req.now, str) and req.now.strip():
        try:
            now_dt = datetime.fromisoformat(req.now.strip().replace("Z", "+00:00"))
        except Exception:
            now_dt = None

    ranked = recommend(user=user_profile, events=event_infos, now=now_dt)
    if req.limit:
        ranked = ranked[: req.limit]

    items = [
        {
            "event_id": r.event_id,
            "score": round(r.score, 4),
            "reason": r.reason,
            "components": r.components,
        }
        for r in ranked
    ]

    return {
        "items": items,
        "total": len(items),
    }


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("api_demo:app", host="127.0.0.1", port=8000, reload=True)
