import uuid
from pydantic import BaseModel, EmailStr

from app.models.enums import UserRole
from app.schemas.user import UserResponse


class RegisterRequest(BaseModel):
    name: str
    email: EmailStr
    password: str
    college_id: uuid.UUID | None = None
    role: UserRole = UserRole.STUDENT
    organization_name: str | None = None


class RegisterResponse(BaseModel):
    message: str
    user_id: uuid.UUID
    email: str
    role: UserRole
    dev_otp: str | None = None


class VerifyEmailRequest(BaseModel):
    email: EmailStr
    code: str


class LoginRequest(BaseModel):
    email: EmailStr
    password: str


class RefreshTokenRequest(BaseModel):
    refresh_token: str


class TokenResponse(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"
    user: UserResponse | None = None
