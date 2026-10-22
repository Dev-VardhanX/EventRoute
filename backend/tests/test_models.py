import uuid
from datetime import datetime, timedelta, timezone
import pytest
from sqlalchemy.exc import IntegrityError

from app.core.db import SessionLocal
from app.models import (
    College,
    Event,
    EventMode,
    EventStatus,
    ParticipationType,
    Registration,
    RegistrationStatus,
    RouteCache,
    ScheduledNotification,
    Team,
    User,
    UserRole,
)


@pytest.fixture
def db():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.rollback()
        session.close()


def create_test_user(db, email_prefix="user") -> User:
    uid = uuid.uuid4().hex[:8]
    user = User(
        name=f"Test User {uid}",
        email=f"{email_prefix}_{uid}@example.com",
        role=UserRole.STUDENT,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user


def create_test_college(db) -> College:
    uid = uuid.uuid4().hex[:8]
    college = College(
        name=f"Test Institute {uid}",
        city="New Delhi",
        lat=28.5459,
        lng=77.1926,
    )
    db.add(college)
    db.commit()
    db.refresh(college)
    return college


def create_test_event(
    db,
    organizer: User,
    team_min: int = 1,
    team_max: int = 4,
    start_at: datetime | None = None,
    check_in_at: datetime | None = None,
    registration_deadline: datetime | None = None,
) -> Event:
    now = datetime.now(timezone.utc)
    now_start = start_at or (now + timedelta(days=5))
    now_checkin = check_in_at or (now_start - timedelta(hours=1))
    now_deadline = registration_deadline or (now_checkin - timedelta(hours=2))

    event = Event(
        organizer_id=organizer.id,
        title=f"Test Event {uuid.uuid4().hex[:8]}",
        category="hackathon",
        mode=EventMode.OFFLINE,
        start_at=now_start,
        check_in_at=now_checkin,
        registration_deadline=now_deadline,
        participation_type=ParticipationType.EITHER,
        team_min=team_min,
        team_max=team_max,
        status=EventStatus.PUBLISHED,
    )
    db.add(event)
    db.commit()
    db.refresh(event)
    return event


def test_duplicate_registration_is_rejected(db):
    user = create_test_user(db, "reg_user")
    org = create_test_user(db, "org_user")
    event = create_test_event(db, org)

    reg1 = Registration(
        event_id=event.id,
        user_id=user.id,
        status=RegistrationStatus.REGISTERED,
    )
    db.add(reg1)
    db.commit()

    reg2 = Registration(
        event_id=event.id,
        user_id=user.id,
        status=RegistrationStatus.REGISTERED,
    )
    db.add(reg2)
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()


def test_team_min_greater_than_team_max_is_rejected(db):
    org = create_test_user(db, "org_minmax")
    now = datetime.now(timezone.utc)
    event = Event(
        organizer_id=org.id,
        title="Invalid MinMax Event",
        category="hackathon",
        start_at=now + timedelta(days=2),
        check_in_at=now + timedelta(days=2, hours=-1),
        registration_deadline=now + timedelta(days=2, hours=-2),
        team_min=5,
        team_max=2,  # Invalid: team_min > team_max
    )
    db.add(event)
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()


def test_bad_deadline_order_is_rejected(db):
    org = create_test_user(db, "org_deadline")
    now = datetime.now(timezone.utc)

    # 1. check_in_at > start_at
    event1 = Event(
        organizer_id=org.id,
        title="Bad CheckIn Order",
        category="hackathon",
        start_at=now + timedelta(days=1),
        check_in_at=now + timedelta(days=1, hours=2),  # check_in after start!
        registration_deadline=now + timedelta(hours=12),
        team_min=1,
        team_max=2,
    )
    db.add(event1)
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()

    # 2. registration_deadline > check_in_at
    event2 = Event(
        organizer_id=org.id,
        title="Bad Deadline Order",
        category="hackathon",
        start_at=now + timedelta(days=2),
        check_in_at=now + timedelta(days=2, hours=-2),
        registration_deadline=now + timedelta(days=2, hours=-1),  # deadline after check_in!
        team_min=1,
        team_max=2,
    )
    db.add(event2)
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()


def test_duplicate_invite_code_is_rejected(db):
    org = create_test_user(db, "org_team")
    user1 = create_test_user(db, "leader1")
    user2 = create_test_user(db, "leader2")
    event = create_test_event(db, org)

    invite_code = f"INV-{uuid.uuid4().hex[:6].upper()}"

    team1 = Team(
        event_id=event.id,
        leader_id=user1.id,
        name="Team Alpha",
        invite_code=invite_code,
    )
    db.add(team1)
    db.commit()

    team2 = Team(
        event_id=event.id,
        leader_id=user2.id,
        name="Team Beta",
        invite_code=invite_code,  # Duplicate!
    )
    db.add(team2)
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()


def test_event_cannot_be_deleted_when_registrations_exist(db):
    org = create_test_user(db, "org_del")
    user = create_test_user(db, "student_del")
    event = create_test_event(db, org)

    reg = Registration(
        event_id=event.id,
        user_id=user.id,
    )
    db.add(reg)
    db.commit()

    # Trying to delete event should fail due to ondelete=RESTRICT
    db.delete(event)
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()


def test_duplicate_email_and_lowercase_enforcement(db):
    uid = uuid.uuid4().hex[:6]
    user1 = User(
        name="Case Test User",
        email=f"User_{uid}@Example.COM",
    )
    db.add(user1)
    db.commit()
    db.refresh(user1)

    # Validates stored as lowercase
    assert user1.email == f"user_{uid}@example.com"

    # Attempt duplicate insert with different case
    user2 = User(
        name="Duplicate User",
        email=f"USER_{uid}@EXAMPLE.com",
    )
    db.add(user2)
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()


def test_duplicate_route_cache_key_is_rejected(db):
    key = f"cache_{uuid.uuid4().hex[:8]}"
    expires = datetime.now(timezone.utc) + timedelta(minutes=10)
    c1 = RouteCache(cache_key=key, response_json={"data": 1}, expires_at=expires)
    db.add(c1)
    db.commit()

    c2 = RouteCache(cache_key=key, response_json={"data": 2}, expires_at=expires)
    db.add(c2)
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()


def test_duplicate_scheduled_notification_is_rejected(db):
    user = create_test_user(db, "sched_user")
    org = create_test_user(db, "sched_org")
    event = create_test_event(db, org)
    due_at = datetime.now(timezone.utc) + timedelta(hours=3)

    sn1 = ScheduledNotification(
        user_id=user.id,
        type="leave_soon",
        event_id=event.id,
        due_at=due_at,
    )
    db.add(sn1)
    db.commit()

    sn2 = ScheduledNotification(
        user_id=user.id,
        type="leave_soon",
        event_id=event.id,
        due_at=due_at,
    )
    db.add(sn2)
    with pytest.raises(IntegrityError):
        db.commit()
    db.rollback()
