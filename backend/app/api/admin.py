import uuid
from fastapi import APIRouter, Depends, Query, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.errors import AppError
from app.core.security import require_role
from app.models.college import College
from app.models.enums import OrganizerStatus, UserRole
from app.models.organizer_profile import OrganizerProfile
from app.models.user import User
from app.schemas.admin import OrganizerListResponse, OrganizerProfileResponse
from app.schemas.college import (
    CollegeListResponse,
    CollegeResponse,
    CreateCollegeRequest,
    UpdateCollegeRequest,
)
from app.schemas.user import UserResponse

router = APIRouter(
    prefix="/admin",
    tags=["admin"],
    dependencies=[Depends(require_role(UserRole.ADMIN))],
)


# --- Organizer Moderation ---

@router.get("/organizers", response_model=OrganizerListResponse)
def list_organizers(
    status: OrganizerStatus | None = OrganizerStatus.PENDING,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
):
    """List organizer profiles filtered by status with user details (admin only)."""
    base_query = select(OrganizerProfile)
    if status is not None:
        base_query = base_query.where(OrganizerProfile.status == status)

    total = db.scalar(select(func.count()).select_from(base_query.subquery())) or 0
    profiles = (
        db.execute(
            base_query.order_by(OrganizerProfile.created_at.desc())
            .offset((page - 1) * page_size)
            .limit(page_size)
        )
        .scalars()
        .all()
    )

    items: list[OrganizerProfileResponse] = []
    for p in profiles:
        user = db.get(User, p.user_id)
        user_resp = UserResponse.model_validate(user) if user else None
        items.append(
            OrganizerProfileResponse(
                id=p.id,
                user_id=p.user_id,
                organization_name=p.organization_name,
                college_id=p.college_id,
                status=p.status,
                reviewed_by=p.reviewed_by,
                created_at=p.created_at,
                user=user_resp,
            )
        )

    return OrganizerListResponse(
        items=items,
        total=total,
        page=page,
        page_size=page_size,
    )


def _get_profile_by_id_or_user_id(db: Session, target_id: uuid.UUID) -> OrganizerProfile:
    profile = db.get(OrganizerProfile, target_id)
    if not profile:
        # Fallback: check if target_id is user_id
        profile = db.execute(
            select(OrganizerProfile).where(OrganizerProfile.user_id == target_id)
        ).scalar_one_or_none()
    if not profile:
        raise AppError("NOT_FOUND", "Organizer profile not found")
    return profile


@router.post("/organizers/{id}/approve", response_model=OrganizerProfileResponse)
def approve_organizer(
    id: uuid.UUID,
    current_user: User = Depends(require_role(UserRole.ADMIN)),
    db: Session = Depends(get_db),
):
    """Approve an organizer application (admin only)."""
    profile = _get_profile_by_id_or_user_id(db, id)
    profile.status = OrganizerStatus.APPROVED
    profile.reviewed_by = current_user.id
    db.commit()
    db.refresh(profile)

    user = db.get(User, profile.user_id)
    return OrganizerProfileResponse(
        id=profile.id,
        user_id=profile.user_id,
        organization_name=profile.organization_name,
        college_id=profile.college_id,
        status=profile.status,
        reviewed_by=profile.reviewed_by,
        created_at=profile.created_at,
        user=UserResponse.model_validate(user) if user else None,
    )


@router.post("/organizers/{id}/reject", response_model=OrganizerProfileResponse)
def reject_organizer(
    id: uuid.UUID,
    current_user: User = Depends(require_role(UserRole.ADMIN)),
    db: Session = Depends(get_db),
):
    """Reject an organizer application (admin only)."""
    profile = _get_profile_by_id_or_user_id(db, id)
    profile.status = OrganizerStatus.REJECTED
    profile.reviewed_by = current_user.id
    db.commit()
    db.refresh(profile)

    user = db.get(User, profile.user_id)
    return OrganizerProfileResponse(
        id=profile.id,
        user_id=profile.user_id,
        organization_name=profile.organization_name,
        college_id=profile.college_id,
        status=profile.status,
        reviewed_by=profile.reviewed_by,
        created_at=profile.created_at,
        user=UserResponse.model_validate(user) if user else None,
    )


# --- College Management ---

@router.get("/colleges", response_model=CollegeListResponse)
def list_colleges_admin(
    search: str | None = None,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
):
    """List colleges for admin management."""
    base_query = select(College)
    if search and search.strip():
        term = f"%{search.strip()}%"
        base_query = base_query.where(
            College.name.ilike(term) | College.city.ilike(term) | College.email_domain.ilike(term)
        )

    total = db.scalar(select(func.count()).select_from(base_query.subquery())) or 0
    items = (
        db.execute(
            base_query.order_by(College.name)
            .offset((page - 1) * page_size)
            .limit(page_size)
        )
        .scalars()
        .all()
    )

    return CollegeListResponse(
        items=list(items),
        total=total,
        page=page,
        page_size=page_size,
    )


@router.post("/colleges", response_model=CollegeResponse, status_code=status.HTTP_201_CREATED)
def create_college(
    req: CreateCollegeRequest,
    db: Session = Depends(get_db),
):
    """Create a new college (admin only)."""
    if req.email_domain:
        existing = db.execute(
            select(College).where(College.email_domain == req.email_domain.strip().lower())
        ).scalar_one_or_none()
        if existing:
            raise AppError("VALIDATION_ERROR", "College with this email domain already exists")

    college = College(
        name=req.name.strip(),
        city=req.city.strip(),
        lat=req.lat,
        lng=req.lng,
        email_domain=req.email_domain.strip().lower() if req.email_domain else None,
        verified=req.verified,
        is_demo=req.is_demo,
    )
    db.add(college)
    db.commit()
    db.refresh(college)
    return college


@router.patch("/colleges/{id}", response_model=CollegeResponse)
def update_college(
    id: uuid.UUID,
    req: UpdateCollegeRequest,
    db: Session = Depends(get_db),
):
    """Update or verify a college (admin only)."""
    college = db.get(College, id)
    if not college:
        raise AppError("NOT_FOUND", "College not found")

    if req.email_domain is not None:
        clean_domain = req.email_domain.strip().lower() if req.email_domain else None
        if clean_domain and clean_domain != college.email_domain:
            existing = db.execute(
                select(College).where(College.email_domain == clean_domain)
            ).scalar_one_or_none()
            if existing:
                raise AppError("VALIDATION_ERROR", "Email domain already in use by another college")
        college.email_domain = clean_domain

    if req.name is not None:
        college.name = req.name.strip()
    if req.city is not None:
        college.city = req.city.strip()
    if req.lat is not None:
        college.lat = req.lat
    if req.lng is not None:
        college.lng = req.lng
    if req.verified is not None:
        college.verified = req.verified
    if req.is_demo is not None:
        college.is_demo = req.is_demo

    db.commit()
    db.refresh(college)
    return college
