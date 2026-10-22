import uuid
from datetime import datetime
from sqlalchemy import DateTime, Enum as SQLEnum, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, IdTimestampMixin
from app.models.enums import TeamStatus


class Team(Base, IdTimestampMixin):
    __tablename__ = "teams"
    __table_args__ = (
        UniqueConstraint("invite_code", name="uq_teams_invite_code"),
    )

    event_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("events.id", ondelete="CASCADE"), nullable=False
    )
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    leader_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    invite_code: Mapped[str] = mapped_column(String(32), nullable=False)
    status: Mapped[TeamStatus] = mapped_column(
        SQLEnum(TeamStatus, native_enum=False), default=TeamStatus.FORMING, nullable=False
    )
    planned_arrival_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    plan_version: Mapped[int] = mapped_column(Integer, default=1, nullable=False)
