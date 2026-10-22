import uuid
from datetime import datetime, timedelta, timezone

import pytest

from app.models.enums import EventMode, EventStatus, ParticipationType, RegistrationStatus


@pytest.fixture
def admin_headers(client):
    login = client.post(
        "/api/v1/auth/login",
        json={"email": "admin@eventroute.dev", "password": "Admin@123"},
    )
    assert login.status_code == 200, login.text
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


@pytest.fixture
def org_headers(client):
    login = client.post(
        "/api/v1/auth/login",
        json={"email": "organizer@eventroute.dev", "password": "Organizer@123"},
    )
    assert login.status_code == 200, login.text
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


def make_student(client, domain="iitd.ac.in"):
    uid = uuid.uuid4().hex[:6]
    email = f"stu_{uid}@{domain}"
    pwd = "Password123!"
    r = client.post(
        "/api/v1/auth/register",
        json={"name": f"Student {uid}", "email": email, "password": pwd},
    )
    assert r.status_code == 201, r.text
    otp = r.json()["dev_otp"]
    client.post("/api/v1/auth/verify-email", json={"email": email, "code": otp})
    login = client.post("/api/v1/auth/login", json={"email": email, "password": pwd})
    return {"Authorization": f"Bearer {login.json()['access_token']}"}


def create_demo_event(
    client,
    org_headers,
    uid: str,
    participation: str = "solo",
    seat_limit: int | None = 100,
    eligibility: str | None = None,
    deadline_offset_hours: int = 24,
    start_offset_hours: int = 48,
):
    now = datetime.now(timezone.utc)
    start_at = now + timedelta(hours=start_offset_hours)
    check_in_at = start_at - timedelta(hours=1)
    deadline = now + timedelta(hours=deadline_offset_hours)

    payload = {
        "title": f"Test Solo Event {uid}",
        "description": "Solo test event description",
        "category": "Workshop",
        "tags": ["Coding"],
        "mode": "offline",
        "start_at": start_at.isoformat(),
        "end_at": (start_at + timedelta(hours=3)).isoformat(),
        "check_in_at": check_in_at.isoformat(),
        "registration_deadline": deadline.isoformat(),
        "timezone": "Asia/Kolkata",
        "venue_name": "Auditorium",
        "venue_lat": 28.5459,
        "venue_lng": 77.1926,
        "participation_type": participation,
        "team_min": 1 if participation == "solo" else 2,
        "team_max": 1 if participation == "solo" else 4,
        "seat_limit": seat_limit,
        "fee_amount": 0.0,
        "eligibility": eligibility,
    }

    res = client.post("/api/v1/events", headers=org_headers, json=payload)
    assert res.status_code == 201, res.text
    event_id = res.json()["id"]

    # Publish event
    pub = client.post(f"/api/v1/events/{event_id}/publish", headers=org_headers)
    assert pub.status_code == 200, pub.text
    return event_id


def test_solo_registration_happy_path_and_me_list(client, org_headers):
    uid = uuid.uuid4().hex[:6]
    event_id = create_demo_event(client, org_headers, uid)
    student_headers = make_student(client)

    # 1. Register solo
    reg_res = client.post(f"/api/v1/events/{event_id}/register", headers=student_headers, json={})
    assert reg_res.status_code == 201, reg_res.text
    data = reg_res.json()
    assert data["event_id"] == event_id
    assert data["status"] == "registered"
    assert data["ticket_token"] is not None
    assert data["journey_plan"] is None
    assert data["event"]["title"] == f"Test Solo Event {uid}"

    # 2. GET /me/registrations
    me_res = client.get("/api/v1/me/registrations", headers=student_headers)
    assert me_res.status_code == 200
    me_data = me_res.json()
    assert me_data["total"] >= 1
    assert any(item["event_id"] == event_id for item in me_data["items"])


def test_registration_error_codes(client, org_headers):
    uid = uuid.uuid4().hex[:6]
    student_headers = make_student(client, "iitd.ac.in")
    student2_headers = make_student(client, "nsut.ac.in")

    # 1. NOT_FOUND: non-existent event
    fake_id = uuid.uuid4()
    res_nf = client.post(f"/api/v1/events/{fake_id}/register", headers=student_headers, json={})
    assert res_nf.status_code == 404
    assert res_nf.json()["code"] == "NOT_FOUND"

    # 2. EVENT_CLOSED: deadline in past
    past_event_id = create_demo_event(client, org_headers, f"{uid}_past", deadline_offset_hours=-2)
    res_closed = client.post(f"/api/v1/events/{past_event_id}/register", headers=student_headers, json={})
    assert res_closed.status_code == 409
    assert res_closed.json()["code"] == "EVENT_CLOSED"

    # 3. VALIDATION_ERROR: solo registration on team-only event
    team_event_id = create_demo_event(client, org_headers, f"{uid}_team", participation="team")
    res_val = client.post(f"/api/v1/events/{team_event_id}/register", headers=student_headers, json={})
    assert res_val.status_code == 422
    assert res_val.json()["code"] == "VALIDATION_ERROR"

    # 4. NOT_ELIGIBLE: eligibility restricts to @iitd.ac.in, but student2 is @nsut.ac.in
    el_event_id = create_demo_event(client, org_headers, f"{uid}_el", eligibility="@iitd.ac.in")
    res_not_el = client.post(f"/api/v1/events/{el_event_id}/register", headers=student2_headers, json={})
    assert res_not_el.status_code == 403
    assert res_not_el.json()["code"] == "NOT_ELIGIBLE"

    # 5. ALREADY_REGISTERED: register twice
    ev_dup = create_demo_event(client, org_headers, f"{uid}_dup")
    r1 = client.post(f"/api/v1/events/{ev_dup}/register", headers=student_headers, json={})
    assert r1.status_code == 201
    r2 = client.post(f"/api/v1/events/{ev_dup}/register", headers=student_headers, json={})
    assert r2.status_code == 409
    assert r2.json()["code"] == "ALREADY_REGISTERED"

    # 6. TIME_CLASH: Event A and Event B at identical time
    ev_clash = create_demo_event(client, org_headers, f"{uid}_clash")
    res_clash = client.post(f"/api/v1/events/{ev_clash}/register", headers=student_headers, json={})
    assert res_clash.status_code == 409
    assert res_clash.json()["code"] == "TIME_CLASH"

    # Acknowledge clash succeeds
    res_ack = client.post(
        f"/api/v1/events/{ev_clash}/register",
        headers=student_headers,
        json={"acknowledge_clash": True},
    )
    assert res_ack.status_code == 201


def test_cancel_frees_seat_and_re_registration(client, org_headers):
    uid = uuid.uuid4().hex[:6]
    # Event with seat limit = 1
    event_id = create_demo_event(client, org_headers, uid, seat_limit=1)
    student1_headers = make_student(client)
    student2_headers = make_student(client)

    # Student 1 registers -> takes the 1 available seat
    r1 = client.post(f"/api/v1/events/{event_id}/register", headers=student1_headers, json={})
    assert r1.status_code == 201
    reg1_id = r1.json()["id"]

    # Student 2 tries -> EVENT_FULL (409)
    r2 = client.post(f"/api/v1/events/{event_id}/register", headers=student2_headers, json={})
    assert r2.status_code == 409
    assert r2.json()["code"] == "EVENT_FULL"

    # Student 1 cancels
    del_res = client.delete(f"/api/v1/registrations/{reg1_id}", headers=student1_headers)
    assert del_res.status_code == 204

    # Now Student 2 registers -> succeeds
    r2_ok = client.post(f"/api/v1/events/{event_id}/register", headers=student2_headers, json={})
    assert r2_ok.status_code == 201

    # Student 2 cancels, then Student 1 re-registers (cancelled re-register)
    client.delete(f"/api/v1/registrations/{r2_ok.json()['id']}", headers=student2_headers)
    r1_re = client.post(f"/api/v1/events/{event_id}/register", headers=student1_headers, json={})
    assert r1_re.status_code == 201
    assert r1_re.json()["status"] == "registered"


def test_organizer_registrations_roster_csv_and_checkin(client, org_headers, admin_headers):
    uid = uuid.uuid4().hex[:6]
    event_id = create_demo_event(client, org_headers, uid)
    student_headers = make_student(client)

    # Student registers
    reg_res = client.post(f"/api/v1/events/{event_id}/register", headers=student_headers, json={})
    assert reg_res.status_code == 201
    ticket_token = reg_res.json()["ticket_token"]
    reg_id = reg_res.json()["id"]

    # Register a second organizer to test FORBIDDEN check
    uid2 = uuid.uuid4().hex[:6]
    other_org_email = f"other_{uid2}@example.com"
    r = client.post(
        "/api/v1/auth/register",
        json={"name": "Other", "email": other_org_email, "password": "Password123!", "role": "organizer", "organization_name": "Club"},
    )
    client.post("/api/v1/auth/verify-email", json={"email": other_org_email, "code": r.json()["dev_otp"]})
    admin_list = client.get("/api/v1/admin/organizers?status=pending", headers=admin_headers)
    other_prof = next(item for item in admin_list.json()["items"] if item["user"]["email"] == other_org_email)
    client.post(f"/api/v1/admin/organizers/{other_prof['id']}/approve", headers=admin_headers)
    other_login = client.post("/api/v1/auth/login", json={"email": other_org_email, "password": "Password123!"})
    other_headers = {"Authorization": f"Bearer {other_login.json()['access_token']}"}

    # Non-owner organizer gets FORBIDDEN on registrations roster
    res_forbidden = client.get(f"/api/v1/events/{event_id}/registrations", headers=other_headers)
    assert res_forbidden.status_code == 403
    assert res_forbidden.json()["code"] == "FORBIDDEN"

    # Owner organizer gets registrations list
    res_roster = client.get(f"/api/v1/events/{event_id}/registrations", headers=org_headers)
    assert res_roster.status_code == 200
    roster_data = res_roster.json()
    assert roster_data["total"] >= 1
    assert any(item["id"] == reg_id for item in roster_data["items"])

    # CSV export
    res_csv = client.get(f"/api/v1/events/{event_id}/registrations/export", headers=org_headers)
    assert res_csv.status_code == 200
    assert "text/csv" in res_csv.headers["content-type"]
    csv_content = res_csv.text
    assert "Registration ID" in csv_content
    assert "Student Email" in csv_content
    assert "Student Name" in csv_content

    # Check-in with tampered token fails
    res_tampered = client.post(
        f"/api/v1/events/{event_id}/check-in",
        headers=org_headers,
        json={"ticket_token": ticket_token + "tampered"},
    )
    assert res_tampered.status_code == 422
    assert res_tampered.json()["code"] == "VALIDATION_ERROR"

    # Valid check-in succeeds
    res_checkin = client.post(
        f"/api/v1/events/{event_id}/check-in",
        headers=org_headers,
        json={"ticket_token": ticket_token},
    )
    assert res_checkin.status_code == 200
    assert res_checkin.json()["status"] == "checked_in"

    # Repeat check-in rejected
    res_repeat = client.post(
        f"/api/v1/events/{event_id}/check-in",
        headers=org_headers,
        json={"ticket_token": ticket_token},
    )
    assert res_repeat.status_code == 422
    assert res_repeat.json()["code"] == "VALIDATION_ERROR"


def test_event_cancel_cascades_to_registrations(client, org_headers):
    uid = uuid.uuid4().hex[:6]
    event_id = create_demo_event(client, org_headers, uid)
    student_headers = make_student(client)

    # Register student
    reg_res = client.post(f"/api/v1/events/{event_id}/register", headers=student_headers, json={})
    assert reg_res.status_code == 201
    reg_id = reg_res.json()["id"]

    # Organizer cancels event
    cancel_res = client.post(f"/api/v1/events/{event_id}/cancel", headers=org_headers)
    assert cancel_res.status_code == 200

    # Registration status should now be cancelled
    my_regs = client.get("/api/v1/me/registrations", headers=student_headers)
    reg_item = next(item for item in my_regs.json()["items"] if item["id"] == reg_id)
    assert reg_item["status"] == "cancelled"
