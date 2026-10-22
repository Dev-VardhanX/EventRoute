import uuid
from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import select

from app.core.db import SessionLocal
from app.core.security import require_role
from app.models.college import College
from app.models.enums import OrganizerStatus, UserRole
from app.models.organizer_profile import OrganizerProfile
from app.models.otp_code import OtpCode
from app.models.user import User


@pytest.fixture
def db():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.rollback()
        session.close()


def test_register_verify_login_flow(client):
    uid = uuid.uuid4().hex[:8]
    email = f"student_{uid}@example.com"
    password = "SecurePassword123!"

    # 1. Register student
    reg_res = client.post(
        "/api/v1/auth/register",
        json={
            "name": f"Student {uid}",
            "email": email,
            "password": password,
            "role": "student",
        },
    )
    assert reg_res.status_code == 201, reg_res.text
    data = reg_res.json()
    assert data["email"] == email
    assert data["role"] == "student"
    otp_code = data["dev_otp"]
    assert otp_code is not None and len(otp_code) == 6

    # 2. Verify email
    verify_res = client.post(
        "/api/v1/auth/verify-email",
        json={"email": email, "code": otp_code},
    )
    assert verify_res.status_code == 200, verify_res.text
    tokens = verify_res.json()
    assert "access_token" in tokens
    assert "refresh_token" in tokens
    assert tokens["user"]["email_verified"] is True

    # 3. Login
    login_res = client.post(
        "/api/v1/auth/login",
        json={"email": email, "password": password},
    )
    assert login_res.status_code == 200, login_res.text
    login_data = login_res.json()
    access_token = login_data["access_token"]
    assert access_token

    # 4. Access protected /me
    me_res = client.get(
        "/api/v1/me",
        headers={"Authorization": f"Bearer {access_token}"},
    )
    assert me_res.status_code == 200, me_res.text
    me_data = me_res.json()
    assert me_data["email"] == email
    assert me_data["name"] == f"Student {uid}"


def test_duplicate_email_rejected(client):
    uid = uuid.uuid4().hex[:8]
    email = f"dup_{uid}@example.com"

    res1 = client.post(
        "/api/v1/auth/register",
        json={
            "name": "User One",
            "email": email,
            "password": "Password123!",
            "role": "student",
        },
    )
    assert res1.status_code == 201

    # Attempt registration with same email (even with uppercase)
    res2 = client.post(
        "/api/v1/auth/register",
        json={
            "name": "User Two",
            "email": email.upper(),
            "password": "Password123!",
            "role": "student",
        },
    )
    assert res2.status_code == 422
    assert res2.json()["code"] == "VALIDATION_ERROR"


def test_wrong_password_rejected(client):
    uid = uuid.uuid4().hex[:8]
    email = f"wrongpw_{uid}@example.com"
    password = "CorrectPassword123!"

    reg_res = client.post(
        "/api/v1/auth/register",
        json={"name": "User", "email": email, "password": password},
    )
    otp = reg_res.json()["dev_otp"]
    client.post("/api/v1/auth/verify-email", json={"email": email, "code": otp})

    login_res = client.post(
        "/api/v1/auth/login",
        json={"email": email, "password": "WrongPassword!"},
    )
    assert login_res.status_code == 401
    assert login_res.json()["code"] == "UNAUTHENTICATED"


def test_unverified_login_blocked(client):
    uid = uuid.uuid4().hex[:8]
    email = f"unverified_{uid}@example.com"
    password = "Password123!"

    client.post(
        "/api/v1/auth/register",
        json={"name": "Unverified User", "email": email, "password": password},
    )

    # Attempt login before verifying OTP
    login_res = client.post(
        "/api/v1/auth/login",
        json={"email": email, "password": password},
    )
    assert login_res.status_code == 401
    assert login_res.json()["code"] == "UNAUTHENTICATED"
    assert "not verified" in login_res.json()["message"].lower()


def test_expired_otp_rejected(client, db):
    uid = uuid.uuid4().hex[:8]
    email = f"expired_{uid}@example.com"

    reg_res = client.post(
        "/api/v1/auth/register",
        json={"name": "User", "email": email, "password": "Password123!"},
    )
    code = reg_res.json()["dev_otp"]

    # Expire the OTP in database
    otp = db.execute(
        select(OtpCode).where(OtpCode.email == email, OtpCode.code == code)
    ).scalars().first()
    otp.expires_at = datetime.now(timezone.utc) - timedelta(minutes=5)
    db.commit()

    verify_res = client.post(
        "/api/v1/auth/verify-email",
        json={"email": email, "code": code},
    )
    assert verify_res.status_code == 422
    assert verify_res.json()["code"] == "VALIDATION_ERROR"
    assert "expired" in verify_res.json()["message"].lower()


def test_refresh_flow(client):
    uid = uuid.uuid4().hex[:8]
    email = f"refresh_{uid}@example.com"
    password = "Password123!"

    reg_res = client.post(
        "/api/v1/auth/register",
        json={"name": "User", "email": email, "password": password},
    )
    otp = reg_res.json()["dev_otp"]
    verify_res = client.post(
        "/api/v1/auth/verify-email",
        json={"email": email, "code": otp},
    )
    refresh_token = verify_res.json()["refresh_token"]

    # Refresh tokens
    ref_res = client.post(
        "/api/v1/auth/refresh",
        json={"refresh_token": refresh_token},
    )
    assert ref_res.status_code == 200, ref_res.text
    ref_data = ref_res.json()
    new_access = ref_data["access_token"]
    new_refresh = ref_data["refresh_token"]
    assert new_access
    assert new_refresh

    # Verify new access token works
    me_res = client.get(
        "/api/v1/me",
        headers={"Authorization": f"Bearer {new_access}"},
    )
    assert me_res.status_code == 200


def test_require_role_blocks_student_from_organizer_route(client):
    # Add dummy test route to app
    @client.app.get("/api/v1/test-dummy-organizer-only")
    def dummy_organizer_route(user: User = pytest.importorskip("fastapi").Depends(require_role(UserRole.ORGANIZER))):
        return {"status": "ok", "user": user.name}

    uid = uuid.uuid4().hex[:8]
    student_email = f"stu_{uid}@example.com"
    org_email = f"org_{uid}@example.com"

    # Register & verify student
    r1 = client.post(
        "/api/v1/auth/register",
        json={"name": "Student", "email": student_email, "password": "Password123!", "role": "student"},
    )
    client.post("/api/v1/auth/verify-email", json={"email": student_email, "code": r1.json()["dev_otp"]})
    stu_login = client.post("/api/v1/auth/login", json={"email": student_email, "password": "Password123!"})
    stu_token = stu_login.json()["access_token"]

    # Register & verify organizer
    r2 = client.post(
        "/api/v1/auth/register",
        json={
            "name": "Organizer",
            "email": org_email,
            "password": "Password123!",
            "role": "organizer",
            "organization_name": "Delhi Tech Club",
        },
    )
    client.post("/api/v1/auth/verify-email", json={"email": org_email, "code": r2.json()["dev_otp"]})
    org_login = client.post("/api/v1/auth/login", json={"email": org_email, "password": "Password123!"})
    org_token = org_login.json()["access_token"]

    # Student should be blocked with 403 FORBIDDEN
    res_stu = client.get(
        "/api/v1/test-dummy-organizer-only",
        headers={"Authorization": f"Bearer {stu_token}"},
    )
    assert res_stu.status_code == 403
    assert res_stu.json()["code"] == "FORBIDDEN"

    # Organizer should be allowed with 200 OK
    res_org = client.get(
        "/api/v1/test-dummy-organizer-only",
        headers={"Authorization": f"Bearer {org_token}"},
    )
    assert res_org.status_code == 200
    assert res_org.json()["status"] == "ok"


def test_organizer_gets_pending_profile(client, db):
    uid = uuid.uuid4().hex[:8]
    email = f"organizer_{uid}@example.com"
    org_name = f"Society of Coders {uid}"

    reg_res = client.post(
        "/api/v1/auth/register",
        json={
            "name": "Org Lead",
            "email": email,
            "password": "Password123!",
            "role": "organizer",
            "organization_name": org_name,
        },
    )
    assert reg_res.status_code == 201
    user_id = uuid.UUID(reg_res.json()["user_id"])

    profile = db.execute(
        select(OrganizerProfile).where(OrganizerProfile.user_id == user_id)
    ).scalars().first()
    assert profile is not None
    assert profile.organization_name == org_name
    assert profile.status == OrganizerStatus.PENDING


def test_auto_match_college_by_email_domain(client, db):
    uid = uuid.uuid4().hex[:6]
    # Existing demo college in seed is iitd.ac.in
    email = f"student_{uid}@iitd.ac.in"

    reg_res = client.post(
        "/api/v1/auth/register",
        json={
            "name": "IITD Student",
            "email": email,
            "password": "Password123!",
            "role": "student",
        },
    )
    assert reg_res.status_code == 201
    user_id = uuid.UUID(reg_res.json()["user_id"])

    user = db.get(User, user_id)
    assert user.college_id is not None
    college = db.get(College, user.college_id)
    assert college.email_domain == "iitd.ac.in"


def test_user_me_patch_and_start_point_endpoints(client):
    uid = uuid.uuid4().hex[:8]
    email = f"me_{uid}@example.com"
    r = client.post(
        "/api/v1/auth/register",
        json={"name": "Initial Name", "email": email, "password": "Password123!"},
    )
    client.post("/api/v1/auth/verify-email", json={"email": email, "code": r.json()["dev_otp"]})
    token = client.post("/api/v1/auth/login", json={"email": email, "password": "Password123!"}).json()["access_token"]

    headers = {"Authorization": f"Bearer {token}"}

    # 1. PATCH /me
    patch_res = client.patch(
        "/api/v1/me",
        headers=headers,
        json={"name": "Updated Name", "interests": ["#Hackathons", "#Robotics"], "home_label": "Green Park"},
    )
    assert patch_res.status_code == 200
    assert patch_res.json()["name"] == "Updated Name"
    assert patch_res.json()["interests"] == ["#Hackathons", "#Robotics"]
    assert patch_res.json()["home_label"] == "Green Park"

    # 2. PUT /me/start-point
    sp_res = client.put(
        "/api/v1/me/start-point",
        headers=headers,
        json={"default_start": "custom", "lat": 28.5459, "lng": 77.1926, "label": "IIT Delhi Gate"},
    )
    assert sp_res.status_code == 200
    sp_data = sp_res.json()
    assert sp_data["default_start"] == "custom"
    assert sp_data["home_lat"] == 28.5459
    assert sp_data["home_lng"] == 77.1926

    # 3. POST /me/device-token
    dev_res = client.post(
        "/api/v1/me/device-token",
        headers=headers,
        json={"token": f"fcm_token_{uid}", "platform": "android"},
    )
    assert dev_res.status_code == 200


def test_colleges_public_search_endpoint(client):
    res = client.get("/api/v1/colleges")
    assert res.status_code == 200
    data = res.json()
    assert "items" in data
    assert data["total"] >= 3

    # Search by text
    res_search = client.get("/api/v1/colleges?search=NSUT")
    assert res_search.status_code == 200
    search_data = res_search.json()
    assert search_data["total"] >= 1
    assert any("NSUT" in item["name"] for item in search_data["items"])


def test_logout_endpoint(client):
    res = client.post("/api/v1/auth/logout")
    assert res.status_code == 204


def test_rate_limit_on_failed_logins(client):
    uid = uuid.uuid4().hex[:8]
    email = f"ratelimit_{uid}@example.com"
    password = "CorrectPassword123!"

    # Register and verify
    reg_res = client.post(
        "/api/v1/auth/register",
        json={"name": "Rate Limited User", "email": email, "password": password},
    )
    otp = reg_res.json()["dev_otp"]
    client.post("/api/v1/auth/verify-email", json={"email": email, "code": otp})

    # Fail login 5 times
    for _ in range(5):
        res = client.post("/api/v1/auth/login", json={"email": email, "password": "WrongPassword!"})
        assert res.status_code == 401
        assert res.json()["code"] == "UNAUTHENTICATED"

    # 6th attempt should be blocked by rate limit
    res_blocked = client.post("/api/v1/auth/login", json={"email": email, "password": password})
    assert res_blocked.status_code == 429
    assert res_blocked.json()["code"] == "RATE_LIMITED"

