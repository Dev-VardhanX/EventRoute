# EventRoute

Team Kiko · SGU_HACKX-1.0 · Dev Vardhan, Khushi, Lucky

> Others tell you what to attend. EventRoute gets you there.

Students discover inter-college events on a map, register solo or as a team, and get a full journey plan from their college to the venue. **Team Travel Mode** gives every teammate a personal leave-by time so the whole team arrives together.

Core formula: `leave_by = check_in_time - travel_time - buffer`

## Read these first

| Doc | For | Owner |
| --- | --- | --- |
| [docs/BACKEND.md](docs/BACKEND.md) | FastAPI, database, routing, team planner, notifications, ML | Backend + ML lead |
| [docs/WEBSITE.md](docs/WEBSITE.md) | Student website, organizer portal, admin portal, demo data, pitch | Web + Product lead |
| [docs/MOBILE_APP.md](docs/MOBILE_APP.md) | Android app (Kotlin, Compose) | Android lead |

Everyone should read all three at least once. The API contract (OpenAPI at `/docs` on the running backend) is the single source of truth.

## Repo layout

```
eventroute/
  backend/            FastAPI app (owner: backend lead)
    app/
      api/            route handlers, one file per area (auth, events, teams, ...)
      models/         SQLAlchemy models
      schemas/        Pydantic request/response models
      services/       business logic (registration, routing, planner, notifications)
      core/           config, security, db session, errors
    alembic/          migrations
    tests/
    scripts/          seed script, helpers
    Dockerfile
    requirements.txt
  android/            Android Studio project (owner: Android lead)
  web/                (owner: web lead)
    shared/           api.js wrapper, mock data, shared base CSS
    student/          student website
    admin/            organizer + admin portal
    assets/           images, icons
  ml/                 data scripts, training, evaluation, saved models
  docs/
    BACKEND.md  WEBSITE.md  MOBILE_APP.md
    api/mock/         mock JSON so Android and web can start before endpoints exist
    design/           Stitch exports (android/, web/)
    pitch/            deck, survey results, demo script
  .github/            PR template
  docker-compose.yml  Postgres + API
  .env.example        copy to .env, never commit .env
```

## Quick start

```bash
git clone <repo-url> && cd eventroute
cp .env.example .env
docker compose up --build        # API on http://localhost:8000, docs at /docs
```

- Android: open `android/` in Android Studio.
- Web: serve `web/` with any static server, for example `npx serve web -l 5500`.

## Working agreements

- **One owner per area.** Backend: backend lead. Android: Android lead. Web, demo data, pitch: web lead.
- **Branches:** `main` is always demo-ready. `develop` is for integration. Work on short branches named `area/short-description` (for example `android/home-screen`, `backend/team-rules`, `web/event-wizard`). Open a PR into `develop`.
- **Reviews:** Android lead reviews backend PRs, backend lead reviews web PRs, web lead reviews Android PRs.
- **API changes:** announce in the group chat before merging. Update the OpenAPI schema in the same PR.
- **Secrets:** environment variables only. Never commit keys, `google-services.json`, `local.properties`, or `.env`.
- **Definition of done:** works against the real backend, has loading/empty/error states, tested on a real device or browser, and has a basic test for its rules.
- **Sync:** short check-in every couple of days (done, blocked, API changes). Demo to each other at the end of every phase.

## Phases at a glance

| Phase | Goal | Done when |
| --- | --- | --- |
| 0 Setup | Everyone can run their part, API stubs published | Swagger shows every MVP endpoint with example responses |
| 1 Core data | Events exist and can be browsed | Organizer creates an event on web and it appears in Android |
| 2 Registration and teams | Solo register, create/join/confirm team | Three students form and confirm a team |
| 3 Routing | Travel time on cards, full route with leave-by | Event card shows travel time and a full route |
| 4 Team Travel Mode | Arrive-together planner | Three members see personal leave-by, arrival spread, a travel-together suggestion |
| 5 Smart features and alerts | Scheduler, push, ML buffer | Leave-soon push arrives and a delay changes the team plan |
| 6 Polish, deploy, rehearse | Demo-ready | Full demo runs twice on real devices, once on poor network |

**Cut order if time runs short:** recommendation score, WebSocket upgrade, admin stats, join-on-the-way, ML model (keep the rule-based buffer), live tracking (keep static leave-by times).
**Never drop:** discovery, registration, solo route with leave-by, arrive-together team plan.
