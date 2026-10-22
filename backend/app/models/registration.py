import uuid
from sqlalchemy import Enum as SQLEnum, ForeignKey, Index, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, IdTimestampMixin
from app.models.enums import RegistrationStatus


class Registration(Base, IdTimestampMixin):
    __tablename__ = "registrations"
    __table_args__ = (
        UniqueConstraint("event_id", "user_id", name="uq_registrations_event_user"),
        Index("ix_registrations_user_id", "user_id"),
    )

    event_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("events.id", ondelete="RESTRICT"), nullable=False
    )
    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False
    )
    team_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("teams.id", ondelete="SET NULL"), nullable=True
    )
    status: Mapped[RegistrationStatus] = mapped_column(
        SQLEnum(RegistrationStatus, native_enum=False),
        default=RegistrationStatus.REGISTERED,
        nullable=False,
    )
    ticket_token: Mapped[str | None] = mapped_column(String(512), nullable=True)
