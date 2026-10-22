import uuid
from sqlalchemy import Boolean, CheckConstraint, Enum as SQLEnum, Float, ForeignKey, String, UniqueConstraint
from sqlalchemy.dialects.postgresql import ARRAY, UUID
from sqlalchemy.orm import Mapped, mapped_column, validates

from app.models.base import Base, IdTimestampMixin
from app.models.enums import DefaultStart, UserRole


class User(Base, IdTimestampMixin):
    __tablename__ = "users"
    __table_args__ = (
        CheckConstraint("email = lower(email)", name="ck_users_email_lowercase"),
        UniqueConstraint("email", name="uq_users_email"),
    )

    name: Mapped[str] = mapped_column(String(255), nullable=False)
    email: Mapped[str] = mapped_column(String(255), nullable=False)
    email_verified: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    password_hash: Mapped[str | None] = mapped_column(String(255), nullable=True)
    role: Mapped[UserRole] = mapped_column(
        SQLEnum(UserRole, native_enum=False), default=UserRole.STUDENT, nullable=False
    )
    college_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("colleges.id", ondelete="SET NULL"), nullable=True
    )
    interests: Mapped[list[str]] = mapped_column(ARRAY(String), default=list, server_default="{}")
    home_lat: Mapped[float | None] = mapped_column(Float, nullable=True)
    home_lng: Mapped[float | None] = mapped_column(Float, nullable=True)
    home_label: Mapped[str | None] = mapped_column(String(255), nullable=True)
    default_start: Mapped[DefaultStart] = mapped_column(
        SQLEnum(DefaultStart, native_enum=False), default=DefaultStart.COLLEGE, nullable=False
    )
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, nullable=False)
    is_demo: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

    @validates("email")
    def validate_email(self, key: str, email: str) -> str:
        if email is not None:
            return email.strip().lower()
        return email
