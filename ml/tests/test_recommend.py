import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

from eventroute_ml.config import REC_WEIGHTS
from eventroute_ml.recommend import (
    EventInfo,
    UserProfile,
    canon,
    interest_match,
    popularity,
    proximity,
    recommend,
    urgency,
)

NOW = datetime(2026, 10, 22, 12, 0, tzinfo=UTC)
USER = UserProfile(interests=["#Hackathons", "#OpenMic", "#Robotics", "#AI"], lat=28.6094, lng=77.0368)


def ev(i, **kw):
    base = dict(
        id=i,
        title=i,
        category="Fest",
        registration_deadline=NOW + timedelta(days=3),
        start_at=NOW + timedelta(days=5),
        registrations_count=10,
    )
    base.update(kw)
    return EventInfo(**base)


def test_weights_sum_to_one():
    assert abs(sum(REC_WEIGHTS.values()) - 1.0) < 1e-9


def test_canon_and_synonyms():
    assert canon("#Hackathons") == "hackathon" and canon("Open Mic") == "openmic"
    v, m, kind = interest_match(USER, ev("a", category="Hackathon"))
    assert v >= 0.7 and kind == "category" and m == "#Hackathons"
    v2, m2, kind2 = interest_match(USER, ev("b", category="Fest", tags=["machine learning", "ai"]))
    assert kind2 == "tag" and abs(v2 - 0.3) < 1e-9
    v3, _, k3 = interest_match(USER, ev("c", category="Fest"))
    assert v3 == 0.0 and k3 == "none"


def test_cold_start_is_neutral():
    v, m, k = interest_match(UserProfile(), ev("x"))
    assert v == 0.5 and m is None and k == "unknown"


def test_urgency_curve():
    assert urgency(ev("u", registration_deadline=NOW + timedelta(hours=5)), NOW)[0] == 1.0
    assert urgency(ev("u", registration_deadline=NOW + timedelta(days=9)), NOW)[0] == 0.0
    mid = urgency(ev("u", registration_deadline=NOW + timedelta(hours=96)), NOW)[0]
    assert 0.0 < mid < 1.0


def test_ranking_reason_and_filters():
    events = [
        ev(
            "hackx",
            title="HackX 2024",
            category="Hackathon",
            tags=["AI"],
            lat=28.5459,
            lng=77.1926,
            travel_minutes=45,
            registrations_count=42,
            registration_deadline=NOW + timedelta(days=2),
        ),
        ev("robo", title="RoboCup Season 5", category="Robotics", lat=28.7501, lng=77.1177, travel_minutes=15),
        ev("fest", category="Fest"),
        ev("cancelled", category="Hackathon", cancelled=True),
        ev("done", category="Hackathon", registered=True),
        ev("past", category="Hackathon", registration_deadline=NOW - timedelta(hours=1)),
        ev("full", category="Hackathon", full=True),
    ]
    recs = recommend(USER, events, NOW)
    ids = [r.event_id for r in recs]
    assert ids[-1] == "fest"
    assert not ({"cancelled", "done", "past", "full"} & set(ids))
    assert ids[0] in ("hackx", "robo")
    top = recs[0]
    assert 0.0 <= top.score <= 1.0 and top.reason and "interest" in top.reason
    assert recommend(USER, events, NOW, limit=1)[0].event_id == ids[0]


def test_deterministic_and_bad_event_skipped():
    events = [ev("a", category="Hackathon"), ev("b", category="Hackathon"), None]
    r1 = [x.event_id for x in recommend(USER, events, NOW)]
    r2 = [x.event_id for x in recommend(USER, list(reversed(events)), NOW)]
    assert r1 == r2 == ["a", "b"]


def test_proximity_from_coordinates():
    near = ev("near", category="Fest", lat=28.61, lng=77.04)
    far = ev("far", category="Fest", lat=28.75, lng=77.12)
    recs = {r.event_id: r for r in recommend(USER, [near, far], NOW)}
    assert recs["near"].components["proximity"] > recs["far"].components["proximity"]


def test_urgency_passed_deadline_is_zero():
    u_val, hours = urgency(ev("past", registration_deadline=NOW - timedelta(hours=3)), NOW)
    assert u_val == 0.0
    assert hours == -3.0


def test_proximity_nan_coordinates_neutral():
    u_nan = UserProfile(lat=float("nan"), lng=77.0368)
    ev_nan = ev("ev_nan", lat=float("nan"), lng=77.1)
    p_val, mins = proximity(u_nan, ev("a"))
    assert p_val == 0.5 and mins is None
    p_val2, mins2 = proximity(USER, ev_nan)
    assert p_val2 == 0.5 and mins2 is None


def test_recommend_iso_and_nan_popularity():
    iso_ev = EventInfo(
        id="iso1",
        title="ISO Event",
        category="Hackathon",
        registration_deadline="2026-10-24T12:00:00Z",
        start_at="2026-10-25T12:00:00Z",
        capacity=float("nan"),
        registrations_count=None,
    )
    recs = recommend(USER, [iso_ev], NOW)
    assert len(recs) == 1 and recs[0].event_id == "iso1"


def test_edge_cases_coordinates_deadline_capacity_counts():
    """Verify events with no coords, no deadline, no capacity, zero or huge registrations."""
    weird_events = [
        ev("no_coords", lat=None, lng=None, travel_minutes=None),
        ev("no_deadline", registration_deadline=None),
        ev("no_capacity", capacity=None, registrations_count=0),
        ev("zero_reg", registrations_count=0, capacity=100),
        ev("huge_reg", registrations_count=1_000_000, capacity=None),
        ev("zero_cap", capacity=0, registrations_count=50),
    ]
    recs = recommend(USER, weird_events, NOW)
    assert len(recs) == len(weird_events)
    for r in recs:
        assert 0.0 <= r.score <= 1.0
        assert r.reason != ""
        assert len(r.reason) <= 70

    # Test popularity bounds directly
    assert popularity(ev("zero", registrations_count=0))[0] == 0.0
    assert popularity(ev("huge", registrations_count=1_000_000))[0] == 1.0


def test_odd_casing_and_punctuation_interests():
    """Verify interests with odd casing, whitespace, and symbols like #, !, /."""
    odd_user = UserProfile(
        interests=[" #AI! ", " Open Mic ", "ai/ml", " #!Robotics?! "],
        lat=28.6,
        lng=77.0,
    )
    e_ai = ev("e_ai", category="AI", title="Artificial Intelligence Con")
    e_mic = ev("e_mic", category="Open Mic", title="Comedy Night")
    e_rob = ev("e_rob", category="Robotics")
    e_unrelated = ev("e_none", category="Pottery")

    recs = recommend(odd_user, [e_ai, e_mic, e_rob, e_unrelated], NOW)
    ids = [r.event_id for r in recs]

    assert "e_none" == ids[-1]
    assert recs[0].components["interest"] >= 0.7
    # Check that reason display does not have odd double ## or trailing spaces
    for r in recs[:3]:
        assert "##" not in r.reason
        assert " # " not in r.reason
        assert len(r.reason) <= 70


def test_duplicate_event_ids_deduplicated():
    """Verify that duplicate event IDs in input list are deduplicated."""
    events = [
        ev("dup1", category="Hackathon", registrations_count=10),
        ev("dup1", category="Hackathon", registrations_count=20),
        ev("unique2", category="Robotics"),
    ]
    recs = recommend(USER, events, NOW)
    assert len(recs) == 2
    assert [r.event_id for r in recs] == ["dup1", "unique2"]


def test_deterministic_tie_breaking():
    """Verify tie-breaking is strictly deterministic regardless of input list ordering."""
    # Two events with identical scores and identical dates
    e1 = ev("alpha_event", category="Hackathon", registrations_count=10, travel_minutes=30)
    e2 = ev("beta_event", category="Hackathon", registrations_count=10, travel_minutes=30)

    recs_order1 = [r.event_id for r in recommend(USER, [e1, e2], NOW)]
    recs_order2 = [r.event_id for r in recommend(USER, [e2, e1], NOW)]

    # "alpha_event" < "beta_event" lexicographically
    assert recs_order1 == ["alpha_event", "beta_event"]
    assert recs_order2 == ["alpha_event", "beta_event"]


def test_reason_string_properties():
    """Verify reason string is never empty, <= 70 chars, and never mentions missing data."""
    # Event with missing travel minutes and missing coords
    no_loc = ev("no_loc", lat=None, lng=None, travel_minutes=None)
    rec_no_loc = recommend(USER, [no_loc], NOW)[0]
    assert "min away" not in rec_no_loc.reason
    assert len(rec_no_loc.reason) <= 70
    assert rec_no_loc.reason != ""

    # Long event with multiple components
    long_ev = ev(
        "long",
        title="Super Extremely Long Tech Festival Hackathon and Global Gathering",
        category="Hackathons",
        tags=["AI", "Web3", "Robotics"],
        travel_minutes=5,
        registrations_count=99,
        capacity=100,
        registration_deadline=NOW + timedelta(hours=4),
    )
    rec_long = recommend(USER, [long_ev], NOW)[0]
    assert len(rec_long.reason) <= 70
    assert rec_long.reason != ""


def test_interests_rank_above_non_matching():
    """Verify events matching user interests rank above non-matching ones with equal proximity/popularity."""
    u = UserProfile(interests=["Hackathons"], lat=28.6, lng=77.0)
    matching = ev("matching", category="Hackathons", travel_minutes=30, registrations_count=50)
    non_matching = ev("non_matching", category="Gardening", travel_minutes=30, registrations_count=50)

    recs = recommend(u, [non_matching, matching], NOW)
    assert recs[0].event_id == "matching"
    assert recs[0].score > recs[1].score


def test_recommendations_json_artifact():
    """Verify that recommendations.json was generated and only contains valid event IDs from events.json."""
    fixtures_dir = Path(__file__).parent / "fixtures"
    out_file = Path(__file__).parent.parent / "out" / "recommendations.json"

    with open(fixtures_dir / "events.json", "r", encoding="utf-8") as f:
        valid_events = json.load(f)["items"]
    valid_ids = {e["id"] for e in valid_events}

    assert out_file.exists(), "ml/out/recommendations.json should exist"
    with open(out_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    assert "user_id" in data
    assert "items" in data
    assert len(data["items"]) > 0

    for item in data["items"]:
        assert item["event_id"] in valid_ids, f"Event ID {item['event_id']} not found in events.json"
        assert 0.0 <= item["score"] <= 1.0
        assert item["reason"] != ""
        assert len(item["reason"]) <= 70
