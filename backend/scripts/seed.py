"""Seed script for EventRoute demo data.

Usage:
    python scripts/seed.py
"""
import sys
from pathlib import Path

# Ensure app package is accessible
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from sqlalchemy import select
from app.core.db import SessionLocal
from app.core.security import hash_password
from app.models.college import College
from app.models.enums import OrganizerStatus, UserRole
from app.models.organizer_profile import OrganizerProfile
from app.models.user import User

DEMO_COLLEGES = [
    {
        "name": "Indian Institute of Technology Delhi (IITD)",
        "city": "New Delhi",
        "lat": 28.5459,
        "lng": 77.1926,
        "email_domain": "iitd.ac.in",
        "verified": True,
        "is_demo": True,
    },
    {
        "name": "Netaji Subhas University of Technology (NSUT)",
        "city": "New Delhi",
        "lat": 28.6094,
        "lng": 77.0368,
        "email_domain": "nsut.ac.in",
        "verified": True,
        "is_demo": True,
    },
    {
        "name": "Indira Gandhi Delhi Technical University for Women (IGDTUW)",
        "city": "Delhi",
        "lat": 28.6652,
        "lng": 77.2324,
        "email_domain": "igdtuw.ac.in",
        "verified": True,
        "is_demo": True,
    },
]


def seed_colleges(db) -> dict[str, College]:
    colleges_by_domain: dict[str, College] = {}
    for c_data in DEMO_COLLEGES:
        domain = c_data["email_domain"]
        existing = db.execute(
            select(College).where(College.email_domain == domain)
        ).scalars().first()
        if not existing:
            college = College(**c_data)
            db.add(college)
            db.flush()
            colleges_by_domain[domain] = college
        else:
            existing.name = c_data["name"]
            existing.city = c_data["city"]
            existing.lat = c_data["lat"]
            existing.lng = c_data["lng"]
            existing.is_demo = True
            existing.verified = True
            colleges_by_domain[domain] = existing
    db.commit()
    return colleges_by_domain


def seed_users(db, colleges: dict[str, College]) -> None:
    # 1. Admin user
    admin_email = "admin@eventroute.dev"
    admin = db.execute(select(User).where(User.email == admin_email)).scalar_one_or_none()
    if not admin:
        admin = User(
            name="EventRoute Admin",
            email=admin_email,
            password_hash=hash_password("Admin@123"),
            role=UserRole.ADMIN,
            email_verified=True,
            is_active=True,
            is_demo=True,
        )
        db.add(admin)
        db.flush()
    else:
        admin.password_hash = hash_password("Admin@123")
        admin.email_verified = True
        admin.role = UserRole.ADMIN
        admin.is_active = True
        admin.is_demo = True

    # 2. Approved Organizer
    org_email = "organizer@eventroute.dev"
    org_user = db.execute(select(User).where(User.email == org_email)).scalar_one_or_none()
    if not org_user:
        org_user = User(
            name="EventRoute Demo Organizer",
            email=org_email,
            password_hash=hash_password("Organizer@123"),
            role=UserRole.ORGANIZER,
            email_verified=True,
            is_active=True,
            is_demo=True,
            college_id=colleges.get("iitd.ac.in").id if "iitd.ac.in" in colleges else None,
        )
        db.add(org_user)
        db.flush()
    else:
        org_user.password_hash = hash_password("Organizer@123")
        org_user.email_verified = True
        org_user.role = UserRole.ORGANIZER
        org_user.is_active = True
        org_user.is_demo = True

    # Organizer Profile
    profile = db.execute(
        select(OrganizerProfile).where(OrganizerProfile.user_id == org_user.id)
    ).scalar_one_or_none()
    if not profile:
        profile = OrganizerProfile(
            user_id=org_user.id,
            organization_name="ACM Student Chapter",
            college_id=org_user.college_id,
            status=OrganizerStatus.APPROVED,
            reviewed_by=admin.id,
        )
        db.add(profile)
    else:
        profile.organization_name = "ACM Student Chapter"
        profile.status = OrganizerStatus.APPROVED
        profile.reviewed_by = admin.id

    # 3. 3 Verified Students on different demo colleges
    students_data = [
        {
            "name": "Aarav Sharma",
            "email": "student_iitd@iitd.ac.in",
            "domain": "iitd.ac.in",
            "home_lat": 28.5355,
            "home_lng": 77.2100,
            "home_label": "Saket",
        },
        {
            "name": "Bhavya Verma",
            "email": "student_nsut@nsut.ac.in",
            "domain": "nsut.ac.in",
            "home_lat": 28.6280,
            "home_lng": 77.0850,
            "home_label": "Janakpuri",
        },
        {
            "name": "Chhavi Gupta",
            "email": "student_igdtuw@igdtuw.ac.in",
            "domain": "igdtuw.ac.in",
            "home_lat": 28.6920,
            "home_lng": 77.1450,
            "home_label": "Pitampura",
        },
    ]

    for s_info in students_data:
        s_user = db.execute(select(User).where(User.email == s_info["email"])).scalar_one_or_none()
        college = colleges.get(s_info["domain"])
        if not s_user:
            s_user = User(
                name=s_info["name"],
                email=s_info["email"],
                password_hash=hash_password("Student@123"),
                role=UserRole.STUDENT,
                email_verified=True,
                is_active=True,
                is_demo=True,
                college_id=college.id if college else None,
                home_lat=s_info["home_lat"],
                home_lng=s_info["home_lng"],
                home_label=s_info["home_label"],
            )
            db.add(s_user)
        else:
            s_user.name = s_info["name"]
            s_user.password_hash = hash_password("Student@123")
            s_user.role = UserRole.STUDENT
            s_user.email_verified = True
            s_user.is_active = True
            s_user.is_demo = True
            if college:
                s_user.college_id = college.id
            s_user.home_lat = s_info["home_lat"]
            s_user.home_lng = s_info["home_lng"]
            s_user.home_label = s_info["home_label"]

    db.commit()


def seed_all():
    db = SessionLocal()
    try:
        colleges = seed_colleges(db)
        seed_users(db, colleges)
        print("Demo colleges and users seeded successfully.")
    finally:
        db.close()


if __name__ == "__main__":
    seed_all()
