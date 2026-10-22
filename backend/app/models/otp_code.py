from datetime import datetime
from sqlalchemy import Boolean, DateTime, Index, String
from sqlalchemy.orm import Mapped, mapped_column, validates

from app.models.base import Base, IdTimestampMixin


class OtpCode(Base, IdTimestampMixin):
    __tablename__ = "otp_codes"
    __table_args__ = (
        Index("ix_otp_codes_email_code", "email", "code"),
    )

    email: Mapped[str] = mapped_column(String(255), nullable=False)
    code: Mapped[str] = mapped_column(String(10), nullable=False)
    purpose: Mapped[str] = mapped_column(String(50), default="verify_email", nullable=False)
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    used: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)

    @validates("email")
    def validate_email(self, key: str, email: str) -> str:
        if email is not None:
            return email.strip().lower()
        return email
