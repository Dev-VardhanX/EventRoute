import uuid
from datetime import datetime
from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    Enum as SQLEnum,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
)
from sqlalchemy.dialects.postgresql import ARRAY, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, IdTimestampMixin
from app.models.enums import EventMode, EventStatus, ParticipationType


class Event(Base, IdTimestampMixin):
    __tablename__ = "events"
    __table_args__ = (
        CheckConstraint("team_min <= team_max", name="ck_events_team_min_lte_team_max"),
        CheckConstraint("check_in_at <= start_at", name="ck_events_check_in_at_lte_start_at"),
        CheckConstraint(
            "registration_deadline <= check_in_at",
            name="ck_events_registration_deadline_lte_check_in_at",
        ),
        Index("ix_events_start_at", "start_at"),
        Index("ix_events_status", "status"),
        Index("ix_events_venue_lat_lng", "venue_lat", "venue_lng"),
    )

    organizer_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    college_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("colleges.id", ondelete="SET NULL"), nullable=True
    )
    title: Mapped[str] = mapped_column(String(255), nullable=False)
    description: Mapped[str | None] = mapped_column(Text, nullable=True)
    category: Mapped[str] = mapped_column(String(100), nullable=False)
    tags: Mapped[list[str]] = mapped_column(ARRAY(String), default=list, server_default="{}")
    banner_url: Mapped[str | None] = mapped_column(String(1024), nullable=True)
    mode: Mapped[EventMode] = mapped_column(
        SQLEnum(EventMode, native_enum=False), default=EventMode.OFFLINE, nullable=False
    )
    start_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    end_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    check_in_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    registration_deadline: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    timezone: Mapped[str] = mapped_column(String(50), default="Asia/Kolkata", nullable=False)
    venue_name: Mapped[str | None] = mapped_column(String(255), nullable=True)
    venue_lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    venue_lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    participation_type: Mapped[ParticipationType] = mapped_column(
        SQLEnum(ParticipationType, native_enum=False), default=ParticipationType.EITHER, nullable=False
    )
    team_min: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    team_max: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
    seat_limit: Mapped[int | None] = mapped_column(Integer, nullable=True)
    fee_amount: Mapped[float] = mapped_column(Float, default=0.0, nullable=False)
    eligibility: Mapped[str | None] = mapped_column(String(255), nullable=True)
    status: Mapped[EventStatus] = mapped_column(
        SQLEnum(EventStatus, native_enum=False), default=EventStatus.DRAFT, nullable=False
    )
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
