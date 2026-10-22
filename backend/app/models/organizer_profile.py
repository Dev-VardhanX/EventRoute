import uuid
from sqlalchemy import Enum as SQLEnum, ForeignKey, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.models.base import Base, IdTimestampMixin
from app.models.enums import OrganizerStatus


class OrganizerProfile(Base, IdTimestampMixin):
    __tablename__ = "organizer_profiles"

    user_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), unique=True, nullable=False
    )
    organization_name: Mapped[str] = mapped_column(String(255), nullable=False)
    college_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("colleges.id", ondelete="SET NULL"), nullable=True
    )
    status: Mapped[OrganizerStatus] = mapped_column(
        SQLEnum(OrganizerStatus, native_enum=False), default=OrganizerStatus.PENDING, nullable=False
    )
    reviewed_by: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True
    )
