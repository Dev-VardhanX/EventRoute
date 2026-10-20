# Mobile App Guide (Android, Kotlin + Jetpack Compose)

**Owner:** Android lead · **Depends on:** Backend API (starts from mock JSON on day one)

The Android app is the full EventRoute experience: discovery, registration, teams, journey planning, Team Travel Mode, live team ETA, and push alerts.

## 1. Stack

Kotlin, Jetpack Compose, Material 3, MVVM with repositories, Hilt, Retrofit + OkHttp (auth interceptor + refresh authenticator), Room, DataStore, Navigation Compose, Maps Compose, fused location, FCM, WorkManager, Coil, ZXing.

## 2. Project layout

Create the Android Studio project in `android/`. Suggested package: `com.eventroute.app`.

```
app/src/main/java/com/eventroute/app/
  data/
    remote/        Retrofit API interfaces, DTOs, auth interceptor, token authenticator
    local/         Room database, DAOs (cache of My Events), DataStore (settings, tokens via encrypted storage)
    repository/    one repository per area (Auth, Events, Registration, Teams, Journey, Notifications)
  domain/
    model/         domain models
    usecase/       leave-by display helpers, validation, mapping
  ui/
    theme/         colors, typography, shapes (indigo primary, amber for leave-by only)
    components/    EventCard, TravelTimeChip, ParticipationBadge, SeatsLeftPill,
                   LeaveByBanner, RouteModeChip, TeamAvatarRow, LoadingState, EmptyState, ErrorState
    navigation/    NavGraph, routes, deep link config
    screens/       onboarding, auth, home, eventdetails, registration, teams, myevents,
                   journey, teamplan, livejourney, notifications, profile
  service/         LocationService (foreground), AppMessagingService (FCM)
  di/              Hilt modules
  util/            time formatting (event time zone), permissions, QR
```

## 3. Local setup

1. Open `android/` in Android Studio.
2. Add to `local.properties` (never committed): `MAPS_API_KEY=...`, `API_BASE_URL=http://10.0.2.2:8000/api/v1/` (emulator) or your LAN IP for a real device.
3. Put `google-services.json` in `app/` (never committed) once Firebase is created.
4. Start with `USE_MOCK = true` in a debug build flag so repositories read bundled JSON copied from `docs/api/mock/`. Switch to the real backend per endpoint as it goes live.

## 4. Rules for every screen

- Every screen has **loading, empty, and error** states.
- Talk only to the backend. Routing keys never live in the app.
- Auth: access token in the `Authorization` header; on 401 do one silent refresh and retry; if refresh fails, go to Login. Tokens in encrypted storage.
- Errors use `{ code, message, details? }`. Map each code to a friendly message: EVENT_FULL, EVENT_CLOSED, ALREADY_REGISTERED, TEAM_FULL, TEAM_TOO_SMALL, NOT_ELIGIBLE, TIME_CLASH, INVITE_INVALID, ROUTE_UNAVAILABLE, RATE_LIMITED.
- Times arrive as UTC ISO strings; display in the event's `timezone` (default Asia/Kolkata).
- **Location denied or GPS off:** fall back to the user's saved start point. Never block the flow.
- No route or provider down: show the cached plan or straight-line distance with a retry button.
- Amber is used only for leave-by times and countdowns.
- Keys in `local.properties`, never committed.

## 5. Screens and API usage

| Screen | Priority | Backend endpoints |
| --- | --- | --- |
| Onboarding | Should | n/a |
| Sign up (name, college, college email, role), email code verification, login, token refresh | MVP | `GET /colleges`, `/auth/register`, `/auth/verify-email`, `/auth/login`, `/auth/refresh` |
| Home: map (clustering) + list toggle, filters, search, travel-time chips | MVP | `GET /events`, `POST /events/travel-estimates` |
| Home sections (Near my college, Closing soon, Recommended), save events, share link | Should | `GET /events` with sort params |
| Event details (badge, seats left, countdown, venue map, Get directions, travel preview with leave-by, state-based action button) | MVP | `GET /events/{id}`, `GET /events/{id}/journey` |
| Solo registration (one tap) | MVP | `POST /events/{id}/register` |
| Create team (invite code, link, QR), join team, team lobby, leader confirm, leave | MVP | `/events/{id}/teams`, `/teams/join`, `/teams/{id}`, `/teams/{id}/confirm`, `DELETE /teams/{id}/members/{user_id}` |
| My Events (cached with Room for offline) + ticket QR | MVP / Should | `GET /me/registrations`, `DELETE /registrations/{id}` |
| Journey planner: origin selector, up to 3 route cards, step-by-step legs, polyline map, leave-by countdown with "how we calculated this", save plan | MVP | `GET /route`, `GET /events/{id}/journey`, `POST /journey/{plan_id}/select` |
| Team plan: member start points and preferences, team arrival time, personal leave-by per member, arrival spread badge | MVP | `PUT /teams/{id}/me/start-point`, `GET /teams/{id}/plan` |
| Join-on-the-way suggestions (accept/decline) | Should | `POST /teams/{id}/suggestions/{sid}/respond` |
| Live journey: foreground service, location every 15 s, team ETA board, quick status, delay alert and re-plan | Should | `/teams/{id}/journey/start` and `/stop`, `/location`, `/live`, `/status` |
| Notifications list, notification settings | Should | `GET /me/notifications`, `POST /me/notifications/{id}/read` |
| Profile (photo, interests, default start point) | MVP | `GET/PATCH /me`, `PUT /me/start-point`, `POST /me/device-token` |

## 6. Leave-by display rules

- Show the `leave_by` time returned by the backend as the main number, with a live countdown.
- "How we calculated this" shows the breakdown the backend returns (travel time, buffer parts, check-in). Do not recompute leave-by on the device.
- If the backend returns **Leave now** mode, show an honest earliest arrival instead.
- Team plan: show the spread badge (green when small, red "At risk" when the backend flags it, with who and by how much).
- Cab split estimates must be labelled as estimates.

## 7. Deep links and push

- Deep links: `/event/{id}` opens event details, `/invite/{code}` opens the join-team flow (also works from QR scan).
- FCM: register the token with `POST /me/device-token` after login. Handle notification types (registration confirmed, team updates, deadline reminder, event reminder, leave soon, leave now, join suggestion, teammate delayed, event updated/cancelled) and route taps to the right screen.
- Notification channels per type group so users can mute categories.

## 8. Live journey and privacy

- Start sharing only when the user taps Start journey. Show a persistent notification while the foreground service runs.
- Send position every 15 s. Poll `/teams/{id}/live` every 15 s.
- Visible only to the team. Stop sharing button always available. Stops near the venue (backend marks arrived).
- Clearly say who can see the location.

## 9. Flow of work (phase by phase)

| Phase | Your tasks | Done when |
| --- | --- | --- |
| **0 Setup** | Project with theme, navigation, Hilt, Retrofit pointed at mock data, base components, loading/empty/error components. Create Firebase project with backend lead. Put design exports in `docs/design/android/` | App launches and shows mock events |
| **1 Core data** | Sign up, email code, login, token refresh, home list, event details (switch to real API as endpoints go live) | Organizer creates an event on web and it appears in the app |
| **2 Registration and teams** | Solo register, create/join team, team lobby, My Events | Student registers solo; three students form and confirm a team |
| **3 Routing** | Journey planner: route cards, step list, polylines, leave-by countdown, travel-time chips on cards. Pair with backend lead on the route response format | Event card shows travel time; route screen shows full route with leave-by |
| **4 Team Travel Mode** | Member start-point setting, team plan screen, live journey screen with team ETA board. Pair with backend lead on the team plan screen | Three members at different start points each see personal leave-by, spread badge, a travel-together suggestion |
| **5 Smart features and alerts** | FCM receiving, reminders, deep links | Leave-soon push arrives on a phone and a delay changes the team plan |
| **6 Polish, deploy, rehearse** | Release build, APK + QR install, performance, offline states | Demo runs twice on real devices, once on poor network |

If time runs short, drop in this order: recommendation UI, live tracking (keep static leave-by times), join-on-the-way. Never drop discovery, registration, solo route with leave-by, and the team plan.

## 10. Testing

ViewModel and repository tests, plus a few Compose tests for registration and journey. Test on a real device, with location denied, and on a poor network. Run the full demo script twice with the team.

## 11. Working agreements

- Branch `android/<topic>`, PR into `develop`. Web lead reviews your PRs.
- Pair with backend lead on the route response format and the team plan screen.
- Ask before assuming an endpoint shape. If it is not in Swagger, request it in the group chat.
- Definition of done: works against the real backend, has loading/empty/error states, tested on a real device, basic tests for its rules, no secrets committed.
- Demo release: build a release APK, share via QR for backup install, and install on all demo phones before the final run.
