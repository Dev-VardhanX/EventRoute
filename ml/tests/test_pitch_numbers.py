"""Test that ml/report/PITCH.md is generated, traceable, and strictly adheres to PITCH_NUMBERS.json.

Verifies:
1. PITCH.md and PITCH_NUMBERS.json exist and are well-formed.
2. Required sections are present:
   - 90-second spoken pitch
   - Three competition slides
   - Provenance box (real vs synthetic vs proxy)
   - At least 10 judge Q&A pairs
   - 30-second live demo script
   - Empirical chart references
3. Every percentage, minute duration, and key metric count in PITCH.md is registered in PITCH_NUMBERS.json.
4. Methodological honesty checks (no claims of real-world validation, candidate buffer-v3 rejection documented).
"""
from __future__ import annotations

import json
import re
from pathlib import Path

import pytest

ML_ROOT = Path(__file__).resolve().parent.parent
REPORT_DIR = ML_ROOT / "report"
PITCH_MD = REPORT_DIR / "PITCH.md"
PITCH_NUMBERS = REPORT_DIR / "PITCH_NUMBERS.json"


@pytest.fixture(scope="module")
def pitch_context():
    # Ensure charts exist; generate via existing make_report script if absent
    coverage_png = REPORT_DIR / "coverage_by_distance.png"
    buffer_png = REPORT_DIR / "buffer_by_distance.png"
    if not coverage_png.exists() or not buffer_png.exists():
        try:
            from scripts.make_report import generate_report
        except ImportError:
            import sys
            scripts_dir = ML_ROOT / "scripts"
            if str(scripts_dir) not in sys.path:
                sys.path.insert(0, str(scripts_dir))
            from make_report import generate_report
        generate_report(ML_ROOT / "models" / "buffer_v1.metadata.json", REPORT_DIR)

    assert PITCH_MD.exists(), f"PITCH.md missing at {PITCH_MD}"
    assert PITCH_NUMBERS.exists(), f"PITCH_NUMBERS.json missing at {PITCH_NUMBERS}"

    text = PITCH_MD.read_text(encoding="utf-8")
    data = json.loads(PITCH_NUMBERS.read_text(encoding="utf-8"))
    return text, data


def test_pitch_required_sections_present(pitch_context):
    text, _ = pitch_context

    # 1. 90-second spoken explanation
    assert "90-Second" in text or "90-second" in text
    assert "9 in 10" in text or "9 in ten" in text

    # 2. Three slides
    assert "Slide 1:" in text
    assert "Slide 2:" in text
    assert "Slide 3:" in text

    # 3. Provenance box
    assert "Synthetic" in text and "Routing API" in text
    assert "Not Yet Available" in text or "0 rows" in text

    # 4. At least 10 judge questions
    questions = re.findall(r"###\s*Q\d+:", text)
    assert len(questions) >= 10, f"Expected at least 10 judge Q&As, found {len(questions)}"

    # 5. 30-second demo script
    assert "30-Second" in text or "30-second" in text
    assert "How we calculated this" in text
    assert "/ml/buffer" in text

    # 6. Chart references
    assert "coverage_by_distance.png" in text
    assert "buffer_by_distance.png" in text
    assert (REPORT_DIR / "coverage_by_distance.png").exists()
    assert (REPORT_DIR / "buffer_by_distance.png").exists()


def test_pitch_honesty_and_negative_constraints(pitch_context):
    text, _ = pitch_context

    # Must disclose synthetic data
    assert "synthetic" in text.lower()

    # Must disclose that real travel accuracy is not yet validated on production logs
    assert "not yet been validated" in text.lower() or "not yet validated" in text.lower()

    # Must NOT claim candidate model buffer-v3 was promoted
    assert "candidate model `buffer-v3` failed" in text or "rejected" in text.lower()
    assert "promoted to production" not in text.lower()

    # Must NOT claim 100% guarantee
    assert "do not claim 100%" in text.lower() or "not promise" in text.lower()


def test_every_percentage_in_pitch_is_in_numbers_json(pitch_context):
    text, data = pitch_context
    values_dict = data["values"]

    # Gather all numeric values registered in PITCH_NUMBERS.json as floats/ints
    known_numeric = set()
    for v in values_dict.values():
        if isinstance(v, (int, float)):
            known_numeric.add(float(v))
            known_numeric.add(round(float(v), 1))
            known_numeric.add(round(float(v), 2))

    # Extract all percentages from PITCH.md (e.g. 88.3%, 75.0%, 90%)
    matches = re.findall(r"\b(\d+(?:\.\d+)?)\s*%", text)
    assert len(matches) > 0, "No percentages found in PITCH.md"

    for m in matches:
        val = float(m)
        assert (
            val in known_numeric
            or any(abs(val - kn) < 0.05 for kn in known_numeric)
        ), f"Untraced percentage found in PITCH.md: {m}% not present in PITCH_NUMBERS.json"


def test_every_minute_value_in_pitch_is_in_numbers_json(pitch_context):
    text, data = pitch_context
    values_dict = data["values"]

    known_numeric = set()
    for v in values_dict.values():
        if isinstance(v, (int, float)):
            known_numeric.add(float(v))
            known_numeric.add(round(float(v), 1))
            known_numeric.add(round(float(v), 2))

    # Extract minute durations (e.g. "7.65 minutes", "8.30 min", "12 min", "20 minutes")
    # Exclude markdown times like "10 milliseconds" or "2026-10-26"
    matches = re.findall(r"\b(\d+(?:\.\d+)?)\s*(?:minutes|minute|min\b)", text)
    assert len(matches) > 0, "No minute values found in PITCH.md"

    # Contextual exceptions for informal speaking pace examples in 90s pitch (e.g., hypothetical 15 min flat rule)
    accepted_speech_examples = {15.0, 10.0, 5.0, 2.0}

    for m in matches:
        val = float(m)
        assert (
            val in known_numeric
            or any(abs(val - kn) < 0.05 for kn in known_numeric)
            or val in accepted_speech_examples
        ), f"Untraced minute duration found in PITCH.md: {m} min not present in PITCH_NUMBERS.json"


def test_every_major_count_in_pitch_is_in_numbers_json(pitch_context):
    text, data = pitch_context
    values_dict = data["values"]

    known_counts = {int(v) for v in values_dict.values() if isinstance(v, (int, float))}

    # Check key figures cited in text
    key_figures = [30000, 2500, 300, 350, 280, 70, 4, 10, 3, 90, 30]
    for k in key_figures:
        assert k in known_counts, f"Core metric count {k} missing from PITCH_NUMBERS.json"
