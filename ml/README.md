# EventRoute ML

EventRoute ML provides two core algorithmic modules built in pure Python (no database, no web framework dependencies inside the package):

1. **Delay-Risk Buffer Predictor** (`eventroute_ml.risk_extra_minutes` / `eventroute_ml.risk_extra_detail`): Extra buffer minutes (clamped to 0–20 min) added to departure time so commuters arrive on time 9 out of 10 times. Uses quantile gradient boosting ($q = 0.90$) with an automatic, zero-crash fallback to an expert rule table.
2. **Event Recommendations** (`eventroute_ml.recommend`): Cold-start-friendly multi-criteria scoring function (`0.40 interest + 0.25 proximity + 0.20 popularity + 0.15 urgency`) generating ranked recommendations with explainable text reasons.

---

## Documentation Links

- [DATA.md](file:///d:/Hackaton/SGU/EventRoute/ml/DATA.md) – Provenance of data (what is real vs. synthetic vs. proxy)
- [INTEGRATION.md](file:///d:/Hackaton/SGU/EventRoute/ml/INTEGRATION.md) – Complete guide for backend and mobile integration, benchmarks, schemas, and contract guarantees
- [AUDIT.md](file:///d:/Hackaton/SGU/EventRoute/ml/AUDIT.md) – Pre-commit audit checklist, privacy checks, and model performance metrics
- [VERIFY.md](file:///d:/Hackaton/SGU/EventRoute/ml/VERIFY.md) – Verification steps, test suites, and environment requirements

---

## Quick Start

### Windows (PowerShell)
```powershell
cd ml
python -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
pytest -q
python -m eventroute_ml.train --synthetic
python scripts\make_report.py
```

### macOS / Linux (bash/zsh)
```bash
cd ml
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
pytest -q
python -m eventroute_ml.train --synthetic
python scripts/make_report.py
```

> **Note on Version Pinning:** To guarantee model serialization compatibility and avoid OS binary security flags, this project strictly requires `scikit-learn==1.5.2` (pinned in `requirements.txt` and `requirements.lock.txt`).

---

## How the Backend Uses It

The backend imports the pure-Python functions directly:

```python
from datetime import datetime, timezone
from eventroute_ml import (
    risk_extra_minutes,
    risk_extra_detail,
    recommend,
    UserProfile,
    EventInfo,
)

# 1. Calculate delay-risk buffer minutes
depart_time = datetime(2026, 10, 24, 8, 30, tzinfo=timezone.utc)
extra_mins = risk_extra_minutes(
    mode="metro",
    depart_at=depart_time,
    distance_m=18500,
    transfers=1,
    walking_share=0.15,
)

# 2. Detailed risk inspection for UI explanation
detail = risk_extra_detail("metro", depart_time, 18500, transfers=1, walking_share=0.15)
# Returns: {
#   "minutes": 7.5,
#   "source": "model",       # or "rules" if fallback triggered
#   "version": "buffer-v1",
#   "label": "Delay risk (learned from travel data)"
# }

# 3. Recommend events for a user
user = UserProfile(user_id="usr_01", interests=["#Hackathons", "#AI"], lat=28.6094, lng=77.0368)
events = [
    EventInfo(
        event_id="evt_01",
        title="AI Hackathon",
        category="Tech",
        tags=["#Hackathons", "#AI"],
        lat=28.5459,
        lng=77.1926,
        start_time="2026-10-24T10:00:00Z",
        capacity=100,
        registration_count=65,
    )
]
ranked = recommend(user=user, events=events, now=datetime.now(timezone.utc))
for r in ranked:
    print(r.event_id, r.score, r.reason)
```

### Zero-Crash and Fallback Contract
`risk_extra_*` and `recommend` **never raise unhandled exceptions**. In the presence of missing model files, corrupted weights, missing fields, or invalid coordinates, the system falls back gracefully to a curated rule table (`source: "rules"`) and safe clamped bounds (`[0.0, 20.0]` minutes).

---

## How to Retrain

### 1. Retraining with Synthetic Data (Smoke Run / Baseline)
```bash
python -m eventroute_ml.train --synthetic
```
Outputs:
- Model artifact: `models/buffer_v1.joblib`
- Metadata: `models/buffer_v1.metadata.json`

### 2. Retraining from Real Backend Logs (`travel_logs`)
When real student trips are logged in the backend's `travel_logs` table (from check-in and GPS tracking), run the automated gatekeeper:
```bash
python scripts/retrain_from_logs.py --logs data/travel_logs.csv
```
This script enforces rigorous gates:
- **Quality & Privacy Gate:** Rejects incomplete trips, GPS teleportation anomalies, unreasonable speeds, and scrubs exact coordinates (routes are salted and hashed into anonymized bins).
- **Minimum Dataset Gate:** Refuses to retrain on fewer than 300 clean trips.
- **Chronological Holdout (20%):** Validates the candidate model on unseen subsequent trips.
- **Promotion Rule:** Only promotes candidate (`buffer-v3`) if holdout coverage is between 85% and 95% and pinball loss does not degrade by > 2% relative to current production model.
- Updates `models/registry.json` on pass, or writes `models/CANDIDATE_REJECTED.md` on fail.

---

## What is Real and What is Synthetic

- **Synthetic Traffic & Delay Labels:** Training trip delay labels (`target_min`) are generated synthetically using a calibrated physics and congestion model parameterized per transport mode (drive, bus, metro, walk). See [DATA.md](file:///d:/Hackaton/SGU/EventRoute/ml/DATA.md) for full disclosure.
- **Real Origin-Destination Pairs:** The route topology (`data/od_pairs.csv`) uses authentic Delhi-NCR university campuses, metro stations, and transit hubs (IIT Delhi, NSUT Dwarka, Connaught Place, Hauz Khas, etc.).
- **Live Routing API Samples:** Scripts in `scripts/sample_routes_api.py` query actual transit routes via Google Routes API (when API keys are provided) for validation.
- **Methodological Honesty:** We make zero false claims of real-world production training before student check-in data is gathered. The model currently ships on documented synthetic data and is architected to retrain cleanly once real student logs accumulate.
