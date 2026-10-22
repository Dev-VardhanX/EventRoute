from fastapi import APIRouter, Depends, Response, status
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.schemas.auth import (
    LoginRequest,
    RefreshTokenRequest,
    RegisterRequest,
    RegisterResponse,
    TokenResponse,
    VerifyEmailRequest,
)
from app.services.auth import (
    login_user,
    refresh_tokens,
    register_user,
    verify_email,
)

router = APIRouter(tags=["auth"])


@router.post("/auth/register", response_model=RegisterResponse, status_code=status.HTTP_201_CREATED)
def register(req: RegisterRequest, db: Session = Depends(get_db)):
    """Register a new student or organizer user and trigger OTP verification."""
    return register_user(db, req)


@router.post("/auth/verify-email", response_model=TokenResponse)
def verify(req: VerifyEmailRequest, db: Session = Depends(get_db)):
    """Verify email with OTP code and return auth tokens."""
    return verify_email(db, req)


@router.post("/auth/login", response_model=TokenResponse)
def login(req: LoginRequest, db: Session = Depends(get_db)):
    """Authenticate user with email and password."""
    return login_user(db, req)


@router.post("/auth/refresh", response_model=TokenResponse)
def refresh(req: RefreshTokenRequest, db: Session = Depends(get_db)):
    """Refresh expired access token using valid refresh token."""
    return refresh_tokens(db, req)


@router.post("/auth/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout():
    """Stateless logout; client clears local tokens."""
    return Response(status_code=status.HTTP_204_NO_CONTENT)
