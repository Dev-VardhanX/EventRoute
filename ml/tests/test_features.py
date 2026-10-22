from eventroute_ml.features import (
    FEATURE_NAMES,
    TripFeatures,
    dominant_mode,
    route_to_features,
    row,
    sanitize,
)


def test_row_length_matches_names():
    assert len(row(TripFeatures("metro", 9, 2, 12000, 1, 0.2))) == len(FEATURE_NAMES)


def test_sanitize_clamps():
    f = sanitize(TripFeatures("METRO", 27, 9, -5.0, -2, 7.0))
    assert f.mode == "metro" and f.hour == 3 and f.weekday == 2
    assert f.distance_m == 0.0 and f.transfers == 0 and f.walking_share == 1.0


def test_nan_rejected():
    try:
        sanitize(TripFeatures("bus", 9, 1, float("nan")))
    except ValueError:
        return
    raise AssertionError("NaN distance must be rejected")


def test_dominant_mode_and_route_mapping():
    legs = [{"mode": "walk", "duration_s": 480}, {"mode": "metro", "duration_s": 720},
            {"mode": "metro", "duration_s": 1200}, {"mode": "walk", "duration_s": 300}]
    assert dominant_mode(legs) == "metro"
    assert dominant_mode([{"mode": "walk", "duration_s": 100}]) == "walk"
    route = {"duration_s": 2700, "distance_m": 23500, "transfers": 1, "walking_s": 780, "legs": legs}
    f = route_to_features(route, 8, 4)
    assert f.mode == "metro" and f.transfers == 1 and abs(f.walking_share - 780 / 2700) < 1e-9


def test_sanitize_robustness():
    # None transfers, hour, weekday, walking_share should be made safe with defaults
    f = sanitize(TripFeatures(" drive ", None, None, 5000.0, None, None))
    assert f.mode == "drive"
    assert f.hour == 12 and f.weekday == 2
    assert f.distance_m == 5000.0 and f.transfers == 0 and f.walking_share == 0.0

    # NaN transfers or hour must raise ValueError
    import pytest
    with pytest.raises(ValueError):
        sanitize(TripFeatures("bus", 9, 1, 1000.0, transfers=float("nan")))


def test_route_to_features_empty_or_malformed():
    f = route_to_features({}, 9, 1)
    assert f.mode == "walk" and f.distance_m == 0.0 and f.transfers == 0 and f.walking_share == 0.0

    # Legs containing None or non-dict items are ignored safely
    legs = [None, {"mode": "drive", "duration_s": 600}]
    assert dominant_mode(legs) == "drive"

