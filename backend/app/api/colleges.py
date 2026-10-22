from fastapi import APIRouter, Depends, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.models.college import College
from app.schemas.college import CollegeListResponse

router = APIRouter(tags=["colleges"])


@router.get("/colleges", response_model=CollegeListResponse)
def list_colleges(
    search: str | None = None,
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    db: Session = Depends(get_db),
):
    """List colleges with optional text search and pagination (public endpoint)."""
    base_query = select(College)

    if search and search.strip():
        term = f"%{search.strip()}%"
        base_query = base_query.where(
            College.name.ilike(term) | College.city.ilike(term)
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
