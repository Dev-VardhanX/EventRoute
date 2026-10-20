# EventRoute: Build Order, System Design & Team Split

Team Kiko · SGU\_HACKX-1.0 · Dev Vardhan, Khushi, Lucky

*Pitch line: Others tell you what to attend. EventRoute gets you there.*

## 1. The project in 60 seconds

EventRoute is a platform where students discover inter-college events on a map, register (solo or as a team), and get a full journey plan from their college to the venue. The special feature is **Team Travel Mode**: every teammate gets a personal leave-by time so the whole team arrives together, with join-on-the-way suggestions and live team ETA.

Three surfaces, one backend:

- **Android app** (Kotlin, Compose): the full experience, including live ETA and push alerts.
- **Student website** (HTML/CSS/JS): lighter version with a basic route and a link to the app.
- **Admin/organizer web portal** (HTML/CSS/JS): post events with a map pin, manage registrations, approvals.
- **FastAPI backend** (Python + PostgreSQL): single source of truth for rules, routing, team planner, notifications, ML.

The core idea that drives everything: `leave_by = check_in_time − travel_time − buffer`.

## 2. Team split (3 members)

The original plan had two people (Android+backend+ML, and web). With three, the backend becomes its own role because everything depends on it. Roles are defined by area, so decide who takes which based on strengths. A suggested default is below.

| Role | Owns | Suggested |
| --- | --- | --- |
| **A. Android Lead** | Android app (all screens), Maps SDK, location service, FCM on the client, deep links, APK distribution | Dev Vardhan (Android is the strongest stack) |
| **B. Backend + ML Lead** | FastAPI, database and migrations, auth/RBAC, registration and team rules, route service, team planner, scheduler and FCM sending, ML models, deployment of the API | Khushi or Lucky |
| **C. Web + Product Lead** | Admin/organizer portal, student website, seed data and demo content, survey, pitch deck and demo script, testing checklist | Khushi or Lucky |

Swap names freely. What matters is that each area has exactly one owner.

**Why this split works:** B is the bottleneck, so B gets the least UI work. A and C both consume the API, so both can start from mock JSON on day one. C also owns pitch and demo prep, which is real work that usually gets crushed at the end.

### Shared responsibilities

- **API contract:** B writes it, A and C review before it is locked.
- **Code reviews:** A reviews B's PRs, B reviews C's, C reviews A's. Everyone reads each other's code at least once.
- **Testing and demo rehearsal:** all three, on real devices.
- **Pair up on:** the route response format (A+B), the map pin picker and event wizard validation (B+C), and the team plan screen (A+B).

### Per-person task lists

**A. Android Lead**

1. Project setup: Compose, Material 3, Hilt, Retrofit/OkHttp with auth interceptor, Room, Navigation, theme.
2. Auth screens: sign up, email code verification, login, token refresh.
3. Home: map + list toggle, filters, search, travel-time chips.
4. Event details with participation badge, seats left, travel preview, action button states.
5. Solo registration, create/join team, team lobby, My Events.
6. Journey planner: origin selector, route options, step list, polyline map, leave-by countdown.
7. Team plan screen and live journey screen (foreground service, 15 s location updates, team ETA board).
8. FCM handling, deep links (`/event/{id}`, `/invite/{code}`), release build and APK/QR distribution.

**B. Backend + ML Lead**

1. FastAPI skeleton, Docker Compose (Postgres + API), SQLAlchemy models, Alembic migrations, `/health`.
2. Auth: register, verify code, login, refresh, roles (student, organizer, admin), organizer approval.
3. Colleges, events CRUD, image upload, geo query (haversine first, PostGIS optional), travel-estimates endpoint.
4. Registration and team rules (size, deadline, seats, eligibility, invite codes, confirm).
5. Route service: provider interface, Google (transit), OpenRouteService (walk/bike/drive), common route format, cache, fallback.
6. Leave-by calculation, team arrive-together planner, join-on-the-way suggestions.
7. Scheduler (APScheduler, runs every minute), notification records, FCM sending, live location endpoints.
8. ML: rule-based buffer first, then quantile gradient boosting model; recommendation score formula.
9. Deploy API (Render/Railway), run migrations, keep seeded data in sync with C.

**C. Web + Product Lead**

1. Web setup: folder structure, shared `api.js` fetch wrapper with token refresh, base CSS, mock JSON.
2. Admin/organizer: login, event wizard with Leaflet map pin picker, dashboard, edit/cancel, registrations table with CSV export.
3. Admin: organizer approval queue, colleges, later stats.
4. Student website: discovery map + list, event page, register, team page, My Events, basic journey page, invite landing page, Get-the-app banner with QR.
5. Check-in page (QR scan) and announcements page if time allows.
6. Seed data script content: 15 to 20 clearly labelled demo events in one city, demo accounts.
7. Survey (20 to 30 students) and results for the deck, pitch slides, demo script, backup screen recording.
8. Deploy websites (Netlify/Firebase Hosting), responsive checks.

## 3. Build order

Principle: **contract first, then vertical slices.** Backend publishes the API contract (OpenAPI stubs) in Phase 0 so Android and web never wait for it. Each phase ends with something demoable.

### Phase 0: Setup (everyone, do this together)

| Who | Task |
| --- | --- |
| All | Create the GitHub repo with the layout in section 5, branch rules, and a shared `.env.example`. Create accounts: Google Cloud (restricted keys for Android, web, server, budget alert), OpenRouteService, Firebase, email provider, hosting |
| B | FastAPI skeleton, Postgres via Docker Compose, Alembic, `/health`, and **OpenAPI stubs for every MVP endpoint** (section 4.6) |
| A | Android project with theme, navigation, Hilt, Retrofit pointed at mock data |
| C | `web/` structure, `api.js`, base CSS, static layouts reading mock JSON |

**Done when:** each person can run their part locally, and the Swagger page shows every MVP endpoint with example responses.

### Phase 1: Core data (events exist and can be browsed)

| Who | Task |
| --- | --- |
| B | Auth with roles and email verification, colleges, events CRUD, image upload, geo query, seed script |
| A | Sign up, login, home list, event details (against real API as endpoints go live) |
| C | Admin login, event wizard with map pin picker, organizer dashboard, student event list and detail |

**Done when:** an organizer creates an event on the web portal and it appears in the Android app.

### Phase 2: Registration and teams

| Who | Task |
| --- | --- |
| B | Registration rules, teams, invite codes, confirm, My Events endpoints, error codes |
| A | Solo register, create/join team, team lobby, My Events |
| C | Register and team pages, registrations table, CSV export |

**Done when:** a student registers solo, and three students form and confirm a team.

### Phase 3: Routing (the heart of the product)

| Who | Task |
| --- | --- |
| B | Route endpoint with providers, cache and fallback, common route format, leave-by calculation, travel-estimates for discovery cards |
| A | Journey planner screen: route cards, step list, polylines, leave-by countdown |
| C | Basic route page on the student website, Get-the-app banner |

**Done when:** an event shows travel time on its card and a full route with a leave-by time. **Test transit coverage in the demo city now**, not at the end.

### Phase 4: Team Travel Mode (the differentiator)

| Who | Task |
| --- | --- |
| B | Arrive-together planner, join-on-the-way suggestions, plan versioning, live location endpoints |
| A | Member start-point setting, team plan screen, live journey screen with team ETA board |
| C | Team plan view on web, check-in page, begin demo dataset for team scenarios |

**Done when:** three members at different start points each see a personal leave-by time, arrival spread, and at least one travel-together suggestion.

### Phase 5: Smart features and alerts

| Who | Task |
| --- | --- |
| B | Scheduler, FCM sending, notification catalogue (MVP subset), delay-risk model, recommendation score |
| A | FCM receiving, reminders, deep links |
| C | Announcements page, admin approvals and stats |

**Done when:** a leave-soon push arrives on a phone and a delay changes the team plan.

### Phase 6: Polish, deploy, rehearse

| Who | Task |
| --- | --- |
| B | Deploy API, run migrations, wake-up plan, fix bugs, final seed |
| A | Release build, APK + QR install, performance and offline states |
| C | UI polish, responsive checks, pitch deck, survey results, backup screen recording |
| All | Run the full demo twice on real devices, once with poor network |

### Cut order if time runs short

Drop in this order: recommendation score, WebSocket upgrade, admin stats, join-on-the-way, ML model (keep the rule-based buffer), live tracking (keep static leave-by times).

**Never drop:** discovery, registration, solo route with leave-by, and the arrive-together team plan.

## 4. System design

### 4.1 Architecture

```
 Android app (Kotlin)      Student web (JS)       Admin web (JS)
        |                        |                     |
        |   HTTPS + JSON (REST, JWT bearer token)      |
        +------------------------+---------------------+
                                 |
                          FastAPI backend  (/api/v1)
        +-----------+------------+-------------+-------------+
        |           |            |             |             |
   PostgreSQL   Routing APIs   Places API   FCM push    Email (SMTP)
   (+PostGIS)   (Google, ORS)                                
        |
   Scheduler jobs + ML models (loaded in the backend)
```

### 4.2 Components

| Component | Responsibility |
| --- | --- |
| Android app | Full student experience, live location, push, offline cache of My Events |
| Student website | Discovery, registration, teams, basic route view |
| Admin website | Organizer and admin tools (web only) |
| FastAPI backend | Auth, business rules, routing, team planner, notifications. Stateless |
| PostgreSQL | All data. PostGIS is optional for geo queries |
| Routing providers | Google Routes/Directions for transit; OpenRouteService or OSRM for walk, bike, drive |
| Places/geocoding | Google Places or Nominatim for place search and the venue pin picker |
| Firebase Cloud Messaging | Push to the Android app |
| Email service | SMTP or Brevo for verification codes and announcements |
| File storage | Cloudinary or Firebase Storage for banners and avatars |
| Scheduler | APScheduler inside the backend, runs every minute |
| ML module | joblib models loaded by the backend |

### 4.3 Connection rules (agree these in Phase 0)

- Clients talk **only** to the backend. Never to routing providers, the database, or email directly. Routing keys stay on the backend.
- Base path `/api/v1`. JSON. Timestamps ISO-8601 UTC. IDs are UUIDs. Event times shown in the event's own time zone (default Asia/Kolkata).
- Auth: JWT access token (about 15 min) in the `Authorization` header, refresh token (about 14 days). A 401 triggers one silent refresh and a retry.
- One error shape: `{ code, message, details? }`. Codes: VALIDATION\_ERROR, UNAUTHENTICATED, FORBIDDEN, NOT\_FOUND, EVENT\_CLOSED, EVENT\_FULL, ALREADY\_REGISTERED, TEAM\_FULL, TEAM\_TOO\_SMALL, NOT\_ELIGIBLE, TIME\_CLASH, INVITE\_INVALID, ROUTE\_UNAVAILABLE, RATE\_LIMITED.
- Lists use `page` and `page_size` and return `items, total, page, page_size`.
- The FastAPI OpenAPI document is the contract. Changes are announced in the group chat before merging.

### 4.4 Data model (core tables)

All tables have `id` (UUID), `created_at`, `updated_at`. Times are `timestamptz`.

| Table | Key columns |
| --- | --- |
| users | name, email (unique, lower), email\_verified, password\_hash, role (student/organizer/admin), college\_id, interests\[\], home\_lat/lng/label, default\_start (college/home/custom), is\_active, is\_demo |
| colleges | name, city, lat, lng, email\_domain, verified |
| organizer\_profiles | user\_id, organization\_name, college\_id, status (pending/approved/rejected), reviewed\_by |
| events | organizer\_id, college\_id, title, description, category, tags\[\], banner\_url, mode (offline/online/hybrid), start\_at, end\_at, check\_in\_at, registration\_deadline, timezone, venue\_name, venue\_lat, venue\_lng, participation\_type (solo/team/either), team\_min, team\_max, seat\_limit, fee\_amount, eligibility, status (draft/pending\_review/published/cancelled/completed), is\_demo |
| registrations | event\_id, user\_id (unique together), team\_id (null for solo), status (registered/cancelled/checked\_in/no\_show), ticket\_token |
| teams | event\_id, name, leader\_id, invite\_code (unique), status (forming/confirmed/cancelled), planned\_arrival\_at, plan\_version |
| team\_members | team\_id, user\_id, start\_lat/lng/source, travel\_pref, planned\_leave\_by, planned\_arrival\_at, journey\_status, live\_lat/lng, live\_eta\_at |
| journey\_plans | registration\_id, origin, leave\_by, arrive\_at, buffer\_minutes, route\_json, provider |
| join\_suggestions | team\_id, member\_ids, meet point, meet\_at, mode, extra\_minutes, status |
| route\_cache | cache\_key (unique), response\_json, expires\_at |
| device\_tokens, notifications, scheduled\_notifications | FCM tokens, sent records, timed reminders (unique per user/type/event/due time so nothing sends twice) |
| travel\_logs | planned vs actual duration, hour\_of\_day, weekday, delay\_s (training data for the buffer model) |
| otp\_codes, saved\_events, reports | verification codes, bookmarks, reported events |

**Constraints worth enforcing in the database:** `team_min <= team_max`, `check_in_at <= start_at`, `registration_deadline <= check_in_at`, unique `(event_id, user_id)`, unique invite code. Events with registrations cannot be deleted, only cancelled.

### 4.5 Roles and permissions

| Action | Guest | Student | Organizer | Admin |
| --- | --- | --- | --- | --- |
| View published events | Yes | Yes | Yes | Yes |
| Register, create or join teams | No | Yes | Yes (as student) | Yes |
| Basic route | Yes | Yes | Yes | Yes |
| Live tracking | No | Yes | Yes | Yes |
| Create and edit own events | No | No | Yes, after approval | Yes |
| See registrants of own events | No | No | Yes | Yes (all) |
| Approve organizers, moderate | No | No | No | Yes |

The server checks role **and** event ownership on every organizer action. Hiding a page is not security.

### 4.6 MVP API surface (B publishes stubs for all of these in Phase 0)

| Area | Endpoints |
| --- | --- |
| Auth | `POST /auth/register`, `/auth/verify-email`, `/auth/login`, `/auth/refresh`, `/auth/logout` · `GET/PATCH /me` · `PUT /me/start-point` · `POST /me/device-token` |
| Colleges | `GET /colleges?search=` |
| Events | `GET /events` (lat, lng, radius\_km, category, from, to, participation, max\_travel\_min, q, sort, page) · `GET /events/{id}` · `POST /events/travel-estimates` · `POST /events` · `PATCH /events/{id}` · `POST /events/{id}/publish` · `POST /events/{id}/cancel` · `POST /uploads/image` |
| Organizer | `GET /events/{id}/registrations` · `GET /events/{id}/registrations/export` · `POST /events/{id}/check-in` |
| Registration | `POST /events/{id}/register` · `DELETE /registrations/{id}` · `GET /me/registrations` |
| Teams | `POST /events/{id}/teams` · `GET /invite/{code}` · `POST /teams/join` · `GET /teams/{id}` · `POST /teams/{id}/confirm` · `PUT /teams/{id}/me/start-point` · `DELETE /teams/{id}/members/{user_id}` |
| Journey | `GET /route` · `GET /events/{id}/journey` · `POST /journey/{plan_id}/select` · `GET /teams/{id}/plan` · `POST /teams/{id}/suggestions/{sid}/respond` |
| Live (Should) | `POST /teams/{id}/journey/start` and `/stop` · `POST /teams/{id}/location` · `GET /teams/{id}/live` · `POST /teams/{id}/status` |
| Notifications | `GET /me/notifications` · `POST /me/notifications/{id}/read` |
| Admin | `GET /admin/organizers?status=pending` · `POST /admin/organizers/{id}/approve` and `/reject` · `GET/POST/PATCH /admin/colleges` |
| System | `GET /health` |

### 4.7 Key flows

**Discovery with travel time**

1. Client calls `GET /events` with the user's location or map area plus filters. Backend does the geo query and returns events with straight-line distance.
2. Client calls `POST /events/travel-estimates` with the visible event IDs. Backend returns a quick cached estimate per event, so cards show travel time without a full route call each.

**Registration**

- Solo: backend checks published, before deadline, seats left, eligible, not already registered. It creates the registration and a signed ticket token, queues a notification, and returns a journey plan.
- Team: leader creates team and gets an invite code. Members join (checks: deadline, team\_max, eligibility). Leader confirms when size is between team\_min and team\_max. Confirmation locks the team and triggers the team plan.

**Route request**

1. Client sends origin, destination, mode, arrive-by time.
2. Backend builds the cache key, returns a fresh hit if one exists, otherwise calls the provider, converts to the common format, caches, and returns.
3. On timeout (about 8 s) retry once, then fallback provider, then `ROUTE_UNAVAILABLE`. The app then shows straight-line distance and a retry button.

**Notification pipeline**

- Immediate events write a notification record. Timed ones (reminders, leave-by) write a scheduled record with a due time.
- The scheduler runs every minute, sends due items through FCM, and marks them sent.

### 4.8 Routing service design

One `RouteProvider` interface, concrete providers behind it. Clients never see raw provider responses, so swapping providers never touches the apps.

| Mode | Primary | Fallback |
| --- | --- | --- |
| Transit | Google | None. Show a clear message, offer walk or cab |
| Walk, bike | OpenRouteService | Google |
| Drive/cab | OpenRouteService | Google |

**Common route format:** route has `id, summary, duration_s, distance_m, departure/arrival times, fare?, transfers, walking_s, legs[]`. Each leg has `mode (walk/bus/metro/train/tram/bike/drive), from, to, times, duration, distance, encoded polyline, steps[]`, and for transit a block with `line name, short name, vehicle type, headsign, stops count, agency, colour`.

**Cache:** key = provider + mode + origin (4 decimals) + destination + arrive-by (rounded to 5 min). TTL: transit 10 min, walk/bike 24 h, drive 5 min. Discovery estimates use one mode per card and a 6 h cache to control cost.

**Cost guard:** a daily paid-call counter. When reached, serve cached or free-provider results only.

### 4.9 Leave-by and team planner (the logic that wins the pitch)

**Leave-by**

```
buffer = base_buffer
       + event_type_extra   (hackathon +10, workshop +5, other 0)
       + risk_extra         (from ML, 0 to 20 min)
       + start_slack        (3 min)

leave_by = check_in_at - route.duration - buffer
```

If `leave_by` has already passed, show **Leave now** mode with an honest earliest arrival. Always show the user what the buffer is made of.

**Arrive-together planner**

1. Team arrival target `T = check_in_at − team margin` (default 15 min).
2. For each member, request routes with `arrive_by = T` and pick the option matching their preference that arrives at or before `T`.
3. Member `leave_by = route departure − member buffer`.
4. Spread = latest arrival − earliest arrival. Show as a badge. If spread is over 15 min, offer alternatives. If anyone cannot make check-in even with the fastest option, mark the team **at risk** and say who and by how much.
5. Save with a version number. Any change in members, start points, or event time creates a new version and notifies the team.

**Join-on-the-way suggestions** (run for each pair, teams are small)

- Start together: starts within about 800 m and walking under 10 min → meet and travel together.
- Same transit line: same line and boarding stop, or one route passes the other's stop → meet at that stop.
- Pick-up (cab/bike): `detour = time(A→B→venue) − time(A→venue)`. Suggest if detour is within 10 min.
- A suggestion becomes active only when every member accepts. Cab cost split is an estimate and labelled so.

**Live tracking (Should)**

- App sends position every 15 s during an active journey via a foreground service. Teammates poll `/teams/{id}/live` every 15 s (WebSocket is a later upgrade).
- If ETA is more than 10 min later than planned, send `teammate_delayed` and recompute the team arrival.
- Within about 150 m of the venue marks arrived and stops sharing.
- Privacy: sharing starts only when the user starts it, is visible only to the team, can be stopped anytime, ends 1 hour after event start, positions deleted 24 h after the event.

### 4.10 ML module (keep it honest)

| Model | Approach |
| --- | --- |
| Delay-risk buffer | Gradient boosting with quantile loss 0.9 (scikit-learn, joblib). Features: mode, hour of day, weekday, distance, transfers, walking share. Output clamped to 0–20 min. **Fallback: rule table by mode and time of day** |
| Recommendations | Transparent formula first: `0.40 interest_match + 0.25 proximity + 0.20 popularity + 0.15 urgency` with a human-readable reason |

Training data: sample the routing API's driving durations across times of day (real signal for road traffic). Transit and walking use the rule-based buffer until real usage data exists. Any synthetic data is **labelled as synthetic** in the repo and the pitch. Report mean absolute error and coverage (target near 90%).

### 4.11 Android app design

- **Stack:** Kotlin, Compose, Material 3, MVVM with repositories, Hilt, Retrofit + OkHttp (auth interceptor + refresh authenticator), Room, DataStore, Maps Compose, fused location, FCM, WorkManager, Coil, ZXing.
- **Layers:** `data/` (remote, local, repositories), `domain/` (models, use cases), `ui/` (theme, components, screens), `service/` (LocationService, MessagingService), `di/`, `util/`.
- **Screens:** Onboarding, Sign up/Login, Home (map + list), Event details, Register solo, Create/Join team, Team lobby, My Events, Journey planner, Team plan, Live journey, Notifications, Profile.
- **Rules:** every screen has loading, empty, and error states. Tokens in encrypted storage. If location is denied, fall back to the saved start point. Keys in `local.properties`, never committed.

### 4.12 Web design

- Plain HTML/CSS/JS with ES modules. Leaflet + OpenStreetMap for discovery and the pin picker. Chart.js for admin stats. html5-qrcode for check-in.
- Shared `api.js` wrapper adds the token and refreshes on 401. Escape all user text when rendering.
- Student web shows a **basic** route only (best route, time, leave-by, short steps) and links to the app for live navigation and alerts.

## 5. Repository and working agreements

```
eventroute/
  backend/   app/ (main.py, api/, models/, schemas/, services/, core/), alembic/, tests/, Dockerfile
  android/   Android Studio project
  web/       shared/, student/, admin/, assets/
  ml/        data scripts, training, evaluation, saved models
  docs/      this document, API notes, pitch material
```

- **Branches:** `main` is always demo-ready, `develop` for integration, short feature branches, PRs reviewed by a teammate.
- **API changes:** announce before merging. The OpenAPI file is the single source of truth. Mock JSON lets A and C work before endpoints exist.
- **Local dev:** Docker Compose runs Postgres and the API, with a seed command. Everyone gets the same setup.
- **Secrets:** environment variables only (`DATABASE_URL`, `JWT_SECRET`, `GOOGLE_MAPS_SERVER_KEY`, `ORS_API_KEY`, `FCM_SERVICE_ACCOUNT_JSON`, SMTP settings, `ALLOWED_ORIGINS`, `APP_BASE_URL`, `CLOUDINARY_URL`, cab fare config, `DAILY_PAID_ROUTE_LIMIT`). Restrict keys: Android by package + signing cert, web by domain, routing keys on the backend only.
- **Definition of done:** works on the real backend, has loading/empty/error states, tested on a real device or browser, and has at least a basic test for its rules.
- **Weekly rhythm:** short sync every couple of days (what is done, what is blocked, what changes in the API). Demo to each other at the end of every phase.

## 6. Testing

| Level | Focus | Owner |
| --- | --- | --- |
| Backend unit | Leave-by, buffer clamp, team size/deadline/seat rules, invite codes, team planner with fixed inputs | B |
| Backend API | Auth flows, role and ownership checks, registration and team flows, error codes | B |
| Provider | Parse saved sample responses, fallback behaviour | B |
| Android | ViewModel and repository tests, a few Compose tests for registration and journey | A |
| Web | Manual pass per page and browser, form validation, token refresh | C |
| End to end | Full demo script on real devices at least twice, once on poor network | All |

## 7. Edge cases to handle

- Event time or venue changes after registration: recompute plans, notify, show a banner.
- Event cancelled: close registrations and teams, remove scheduled reminders.
- Team drops below minimum, or the leader leaves (leadership passes to earliest joiner).
- Late registration where leave-by already passed: Leave now mode.
- Location denied or GPS off: use saved start point.
- No route, provider down, or quota hit: cached plan, straight-line distance, retry.
- Invite code used after the team is confirmed or cancelled.
- Overlapping events: warn the student.

## 8. Demo plan (about 3 minutes) and readiness

1. Admin portal: organizer posts an event by placing a pin on the map.
2. Android app: the event appears with travel time from the student's college.
3. Solo registration, then the full step-by-step route and leave-by time.
4. Team registration for a team event, three members from different places, each personal leave-by, and one travel-together suggestion.
5. Notification and live team ETA.
6. Student website basic route and its link to the app.
7. Close with impact and future scope.

**Readiness checklist**

- 15 to 20 demo events in one city (mix of solo and team), clearly labelled demo data. At least one real club event if possible.
- Accounts: one admin, one organizer, three students on separate phones with different start points.
- Backend awake (free hosts sleep, so wake it minutes before), database seeded, route keys working.
- APK installed on demo phones, backup install via QR.
- Team scenario tested: three start points, leave-by times checked for sense.
- Backup screen recording of the whole flow in case of bad internet.
- Slides, survey results, architecture diagram, and answers to likely judge questions ready.

## 9. Risks and mitigations

| Risk | Mitigation | Owner |
| --- | --- | --- |
| Scope too large | Lock the MVP (discover, register, solo route, team planner). Use the cut order in section 3 | All |
| Transit data is patchy in the demo city | Test coverage in Phase 3; fall back to walk, bike, drive; choose demo routes that work | B |
| Empty platform | Seed labelled demo data, onboard a real club | C |
| Routing API cost or quota | Cache, discovery uses one fast mode, daily budget counter, free ORS as fallback | B |
| ML with little data | Rule-based buffer first, say plainly what is real vs synthetic | B |
| Live location privacy | Team-only, trip-only, consent, off switch, auto-delete | A+B |
| Free backend sleeps during demo | Wake it before presenting, uptime pinger on `/health` | B |
| Integration surprises late | API contract in Phase 0, end-of-phase demos, `main` always demo-ready | All |
