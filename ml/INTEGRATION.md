# EventRoute ML Integration Guide

**Audience:** Backend Lead & Backend Engineers  
**Package:** `ml/eventroute_ml`  
**Demo Server:** `ml/api_demo.py`  

This document describes how to integrate the EventRoute ML module into the FastAPI backend (`backend/app`). The ML package is pure Python, operates in-memory with zero database dependencies, and requires no network calls.

---

## 1. Where `risk_extra` Plugs into Leave-by Calculation

In `docs/BACKEND.md` (Section 8.3 "Leave-by"), the leave-by formula is defined as:

> ```
> buffer   = base_buffer
>          + event_type_extra   (hackathon +10, workshop +5, other 0)
>          + risk_extra         (from ML, 0 to 20 min)
>          + start_slack        (3 min)
> 
> leave_by = check_in_at - route.duration - buffer
> ```
> *If `leave_by` has already passed, return **Leave now** mode with an honest earliest arrival. Always return what the buffer is made of so the apps can show "How we calculated this".*

The ML package computes the **`risk_extra`** component (0.0 to 20.0 minutes) for any route or travel feature tuple.

---

## 2. Python Package & Import Lines

All integration functions are exposed at package root:

```python
from eventroute_ml import (
    route_to_risk,        # Calculate delay risk for 1 route dict
    annotate_routes,      # Deep-copy and annotate a list of route dicts
    recommend,            # Personalized event recommendation ranking
    event_from_json,      # Adapt backend event dict / DB model to EventInfo
    user_from_json,       # Adapt backend user dict / DB model to UserProfile
    risk_extra_detail,    # Low-level feature inference returning breakdown dict
    risk_extra_minutes,   # Low-level feature inference returning float minutes
    reload_model,         # Thread-safe model reloading
    get_predictor,        # Access singleton buffer predictor
)
```

---

## 3. Copy-Paste Code Examples

### Example 1: Single Route (e.g. Inside `services/leave_by.py`)

```python
from eventroute_ml import route_to_risk

# `route` is a route dictionary in the common route format (legs, distance_m, transfers, etc.)
# `depart_at` can be a datetime object or an ISO-8601 string (e.g. "2026-10-26T09:00:00+05:30")
risk_info = route_to_risk(route, depart_at="2026-10-26T09:00:00+05:30", tz="Asia/Kolkata")

risk_extra_min = risk_info["risk_extra_min"]  # e.g. 11.79
risk_source = risk_info["source"]              # "model" or "rules"
risk_label = risk_info["label"]                # "Delay risk (learned from travel data)"

# Append to buffer breakdown for the Android / Web UI
buffer_breakdown.append({
    "label": risk_label,
    "minutes": int(round(risk_extra_min)),
    "source": risk_source,
})
```

### Example 2: List of Routes (e.g. Inside `services/routing/`)

```python
from eventroute_ml import annotate_routes

# Candidate routes retrieved from routing provider / cache
routes = [fastest_route, cheapest_route, direct_route]

# Returns deep copies with `risk_extra_min` and `risk_extra` attached to each route
annotated_routes = annotate_routes(routes, depart_at="2026-10-26T09:00:00+05:30")

for r in annotated_routes:
    print(f"Route {r['id']} ({r.get('summary')}): risk_extra = {r['risk_extra_min']} min")
```

### Example 3: Event Recommendations (e.g. Inside `services/suggestions.py` or `api/events.py`)

```python
from eventroute_ml import event_from_json, user_from_json, recommend

# Convert database / API models into ML dataclasses safely
user_profile = user_from_json(user_dict, colleges=colleges_list)
event_candidates = [event_from_json(e) for e in events_list]

# Compute personalized ranking with explainable reasons
recommendations = recommend(user=user_profile, events=event_candidates)

for rec in recommendations:
    # rec.event_id: str
    # rec.score: float in [0.0, 1.0]
    # rec.reason: str e.g. "Matches your #Hackathons interest · 37 seats left"
    # rec.components: dict {"interest": 1.0, "proximity": 0.5, "popularity": 0.63, "urgency": 1.0}
    print(rec.event_id, rec.score, rec.reason)
```

---

## 4. Model File, Environment Variables, and Deployment

- **Default Model Path:** `ml/models/buffer_v1.joblib`
- **Environment Variable:** `EVENTROUTE_ML_MODEL`
  Set this variable in production/docker to point to the saved model file:
  ```bash
  EVENTROUTE_ML_MODEL=/app/ml/models/buffer_v1.joblib
  ```
- **Hot Reloading:** Call `reload_model()` or `reload_model("/new/path.joblib")`. The reload operation is thread-safe and updates the singleton predictor without restarting the process.
- **Dependency & Version Pinning:** The ML model is serialized via `joblib`. It must be loaded in an environment with the pinned scikit-learn version (`scikit-learn==1.5.2`) to prevent unpickling and bytecode incompatibility issues.

---

## 5. Meaning of `source` and `label`

Every buffer prediction returns both `source` and `label`:

| `source` | `label` | When It Occurs | App Display ("How we calculated this") |
| :--- | :--- | :--- | :--- |
| `"model"` | `"Delay risk (learned from travel data)"` | Gradient boosting quantile model (0.9 quantile) is loaded and executed. | Shows that buffer is tailored from historical / synthetic traffic delay data. |
| `"rules"` | `"Delay risk (rule table)"` | Model file missing, corrupt, incompatible version, or fallback triggered. | Shows baseline conservative rule-table estimate by mode and peak/off-peak. |

The mobile and web UI should display `label` in the buffer breakdown modal/card.

---

## 6. Error Handling & Latency Guarantees

- **Zero-Crash Guarantee:** The functions `route_to_risk`, `annotate_routes`, `risk_extra_detail`, and `recommend` **never raise uncaught exceptions**. Malformed dictionaries, missing keys, invalid timestamps, or `NaN` values are sanitized and safely fall back to conservative defaults (output clamped to `[0.0, 20.0]` minutes).
- **Benchmarked Inference Latency:**
  - `route_to_risk`: **0.17 ms** (p50) / **0.24 ms** (p95) / **0.35 ms** (p99)
  - `recommend` (6 candidate events): **0.24 ms** (p50) / **0.27 ms** (p95) / **0.38 ms** (p99)
  The ML operations add less than 1 ms of total latency to backend requests and can be executed synchronously in the request lifecycle without blocking the async event loop.

---

## 7. Open Questions & Data Mapping for Backend Lead

During mapping of `android/app/src/main/assets/mock/` data, the following schema adaptations were implemented:

1. **Event Coordinates:** In `events.json`, coordinates appear under `venue_lat`/`venue_lng` (and `college.lat`/`college.lng`), whereas `EventInfo` expects `lat`/`lng`. The adapter checks `venue_lat`/`venue_lng` first, then falls back to `lat`/`lng` and `college`.
2. **Registrations Count:** In `events.json`, explicit `registrations_count` was missing; instead, `seat_limit` and `seats_left` were provided. The adapter calculates `registrations_count = max(0, seat_limit - seats_left)`. If the backend DB has an explicit `registrations_count` column, the adapter will use it directly.
3. **User Start Point:** In `me.json`, user coordinates appear as `home_lat`/`home_lng` with `default_start: "college"`. The adapter supports `lat`/`lng`, `home_lat`/`home_lng`, and college coordinate lookup when `colleges` list is supplied.
4. **Dominant Route Mode:** In `journey.json`, routes consist of multiple legs (e.g., walk + metro + walk). The adapter identifies the dominant mode as the longest non-walk leg by duration (e.g. `metro`).

---

## 8. Real HTTP Responses from Demo API (`ml/api_demo.py`)

The demo server was executed via `uvicorn api_demo:app --port 8011` and queried over HTTP using `httpx`. Below are the real captured payloads and responses.

### 8.1 GET `/ml/health`
**HTTP Status:** `200 OK`  
```json
{
  "status": "ok",
  "model_loaded": true,
  "version": "buffer-v1",
  "source": "model",
  "sklearn_version": "1.5.2"
}
```

### 8.2 POST `/ml/buffer` (Direct Feature Inference)
**Request:**
```json
{
  "mode": "metro",
  "depart_at": "2026-10-26T09:00:00+05:30",
  "distance_m": 23500.0,
  "transfers": 1,
  "walking_share": 0.28,
  "tz": "Asia/Kolkata"
}
```
**HTTP Status:** `200 OK`  
```json
{
  "minutes": 11.76,
  "source": "model",
  "version": "buffer-v1",
  "label": "Delay risk (learned from travel data)",
  "risk_extra_min": 11.76
}
```

### 8.3 POST `/ml/buffer/route` (Route in Common Route Format from `journey.json`)
**Request:**
```json
{
  "route": {
    "id": "route-fastest",
    "summary": "Metro + Walk",
    "duration_s": 2700,
    "distance_m": 23500,
    "departure_time": "2026-10-24T03:00:00Z",
    "arrival_time": "2026-10-24T03:45:00Z",
    "transfers": 1,
    "walking_s": 780,
    "legs": [
      { "mode": "walk", "duration_s": 480, "distance_m": 600 },
      { "mode": "metro", "duration_s": 720, "distance_m": 11000 },
      { "mode": "metro", "duration_s": 1200, "distance_m": 11000 },
      { "mode": "walk", "duration_s": 300, "distance_m": 700 }
    ]
  },
  "depart_at": "2026-10-26T09:00:00+05:30",
  "tz": "Asia/Kolkata"
}
```
**HTTP Status:** `200 OK`  
```json
{
  "route_id": "route-fastest",
  "risk_extra_min": 11.79,
  "source": "model",
  "version": "buffer-v1",
  "label": "Delay risk (learned from travel data)",
  "features": {
    "mode": "metro",
    "hour": 9,
    "weekday": 0,
    "distance_m": 23500.0,
    "transfers": 1,
    "walking_share": 0.2889
  }
}
```

### 8.4 POST `/ml/recommend` (Personalized Ranking from `me.json` and `events.json`)
**Request:**
```json
{
  "user": {
    "id": "u-john",
    "name": "John Doe",
    "interests": ["Hackathons", "Robotics", "Web3"],
    "home_lat": 28.6094,
    "home_lng": 77.0368
  },
  "events": [
    {
      "id": "11111111-1111-1111-1111-111111111111",
      "title": "HackX 2024",
      "category": "Hackathons",
      "tags": ["AI", "Web3", "Hardware"],
      "venue_lat": 28.5459,
      "venue_lng": 77.1926,
      "start_at": "2026-10-24T04:30:00Z",
      "registration_deadline": "2026-10-23T18:30:00Z",
      "seat_limit": 100,
      "seats_left": 37
    },
    {
      "id": "22222222-2222-2222-2222-222222222222",
      "title": "RoboCup Season 5",
      "category": "Robotics",
      "tags": ["Robotics", "Drones", "Combat"],
      "venue_lat": 28.7501,
      "venue_lng": 77.1177,
      "start_at": "2026-10-28T04:30:00Z",
      "registration_deadline": "2026-10-26T18:30:00Z",
      "seat_limit": 50,
      "seats_left": 18
    }
  ],
  "now": "2026-10-23T10:00:00Z",
  "limit": 2
}
```
**HTTP Status:** `200 OK`  
```json
{
  "items": [
    {
      "event_id": "11111111-1111-1111-1111-111111111111",
      "score": 0.801,
      "reason": "Matches your #Hackathons interest · Registration closes within a day",
      "components": {
        "interest": 1.0,
        "proximity": 0.5,
        "popularity": 0.63,
        "urgency": 1.0
      }
    },
    {
      "event_id": "22222222-2222-2222-2222-222222222222",
      "score": 0.5964,
      "reason": "Matches your #Robotics interest · 32 students going",
      "components": {
        "interest": 0.7,
        "proximity": 0.3889,
        "popularity": 0.64,
        "urgency": 0.6076
      }
    }
  ],
  "total": 2
}
```

### 8.5 Validation Error Handling Demonstration
**Request (Bad parameters: negative distance and transfers, walking share > 1.0):**
```json
{
  "mode": "bus",
  "depart_at": "2026-10-24T09:00:00+05:30",
  "distance_m": -150.0,
  "transfers": -1,
  "walking_share": 1.8
}
```
**HTTP Status:** `422 Unprocessable Entity`  
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request payload: body -> distance_m: Input should be greater than or equal to 0; body -> transfers: Input should be greater than or equal to 0; body -> walking_share: Input should be less than or equal to 1",
  "details": [
    {
      "type": "greater_than_equal",
      "loc": ["body", "distance_m"],
      "msg": "Input should be greater than or equal to 0",
      "input": -150.0,
      "ctx": { "ge": 0.0 }
    },
    {
      "type": "greater_than_equal",
      "loc": ["body", "transfers"],
      "msg": "Input should be greater than or equal to 0",
      "input": -1,
      "ctx": { "ge": 0 }
    },
    {
      "type": "less_than_equal",
      "loc": ["body", "walking_share"],
      "msg": "Input should be less than or equal to 1",
      "input": 1.8,
      "ctx": { "le": 1.0 }
    }
  ]
}
```

---

## 9. `travel_logs` CSV Export Contract for Offline Retraining

Once the backend tracks live journeys (from check-in and GPS tracking as outlined in `docs/BACKEND.md` Section 5 & 8.7), periodic exports from the `travel_logs` table are required to retrain the buffer model (`scripts/retrain_from_logs.py`).

### 9.1 Privacy Requirement
**Zero Personally Identifiable Information (PII):**
The exported CSV must contain **NO** user IDs, student names, email addresses, phone numbers, or exact street-level addresses. Origin and destination coordinates must only be approximate (e.g. 2-3 decimal places, or campus/venue centroids), and are salted and hashed into anonymized `route_id`s on the ML side.

### 9.2 CSV Column Specification

| Column | Type | Unit / Format | Description |
| :--- | :--- | :--- | :--- |
| `trip_id` | `str` | Anonymized UUID / integer | Unique identifier for the journey record. |
| `origin_lat` | `float` | Decimal degrees (WGS84) | Approximate departure latitude. |
| `origin_lng` | `float` | Decimal degrees (WGS84) | Approximate departure longitude. |
| `dest_lat` | `float` | Decimal degrees (WGS84) | Approximate destination venue latitude. |
| `dest_lng` | `float` | Decimal degrees (WGS84) | Approximate destination venue longitude. |
| `mode` | `str` | `drive`, `metro`, `bus`, `walk`, `bike`, `tram`, `train` | Dominant transit/travel mode. |
| `departure_time`| `str` | ISO-8601 UTC string (e.g. `2026-10-24T03:30:00Z`) | Exact timestamp trip commenced. |
| `planned_duration_s` | `float` | Seconds ($s$) | Estimated route duration from routing service. |
| `actual_duration_s` | `float` | Seconds ($s$) | Real measured duration from departure to venue arrival. |
| `distance_m` | `float` | Meters ($m$) | Traveled route distance. |
| `transfers` | `int` | Count ($\ge 0$) | Number of transit line changes. |
| `walking_s` | `float` | Seconds ($s$) | Duration spent on pedestrian legs. |
| `completed` | `bool` | `true` / `false` | `true` if trip was concluded normally at venue; `false` if abandoned. |
| `gps_jump` | `bool` | `true` / `false` | `true` if telemetry detected unrealistic teleportation or signal dropout. |

### 9.3 Data Cleaning & Validation Rules in ML Retraining Pipeline
When `scripts/retrain_from_logs.py` ingests this CSV:
1. Trips with `completed == false`, missing arrival, or `actual_duration_s is null` are dropped.
2. Trips with `gps_jump == true` or implausible speeds ($> 180 \text{ km/h}$ for drive/transit, $> 30 \text{ km/h}$ for walk/bike) are dropped.
3. Absurd durations ($< 60\text{ s}$ or $> 86400\text{ s}$, or $\text{planned\_duration\_s} \le 0$) are dropped.
4. Target delay is computed as: $\text{target\_min} = \frac{\text{actual\_duration\_s} - \text{planned\_duration\_s}}{60.0}$.
5. Retraining requires a minimum statistical threshold of **$\ge 300$ clean trips** before a candidate model can be trained.

