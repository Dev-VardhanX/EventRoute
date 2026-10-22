"""Generate sample recommendations JSON file for mobile / backend mocking.

Reads Android mock me.json and events.json, computes personalized recommendations,
and outputs ml/out/recommendations.json with real mock event IDs.
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import UTC, datetime
from pathlib import Path

# Ensure package root is in sys.path
ML_ROOT = Path(__file__).parent.parent
if str(ML_ROOT) not in sys.path:
    sys.path.insert(0, str(ML_ROOT))

from eventroute_ml import event_from_json, recommend, user_from_json


def generate_recommendations(
    user_path: Path,
    events_path: Path,
    out_path: Path,
    now: datetime | None = None,
    limit: int = 10,
) -> dict:
    with open(user_path, "r", encoding="utf-8") as f:
        user_raw = json.load(f)
    with open(events_path, "r", encoding="utf-8") as f:
        events_raw = json.load(f)

    user = user_from_json(user_raw)
    raw_items = events_raw.get("items", []) if isinstance(events_raw, dict) else events_raw
    events = [event_from_json(e) for e in raw_items]

    eval_time = now or datetime(2026, 10, 23, 10, 0, tzinfo=UTC)
    recs = recommend(user=user, events=events, now=eval_time, limit=limit)

    output = {
        "user_id": str(user_raw.get("id", "u-anonymous")),
        "generated_at": eval_time.strftime("%Y-%m-%dT%H:%M:%SZ"),
        "items": [
            {
                "event_id": r.event_id,
                "score": r.score,
                "reason": r.reason,
                "components": r.components,
            }
            for r in recs
        ],
    }

    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, indent=2)

    return output


def main():
    parser = argparse.ArgumentParser(description="Generate recommendations.json from mock data.")
    parser.add_argument(
        "--user",
        default=str(ML_ROOT / "tests" / "fixtures" / "me.json"),
        help="Path to user JSON (default: tests/fixtures/me.json)",
    )
    parser.add_argument(
        "--events",
        default=str(ML_ROOT / "tests" / "fixtures" / "events.json"),
        help="Path to events JSON (default: tests/fixtures/events.json)",
    )
    parser.add_argument(
        "--out",
        default=str(ML_ROOT / "out" / "recommendations.json"),
        help="Path to output JSON (default: out/recommendations.json)",
    )
    args = parser.parse_args()

    out = generate_recommendations(
        user_path=Path(args.user),
        events_path=Path(args.events),
        out_path=Path(args.out),
    )
    print(f"Wrote {len(out['items'])} recommendations for user {out['user_id']} to {args.out}")


if __name__ == "__main__":
    main()
