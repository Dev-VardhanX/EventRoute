import tempfile
from datetime import UTC, datetime
from pathlib import Path

from eventroute_ml import train
from eventroute_ml.buffer import (
    BufferPredictor,
    reload_model,
    risk_extra_detail,
    risk_extra_minutes,
)
from eventroute_ml.simulate import simulate

DEP = datetime(2026, 10, 23, 3, 30, tzinfo=UTC)      # 09:00 IST Friday = peak


def test_missing_model_falls_back_to_rules():
    reload_model(Path("/nonexistent/model.joblib"))
    d = risk_extra_detail("drive", DEP, 12000)
    assert d["source"] == "rules" and d["minutes"] == 15.0
    assert "rule table" in d["label"]


def test_never_raises_on_garbage():
    reload_model(Path("/nonexistent/model.joblib"))
    for args in [("", DEP, -1), (None, DEP, float("nan")), ("metro", None, 5000), ("walk", DEP, 1e12, 99, 9.9)]:
        v = risk_extra_minutes(*args)
        assert 0.0 <= v <= 20.0


def test_trained_model_roundtrip_and_sanity():
    with tempfile.TemporaryDirectory() as tmp:
        df = simulate(n_routes=600, trips_per_route=8, seed=3)
        csv = Path(tmp) / "trips.csv"
        df.to_csv(csv, index=False)
        meta = train.run(str(csv), synthetic=False, out_dir=Path(tmp), seed=3)
        assert meta["contains_synthetic_data"] is True
        p = BufferPredictor(Path(tmp) / "buffer_v1.joblib")
        assert p.model is not None and p.load_error is None
        peak = p.predict("drive", 9, 1, 12000)
        off = p.predict("drive", 14, 1, 12000)
        assert peak["source"] == "model" and 0.0 <= peak["minutes"] <= 20.0
        assert peak["minutes"] > off["minutes"]                       # peak drive riskier than off-peak
        assert p.predict("walk", 14, 1, 800)["minutes"] < peak["minutes"]
        cov = meta["report"]["holdout"]["model_unclipped"]["coverage"]
        assert 0.84 <= cov <= 0.95, cov                               # a 0.9 quantile model
    reload_model(Path("/nonexistent/model.joblib"))


def test_matched_coverage_baseline_helper():
    import numpy as np

    from eventroute_ml.metrics import coverage, matched_coverage_scale
    y = np.array([1.0, 2.0, 3.0, 4.0, 10.0] * 20)
    base = np.full_like(y, 2.0)
    r = matched_coverage_scale(y, base, 0.9)
    assert r["reached_target"] and r["coverage"] >= 0.9 and r["scale"] >= 2.0
    assert coverage(y, np.clip(base * r["scale"], 0, 20)) >= 0.9


def test_report_contains_calibration_and_fair_baseline():
    df = simulate(n_routes=500, trips_per_route=8, seed=5)
    rep = train.evaluate_split(df, 5)
    assert "rule_table_scaled_to_same_coverage" in rep["holdout"]
    assert set(rep["calibration_by_distance"]).issubset({"0-3km", "3-8km", "8-15km", "15km+"})
    assert rep["holdout"]["pinball_q"]["model_clipped"] < rep["holdout"]["pinball_q"]["rule_scaled"]


def test_risk_extra_iso_string_and_none_transfers():
    reload_model(Path("/nonexistent/model.joblib"))
    # ISO string with UTC 'Z' should parse to 09:00 IST Friday (peak drive = 15.0 min)
    d = risk_extra_detail("drive", "2026-10-23T03:30:00Z", 12000, transfers=None)
    assert d["source"] == "rules"
    assert d["minutes"] == 15.0


def test_corrupt_bundle_falls_back(tmp_path):
    import joblib
    bad_model_path = tmp_path / "bad.joblib"
    joblib.dump({"broken": True}, bad_model_path)
    p = BufferPredictor(bad_model_path)
    assert p.model is None
    assert p.load_error is not None
    res = p.predict("drive", 9, 1, 12000)
    assert res["source"] == "rules" and res["minutes"] == 15.0


def test_training_determinism(tmp_path):
    dir1 = tmp_path / "run1"
    dir2 = tmp_path / "run2"
    dir1.mkdir(parents=True)
    dir2.mkdir(parents=True)
    df = simulate(n_routes=300, trips_per_route=6, seed=42)
    csv1 = dir1 / "trips.csv"
    csv2 = dir2 / "trips.csv"
    df.to_csv(csv1, index=False)
    df.to_csv(csv2, index=False)

    meta1 = train.run(str(csv1), synthetic=False, out_dir=dir1, seed=42)
    meta2 = train.run(str(csv2), synthetic=False, out_dir=dir2, seed=42)

    assert meta1["rows"] == meta2["rows"]
    assert meta1["routes"] == meta2["routes"]
    assert meta1["report"] == meta2["report"]

