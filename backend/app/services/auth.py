import logging
import secrets
import time
import uuid
from collections import defaultdict
from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.errors import AppError
from app.core.security import (
    create_access_token,
    create_refresh_token,
    decode_token,
    hash_password,
    verify_password,
)
from app.models.college import College
from app.models.enums import OrganizerStatus, UserRole
from app.models.organizer_profile import OrganizerProfile
from app.models.otp_code import OtpCode
from app.models.user import User
from app.schemas.auth import (
    LoginRequest,
    RefreshTokenRequest,
    RegisterRequest,
    RegisterResponse,
    TokenResponse,
    VerifyEmailRequest,
)
from app.schemas.user import UserResponse
from app.services.notifications import send_email

logger = logging.getLogger("eventroute.auth")
settings = get_settings()

# Rate limiting: 5 failed attempts per 15 min per email
_rate_limits: dict[tuple[str, str], list[float]] = defaultdict(list)
RATE_LIMIT_WINDOW_SECONDS = 15 * 60
MAX_ATTEMPTS = 5


def check_rate_limit(email: str, action: str) -> None:
    now = time.time()
    key = (email.strip().lower(), action)
    timestamps = [t for t in _rate_limits[key] if now - t < RATE_LIMIT_WINDOW_SECONDS]
    _rate_limits[key] = timestamps
    if len(timestamps) >= MAX_ATTEMPTS:
        raise AppError("RATE_LIMITED", "Too many attempts. Please try again after 15 minutes.")


def record_failed_attempt(email: str, action: str) -> None:
    now = time.time()
    key = (email.strip().lower(), action)
    timestamps = [t for t in _rate_limits[key] if now - t < RATE_LIMIT_WINDOW_SECONDS]
    timestamps.append(now)
    _rate_limits[key] = timestamps


def clear_rate_limit(email: str, action: str) -> None:
    key = (email.strip().lower(), action)
    _rate_limits.pop(key, None)


def register_user(db: Session, req: RegisterRequest) -> RegisterResponse:
    """Register a new student or organizer, sending verification OTP."""
    email = req.email.strip().lower()

    # Reject duplicate email
    existing = db.execute(select(User).where(User.email == email)).scalar_one_or_none()
    if existing:
        raise AppError("VALIDATION_ERROR", "Email already registered")

    # If organizer, organization_name is required
    if req.role == UserRole.ORGANIZER:
        if not req.organization_name or not req.organization_name.strip():
            raise AppError("VALIDATION_ERROR", "Organization name is required for organizers")

    # Auto-match college by email domain if college_id missing
    college_id = req.college_id
    if not college_id and "@" in email:
        domain = email.split("@")[1].strip().lower()
        matched_college = db.execute(
            select(College).where(College.email_domain == domain)
        ).scalars().first()
        if matched_college:
            college_id = matched_college.id

    password_hash = hash_password(req.password)

    user = User(
        name=req.name.strip(),
        email=email,
        password_hash=password_hash,
        role=req.role,
        college_id=college_id,
        email_verified=False,
        is_active=True,
    )
    db.add(user)
    db.flush()

    # If organizer, create organizer_profiles row with status=pending
    if req.role == UserRole.ORGANIZER:
        profile = OrganizerProfile(
            user_id=user.id,
            organization_name=req.organization_name.strip(),  # type: ignore[union-attr]
            college_id=college_id,
            status=OrganizerStatus.PENDING,
        )
        db.add(profile)

    # Create 6-digit OTP code (10 min expiry)
    code = f"{secrets.randbelow(900000) + 100000}"
    expires_at = datetime.now(timezone.utc) + timedelta(minutes=10)
    otp_entry = OtpCode(
        email=email,
        code=code,
        purpose="verify_email",
        expires_at=expires_at,
        used=False,
    )
    db.add(otp_entry)
    db.commit()
    db.refresh(user)

    # Dispatch email
    send_email(
        to=email,
        subject="Your EventRoute verification code",
        body=f"Your verification code is: {code}",
    )
    logger.info("Registered user %s (%s). Dev OTP: %s", user.email, user.role, code)

    # In dev, return dev_otp; never in prod
    dev_otp = code if settings.app_env == "dev" else None

    return RegisterResponse(
        message="Registration successful. Verification code sent.",
        user_id=user.id,
        email=user.email,
        role=user.role,
        dev_otp=dev_otp,
    )


def verify_email(db: Session, req: VerifyEmailRequest) -> TokenResponse:
    """Verify email via OTP and return access and refresh tokens."""
    email = req.email.strip().lower()
    code = req.code.strip()

    check_rate_limit(email, "otp")

    # Find latest OTP matching email and purpose
    otp = db.execute(
        select(OtpCode)
        .where(
            OtpCode.email == email,
            OtpCode.code == code,
            OtpCode.purpose == "verify_email",
        )
        .order_by(OtpCode.created_at.desc())
    ).scalars().first()

    if not otp or otp.used:
        record_failed_attempt(email, "otp")
        raise AppError("VALIDATION_ERROR", "Invalid or already used verification code")

    now = datetime.now(timezone.utc)
    # Ensure expires_at is timezone-aware for comparison
    otp_exp = otp.expires_at if otp.expires_at.tzinfo else otp.expires_at.replace(tzinfo=timezone.utc)
    if otp_exp < now:
        record_failed_attempt(email, "otp")
        raise AppError("VALIDATION_ERROR", "Verification code has expired")

    otp.used = True

    user = db.execute(select(User).where(User.email == email)).scalar_one_or_none()
    if not user:
        raise AppError("NOT_FOUND", "User not found")

    user.email_verified = True
    db.commit()
    db.refresh(user)

    clear_rate_limit(email, "otp")

    access_token = create_access_token(user.id, user.role)
    refresh_token = create_refresh_token(user.id)

    return TokenResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        token_type="bearer",
        user=UserResponse.model_validate(user),
    )


def login_user(db: Session, req: LoginRequest) -> TokenResponse:
    """Authenticate user with email and password."""
    email = req.email.strip().lower()

    check_rate_limit(email, "login")

    user = db.execute(select(User).where(User.email == email)).scalar_one_or_none()
    if not user or not user.password_hash or not verify_password(req.password, user.password_hash):
        record_failed_attempt(email, "login")
        raise AppError("UNAUTHENTICATED", "Invalid email or password")

    if not user.email_verified:
        raise AppError(
            "UNAUTHENTICATED", "Email is not verified. Please verify your email first."
        )

    if not user.is_active:
        raise AppError("UNAUTHENTICATED", "Account is inactive")

    clear_rate_limit(email, "login")

    access_token = create_access_token(user.id, user.role)
    refresh_token = create_refresh_token(user.id)

    return TokenResponse(
        access_token=access_token,
        refresh_token=refresh_token,
        token_type="bearer",
        user=UserResponse.model_validate(user),
    )


def refresh_tokens(db: Session, req: RefreshTokenRequest) -> TokenResponse:
    """Rotate refresh token and issue new access token."""
    payload = decode_token(req.refresh_token)
    if payload.get("type") != "refresh":
        raise AppError("UNAUTHENTICATED", "Invalid token type, refresh token required")

    sub = payload.get("sub")
    if not sub:
        raise AppError("UNAUTHENTICATED", "Invalid token subject")

    try:
        user_uuid = uuid.UUID(sub)
    except ValueError:
        raise AppError("UNAUTHENTICATED", "Invalid user ID format in token")

    user = db.get(User, user_uuid)
    if not user or not user.is_active:
        raise AppError("UNAUTHENTICATED", "User not found or inactive")

    new_access = create_access_token(user.id, user.role)
    new_refresh = create_refresh_token(user.id)

    return TokenResponse(
        access_token=new_access,
        refresh_token=new_refresh,
        token_type="bearer",
        user=UserResponse.model_validate(user),
    )
