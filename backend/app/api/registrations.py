import uuid
from fastapi import APIRouter, Depends, Query, Response, status
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.security import get_current_user
from app.models.user import User
from app.schemas.registration import (
    RegisterSoloRequest,
    RegistrationListResponse,
    RegistrationResponse,
)
from app.services.registration import (
    cancel_registration,
    list_my_registrations,
    register_solo,
)

router = APIRouter(tags=["registrations"])


@router.post(
    "/events/{id}/register",
    response_model=RegistrationResponse,
    status_code=status.HTTP_201_CREATED,
)
def register_solo_event(
    id: uuid.UUID,
    req: RegisterSoloRequest = RegisterSoloRequest(),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Register the authenticated user for an event as solo participant."""
    return register_solo(db, current_user, id, req)


@router.delete("/registrations/{id}", status_code=status.HTTP_204_NO_CONTENT)
def cancel_my_registration(
    id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Cancel a registration and free the seat / team spot."""
    cancel_registration(db, current_user, id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/me/registrations", response_model=RegistrationListResponse)
def get_my_registrations(
    filter_type: str | None = Query(None, alias="filter", pattern="^(upcoming|past)$"),
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Fetch current user's registrations with event summaries, team summaries, and status filter."""
    items, total = list_my_registrations(
        db, current_user, filter_type=filter_type, page=page, page_size=page_size
    )
    return RegistrationListResponse(items=items, total=total, page=page, page_size=page_size)
