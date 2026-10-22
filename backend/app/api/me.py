from fastapi import APIRouter, Depends, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.security import get_current_user
from app.models.device_token import DeviceToken
from app.models.user import User
from app.schemas.user import (
    DeviceTokenRequest,
    UpdateMeRequest,
    UpdateStartPointRequest,
    UserResponse,
)

router = APIRouter(tags=["me"])


@router.get("/me", response_model=UserResponse)
def get_me(current_user: User = Depends(get_current_user)):
    """Fetch current authenticated user profile."""
    return current_user


@router.patch("/me", response_model=UserResponse)
def patch_me(
    req: UpdateMeRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Update profile fields (name, interests, college_id, home coordinates)."""
    if req.name is not None:
        current_user.name = req.name.strip()
    if req.interests is not None:
        current_user.interests = req.interests
    if req.college_id is not None:
        current_user.college_id = req.college_id
    if req.home_lat is not None:
        current_user.home_lat = req.home_lat
    if req.home_lng is not None:
        current_user.home_lng = req.home_lng
    if req.home_label is not None:
        current_user.home_label = req.home_label

    db.commit()
    db.refresh(current_user)
    return current_user


@router.put("/me/start-point", response_model=UserResponse)
def update_start_point(
    req: UpdateStartPointRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Set default departure origin (college, home, or custom coords)."""
    current_user.default_start = req.default_start
    if req.lat is not None:
        current_user.home_lat = req.lat
    if req.lng is not None:
        current_user.home_lng = req.lng
    if req.label is not None:
        current_user.home_label = req.label

    db.commit()
    db.refresh(current_user)
    return current_user


@router.post("/me/device-token", status_code=status.HTTP_200_OK)
def register_device_token(
    req: DeviceTokenRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Register or refresh FCM push device token."""
    existing = db.execute(
        select(DeviceToken).where(
            DeviceToken.user_id == current_user.id, DeviceToken.token == req.token
        )
    ).scalars().first()

    if existing:
        existing.platform = req.platform
        existing.is_active = True
    else:
        new_token = DeviceToken(
            user_id=current_user.id,
            token=req.token,
            platform=req.platform,
            is_active=True,
        )
        db.add(new_token)

    db.commit()
    return {"status": "ok"}
