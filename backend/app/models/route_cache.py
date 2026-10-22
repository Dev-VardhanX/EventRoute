from datetime import datetime
from sqlalchemy import DateTime, Index, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, IdTimestampMixin


class RouteCache(Base, IdTimestampMixin):
    __tablename__ = "route_cache"
    __table_args__ = (
        UniqueConstraint("cache_key", name="uq_route_cache_key"),
        Index("ix_route_cache_expires_at", "expires_at"),
    )

    cache_key: Mapped[str] = mapped_column(String(255), nullable=False)
    response_json: Mapped[dict] = mapped_column(JSONB, nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
