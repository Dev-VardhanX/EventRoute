# Website Guide (Student site + Organizer portal + Admin portal)

**Owner:** Web + Product lead · **Depends on:** Backend API (starts from mock JSON on day one)

You own three web surfaces plus demo content and pitch material:

1. **Student website:** lighter version of the app. Discovery, registration, teams, a basic route, and a link to the Android app for live navigation.
2. **Organizer portal:** post events with a map pin, manage registrations, announcements, QR check-in.
3. **Admin portal:** approve organizers, manage colleges, moderate, stats.
4. **Product work:** seed data content, survey, pitch deck, demo script, testing checklist.

## 1. Stack

Plain HTML, CSS, JavaScript with ES modules. Leaflet + OpenStreetMap for the discovery map and the pin picker. Chart.js for admin stats. html5-qrcode for check-in. Deploy on Netlify or Firebase Hosting.

## 2. Folder layout

```
web/
  shared/
    api.js          fetch wrapper (adds token, refreshes once on 401, one error shape, esc())
    mock/           mock JSON copied from docs/api/mock/ while endpoints are not live
    base.css        shared variables (colors, spacing), buttons, cards, badges
  student/
    index.html      landing + discovery
    pages/          event.html, team.html, route.html, invite.html, my-events.html, login.html, profile.html
    css/  js/
  admin/            organizer + admin portal
    index.html      login
    pages/          dashboard, event-wizard, registrations, announcements, checkin,
                    approvals, colleges, moderation, reports, users, stats
    css/  js/
  assets/           img/, icons/
```

## 3. Local setup

```bash
npx serve web -l 5500          # or VS Code Live Server
```

- Open `student/index.html` or `admin/index.html`.
- In `shared/api.js`, `USE_MOCK = true` reads `web/shared/mock/*.json`. Flip to `false` when the real endpoint is live.
- Add your dev origin to `ALLOWED_ORIGINS` in the backend `.env`.

## 4. Rules for every page

- Use only `shared/api.js` for network calls. Never call routing providers or other services directly.
- **Escape all user text** with `esc()` before inserting into HTML.
- Every page has loading, empty, and error states.
- Responsive at desktop and mobile widths (student site must be usable on a phone).
- The server enforces permissions. Hiding a button is not security.
- Error shape is `{ code, message, details? }`. Show a friendly message per `code` (EVENT_FULL, EVENT_CLOSED, ALREADY_REGISTERED, TEAM_FULL, NOT_ELIGIBLE, TIME_CLASH, INVITE_INVALID, ROUTE_UNAVAILABLE, RATE_LIMITED, UNAUTHENTICATED).
- Times come as UTC ISO strings. Display in the event's `timezone` (default Asia/Kolkata).

## 5. Pages and priorities

### Student website
| Page | Priority | Backend endpoints |
| --- | --- | --- |
| Landing + discovery (map with clustering, list, filters, search, travel-time chip) | MVP | `GET /events`, `POST /events/travel-estimates`, `GET /colleges` |
| Sign up with OTP, login, logout | MVP | `/auth/register`, `/auth/verify-email`, `/auth/login`, `/auth/refresh`, `/auth/logout` |
| Event page (core info, badges, seats left, countdown, venue map, state-based action button) | MVP | `GET /events/{id}`, `POST /events/{id}/register`, `DELETE /registrations/{id}` |
| Team pages (create, invite code/link/QR, join, lobby, leader confirm) | MVP | `/events/{id}/teams`, `/teams/join`, `/teams/{id}`, `/teams/{id}/confirm`, `DELETE /teams/{id}/members/{user_id}` |
| Basic route page (best route, time, leave-by, short steps) + Get-the-app banner with QR | MVP | `GET /route`, `GET /events/{id}/journey` |
| Invite landing (`/invite/{code}`) | MVP | `GET /invite/{code}`, `POST /teams/join` |
| My Events | MVP | `GET /me/registrations` |
| Profile and default start point | MVP | `GET/PATCH /me`, `PUT /me/start-point` |
| Team plan view | Should | `GET /teams/{id}/plan` |
| Save events, share link, online events tab | Should | saved events endpoints |

The student site shows a **basic** route only. Live navigation, push alerts, and team ETA live in the app, so every route page has a "Get the app" callout.

### Organizer portal
| Page | Priority | Backend endpoints |
| --- | --- | --- |
| Organizer sign-up and "awaiting approval" screen, login | MVP | `/auth/*` |
| Event wizard with Leaflet map pin picker (Basics, Date and venue, Participation, Review and publish) | MVP | `POST /events`, `PATCH /events/{id}`, `POST /events/{id}/publish`, `POST /uploads/image` |
| Draft, edit (warn that time/venue changes notify students), cancel | MVP | `PATCH /events/{id}`, `POST /events/{id}/cancel` |
| Dashboard (stat cards, registrations chart, my events table) | MVP | `GET /events`, registrations endpoints |
| Registrations table with search and CSV export | MVP | `GET /events/{id}/registrations`, `/registrations/export` |
| Team view, announcements, QR check-in, duplicate event | Should | `POST /events/{id}/check-in` |
| Attendee origin map | Stretch | n/a |

Wizard validation (pair with backend): `team_min <= team_max`, `check_in_at <= start_at`, `registration_deadline <= check_in_at`, venue pin required.

### Admin portal
| Page | Priority | Backend endpoints |
| --- | --- | --- |
| Organizer approval queue | MVP | `GET /admin/organizers?status=pending`, `POST /admin/organizers/{id}/approve` and `/reject` |
| College management | MVP | `GET/POST/PATCH /admin/colleges` |
| Event moderation, reports queue, user search/deactivate, platform stats | Should | admin endpoints as they appear |
| Audit log | Stretch | n/a |

## 6. Flow of work (phase by phase)

| Phase | Your tasks | Done when |
| --- | --- | --- |
| **0 Setup** | Folder structure, `api.js`, `base.css`, static layouts reading mock JSON. Create hosting accounts. Start collecting design exports in `docs/design/web/` | You can run both sites locally on mock data |
| **1 Core data** | Admin login, event wizard with map pin picker, organizer dashboard, student event list and detail. Start the seed content list (15-20 demo events in one city, clearly labelled demo, demo accounts: 1 admin, 1 organizer, 3 students) | Organizer creates an event on the web portal and it appears in the Android app |
| **2 Registration and teams** | Register and team pages, registrations table, CSV export. Invite landing page | Student registers; three students form and confirm a team |
| **3 Routing** | Basic route page on the student site, Get-the-app banner with QR. Run the 20-30 student survey | Route page shows best route, time, leave-by |
| **4 Team Travel Mode** | Team plan view on web, check-in page (QR scan), team scenarios in the demo dataset | Team plan view matches the app |
| **5 Smart features and alerts** | Announcements page, admin approvals and stats | Announcement sends push + email; admin approves an organizer |
| **6 Polish, deploy, rehearse** | UI polish, responsive checks, deploy to Netlify/Firebase, pitch deck, survey results, demo script, backup screen recording | Demo runs twice on real devices, once on poor network |

## 7. Product and demo ownership (you own this too)
- **Seed content:** 15-20 clearly labelled demo events in one city, a mix of solo and team. At least one real club event if possible. Keep seed data in sync with the backend lead.
- **Demo accounts:** one admin, one organizer, three students on separate phones with different start points.
- **Survey:** 20-30 students, results go in the deck.
- **Pitch deck and demo script (about 3 minutes):**
  1. Admin portal: organizer posts an event by placing a pin on the map.
  2. Android app: the event appears with travel time from the student's college.
  3. Solo registration, then the full step-by-step route and leave-by time.
  4. Team registration, three members from different places, each personal leave-by, one travel-together suggestion.
  5. Notification and live team ETA.
  6. Student website basic route and its link to the app.
  7. Close with impact and future scope.
- **Backup:** full screen recording of the whole flow in case of bad internet.
- **Readiness checklist:** backend awake before presenting, database seeded, route keys working, APK installed on demo phones with a backup install QR, team scenario tested, slides and likely judge answers ready.

## 8. Testing
Manual pass per page and browser (Chrome and one other), form validation, token refresh (let a token expire and confirm one silent retry), empty/error states, mobile width. Full end-to-end demo run with the team.

## 9. Working agreements
- Branch `web/<topic>`, PR into `develop`. Backend lead reviews your PRs.
- Pair with backend lead on the map pin picker and wizard validation.
- Ask before assuming an endpoint shape. If it is not in Swagger, request it in the group chat.
- Definition of done: works on the real backend, has loading/empty/error states, tested in a browser at desktop and mobile width, no secrets in code.
