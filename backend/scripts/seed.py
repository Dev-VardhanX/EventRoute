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
from app.models.college import College

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


def seed_colleges() -> list[College]:
    db = SessionLocal()
    seeded = []
    try:
        for c_data in DEMO_COLLEGES:
            existing = db.execute(
                select(College).where(College.email_domain == c_data["email_domain"])
            ).scalars().first()
            if not existing:
                college = College(**c_data)
                db.add(college)
                seeded.append(college)
            else:
                existing.is_demo = True
                existing.verified = True
                seeded.append(existing)
        db.commit()
        print(f"Seeded {len(seeded)} demo colleges successfully.")
        return seeded
    finally:
        db.close()


if __name__ == "__main__":
    seed_colleges()
