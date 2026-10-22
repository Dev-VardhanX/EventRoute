import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Callable

import bcrypt
import jwt
from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.core.config import get_settings
from app.core.db import get_db
from app.core.errors import AppError
from app.models.enums import UserRole
from app.models.user import User

settings = get_settings()
bearer_scheme = HTTPBearer(auto_error=False)


def hash_password(password: str) -> str:
    """Hash password using bcrypt."""
    salt = bcrypt.gensalt()
    return bcrypt.hashpw(password.encode("utf-8"), salt).decode("utf-8")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Verify password against bcrypt hash."""
    if not hashed_password:
        return False
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), hashed_password.encode("utf-8"))
    except Exception:
        return False


def create_access_token(user_id: uuid.UUID | str, role: str | UserRole) -> str:
    """Create short-lived JWT access token."""
    role_str = role.value if isinstance(role, UserRole) else str(role)
    now = datetime.now(timezone.utc)
    expire = now + timedelta(minutes=settings.access_token_minutes)
    payload: dict[str, Any] = {
        "sub": str(user_id),
        "role": role_str,
        "type": "access",
        "iat": now,
        "exp": expire,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def create_refresh_token(user_id: uuid.UUID | str) -> str:
    """Create long-lived JWT refresh token."""
    now = datetime.now(timezone.utc)
    expire = now + timedelta(days=settings.refresh_token_days)
    payload: dict[str, Any] = {
        "sub": str(user_id),
        "type": "refresh",
        "iat": now,
        "exp": expire,
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def decode_token(token: str) -> dict[str, Any]:
    """Decode and validate JWT token."""
    try:
        return jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
    except jwt.ExpiredSignatureError:
        raise AppError("UNAUTHENTICATED", "Token has expired")
    except jwt.PyJWTError:
        raise AppError("UNAUTHENTICATED", "Invalid token signature or payload")


def get_current_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> User:
    """FastAPI dependency: Authenticate request via Bearer access token."""
    if not credentials or not credentials.credentials:
        raise AppError("UNAUTHENTICATED", "Missing authorization header")

    payload = decode_token(credentials.credentials)
    if payload.get("type") != "access":
        raise AppError("UNAUTHENTICATED", "Invalid token type, access token required")

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

    return user


def get_optional_user(
    credentials: HTTPAuthorizationCredentials | None = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> User | None:
    """FastAPI dependency: Optional user authentication for public/guest endpoints."""
    if not credentials or not credentials.credentials:
        return None

    try:
        payload = decode_token(credentials.credentials)
        if payload.get("type") != "access":
            return None
        sub = payload.get("sub")
        if not sub:
            return None
        user_uuid = uuid.UUID(sub)
        user = db.get(User, user_uuid)
        if not user or not user.is_active:
            return None
        return user
    except Exception:
        return None


def require_role(*roles: str | UserRole) -> Callable[[User], User]:
    """FastAPI dependency factory: Require authenticated user to possess one of the roles."""
    allowed = {r.value if isinstance(r, UserRole) else str(r) for r in roles}

    def role_checker(current_user: User = Depends(get_current_user)) -> User:
        user_role = (
            current_user.role.value
            if isinstance(current_user.role, UserRole)
            else str(current_user.role)
        )
        if user_role not in allowed:
            raise AppError("FORBIDDEN", f"Access forbidden for role '{user_role}'")
        return current_user

    return role_checker
