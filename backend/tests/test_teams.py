import uuid
from datetime import datetime, timedelta, timezone

import pytest

from app.models.enums import EventStatus, ParticipationType, TeamStatus


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
    return {
        "headers": {"Authorization": f"Bearer {login.json()['access_token']}"},
        "user_id": r.json()["user_id"],
        "name": f"Student {uid}",
        "email": email,
    }


def create_team_event(
    client,
    org_headers,
    uid: str,
    team_min: int = 3,
    team_max: int = 4,
    seat_limit: int = 100,
):
    now = datetime.now(timezone.utc)
    start_at = now + timedelta(days=10)
    check_in_at = start_at - timedelta(hours=1)
    deadline = now + timedelta(days=5)

    payload = {
        "title": f"Team Event {uid}",
        "description": "Team competition",
        "category": "Hackathon",
        "tags": ["AI", "Team"],
        "mode": "offline",
        "start_at": start_at.isoformat(),
        "end_at": (start_at + timedelta(hours=8)).isoformat(),
        "check_in_at": check_in_at.isoformat(),
        "registration_deadline": deadline.isoformat(),
        "timezone": "Asia/Kolkata",
        "venue_name": "Dogra Hall",
        "venue_lat": 28.5459,
        "venue_lng": 77.1926,
        "participation_type": "team",
        "team_min": team_min,
        "team_max": team_max,
        "seat_limit": seat_limit,
        "fee_amount": 0.0,
    }

    res = client.post("/api/v1/events", headers=org_headers, json=payload)
    assert res.status_code == 201, res.text
    event_id = res.json()["id"]

    pub = client.post(f"/api/v1/events/{event_id}/publish", headers=org_headers)
    assert pub.status_code == 200, pub.text
    return event_id


def test_3_students_form_team_and_confirm(client, org_headers):
    uid = uuid.uuid4().hex[:6]
    event_id = create_team_event(client, org_headers, uid, team_min=3, team_max=4)

    s1 = make_student(client)
    s2 = make_student(client)
    s3 = make_student(client)
    s4 = make_student(client)

    # 1. Student 1 creates team
    create_res = client.post(
        f"/api/v1/events/{event_id}/teams",
        headers=s1["headers"],
        json={"name": f"CodeCrafters {uid}"},
    )
    assert create_res.status_code == 201, create_res.text
    team_data = create_res.json()
    team_id = team_data["id"]
    invite_code = team_data["invite_code"]
    assert team_data["status"] == "forming"
    assert team_data["member_count"] == 1
    assert "invite/" in team_data["invite_link"]

    # 2. Public preview via GET /invite/{code}
    invite_preview = client.get(f"/api/v1/invite/{invite_code}")
    assert invite_preview.status_code == 200
    prev_data = invite_preview.json()
    assert prev_data["team_name"] == f"CodeCrafters {uid}"
    assert prev_data["member_count"] == 1
    assert prev_data["spots_left"] == 3  # team_max is 4

    # 3. Student 2 joins
    join2 = client.post(
        "/api/v1/teams/join",
        headers=s2["headers"],
        json={"invite_code": invite_code},
    )
    assert join2.status_code == 200
    assert join2.json()["member_count"] == 2

    # 4. Confirm with only 2 members fails (team_min is 3) -> 409 TEAM_TOO_SMALL
    confirm_fail = client.post(
        f"/api/v1/teams/{team_id}/confirm",
        headers=s1["headers"],
    )
    assert confirm_fail.status_code == 409
    assert confirm_fail.json()["code"] == "TEAM_TOO_SMALL"

    # 5. Student 3 joins
    join3 = client.post(
        "/api/v1/teams/join",
        headers=s3["headers"],
        json={"invite_code": invite_code},
    )
    assert join3.status_code == 200
    assert join3.json()["member_count"] == 3

    # 6. Leader confirms team -> 200 OK, status=confirmed
    confirm_ok = client.post(
        f"/api/v1/teams/{team_id}/confirm",
        headers=s1["headers"],
    )
    assert confirm_ok.status_code == 200
    assert confirm_ok.json()["status"] == "confirmed"

    # 7. Invite after confirm fails -> 404 INVITE_INVALID
    res_inv_after = client.get(f"/api/v1/invite/{invite_code}")
    assert res_inv_after.status_code == 404
    assert res_inv_after.json()["code"] == "INVITE_INVALID"

    join4_fail = client.post(
        "/api/v1/teams/join",
        headers=s4["headers"],
        json={"invite_code": invite_code},
    )
    assert join4_fail.status_code == 404
    assert join4_fail.json()["code"] == "INVITE_INVALID"


def test_team_full_cap(client, org_headers):
    uid = uuid.uuid4().hex[:6]
    event_id = create_team_event(client, org_headers, uid, team_min=2, team_max=2)

    s1 = make_student(client)
    s2 = make_student(client)
    s3 = make_student(client)

    # Student 1 creates team with max 2
    create_res = client.post(
        f"/api/v1/events/{event_id}/teams",
        headers=s1["headers"],
        json={"name": f"Duo {uid}"},
    )
    assert create_res.status_code == 201
    invite_code = create_res.json()["invite_code"]

    # Student 2 joins -> team reaches team_max 2
    j2 = client.post("/api/v1/teams/join", headers=s2["headers"], json={"invite_code": invite_code})
    assert j2.status_code == 200

    # Student 3 tries to join -> 409 TEAM_FULL
    j3 = client.post("/api/v1/teams/join", headers=s3["headers"], json={"invite_code": invite_code})
    assert j3.status_code == 409
    assert j3.json()["code"] == "TEAM_FULL"


def test_leader_leaves_and_leadership_passes(client, org_headers):
    uid = uuid.uuid4().hex[:6]
    event_id = create_team_event(client, org_headers, uid, team_min=2, team_max=4)

    s1 = make_student(client)
    s2 = make_student(client)

    create_res = client.post(
        f"/api/v1/events/{event_id}/teams",
        headers=s1["headers"],
        json={"name": f"PassLeadership {uid}"},
    )
    team_data = create_res.json()
    team_id = team_data["id"]
    invite_code = team_data["invite_code"]

    # S2 joins
    client.post("/api/v1/teams/join", headers=s2["headers"], json={"invite_code": invite_code})

    # Leader S1 removes self from team
    del_res = client.delete(f"/api/v1/teams/{team_id}/members/{s1['user_id']}", headers=s1["headers"])
    assert del_res.status_code == 200

    # Inspect team: leadership passed to S2
    team_res = client.get(f"/api/v1/teams/{team_id}", headers=s2["headers"])
    assert team_res.status_code == 200
    data = team_res.json()
    assert data["leader_id"] == s2["user_id"]
    assert data["member_count"] == 1

    # S2 also leaves -> team is cancelled
    client.delete(f"/api/v1/teams/{team_id}/members/{s2['user_id']}", headers=s2["headers"])
    # Organizer inspects team
    org_team_res = client.get(f"/api/v1/teams/{team_id}", headers=org_headers)
    assert org_team_res.status_code == 200
    assert org_team_res.json()["status"] == "cancelled"


def test_confirmed_team_drops_below_min_and_member_start_point(client, org_headers):
    uid = uuid.uuid4().hex[:6]
    event_id = create_team_event(client, org_headers, uid, team_min=2, team_max=3)

    s1 = make_student(client)
    s2 = make_student(client)

    c = client.post(
        f"/api/v1/events/{event_id}/teams",
        headers=s1["headers"],
        json={"name": f"DropTeam {uid}"},
    )
    team_id = c.json()["id"]
    code = c.json()["invite_code"]

    client.post("/api/v1/teams/join", headers=s2["headers"], json={"invite_code": code})
    client.post(f"/api/v1/teams/{team_id}/confirm", headers=s1["headers"])

    # Update start point
    sp_res = client.put(
        f"/api/v1/teams/{team_id}/me/start-point",
        headers=s2["headers"],
        json={"lat": 28.6094, "lng": 77.0368, "source": "home", "travel_pref": "metro"},
    )
    assert sp_res.status_code == 200
    sp_data = sp_res.json()
    assert sp_data["plan_version"] >= 2  # Incremented when confirmed
    m2 = next(m for m in sp_data["members"] if m["user_id"] == s2["user_id"])
    assert m2["start_lat"] == 28.6094
    assert m2["travel_pref"] == "metro"

    # Member 2 is removed -> team drops below team_min (2) -> demoted back to forming
    client.delete(f"/api/v1/teams/{team_id}/members/{s2['user_id']}", headers=s1["headers"])
    t_after = client.get(f"/api/v1/teams/{team_id}", headers=s1["headers"])
    assert t_after.json()["status"] == "forming"
