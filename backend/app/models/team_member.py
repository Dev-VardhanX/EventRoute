import uuid
from datetime import datetime
from sqlalchemy import DateTime, Enum as SQLEnum, Float, ForeignKey, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, IdTimestampMixin
from app.models.enums import JourneyStatus


class TeamMember(Base, IdTimestampMixin):
    __tablename__ = "team_members"
    __table_args__ = (
        UniqueConstraint("team_id", "user_id", name="uq_team_members_team_user"),
    )

    team_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("teams.id", ondelete="CASCADE"), nullable=False
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    start_lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    start_lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    start_source: Mapped[str | None] = mapped_column(String(50), nullable=True)
    travel_pref: Mapped[str | None] = mapped_column(String(50), nullable=True)
    planned_leave_by: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    planned_arrival_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    journey_status: Mapped[JourneyStatus] = mapped_column(
        SQLEnum(JourneyStatus, native_enum=False), default=JourneyStatus.NOT_STARTED, nullable=False
    )
    live_lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    live_lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    live_eta_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
