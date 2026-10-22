import io
import uuid
from datetime import datetime, timedelta, timezone

import pytest

from app.models.enums import EventMode, EventStatus, ParticipationType, UserRole


@pytest.fixture
def admin_headers(client):
    login = client.post(
        "/api/v1/auth/login",
        json={"email": "admin@eventroute.dev", "password": "Admin@123"},
    )
    assert login.status_code == 200, login.text
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


@pytest.fixture
def approved_org_headers(client):
    login = client.post(
        "/api/v1/auth/login",
        json={"email": "organizer@eventroute.dev", "password": "Organizer@123"},
    )
    assert login.status_code == 200, login.text
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


@pytest.fixture
def student_headers(client):
    login = client.post(
        "/api/v1/auth/login",
        json={"email": "student_iitd@iitd.ac.in", "password": "Student@123"},
    )
    assert login.status_code == 200, login.text
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


def sample_valid_event_payload(uid: str, days_ahead: int = 10, lat: float = 28.5459, lng: float = 77.1926):
    now = datetime.now(timezone.utc)
    start_at = now + timedelta(days=days_ahead, hours=10)
    check_in_at = now + timedelta(days=days_ahead, hours=9)
    registration_deadline = now + timedelta(days=days_ahead, hours=8)
    end_at = now + timedelta(days=days_ahead, hours=18)

    return {
        "title": f"Hackathon {uid}",
        "description": "24 hour flagship hackathon",
        "category": "Hackathon",
        "tags": ["AI", "Web3", "Robotics"],
        "mode": "offline",
        "start_at": start_at.isoformat(),
        "end_at": end_at.isoformat(),
        "check_in_at": check_in_at.isoformat(),
        "registration_deadline": registration_deadline.isoformat(),
        "timezone": "Asia/Kolkata",
        "venue_name": "Dogra Hall, IIT Delhi",
        "venue_lat": lat,
        "venue_lng": lng,
        "participation_type": "team",
        "team_min": 2,
        "team_max": 4,
        "seat_limit": 100,
        "fee_amount": 0.0,
        "eligibility": "Open for all college students",
    }


def test_student_and_unapproved_organizer_cannot_create_event(client, student_headers, admin_headers):
    payload = sample_valid_event_payload("test_roles")

    # 1. Student cannot create event -> 403 FORBIDDEN
    res_stu = client.post("/api/v1/events", headers=student_headers, json=payload)
    assert res_stu.status_code == 403
    assert res_stu.json()["code"] == "FORBIDDEN"

    # 2. Register new unapproved organizer
    uid = uuid.uuid4().hex[:6]
    unapp_email = f"pending_org_{uid}@example.com"
    reg_res = client.post(
        "/api/v1/auth/register",
        json={
            "name": "Pending Org",
            "email": unapp_email,
            "password": "Password123!",
            "role": "organizer",
            "organization_name": "Pending Society",
        },
    )
    otp = reg_res.json()["dev_otp"]
    client.post("/api/v1/auth/verify-email", json={"email": unapp_email, "code": otp})
    unapp_login = client.post("/api/v1/auth/login", json={"email": unapp_email, "password": "Password123!"})
    unapp_token = unapp_login.json()["access_token"]
    unapp_headers = {"Authorization": f"Bearer {unapp_token}"}

    # Unapproved organizer cannot create event -> 403 FORBIDDEN
    res_pending = client.post("/api/v1/events", headers=unapp_headers, json=payload)
    assert res_pending.status_code == 403
    assert res_pending.json()["code"] == "FORBIDDEN"

    # 3. Admin approves organizer
    admin_list = client.get("/api/v1/admin/organizers?status=pending", headers=admin_headers)
    pending_prof = next(item for item in admin_list.json()["items"] if item["user"]["email"] == unapp_email)
    client.post(f"/api/v1/admin/organizers/{pending_prof['id']}/approve", headers=admin_headers)

    # 4. Now approved organizer can create event -> 201 CREATED
    res_approved = client.post("/api/v1/events", headers=unapp_headers, json=payload)
    assert res_approved.status_code == 201
    assert res_approved.json()["status"] == "draft"


def test_invalid_dates_and_rules_rejected(client, approved_org_headers):
    uid = uuid.uuid4().hex[:6]
    payload = sample_valid_event_payload(uid)
    now = datetime.now(timezone.utc)

    # 1. check_in_at > start_at
    bad_payload = dict(payload)
    bad_payload["check_in_at"] = (now + timedelta(days=10, hours=12)).isoformat()
    bad_payload["start_at"] = (now + timedelta(days=10, hours=10)).isoformat()
    res = client.post("/api/v1/events", headers=approved_org_headers, json=bad_payload)
    assert res.status_code == 422
    assert res.json()["code"] == "VALIDATION_ERROR"

    # 2. registration_deadline > check_in_at
    bad_payload2 = dict(payload)
    bad_payload2["registration_deadline"] = (now + timedelta(days=10, hours=10)).isoformat()
    bad_payload2["check_in_at"] = (now + timedelta(days=10, hours=9)).isoformat()
    res2 = client.post("/api/v1/events", headers=approved_org_headers, json=bad_payload2)
    assert res2.status_code == 422
    assert res2.json()["code"] == "VALIDATION_ERROR"

    # 3. team_min > team_max
    bad_payload3 = dict(payload)
    bad_payload3["team_min"] = 5
    bad_payload3["team_max"] = 3
    res3 = client.post("/api/v1/events", headers=approved_org_headers, json=bad_payload3)
    assert res3.status_code == 422
    assert res3.json()["code"] == "VALIDATION_ERROR"

    # 4. offline event missing venue coordinates
    bad_payload4 = dict(payload)
    bad_payload4["venue_lat"] = None
    bad_payload4["venue_lng"] = None
    res4 = client.post("/api/v1/events", headers=approved_org_headers, json=bad_payload4)
    assert res4.status_code == 422
    assert res4.json()["code"] == "VALIDATION_ERROR"

    # 5. team participation with team_min < 2
    bad_payload5 = dict(payload)
    bad_payload5["participation_type"] = "team"
    bad_payload5["team_min"] = 1
    res5 = client.post("/api/v1/events", headers=approved_org_headers, json=bad_payload5)
    assert res5.status_code == 422
    assert res5.json()["code"] == "VALIDATION_ERROR"


def test_ownership_and_draft_visibility(client, approved_org_headers, admin_headers, student_headers):
    uid = uuid.uuid4().hex[:6]
    payload = sample_valid_event_payload(uid)

    # 1. Create draft event with approved organizer
    create_res = client.post("/api/v1/events", headers=approved_org_headers, json=payload)
    assert create_res.status_code == 201
    event_id = create_res.json()["id"]

    # 2. Draft is visible to owner organizer
    owner_get = client.get(f"/api/v1/events/{event_id}", headers=approved_org_headers)
    assert owner_get.status_code == 200

    # 3. Draft is visible to admin
    admin_get = client.get(f"/api/v1/events/{event_id}", headers=admin_headers)
    assert admin_get.status_code == 200

    # 4. Draft is HIDDEN from public (guest) and other students -> 404 NOT_FOUND
    guest_get = client.get(f"/api/v1/events/{event_id}")
    assert guest_get.status_code == 404
    assert guest_get.json()["code"] == "NOT_FOUND"

    student_get = client.get(f"/api/v1/events/{event_id}", headers=student_headers)
    assert student_get.status_code == 404

    # 5. Create another approved organizer and attempt to PATCH
    uid2 = uuid.uuid4().hex[:6]
    other_email = f"other_org_{uid2}@example.com"
    r = client.post(
        "/api/v1/auth/register",
        json={"name": "Other", "email": other_email, "password": "Password123!", "role": "organizer", "organization_name": "Other Club"},
    )
    client.post("/api/v1/auth/verify-email", json={"email": other_email, "code": r.json()["dev_otp"]})
    admin_list = client.get("/api/v1/admin/organizers?status=pending", headers=admin_headers)
    other_prof = next(item for item in admin_list.json()["items"] if item["user"]["email"] == other_email)
    client.post(f"/api/v1/admin/organizers/{other_prof['id']}/approve", headers=admin_headers)
    other_login = client.post("/api/v1/auth/login", json={"email": other_email, "password": "Password123!"})
    other_headers = {"Authorization": f"Bearer {other_login.json()['access_token']}"}

    patch_res = client.patch(
        f"/api/v1/events/{event_id}",
        headers=other_headers,
        json={"title": "Hacked Title"},
    )
    assert patch_res.status_code == 403
    assert patch_res.json()["code"] == "FORBIDDEN"


def test_publish_cancel_and_discovery_with_radius(client, approved_org_headers):
    uid1 = uuid.uuid4().hex[:6]
    uid2 = uuid.uuid4().hex[:6]

    # Event 1: at IIT Delhi (28.5459, 77.1926)
    p1 = sample_valid_event_payload(f"iitd_{uid1}", days_ahead=5, lat=28.5459, lng=77.1926)
    r1 = client.post("/api/v1/events", headers=approved_org_headers, json=p1)
    e1_id = r1.json()["id"]

    # Event 2: at NSUT Dwarka (28.6094, 77.0368) -> ~17 km away from IIT Delhi
    p2 = sample_valid_event_payload(f"nsut_{uid2}", days_ahead=7, lat=28.6094, lng=77.0368)
    r2 = client.post("/api/v1/events", headers=approved_org_headers, json=p2)
    e2_id = r2.json()["id"]

    # Before publishing, neither appears in public GET /events
    pub_list_0 = client.get(f"/api/v1/events?q={uid1}")
    assert pub_list_0.status_code == 200
    assert pub_list_0.json()["total"] == 0

    # Publish both
    pub1 = client.post(f"/api/v1/events/{e1_id}/publish", headers=approved_org_headers)
    assert pub1.status_code == 200
    assert pub1.json()["status"] == "published"

    pub2 = client.post(f"/api/v1/events/{e2_id}/publish", headers=approved_org_headers)
    assert pub2.status_code == 200
    assert pub2.json()["status"] == "published"

    # Now appears in public listing
    pub_list_1 = client.get(f"/api/v1/events?q={uid1}")
    assert pub_list_1.status_code == 200
    assert pub_list_1.json()["total"] == 1
    assert pub_list_1.json()["items"][0]["id"] == e1_id

    # Test Radius filtering from IIT Delhi (28.5459, 77.1926):
    # 5 km radius includes IITD event, excludes NSUT event
    res_near = client.get("/api/v1/events?lat=28.5459&lng=77.1926&radius_km=5&page_size=100")
    assert res_near.status_code == 200
    near_ids = [item["id"] for item in res_near.json()["items"]]
    assert e1_id in near_ids
    assert e2_id not in near_ids

    # 30 km radius includes both
    res_far = client.get("/api/v1/events?lat=28.5459&lng=77.1926&radius_km=30&page_size=100")
    assert res_far.status_code == 200
    far_ids = [item["id"] for item in res_far.json()["items"]]
    assert e1_id in far_ids
    assert e2_id in far_ids

    # Check distance_km is computed and returned
    iitd_item = next(item for item in res_far.json()["items"] if item["id"] == e1_id)
    assert iitd_item["distance_km"] is not None
    assert iitd_item["distance_km"] < 1.0  # At the exact origin

    # Cancel e2_id
    res_cancel = client.post(f"/api/v1/events/{e2_id}/cancel", headers=approved_org_headers)
    assert res_cancel.status_code == 200
    assert res_cancel.json()["status"] == "cancelled"

    # Cancelled event no longer appears in public GET /events
    res_after_cancel = client.get(f"/api/v1/events?q={uid2}")
    assert res_after_cancel.json()["total"] == 0

    # But appears in organizer's GET /events/mine
    mine_res = client.get("/api/v1/events/mine", headers=approved_org_headers)
    assert mine_res.status_code == 200
    mine_ids = [item["id"] for item in mine_res.json()["items"]]
    assert e1_id in mine_ids
    assert e2_id in mine_ids


def test_travel_estimates_endpoint(client, approved_org_headers):
    uid = uuid.uuid4().hex[:6]
    payload = sample_valid_event_payload(uid, days_ahead=3, lat=28.5459, lng=77.1926)
    r = client.post("/api/v1/events", headers=approved_org_headers, json=payload)
    event_id = r.json()["id"]

    est_res = client.post(
        "/api/v1/events/travel-estimates",
        json={
            "origin": {"lat": 28.6139, "lng": 77.2090},  # Connaught Place, ~8-10 km away
            "event_ids": [event_id],
        },
    )
    assert est_res.status_code == 200
    data = est_res.json()
    assert "items" in data
    assert len(data["items"]) == 1
    est = data["items"][0]
    assert est["event_id"] == event_id
    assert est["distance_km"] > 5.0
    assert est["duration_min"] > 0
    assert est["source"] in ("route", "estimate")



def test_image_uploads(client, student_headers, approved_org_headers):
    # 1. Student cannot upload image
    fake_png = io.BytesIO(b"\x89PNG\r\n\x1a\nfakeimagecontent")
    res_stu = client.post(
        "/api/v1/uploads/image",
        headers=student_headers,
        files={"file": ("test.png", fake_png, "image/png")},
    )
    assert res_stu.status_code == 403

    # 2. Organizer can upload png
    fake_png.seek(0)
    res_org = client.post(
        "/api/v1/uploads/image",
        headers=approved_org_headers,
        files={"file": ("poster.png", fake_png, "image/png")},
    )
    assert res_org.status_code == 200
    data = res_org.json()
    assert "url" in data
    assert data["url"].startswith("/uploads/")

    # 3. Uploaded image is accessible via StaticFiles
    get_img = client.get(data["url"])
    assert get_img.status_code == 200
    assert get_img.content == b"\x89PNG\r\n\x1a\nfakeimagecontent"

    # 4. Wrong file type rejected
    fake_txt = io.BytesIO(b"Hello world")
    res_bad_type = client.post(
        "/api/v1/uploads/image",
        headers=approved_org_headers,
        files={"file": ("notes.txt", fake_txt, "text/plain")},
    )
    assert res_bad_type.status_code == 422
    assert res_bad_type.json()["code"] == "VALIDATION_ERROR"

    # 5. Oversized file (> 3MB) rejected
    huge_data = io.BytesIO(b"x" * (3 * 1024 * 1024 + 100))
    res_huge = client.post(
        "/api/v1/uploads/image",
        headers=approved_org_headers,
        files={"file": ("huge.jpg", huge_data, "image/jpeg")},
    )
    assert res_huge.status_code == 422
    assert res_huge.json()["code"] == "VALIDATION_ERROR"
