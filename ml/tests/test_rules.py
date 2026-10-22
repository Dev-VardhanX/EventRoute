from datetime import UTC, datetime

from eventroute_ml.rules import RULE_TABLE, is_peak, local_hour_weekday, rule_risk_extra


def test_peak_windows():
    assert is_peak(8, 0) and is_peak(10, 4) and is_peak(17, 2) and is_peak(20, 1)
    assert not is_peak(11, 0) and not is_peak(16, 0) and not is_peak(21, 0)
    assert not is_peak(9, 5) and not is_peak(18, 6)            # weekends


def test_table_values_and_transfers():
    assert rule_risk_extra("drive", 9, 1) == 15.0
    assert rule_risk_extra("drive", 14, 1) == 5.0
    assert rule_risk_extra("metro", 9, 1, transfers=2) == 4.0 + 4.0


def test_clamped_and_unknown_mode():
    assert rule_risk_extra("drive", 9, 1, transfers=50) == 20.0
    assert 0.0 <= rule_risk_extra("hyperloop", 9, 1) <= 20.0
    assert rule_risk_extra("walk", 9, 1, transfers=-3) == 2.0


def test_all_modes_in_range():
    for m in RULE_TABLE:
        for h in range(24):
            for d in range(7):
                assert 0.0 <= rule_risk_extra(m, h, d, 3) <= 20.0


def test_timezone_conversion():
    # 03:30 UTC is 09:00 in Asia/Kolkata (UTC+5:30), Friday 2026-10-23
    dt = datetime(2026, 10, 23, 3, 30, tzinfo=UTC)
    assert local_hour_weekday(dt) == (9, 4)
    assert local_hour_weekday(datetime(2026, 10, 23, 9, 0)) == (9, 4)    # naive = local


def test_ist_fixed_offset_fallback_matches_zoneinfo():
    from eventroute_ml import rules
    dt = datetime(2026, 10, 23, 3, 30, tzinfo=UTC)
    via_fixed = dt.astimezone(rules._IST)
    assert (via_fixed.hour, via_fixed.weekday()) == (9, 4)
    # an unknown, non-India zone must still raise (buffer.py then falls back safely)
    try:
        rules._zone("Mars/Olympus")
    except Exception:
        return
    raise AssertionError("unknown zone should raise")


def test_rule_edge_cases_transfers_and_whitespace():
    # transfers=None should not raise TypeError and should treat as 0
    assert rule_risk_extra("drive", 9, 1, transfers=None) == 15.0
    # surrounding whitespace in mode should be stripped
    assert rule_risk_extra(" drive ", 9, 1) == 15.0
    assert rule_risk_extra("METRO ", 9, 1, transfers=1) == 6.0
    # None tz in local_hour_weekday defaults to DEFAULT_TZ (Asia/Kolkata)
    dt = datetime(2026, 10, 23, 3, 30, tzinfo=UTC)
    assert local_hour_weekday(dt, tz=None) == (9, 4)
    # String or out-of-range hour/weekday are safely handled
    assert rule_risk_extra("drive", "9", "1") == 15.0
    assert 0.0 <= rule_risk_extra("drive", None, None) <= 20.0

