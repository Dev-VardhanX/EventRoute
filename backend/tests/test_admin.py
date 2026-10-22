import uuid
import pytest

from app.core.security import create_access_token
from app.models.enums import OrganizerStatus, UserRole
from app.models.organizer_profile import OrganizerProfile
from app.models.user import User


@pytest.fixture
def auth_headers(client):
    # Log in as seeded admin
    admin_login = client.post(
        "/api/v1/auth/login",
        json={"email": "admin@eventroute.dev", "password": "Admin@123"},
    )
    assert admin_login.status_code == 200, admin_login.text
    token = admin_login.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def student_headers(client):
    # Log in as seeded student
    login_res = client.post(
        "/api/v1/auth/login",
        json={"email": "student_iitd@iitd.ac.in", "password": "Student@123"},
    )
    assert login_res.status_code == 200, login_res.text
    token = login_res.json()["access_token"]
    return {"Authorization": f"Bearer {token}"}


def test_non_admin_cannot_access_admin_routes(client, student_headers):
    # Student attempts to list pending organizers
    res = client.get("/api/v1/admin/organizers", headers=student_headers)
    assert res.status_code == 403
    assert res.json()["code"] == "FORBIDDEN"

    # Guest attempts to access admin
    res_guest = client.get("/api/v1/admin/organizers")
    assert res_guest.status_code == 401
    assert res_guest.json()["code"] == "UNAUTHENTICATED"


def test_admin_list_and_moderate_organizers(client, auth_headers):
    uid = uuid.uuid4().hex[:6]
    applicant_email = f"applicant_{uid}@example.com"

    # 1. Register a new organizer (starts pending)
    reg_res = client.post(
        "/api/v1/auth/register",
        json={
            "name": f"Applicant {uid}",
            "email": applicant_email,
            "password": "Password123!",
            "role": "organizer",
            "organization_name": f"Tech Org {uid}",
        },
    )
    assert reg_res.status_code == 201
    applicant_user_id = reg_res.json()["user_id"]

    # 2. Admin lists pending organizers
    res_list = client.get("/api/v1/admin/organizers?status=pending", headers=auth_headers)
    assert res_list.status_code == 200
    data = res_list.json()
    assert "items" in data
    assert "total" in data
    assert any(item["user_id"] == applicant_user_id for item in data["items"])

    pending_item = next(item for item in data["items"] if item["user_id"] == applicant_user_id)
    profile_id = pending_item["id"]

    # 3. Admin approves organizer
    res_approve = client.post(
        f"/api/v1/admin/organizers/{profile_id}/approve",
        headers=auth_headers,
    )
    assert res_approve.status_code == 200
    approved_data = res_approve.json()
    assert approved_data["status"] == "approved"
    assert approved_data["reviewed_by"] is not None

    # 4. Reject another applicant
    uid2 = uuid.uuid4().hex[:6]
    reg_res2 = client.post(
        "/api/v1/auth/register",
        json={
            "name": f"Reject Applicant {uid2}",
            "email": f"reject_{uid2}@example.com",
            "password": "Password123!",
            "role": "organizer",
            "organization_name": f"Reject Org {uid2}",
        },
    )
    applicant_user_id2 = reg_res2.json()["user_id"]
    res_reject = client.post(
        f"/api/v1/admin/organizers/{applicant_user_id2}/reject",
        headers=auth_headers,
    )
    assert res_reject.status_code == 200
    assert res_reject.json()["status"] == "rejected"


def test_admin_colleges_crud(client, auth_headers, student_headers):
    uid = uuid.uuid4().hex[:6]
    domain = f"college_{uid}.edu"

    # Student cannot create college
    res_denied = client.post(
        "/api/v1/admin/colleges",
        headers=student_headers,
        json={"name": "Fake College", "city": "Delhi", "lat": 28.5, "lng": 77.2},
    )
    assert res_denied.status_code == 403

    # Admin creates college
    res_create = client.post(
        "/api/v1/admin/colleges",
        headers=auth_headers,
        json={
            "name": f"Test University {uid}",
            "city": "New Delhi",
            "lat": 28.6139,
            "lng": 77.2090,
            "email_domain": domain,
            "verified": True,
            "is_demo": False,
        },
    )
    assert res_create.status_code == 201
    college_data = res_create.json()
    college_id = college_data["id"]
    assert college_data["name"] == f"Test University {uid}"
    assert college_data["email_domain"] == domain
    assert college_data["verified"] is True

    # Admin updates college
    res_patch = client.patch(
        f"/api/v1/admin/colleges/{college_id}",
        headers=auth_headers,
        json={"city": "Central Delhi", "verified": False},
    )
    assert res_patch.status_code == 200
    updated_data = res_patch.json()
    assert updated_data["city"] == "Central Delhi"
    assert updated_data["verified"] is False

    # Admin lists colleges
    res_list = client.get(
        f"/api/v1/admin/colleges?search={uid}",
        headers=auth_headers,
    )
    assert res_list.status_code == 200
    list_data = res_list.json()
    assert list_data["total"] >= 1
    assert any(c["id"] == college_id for c in list_data["items"])
