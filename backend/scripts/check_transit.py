"""Early test script for transit coverage between key origins and demo venues.

Usage:
    python scripts/check_transit.py
"""
import sys
from pathlib import Path

# Ensure app package is accessible
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.core.config import get_settings
from app.services.routing.google import GoogleRouteProvider
from app.services.routing.fake import FakeProvider

VENUE = ("IIT Delhi (Dogra Hall)", 28.5459, 77.1926)

DEMO_ORIGINS = [
    ("NSUT Dwarka", 28.6094, 77.0368),
    ("IGDTUW Kashmiri Gate", 28.6652, 77.2324),
    ("Connaught Place (Central)", 28.6315, 77.2167),
    ("Saket Metro Station", 28.5244, 77.2066),
    ("Noida Sector 18", 28.5708, 77.3260),
    ("Gurgaon DLF Cyber City", 28.4950, 77.0895),
]


def check_transit_coverage():
    settings = get_settings()
    has_google_key = bool(settings.google_maps_server_key)

    if has_google_key:
        print("[INFO] Using Google Routes API with configured server key.")
        provider = GoogleRouteProvider(api_key=settings.google_maps_server_key)
    else:
        print("[INFO] No Google API key found; testing transit logic with FakeProvider.")
        provider = FakeProvider()

    v_name, v_lat, v_lng = VENUE
    print(f"\nEvaluating Transit Coverage to Venue: {v_name} ({v_lat:.4f}, {v_lng:.4f})")
    print("-" * 88)
    print(f"{'Origin Name':<28} | {'Covered':<8} | {'Duration':<10} | {'Transfers':<10} | {'Summary'}")
    print("-" * 88)

    for name, lat, lng in DEMO_ORIGINS:
        try:
            routes = provider.get_routes(
                origin=(lat, lng),
                destination=(v_lat, v_lng),
                mode="transit",
            )
            if routes and len(routes) > 0:
                best = routes[0]
                dur_min = f"{best.duration_s // 60} min"
                summary = best.summary[:25]
                print(f"{name:<28} | {'YES':<8} | {dur_min:<10} | {best.transfers:<10} | {summary}")
            else:
                print(f"{name:<28} | {'NO':<8} | {'-':<10} | {'-':<10} | No routes found")
        except Exception as exc:
            print(f"{name:<28} | {'NO':<8} | {'-':<10} | {'-':<10} | Error: {exc}")

    print("-" * 88)


if __name__ == "__main__":
    check_transit_coverage()
