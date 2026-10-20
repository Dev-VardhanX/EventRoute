# Backend Guide (FastAPI + PostgreSQL + ML)

**Owner:** Backend + ML lead · **Consumers:** Android lead, Web lead

The backend is the single source of truth for rules, routing, team planning, notifications, and ML. It is also the bottleneck of the project, so it gets the least UI work and must publish the API contract first.

## 1. Stack

Python 3.12, FastAPI, SQLAlchemy, Alembic, PostgreSQL (PostGIS optional), Pydantic, APScheduler, firebase-admin (FCM), httpx, scikit-learn + joblib, pytest. Docker Compose for local dev. Deploy on Render or Railway.

## 2. Folder layout

```
backend/
  app/
    main.py            app creation, CORS, router includes, /health
    api/               one router file per area: auth, me, colleges, events, uploads,
                       organizer, registrations, teams, journey, live, notifications, admin
    models/            SQLAlchemy models (one file per table group)
    schemas/           Pydantic request/response models
    services/          registration.py, teams.py, routing/ (provider interface + providers + cache),
                       leave_by.py, team_planner.py, suggestions.py, notifications.py, scheduler.py
    core/              config.py (settings from env), security.py (JWT, hashing, RBAC deps),
                       db.py, errors.py (error codes and handler)
  alembic/             migrations
  tests/               unit and API tests
  scripts/             seed.py (demo data), helpers
ml/                    data scripts, training, evaluation, saved models
```

## 3. Local setup

```bash
cp .env.example .env
docker compose up --build            # Postgres + API on :8000
docker compose exec api alembic upgrade head
docker compose exec api python scripts/seed.py
# Swagger / OpenAPI: http://localhost:8000/docs
docker compose exec api pytest
```

## 4. Connection rules (agreed in Phase 0, do not change without announcing)

- Clients talk **only** to the backend. Routing keys never leave the server.
- Base path `/api/v1`. JSON. Timestamps ISO-8601 UTC. IDs are UUIDs. Event times are displayed in the event's own time zone (default `Asia/Kolkata`).
- Auth: JWT access token (~15 min) in `Authorization: Bearer`, refresh token (~14 days). Clients do one silent refresh on 401.
- One error shape: `{ "code", "message", "details"? }`. Codes: `VALIDATION_ERROR, UNAUTHENTICATED, FORBIDDEN, NOT_FOUND, EVENT_CLOSED, EVENT_FULL, ALREADY_REGISTERED, TEAM_FULL, TEAM_TOO_SMALL, NOT_ELIGIBLE, TIME_CLASH, INVITE_INVALID, ROUTE_UNAVAILABLE, RATE_LIMITED`.
- Lists use `page` and `page_size` and return `items, total, page, page_size`.
- The OpenAPI document is the contract. Announce changes in the group chat before merging.

## 5. Data model (core tables)

All tables have `id` (UUID), `created_at`, `updated_at`. Times are `timestamptz`.

| Table | Key columns |
| --- | --- |
| users | name, email (unique, lowercase), email_verified, password_hash, role (student/organizer/admin), college_id, interests[], home_lat/lng/label, default_start (college/home/custom), is_active, is_demo |
| colleges | name, city, lat, lng, email_domain, verified |
| organizer_profiles | user_id, organization_name, college_id, status (pending/approved/rejected), reviewed_by |
| events | organizer_id, college_id, title, description, category, tags[], banner_url, mode (offline/online/hybrid), start_at, end_at, check_in_at, registration_deadline, timezone, venue_name, venue_lat/lng, participation_type (solo/team/either), team_min, team_max, seat_limit, fee_amount, eligibility, status (draft/pending_review/published/cancelled/completed), is_demo |
| registrations | event_id, user_id (unique together), team_id (null for solo), status (registered/cancelled/checked_in/no_show), ticket_token |
| teams | event_id, name, leader_id, invite_code (unique), status (forming/confirmed/cancelled), planned_arrival_at, plan_version |
| team_members | team_id, user_id, start_lat/lng/source, travel_pref, planned_leave_by, planned_arrival_at, journey_status, live_lat/lng, live_eta_at |
| journey_plans | registration_id, origin, leave_by, arrive_at, buffer_minutes, route_json, provider |
| join_suggestions | team_id, member_ids, meet point, meet_at, mode, extra_minutes, status |
| route_cache | cache_key (unique), response_json, expires_at |
| device_tokens, notifications, scheduled_notifications | FCM tokens, sent records, timed reminders (unique per user/type/event/due time so nothing sends twice) |
| travel_logs | planned vs actual duration, hour_of_day, weekday, delay_s (training data for the buffer model) |
| otp_codes, saved_events, reports | verification codes, bookmarks, reported events |

**Database constraints to enforce:** `team_min <= team_max`, `check_in_at <= start_at`, `registration_deadline <= check_in_at`, unique `(event_id, user_id)`, unique invite code. Events with registrations cannot be deleted, only cancelled.

## 6. Roles and permissions (server-side, always)

| Action | Guest | Student | Organizer | Admin |
| --- | --- | --- | --- | --- |
| View published events | Yes | Yes | Yes | Yes |
| Register, create or join teams | No | Yes | Yes (as student) | Yes |
| Basic route | Yes | Yes | Yes | Yes |
| Live tracking | No | Yes | Yes | Yes |
| Create and edit own events | No | No | Yes, after approval | Yes |
| See registrants of own events | No | No | Yes | Yes (all) |
| Approve organizers, moderate | No | No | No | Yes |

Check role **and** event ownership on every organizer action. Hiding a page in the UI is not security.

## 7. MVP API surface (publish stubs for all of these in Phase 0)

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `/auth/verify-email`, `/auth/login`, `/auth/refresh`, `/auth/logout` · `GET/PATCH /me` · `PUT /me/start-point` · `POST /me/device-token` |
| Colleges | `GET /colleges?search=` |
| Events | `GET /events` (lat, lng, radius_km, category, from, to, participation, max_travel_min, q, sort, page) · `GET /events/{id}` · `POST /events/travel-estimates` · `POST /events` · `PATCH /events/{id}` · `POST /events/{id}/publish` · `POST /events/{id}/cancel` · `POST /uploads/image` |
| Organizer | `GET /events/{id}/registrations` · `GET /events/{id}/registrations/export` · `POST /events/{id}/check-in` |
| Registration | `POST /events/{id}/register` · `DELETE /registrations/{id}` · `GET /me/registrations` |
| Teams | `POST /events/{id}/teams` · `GET /invite/{code}` · `POST /teams/join` · `GET /teams/{id}` · `POST /teams/{id}/confirm` · `PUT /teams/{id}/me/start-point` · `DELETE /teams/{id}/members/{user_id}` |
| Journey | `GET /route` · `GET /events/{id}/journey` · `POST /journey/{plan_id}/select` · `GET /teams/{id}/plan` · `POST /teams/{id}/suggestions/{sid}/respond` |
| Live (Should) | `POST /teams/{id}/journey/start` and `/stop` · `POST /teams/{id}/location` · `GET /teams/{id}/live` · `POST /teams/{id}/status` |
| Notifications | `GET /me/notifications` · `POST /me/notifications/{id}/read` |
| Admin | `GET /admin/organizers?status=pending` · `POST /admin/organizers/{id}/approve` and `/reject` · `GET/POST/PATCH /admin/colleges` |
| System | `GET /health` |

Stubs must return realistic example responses (copy shapes from `docs/api/mock/`). Android and web build against these on day one.

## 8. Core logic

### 8.1 Registration rules
- **Solo:** event is published, before deadline, seats left, user eligible, not already registered. Create the registration and a signed ticket token, queue a notification, return a journey plan.
- **Team:** leader creates a team and gets an invite code. Members join (checks: deadline, `team_max`, eligibility). Leader confirms when size is between `team_min` and `team_max`. Confirmation locks the team and triggers the team plan.
- **Discovery:** `GET /events` does the geo query (haversine first, PostGIS optional) and returns straight-line distance. `POST /events/travel-estimates` returns a cached quick estimate per visible event so cards show travel time without a full route call each.

### 8.2 Routing service
One `RouteProvider` interface with concrete providers behind it. Clients never see raw provider responses.

| Mode | Primary | Fallback |
| --- | --- | --- |
| Transit | Google | None. Clear message, offer walk or cab |
| Walk, bike | OpenRouteService | Google |
| Drive/cab | OpenRouteService | Google |

**Common route format:** route has `id, summary, duration_s, distance_m, departure/arrival times, fare?, transfers, walking_s, legs[]`. Each leg has `mode (walk/bus/metro/train/tram/bike/drive), from, to, times, duration, distance, encoded polyline, steps[]`, and for transit a block with `line name, short name, vehicle type, headsign, stops count, agency, colour`.

**Request flow:** client sends origin, destination, mode, arrive-by. Build cache key, return a fresh hit if present, else call provider, convert to common format, cache, return. On timeout (~8 s) retry once, then fallback provider, then `ROUTE_UNAVAILABLE`.

**Cache:** key = provider + mode + origin (4 decimals) + destination + arrive-by (rounded to 5 min). TTL: transit 10 min, walk/bike 24 h, drive 5 min. Discovery estimates use one mode per card and a 6 h cache.

**Cost guard:** daily paid-call counter (`DAILY_PAID_ROUTE_LIMIT`). When reached, serve cached or free-provider results only.

### 8.3 Leave-by
```
buffer   = base_buffer
         + event_type_extra   (hackathon +10, workshop +5, other 0)
         + risk_extra         (from ML, 0 to 20 min)
         + start_slack        (3 min)

leave_by = check_in_at - route.duration - buffer
```
If `leave_by` has already passed, return **Leave now** mode with an honest earliest arrival. Always return what the buffer is made of so the apps can show "How we calculated this".

### 8.4 Arrive-together team planner
1. Team arrival target `T = check_in_at - team margin` (default 15 min).
2. For each member, request routes with `arrive_by = T` and pick the option matching their preference that arrives at or before `T`.
3. Member `leave_by = route departure - member buffer`.
4. Spread = latest arrival - earliest arrival. Return as a badge value. If spread is over 15 min, offer alternatives. If anyone cannot make check-in even with the fastest option, mark the team **at risk** and say who and by how much.
5. Save with a version number. Any change in members, start points, or event time creates a new version and notifies the team.

### 8.5 Join-on-the-way suggestions (per pair)
- **Start together:** starts within ~800 m and walking under 10 min, so meet and travel together.
- **Same transit line:** same line and boarding stop, or one route passes the other's stop, so meet at that stop.
- **Pick-up (cab/bike):** `detour = time(A->B->venue) - time(A->venue)`. Suggest if detour is within 10 min.
- A suggestion becomes active only when every member accepts. Cab cost split is an estimate and must be labelled so.

### 8.6 Notifications and scheduler
- Immediate events write a notification record. Timed ones (reminders, leave-by) write a scheduled record with a due time.
- APScheduler runs every minute, sends due items through FCM, marks them sent. Unique key per user/type/event/due time prevents duplicates.
- MVP subset: registration confirmed, team member joined/left, team confirmed, deadline reminder, event reminder, leave soon, leave now, event updated/cancelled.

### 8.7 Live tracking (Should)
- App posts position every 15 s. Teammates poll `/teams/{id}/live` every 15 s (WebSocket later).
- ETA more than 10 min later than planned: send `teammate_delayed` and recompute the team arrival.
- Within ~150 m of the venue marks arrived and stops sharing.
- Privacy: starts only when the user starts it, visible only to the team, can be stopped any time, ends 1 hour after event start, positions deleted 24 h after the event.

### 8.8 ML module (keep it honest)
| Model | Approach |
| --- | --- |
| Delay-risk buffer | Gradient boosting, quantile loss 0.9. Features: mode, hour of day, weekday, distance, transfers, walking share. Output clamped to 0-20 min. **Fallback: rule table by mode and time of day.** |
| Recommendations | `0.40 interest_match + 0.25 proximity + 0.20 popularity + 0.15 urgency`, with a human-readable reason |

Build the rule-based buffer first. Sample the routing API's driving durations across times of day for training. Label any synthetic data as synthetic in the repo and the pitch. Report mean absolute error and coverage (target near 90%).

## 9. Flow of work (phase by phase)

| Phase | Your tasks | Hand off to others | Done when |
| --- | --- | --- | --- |
| **0 Setup** | FastAPI skeleton, Docker Compose, Alembic, `/health`, **OpenAPI stubs for every MVP endpoint with example responses**. Create Google Cloud (restricted keys, budget alert), ORS, Firebase, email provider, hosting accounts | Publish Swagger URL and mock JSON in `docs/api/mock/` so Android and web start immediately | Swagger shows every MVP endpoint with examples |
| **1 Core data** | Auth with roles and email OTP, colleges, events CRUD, image upload, geo query, seed script | Tell Android and web the day each endpoint goes live | Organizer creates an event on web and it appears in Android |
| **2 Registration and teams** | Registration rules, teams, invite codes, confirm, My Events endpoints, error codes | Document each error code and when it fires | Student registers solo; three students form and confirm a team |
| **3 Routing** | Route endpoint with providers, cache and fallback, common route format, leave-by calculation, travel-estimates | Pair with Android on the route response format. **Test transit coverage in the demo city now** | Event card shows travel time and a full route with leave-by |
| **4 Team Travel Mode** | Arrive-together planner, join-on-the-way suggestions, plan versioning, live location endpoints | Pair with Android on the team plan screen | Three members at different start points see personal leave-by, arrival spread, and a travel-together suggestion |
| **5 Smart features and alerts** | Scheduler, FCM sending, notification catalogue (MVP subset), delay-risk model, recommendation score | Share notification payload formats with Android | Leave-soon push reaches a phone and a delay changes the team plan |
| **6 Polish, deploy, rehearse** | Deploy API, run migrations, wake-up plan, uptime pinger on `/health`, bug fixes, final seed | Keep seed data in sync with the web lead | Full demo runs twice on real devices, once with poor network |

Free hosts sleep: wake the API minutes before any demo.

## 10. Edge cases you must handle
- Event time or venue changes after registration: recompute plans, notify, banner in clients.
- Event cancelled: close registrations and teams, remove scheduled reminders.
- Team drops below minimum, or the leader leaves (leadership passes to earliest joiner).
- Late registration where leave-by has passed: Leave now mode.
- Location denied or GPS off: clients fall back to the saved start point (accept it as origin).
- No route, provider down, or quota hit: cached plan, straight-line distance, retry.
- Invite code used after the team is confirmed or cancelled.
- Overlapping events: return `TIME_CLASH` warning.

## 11. Testing (you own backend tests)
- **Unit:** leave-by, buffer clamp, team size/deadline/seat rules, invite codes, team planner with fixed inputs.
- **API:** auth flows, role and ownership checks, registration and team flows, error codes.
- **Provider:** parse saved sample responses, fallback behaviour.
- **End to end:** full demo script on real devices, at least twice, once on poor network (all three).

## 12. Working agreements
- Branch `backend/<topic>`, PR into `develop`. Android lead reviews your PRs.
- Every PR that changes an endpoint updates the schemas so OpenAPI stays correct, and is announced in the group chat first.
- Definition of done: works on the real backend, has a basic test for its rules, has no secrets in code.
- Secrets live in environment variables only. Restrict keys: Android by package + signing cert, web by domain, routing keys on the backend only.
- Run sync every couple of days: what is done, what is blocked, what changes in the API.
