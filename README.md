# Belsen-AI

**Plan better. Stay consistent. Grow.**

Belsen-AI is a planned AI-assisted personal growth web application. It will help users set measurable goals, organize tasks, maintain habits, and understand their weekly progress.

This is a personal portfolio project focused on maintainable Java backend engineering and reliable analytics.

## Project status

**Day 5 backend: goals, tasks, habits and analytics API.** Email/password authentication, JDBC sessions and private profiles are implemented. Google OIDC is implemented and tested with a local mock provider; live Google browser login awaits external verification. The React frontend supports accounts, goals, explicit progress/weekly plans, tasks and habit workflows. Dashboard and historical analytics interfaces are implemented.

| Area | Status |
| --- | --- |
| Product scope and architecture | Documented |
| Backend configuration, migrations and health | Implemented; verification results below |
| Email/password, sessions and profiles | Implemented; PostgreSQL/HTTP tests |
| Google OIDC | Implemented; mock-provider tests; live verification pending |
| Goals, progress entries and weekly targets | Implemented; goals-stage verification below |
| Tasks and append-only history | Implemented; task-stage verification below |
| Habit routines, rest and historical check-ins | Implemented; PostgreSQL/HTTP tests |
| Strict habit consistency | Implemented in API and Dashboard/Analytics interfaces |
| Analytics dashboard API | Implemented; one read-only snapshot and historical cutoff |
| Immutable closed-week reports | Planned; closed reports currently remain unfinalized |
| AI coaching | Interface and optional Groq ranking backend implemented; disabled by default; live-provider verification pending |
| Foundation automated tests | Implemented; verification results below |
| React frontend | Accounts, goals, tasks and habits implemented; dashboard/historical analytics implemented |
| Production packaging | Implemented; single-origin JAR and optional runtime image; see B6 below |
| CI and public deployment | Planned |

The original target for the first working MVP was **November 6, 2026**; it remains an unmet historical target. The roadmap describes acceptance criteria; it is not a claim that those criteria have been met.

## Technical direction

- Java 21, Spring Boot, Maven, Spring Security and PostgreSQL.
- A modular monolith with feature-based Java packages and Flyway migrations.
- React, TypeScript and Vite in a separate frontend directory.
- One HTTPS origin: the B6 production package serves React assets and the REST API through Spring Boot.
- Verified analytics calculated in Java; optional AI suggestions displayed separately.

## Local backend setup

Requires Java 21, Docker with a running daemon, and network access for the first dependency/image download. Maven Wrapper pins Maven 3.9.12; a system Maven installation is not required.

Run from the repository root:

```sh
cp .env.example .env
```

Edit the ignored `.env`: fill the six required database values. Choose your own local password and shell-quote values containing special characters. Match both credential sets and use `jdbc:postgresql://127.0.0.1:5433/<your-database-name>` for the JDBC URL. If changing the database host port, update the URL too. Never commit `.env`.

```sh
docker compose --env-file .env -f infra/compose.yaml up -d --wait
set -a
. ./.env
set +a
SPRING_PROFILES_ACTIVE=local ./backend/mvnw -f backend/pom.xml spring-boot:run
```

Source only your trusted local `.env`. Compose reads that file explicitly; the backend needs the exported environment. The `local` profile binds the backend to loopback and permits HTTP cookies. Do not enable it in production. Without this profile, cookies require HTTPS.

Defaults are PostgreSQL `127.0.0.1:5433` and backend `127.0.0.1:8081`. Optional `BELSEN_POSTGRES_PORT` and `BELSEN_SERVER_PORT` overrides are documented in `.env.example`. Check ports again before starting. Compose uses project `belsen-ai` and volume `belsen-ai_postgres-data`; it does not manage TIR 2.0 resources.

In another terminal:

```sh
curl -i http://127.0.0.1:8081/api/v1/health
curl -i http://127.0.0.1:8081/api/v1/me
```

Expected responses are `200` with only `{"status":"UP"}` and `401` Problem Details, respectively. Health is a minimal process endpoint, not a continuous database-readiness probe. Startup requires working database configuration, migrations and schema validation. The unauthenticated `/me` request is rejected; authenticated sessions can read/update their own profile.

Stop the application with Ctrl-C. Stop only this project's database with `docker compose --env-file .env -f infra/compose.yaml stop`. The named volume persists; changing initialization credentials later does not reset an existing database.

## Verification

```sh
./backend/mvnw -f backend/pom.xml test
./backend/mvnw -f backend/pom.xml verify
```

`test` runs database-independent MVC/security/configuration tests. `verify` also packages the application and runs PostgreSQL Testcontainers integration tests (`*IT`). Docker is mandatory for full verification; database tests fail rather than silently skip when Docker is unavailable. Tests use a separate temporary database and do not require the local `.env` or a running Compose database.

Stage B passed its original 20 tests and a Compose + packaged-JAR smoke test. **Stage C verified November 1, 2026:** Maven `verify` passed **51 tests**, with zero failures/errors/skips, including the original 20 foundation cases. PostgreSQL/Flyway and Hibernate validation passed. A packaged-JAR + Compose HTTP smoke test passed registration, login, profile updates, persisted session requests and logout invalidation. Test resources were cleaned up. Live Google browser login and production deployment have not been verified.

## Authentication API

| Method and route | Behavior |
| --- | --- |
| `GET /api/v1/auth/csrf` | Return `headerName` and `token`; establish the browser session cookie |
| `POST /api/v1/auth/register` | Accept email, password, displayName and timezone; return `201` with a safe profile and sign in |
| `POST /api/v1/auth/login` | Accept email/password; return `200` with a safe profile and sign in |
| `POST /api/v1/auth/logout` | Require CSRF, invalidate the session and clear its cookie; return `204` |
| `GET /api/v1/me` | Return the current account's profile |
| `PATCH /api/v1/me` | Update displayName and/or IANA timezone only |

Use JSON and retain the cookie on subsequent requests. Before a mutation, fetch `/api/v1/auth/csrf` and send the returned token in the returned header (normally `X-CSRF-TOKEN`). **Fetch a fresh token after registration, login or Google login**, because authentication rotates the session ID and invalidates the old token. Logout also requires the token. Do not use localStorage tokens.

Emails are stripped and lowercased. Registration passwords need at least 12 characters and at most 72 UTF-8 bytes; passwords are never trimmed. Timezones must be named IANA identifiers such as `Asia/Almaty` or `UTC`, not numeric offsets. Unknown JSON fields, including `userId` and `accountId`, are rejected. Errors use Problem Details, with `fieldErrors` for validation.

## Google setup and verification status

Setup reference: [Google OpenID Connect documentation](https://developers.google.com/identity/openid-connect/openid-connect). Google is disabled unless `BELSEN_GOOGLE_ENABLED=true`. Password login works without Google credentials. To enable Google:

1. Create/select a Google Cloud project and configure Google Auth Platform branding, audience/consent and data access. Request only `openid`, `profile` and `email`; add your account as a test user when the consent application is in testing mode.
2. Create an OAuth client of type **Web application**.
3. Add the exact authorized redirect URI **`http://localhost:5173/login/oauth2/code/google`** for the Vite browser journey. If the browser-facing port changes, change this URI too. Production needs its separate exact HTTPS callback.
4. Put `BELSEN_GOOGLE_ENABLED`, `BELSEN_GOOGLE_CLIENT_ID` and `BELSEN_GOOGLE_CLIENT_SECRET` in your ignored `.env`, export them and restart. Missing credentials fail startup when enabled. No JavaScript origin is needed for this server-side authorization-code flow.
5. Start Vite with `VITE_GOOGLE_ENABLED=true` only when the backend Google configuration is enabled. Open **`http://localhost:5173/auth`** and choose Continue with Google. Use the same hostname throughout; `localhost` and `127.0.0.1` are different cookie hosts. Success uses `/auth/callback`; failure uses `/auth?error=google`, with no provider diagnostics. Standalone backend JSON use does not serve these SPA pages.

Automated tests use a local mock provider, signed ID tokens and PostgreSQL. They verify state, nonce/signature rejection, account mapping and sessions without contacting Google. **A real Google browser login has not been run.** No account linking is implemented: an existing email with a different sign-in identity is rejected. New Google accounts start in `UTC`; update the profile afterward.

## Goals API (Day 3, Stage B)

All routes below require the session cookie. Mutations also require a valid CSRF token. Owner IDs are never accepted. Another account's goal or progress entry returns `404`.

| Method and route | Contract |
| --- | --- |
| `POST /api/v1/goals` | `title`, optional `description`, positive `target`, `unit`, optional `startDate`/`deadline`; returns `201` |
| `GET /api/v1/goals?page=0&size=20` | Stable paginated list, size 1–100 |
| `GET /api/v1/goals/{id}` | Metadata, version, lifetime units/percentage and separate weekly-plan results |
| `PATCH /api/v1/goals/{id}` | Required `version` plus changed metadata; omission preserves values, explicit null clears description/startDate/deadline only |
| `POST /api/v1/goals/{id}/archive` | Body contains expected `version` |
| `POST /api/v1/goals/{id}/progress` | `submissionId` UUID, positive `quantity`, `effectiveDate`; new entry `201`, identical retry `200`, conflicting reuse `409` |
| `GET /api/v1/goals/{id}/progress` | Paginated history including voided entries and frozen reporting-week metadata |
| `POST /api/v1/goals/{id}/progress/{entryId}/void` | Void an open-week entry without deleting it; replacement is a separate submission |
| `PUT /api/v1/goals/{id}/weekly-plans/{weekStart}` | Positive `plannedUnits`; omit version on creation, supply the current plan version for future-week updates |

Quantities use `NUMERIC(19,4)`/BigDecimal; invalid precision is rejected, not rounded. Percentages alone are rounded to one decimal and capped at 100. Lifetime progress divides all unvoided units by the total goal target. Each weekly result divides only that week's units by its own weekly target. No weekly plan is represented by an empty list, not an invented percentage.

The first progress or weekly-plan write freezes the account's current reporting week, even with no progress. Future plans freeze their own weeks too. Stored timezone and instant boundaries survive profile timezone changes; started plans cannot be edited, and closed-week progress cannot be added or voided. A timezone transition can shorten an unreserved adjacent week's window to avoid overlap. If a westward timezone change still labels the current local date as an already-closed week, writes return `409` until the next eligible local week rather than reopening history. Reads remain available.

Archived goals retain history but reject changes. Identical progress retries still return their original entry, including its void state. Units cannot change after entries or plans exist; changing the lifetime target recalculates only the lifetime percentage. Stale versions return `409`.

**Verified November 2, 2026:** Maven `verify` passed 83 tests (all 58 existing cases plus 4 goal-rule tests and 21 PostgreSQL/HTTP goals tests), with zero failures/errors/skips. V1–V3 and Hibernate schema validation passed. Tasks were subsequently implemented in Stage C and habits in Day 4 B2; the dashboard API was subsequently added in Day 5; AI remains unimplemented; account interfaces arrived in Day 6 B2 and goals/tasks in B3.

## Tasks API (Day 3, Stage C)

Task requests require the existing authenticated session. Mutations require CSRF. No owner field is accepted, and foreign task IDs or linked goal IDs return `404`.

| Method and route | Request/behavior |
| --- | --- |
| `POST /api/v1/tasks` | Required `title`; optional `description`, `goalId`, `dueDate`, `estimatedDurationMinutes`; returns `201` |
| `GET /api/v1/tasks` | Bounded pagination and owned-task filters |
| `GET /api/v1/tasks/{id}` | Current task, audit timestamps, version, event sequence and original reporting-week context |
| `PATCH /api/v1/tasks/{id}` | Required `version`; optional metadata fields. Omission preserves values; explicit null clears description/goal/duration. Dates and status use actions |
| `POST /api/v1/tasks/{id}/complete` | Body `{"version": N}` |
| `POST /api/v1/tasks/{id}/reopen` | Body `{"version": N}` |
| `POST /api/v1/tasks/{id}/archive` | Body `{"version": N}` |
| `POST /api/v1/tasks/{id}/reschedule` | Required `version` and `dueDate`; explicit `null` removes the current date |

Use returned versions; do not assume a newly created dated task has version zero. A stale version always returns `409`, even if the requested state already matches. A repeat using the current version is a no-op with no extra event. Archived tasks retain OPEN/COMPLETED state and completion timestamp; only a matching-version repeat archive is allowed. Reopen a completed task before rescheduling.

Titles are 1–200 nonblank characters, descriptions at most 2,000, and optional duration 1–1,440 minutes. Linking requires an active owned goal. Archiving a goal does not archive existing tasks, and task completion never adds goal units.

List filters: `page` (default 0), `size` (1–100, default 20), `status=OPEN|COMPLETED`, `archived=true|false` (default false), `goalId`, and either exact `dueDate` or inclusive `from`/`to`. Date filters use the current due date. Ordering is creation instant then UUID. There is no task-event HTTP endpoint yet.

Undated tasks enter a reporting cohort on first scheduling. That original due date and frozen week never change, even if the current date is later removed. First assignment into a closed week is rejected. Subsequent rescheduling can change the planning date without moving the original reporting cohort. Completion/reopening/archive are recorded at the actual operation time; none backdate or rewrite closed history.

Events are stored transactionally with task state, have per-task sequence numbers, and retain state after each action. PostgreSQL rejects event UPDATE/DELETE. Completion/archive timestamps match their event timestamp. If an operation crosses its frozen calendar boundary before recording, it fails with `409` for retry instead of storing mixed-week context. Analytics calculations and event-history screens are not implemented.

**Verified November 2, 2026:** full Maven `verify` passed 109 tests, zero failures/errors/skips: all 83 previous cases plus 23 PostgreSQL/HTTP task tests and 3 task validation tests. Flyway V1–V4 and Hibernate validation passed.

## Habits API (Day 4, Stage B2)

Habit routes require the existing session; every mutation requires CSRF. Foreign IDs return `404`. Owners and audit fields are server-derived. No public consistency dashboard endpoint exists.

| Method and route under `/api/v1/habits` | Contract |
| --- | --- |
| `POST /` | `title`, optional `description`, 1–7 distinct ISO `weekdays` (Monday=1), `startDate` today or later, optional inclusive `endDate`; `201` |
| `GET /`, `GET /{id}` | Paginated non-archived list by default (`archived=true` selects archived); details include current and latest pending schedule and effective reporting week |
| `PATCH /{id}` | Expected `version`, changed title/description; omission preserves values, explicit null clears description |
| `POST /{id}/schedules` | `version`, `effectiveFrom` tomorrow or later, weekdays, startDate and optional endDate |
| `PUT /{id}/completions/{date}` | `version` and the date's Monday `weekStart`; scheduled non-rest dates in the current open week only, never future days |
| `DELETE /{id}/completions/{date}?weekStart=YYYY-MM-DD` | Undo using strict `If-Match: "N"` |
| `PUT /{id}/rest-days/{date}` | `version`, `weekStart`, nonblank `reason`; must precede the target day start |
| `DELETE /{id}/rest-days/{date}?weekStart=YYYY-MM-DD` | Cancel before day start, using `If-Match: "N"` |
| `GET /{id}/history?from=YYYY-MM-DD&to=YYYY-MM-DD` | At most 93 dates; independent day/action/schedule pagination, frozen calendar context and audit history |
| `POST /{id}/archive` | `version`; disable scheduling from tomorrow, retaining earlier obligations and history |

All mutations use the habit's returned version, including daily actions. Matching-version same-state repeats do not add events or increment the version; stale repeats return `409`. A delayed completion retry cannot undo a newer undo. DELETE requires exactly one quoted nonnegative integer: missing headers, weak validators, wildcards and lists return `400`. Dates use years 1900–9998. Collection sizes are 1–100.

Schedules are immutable. For a date, the highest accepted revision sequence whose effective date has arrived wins. A new revision supersedes older pending revisions from its effective date onward. `latestPendingSchedule` is the most recently accepted pending revision still capable of applying, not a full calendar of upcoming changes; paginated history retains all revisions. Archive wins over older pending schedules. Today's obligations cannot be removed by editing tomorrow's schedule, and archived habits can still complete or undo retained open-week obligations.

Rest is habit-specific, prospective and auditable. It cannot erase incomplete work after a day starts. If a schedule makes an existing rest inapplicable, history keeps `restPlanned=true` with `restApplicable=false`; cancellation remains possible before the day starts. Full-day account exclusions are not implemented. Planning a future rest freezes that week; conflicting later timezone changes return `409`. Matching-version no-op cancellation does not create a future reservation.

History returns separate `days`, `actions` and `schedules` pages. Parameters are `page`, `actionPage`, `schedulePage` (default 0) and shared `size` (default 20). Without `cutoff`, it returns current committed state, including equal-timestamp accepted events. With an explicit ISO instant `cutoff`, it returns only schedules/actions accepted strictly before that instant; future cutoffs return `400`, and a habit not yet created returns `404`. Sub-microsecond cutoffs are handled without losing persisted events. Unstarted dates are ineligible; an explicit-cutoff response omits their `reportingDay` because later timezone intentions must not appear as historical facts. Started dates carry effective timezone and frozen/clipped day/week boundaries. A computed, unreserved week has a null ID; detail reporting context can be null during a timezone transition gap, when calendar-dependent writes return `409`. `version` is the current concurrency token, not historical metadata. Reads never create missing occurrences or calendar rows.

Consistency rules are internal and tested: one day succeeds only when all its remaining scheduled obligations are complete; habit rest removes only that habit, future days are excluded, and zero eligible days produces a null percentage. No habit action creates goal progress.

**Verified November 4, 2026:** Maven `verify` passed **182 tests**: 42 unit and 140 PostgreSQL integration tests, zero failures/errors/skips. This preserves the 138-test B1 baseline and adds 9 rule tests plus 35 habit HTTP/integration tests. Flyway V1–V5 and Hibernate validation passed.

## Analytics dashboard API (Day 5, Stage B1)

`GET /api/v1/analytics/dashboard` requires the existing session and returns `Cache-Control: no-store`. No frontend dashboard is implemented. The endpoint never creates reporting weeks, occurrences or finalized snapshots.

| Parameter | Contract |
| --- | --- |
| `weekStart` | Optional ISO Monday, years 1900–9998; defaults to the current reporting week |
| `cutoff` | Optional ISO instant; requires weekStart and must be after period start, at or before period end, and not in the future |
| `goalsPage` | Default 0; nonnegative |
| `goalsSize` | Default 20; 1–100 |

Unknown, repeated or blank parameters are rejected with `400`, including owner IDs and timezone overrides. Unavailable historical periods return `404`; invalid calendar data or a current calendar gap returns `409`. Only an account with no calendar history can receive an explicitly `UNFROZEN` current-week preview from its profile timezone. Existing frozen periods and stored reporting policy always take precedence.

The response contains:

- `period`: weekStart, timezone, startsAt, endsAt, cutoff, cutoffExclusive, calendarStatus, reportStatus and calculationVersion.
- `tasks`: scheduled, completed, incomplete, percentage and reason.
- `habits`: eligibleDays, successfulDays, percentage and reason.
- `goals`: paginated items, page, size, totalElements and reason. Items include goalId, title, titleMetadata=`CURRENT`, unit, completedUnits, plannedUnits, percentage and reason.

Task completion divides tasks complete at cutoff by their original due-week cohort. First due assignment must exist before cutoff. Future due dates within that week count once assigned. Rescheduling, removing the current date and archive never remove obligations; reopening changes the reconstructed completion state. Zero tasks returns a null percentage and `NO_SCHEDULED_TASKS`.

Habit consistency counts fully successful eligible days, not individual checkmarks. Only started, valid reporting-day intervals count. Reconstruct effective schedules and last completion/rest events before cutoff; absent occurrences can still be incomplete obligations. Habit-specific rest removes only that habit. Zero eligible days returns null with `NO_SCHEDULED_ACTIVITY`.

Weekly goals use explicit entries created before cutoff and not voided before it. A void exactly at cutoff has not taken effect. A plan must exist before cutoff; missing targets return actual units, null plannedUnits/percentage and `NO_WEEKLY_TARGET`. Percentages cap at 100 while units remain uncapped. Include goals active at cutoff and relevant archived goals with weekly plan/activity; exclude later-created goals. Empty collections use `NO_GOALS`. Goal titles are current metadata, not historical titles. Units also come from the current goal row; they become fixed once planning/progress exists, but earlier metadata versions are not reconstructed.

One exclusive cutoff applies to all three metrics. Open weeks default to the captured current instant; closed weeks default to their end. A default request exactly at week start is valid with no elapsed habit day; an explicit cutoff must be after start. Microsecond-aware SQL bounds preserve exclusive semantics for sub-microsecond requests. Decimal numeric literals are emitted from BigDecimal without narrowing; exact large values require decimal-capable client parsing.

`calendarStatus` is `FROZEN` for stored weeks, `RECONSTRUCTED` for policy-derived intervals, or `UNFROZEN` for a new-account preview. `reportStatus` is `PROVISIONAL` while the week is open and `CLOSED_UNFINALIZED` afterward. There is no FINALIZED state in this implementation. A transaction accepted before week-end can commit later and change a closed unfinalized result. Current labels can also change. One response uses one PostgreSQL REPEATABLE READ snapshot; separate requests/pages do not share a pinned snapshot.

Missing pre-baseline timezone history is never fabricated. A pre-habit account with older frozen weeks but no policy/current reservation can receive a current-calendar conflict until an ordinary authorized activity reserves the current week. Shared calendar gaps and conflicting future reservations retain their existing behavior. Immutable closed-week finalization remains a separate future stage; there is no V6.

**Verified November 5, 2026:** full Maven `verify` passed **231 tests** (50 unit, 181 PostgreSQL integration), zero failures/errors/skips. All 182 baseline cases remain, plus 8 analytics-rule tests and 41 dashboard integration tests. Flyway V1–V5 and Hibernate validation passed. No migrations or dependencies changed.

## Current limitations

Login/registration throttling uses a bounded in-memory store: 10 attempts per normalized email and 50 per client address per 15 minutes, including successful attempts. Counters reset on process restart and are not shared across instances. A reverse proxy may group clients under one address until trusted-proxy handling is configured; arbitrary forwarded headers are not trusted. Before public deployment, configure the trusted proxy, HTTPS, registration/email verification and password recovery policy. Registration conflicts can reveal whether an email exists; login errors stay generic.

Account linking, password reset, email verification, immutable report finalization, AI coaching, CI and public deployment remain planned. Account browser coverage is described below; live Google and production HTTPS deployment remain unverified.

## Documentation

- [Product vision](docs/PRODUCT_VISION.md): users, MVP scope and reporting definitions.
- [Architecture](docs/ARCHITECTURE.md): modules, data, API, security and deployment.
- [Roadmap](docs/ROADMAP.md): seven development days and release criteria.
- [Decisions](docs/DECISIONS.md): accepted choices, tradeoffs and open questions.
- [Production setup](docs/DEPLOYMENT.md): verified packaging, HTTPS, proxy boundaries and deployment prerequisites.
- [Contributing](CONTRIBUTING.md): development and manual Git workflow.

The display identity is **belsen.** Primary yellow `#FACC15`, background `#FAFAF7`, dark text `#171717`, and white surfaces `#FFFFFF` define the implemented account interface and the visual direction for future product pages.


## Frontend accounts (Day 6 B2)

`frontend/` contains the React/TypeScript account interface: accessible registration and login, password visibility, protected deep links, refresh restoration, logout and profile settings. Email is read-only; display name and IANA reporting timezone can be edited. Reserved weeks retain their timezone, and conflicting future reservations can reject a change with 409. At the B2 checkpoint, product pages were placeholders; B3 below implements goals/tasks. B4 below implements habits; B5 implements Dashboard/Analytics. AI Coach remains planned.

Use Node 24.x and the existing pinned lockfile (TypeScript 6 supports the selected ESLint integration):

```sh
cd frontend
npm ci
npm run dev
```

Start Spring Boot separately with the local profile. Open `http://localhost:5173`. Vite proxies `/api`, `/oauth2` and `/login/oauth2` to `http://127.0.0.1:8081`, preserving the browser Host. `BELSEN_DEV_BACKEND` changes the development target only. Keep `localhost` consistent. Production Secure/HttpOnly/SameSite=Lax cookies and deny-by-default API rules are unchanged; local HTTP explicitly disables Secure. No CORS or forwarded-header relaxation was added.

Google is hidden unless the public build flag `VITE_GOOGLE_ENABLED=true` matches an enabled backend deployment. It starts by top-level navigation. Register the exact browser-facing callback URI above; client secrets remain server-side. Fixed relative redirects return to the SPA, where CSRF and the profile are refreshed. Mock-provider state/nonce/signature/collision tests remain separate from live Google verification, which has not been performed.

Initialization bootstraps CSRF before `/me`. Successful password authentication renews CSRF after rotation, discards old account caches and resumes only one of six fixed protected paths. Logout requires CSRF, cancels queries and clears private caches/tokens only after confirmed success. Network failures do not claim logout. Active-generation 401 responses clear private state; obsolete responses cannot restore it or expire a newer session. Forbidden mutations are not replayed; security state is refreshed where appropriate and the user explicitly retries. Session reconciliation on focus also discards caches if account identity changed. Other tabs are not notified instantly; reload or focus reconciles their session.

Passwords remain in form memory only and are never trimmed: registration checks at least 12 Unicode code points and at most 72 UTF-8 bytes, with backend validation authoritative. CSRF and session credentials are never persisted in browser storage. The exact JSON codec preserves decimals and long versions; Java remains responsible for metrics.

```sh
npm run typecheck
npm run lint
npm test
npm run build
npm run e2e
# After a successful backend Maven verify, with Docker and Java 21 available:
npm run e2e:real
npm audit
npm audit --omit=dev
```

`e2e` serves the production bundle with mocked CSRF/session APIs for shell coverage. `e2e:real` uses Chromium, Vite on localhost:5183, the packaged Spring Boot JAR on loopback:18081 and disposable PostgreSQL 17.11 on a random loopback port. It creates random test accounts, verifies rotation, cookies, profile changes, deep-link reloads, logout/login and account isolation. Its generated database password stays in process environment; no persistent volume or other project's infrastructure is used. Test processes/container/temp JAR are cleaned up. Screenshots, traces, results, dist and node_modules remain ignored.

At the B2 checkpoint, production asset packaging and Spring Boot SPA delivery remained later work. B6 below implements the combined package and exact document routes. Vite still supports local development; API errors never fall through to HTML.

Verified November 14, 2026: typecheck, ESLint, **74 frontend tests**, production build, **8 mocked smoke tests**, **6 real-backend Chromium tests**, and **231 backend tests** (50 unit + 181 PostgreSQL integration) passed, with no failures or skips. Flyway V1–V5 and Hibernate validation passed. Full/production npm advisory audits reported zero vulnerabilities. Keyboard navigation, labels, visible focus, error feedback, responsive overflow and account screenshots were reviewed; this is focused accessibility coverage, not a full assistive-technology certification.


## Goals and tasks interface (Day 6 B3)

After starting the local backend and `npm run dev` as above, sign in and open **Goals** or **Tasks**. These pages use the actual session/CSRF APIs, not local persistence. No backend logic, migrations, dependencies or security configuration changed in B3.

- Goals: paginated list and details; create/edit/archive; explicit progress, paginated history and auditable voiding; current/future weekly target creation and supported future-plan edits. Lifetime and weekly units/percentages come from Java. Unit changes remain restricted after progress/planning and are paused while a progress outcome is uncertain.
- Tasks: paginated list and details; create/edit, optional owned goal and whole-minute estimate; complete/reopen, reschedule/remove date and archive. Filters select active **or** archived tasks, status, owned goal and either an exact current due date or an inclusive range. Original reporting due dates remain visible and never change through rescheduling/archive. An empty goal filter means all tasks regardless of association; the backend has no unassigned-only filter.
- Conflicts: drafts survive 409 responses. The UI refreshes the server snapshot and requires an explicit choice to keep the draft with the latest version or replace it. No stale mutation is automatically replayed; actions do not optimistically invent success. Archive and progress-void confirmations support keyboard cancellation.

Quantities remain strings until the existing exact JSON codec emits numeric literals, with at most 15 integer digits and four fractional digits; invalid inputs are rejected without rounding. Response versions remain exact strings internally and are serialized as exact integer tokens. Goal, task and plan versions are separate. First plan creation omits version; an existing future plan sends only its plan token. Counts are not narrowed through unsafe JavaScript numbers.

Progress creates a submission UUID before the first request. An uncertain outcome freezes that UUID, date and quantity; explicit retry sends the identical payload. The private in-memory intent survives navigation within this application session. It does **not** survive reload, tab closure or logout: check persisted history before recording again in those cases. Conflicting UUID reuse remains frozen until the user explicitly starts a separate entry after reviewing history. Voiding and replacement are independent operations.

Date-only fields stay ISO strings. The goal detail reads only reporting-period metadata from the existing dashboard endpoint; no analytics dashboard UI is implemented. The server's cutoff/timezone determines the initial progress date, not the device clock or current profile zone. Unknown/conflicting calendar context pauses calendar-dependent writes. The UI supports civil dates 1900–9998; the backend remains authoritative. A first plan during an open week is allowed, then fixed; existing started/closed plans cannot be edited. Future timezone metadata is confirmed only when the server reserves the week.

Run the same checks listed above. `npm run e2e:real` now includes B2 and B3 cases against disposable PostgreSQL/Spring Boot. The lost-response test forwards the original POST to the real backend, verifies its commit, then drops only the browser response; its retry must return the same entry. Two independent browser contexts verify goal, weekly-plan and task conflicts. Isolated unit/component fixtures remain separate from this evidence.

Known limits: lists and history are paginated, but the existing goal response returns all weekly plans. Large plan histories retain that backend response/query cost. Filters/pagination reset when the page reloads; selected goal/task IDs in the URL restore details. Calendar conflicts, archived resources and uncertain creates require deliberate review. At the B3 checkpoint, habits, analytics, AI Coach and production SPA delivery remained later work. B4 below implements habits only. Live Google authentication is still unverified.


Verified November 15, 2026: **132 frontend tests** (all 74 B2 cases plus 58 B3 cases), **8 mocked Chromium smoke tests**, **12 real-backend Chromium tests** (six B2 and six B3 desktop/mobile cases), typecheck, ESLint and production build passed. Full Maven verification passed **231 tests** (50 unit, 181 PostgreSQL integration), with zero failures/errors/skips; V1–V5 and Hibernate validation remained intact. Full/production npm audits reported zero vulnerabilities. Real browser verification included exact maximum target quantities, response-loss retries without duplicate progress, void history, task lifecycle/persistence, real two-context conflicts, filtering/pagination, account isolation and keyboard confirmations. Fixture processes and disposable PostgreSQL were cleaned up. Desktop/mobile screenshots and focus/contrast/overflow were reviewed; this is focused accessibility evidence, not complete assistive-technology certification.


## Habits interface (Day 6 B4)

Sign in and open **Habits** using the local setup above. The checklist shows at most five active or archived routines per page, with one bounded daily-history read for each visible habit. Browse all pages for other obligations; this is not an account-wide score. An unscheduled or unstarted day is never presented as an incomplete obligation, and planned rest is distinct from completion.

- Create a routine with title, optional description, distinct Monday–Sunday weekday controls, start date and optional inclusive end date. Metadata can be edited independently from scheduling.
- Complete or undo scheduled days in the current open reporting week. Undo sends the exact strong quoted habit version through `If-Match`, plus the reporting week supplied by the day response. Completion never changes before server confirmation.
- Check a future rest date to read its own reporting-day window, then confirm a reasoned rest plan or cancellation before its server-defined start. A future week can use a different timezone from the overview; its returned week is used for the request.
- View the current schedule separately from the latest pending revision. Revisions become effective tomorrow or later, preserve earlier obligations and can supersede older pending changes where applicable. Earlier accepted revisions remain in history.
- Review up to 93 dates with independent day/action/schedule pagination and an optional exact UTC cutoff. Historical day states and applied revisions come from backend reconstruction, not the current schedule. Explicit-cutoff views are read-only and their schedule records are not mislabeled using today's current/pending selection.
- Archive after confirmation: scheduling stops from tomorrow in the reporting calendar, while earlier obligations and audit records remain. Archived habits can still complete/undo retained open-week obligations. There is no unarchive action.

Every action uses the existing authenticated, CSRF-protected API. All dates of a habit share one version; no occurrence token or schedule sequence substitutes for it. A memory-only per-habit lock survives component navigation/remounts and rejects overlapping requests rather than queuing stale actions. Returned versions advance an account-scoped version floor. Metadata/schedule drafts survive conflicts and require explicit review before using a fresh version; daily/rest actions are never automatically replayed. Habit, reporting and relevant dashboard/analytics caches are invalidated after writes.

Reporting-date defaults come from server period metadata. Each daily/rest/history view uses the returned day window, including frozen timezone, clipped boundaries and DST. No device date is substituted when context is unavailable. Creation can instead accept an intentional user-entered start date for backend validation. Day actions require actual server evidence; a reporting-day rollover or unavailable context pauses those controls until refreshed. Rest confirmations close when submitted, so a late failure cannot close a newly opened retry; failed preview refreshes require fresh evidence before confirmation. The same checked-date controls stay mounted while detail and preview responses arrive at different times, preserving an unsaved rest reason through conflict refreshes.

Use the existing commands (`npm run typecheck`, `npm run lint`, `npm test`, `npm run build`, `npm run e2e`, `npm run e2e:real`). Run backend Maven `verify` before real browser tests. The real-browser fixture now loads a **test-only** launcher from compiled test classes and anchors the reporting clock to Monday **2026-11-02T12:00:00Z**, with advancing acceptance instants. This makes scheduled days deterministic and permits genuine historical-cutoff tests. It still runs the actual Spring Boot application and disposable PostgreSQL. The launcher is outside production scanning, absent from the production JAR, exposes no clock-control endpoint and does not change production `Clock.systemUTC()` or validation.

Limits: the board is paginated rather than a universal daily-board endpoint; visible day reads are bounded to five. One rendered habit page permits one operation at a time, with an additional per-habit lock across remounts. Context is a server snapshot, not a live clock; refresh after inactivity. Explicit-cutoff pages are separate database reads and are not immutable finalized reports. No lifetime streak or consistency percentage is invented. Production backend APIs, security configuration, V1–V5 and dependency versions are unchanged. Analytics, AI Coach, deployment and live Google verification remain outside B4.


Verified November 16, 2026: **177 frontend tests** (132 existing plus 45 B4), **8 mocked Chromium smoke tests**, **18 real Spring Boot/PostgreSQL Chromium tests** and **three additional desktop concurrency repeats** passed. Typecheck, ESLint and production build passed. Full Maven verification passed **231 tests** (50 unit, 181 PostgreSQL integration), with zero failures, errors or skips; Flyway V1–V5 and Hibernate validation passed. Full and production npm audits reported zero vulnerabilities. Source, credential and generated-artifact checks passed, and the test launcher was confirmed absent from the production JAR. Focused keyboard, dialog focus/Escape, weekday input, account isolation and 320/375/768-pixel overflow checks passed; desktop/mobile screenshots were reviewed. This is not full assistive-technology or cross-browser certification. No unresolved critical, high or medium B4 review finding remained.


## Dashboard and historical analytics (Day 6 B5)

After sign-in, **Dashboard** shows your display name, server reporting week/timezone, task completion, strict habit consistency and five weekly goal rows per page. Links lead to the existing goals, tasks and habits workflows. **Analytics** uses the same metrics with a Monday week selector; leaving it blank requests the current server week. Earlier history can be unavailable and future periods are rejected. No browser-local reporting week is guessed.

- **Tasks:** completed / originally assigned tasks at the exclusive cutoff. The original due-date cohort survives rescheduling and archive; reopened tasks are incomplete. Dates later in the same assigned week can already enter the denominator.
- **Habits:** successful / eligible started days. Every remaining non-rest scheduled obligation must be complete for a day to succeed. Planned rest is not completion, and future unstarted days are excluded.
- **Goals:** explicit accepted, non-voided weekly units against that goal's weekly target. Actual units stay exact even above target; the backend caps its percentage at 100%. Missing targets retain units with “No weekly target”, without a bar or average. Goal titles are current metadata; relevant archived goals can appear without an archive label because the response has no status field.

Undefined percentages remain absent, with useful next actions. They never become 0%. Progress bars use server percentages, not current task/habit lists or totals recalculated from one goal page. Goals with different units are never combined into a score. All report boundaries and FROZEN, RECONSTRUCTED or UNFROZEN calendar states come from the response. Provisional reports remain in progress; **closed, not finalized** reports are not immutable snapshots and can change after late commits accepted before their cutoff.

The optional advanced cutoff accepts an exact UTC instant with `Z` and up to nine fractional digits. Select a Monday first; the value is sent unchanged, never converted from a local date. The server enforces start < cutoff <= end and cutoff <= now. Report status describes whether the week has ended, even for an earlier cutoff. Each response is one database snapshot; changing pages can observe later commits.

Queries are account-scoped, cancellable and shared between both screens for the same current selection. Product mutations invalidate analytics; safe network reads retry once, HTTP errors do not retry, and there are no automatic writes. Refresh failures label retained values as an older snapshot. Week changes discard the previous result while loading; drafts survive background refreshes. Returning to Dashboard resets historical selection. No extra metrics request is made just for calendar metadata on these pages.

Run the existing frontend quality commands and `npm run e2e` for mocked smoke tests; run backend Maven `verify`, then `npm run e2e:real` for actual Spring Boot/PostgreSQL Chromium tests. Historical browser cases seed only the disposable database, including a closed DST week and auditable void timestamps; production clock/configuration and V1–V5 are unchanged. No dependencies or charting library were added.

Limitations: report selections/pagination reset on reload, separate pages are separate snapshots, metadata is not fully historical, and unavailable timezone history is never invented. There are no finalized reports, trend series, overall scores, streaks or predictions. Live Google login, full assistive-technology/cross-browser certification, AI Coach and production delivery remain separate gates.


Verified November 16, 2026 for B5: **225 frontend tests** (177 baseline plus 48 B5), **8 mocked Chromium smoke tests** and **24 real Spring Boot/PostgreSQL Chromium tests** (18 baseline plus six B5) passed. Typecheck, ESLint and production build passed. Full Maven verification passed **231 tests** (50 unit, 181 PostgreSQL integration), with zero failures/errors/skips; V1–V5 and Hibernate validation passed. Both npm audits reported zero vulnerabilities. Review checked metric/null accuracy, exact totals/cutoffs, reporting context, cancellation, invalidation, session isolation, keyboard focus and responsive overflow. Desktop/mobile Dashboard and historical Analytics screenshots were reviewed. No unresolved critical/high/medium B5 finding remained. Credential/artifact/whitespace checks passed; browser fixtures were absent from production JavaScript and the test launcher from the production JAR. Temporary fixture resources were cleaned up.


## Production integration (Day 6 B6)

Build the complete site with **Node 24**, Java 21 and Docker:

```sh
./scripts/build-production.sh
```

This runs locked frontend installation and checks, builds React, then packages and verifies the combined Spring Boot JAR with Maven's `spa` build profile. `application-local.yml`, test fixtures and clock launchers are excluded from the production artifact. Serve it with the `production` runtime profile, required PostgreSQL credentials, an exact `BELSEN_PUBLIC_BASE_URL` HTTPS origin and an external PKCS12 certificate. No Vite proxy is used in production. The optional Dockerfile packages that verified JAR into a pinned, non-root Java 21 runtime image.

Only the implemented frontend document routes receive HTML. HTML stays uncached; controlled hashed assets have explicit types and immutable caching. Unknown API/actuator/private-file routes never fall through to the SPA. Private APIs retain session ownership and CSRF; production enforces HTTPS and secure cookies, ignores forwarding headers by default, and pins Google callbacks to the configured origin. CSP/permissions headers and production SQL-log restrictions complement existing security controls.

Use `npm run e2e:packaged` in `frontend/` after the combined build for actual production-profile JAR/PostgreSQL browser tests over local HTTPS, including automated axe checks. Existing mocked and Vite/real-backend browser suites remain separate. The packaged harness uses the normal production clock, server-derived dates and a temporary self-signed certificate; only Playwright ignores its trust error. It does not claim public TLS, a live Google flow or accessibility certification.

See [production setup and deployment prerequisites](docs/DEPLOYMENT.md) for environment variables, direct TLS, restricted proxy handling, build/test commands, container use and remaining launch decisions. No public deployment or AI Coach is included. Public registration/email verification/password recovery policy, distributed throttle behavior, CI, database backups/restore, real provider credentials, ingress and certificate renewal still need an operator's review.


Verified November 16, 2026 for B6 under **Node 24.22.1 / Java 21**: **248 Maven tests** (67 unit, 181 PostgreSQL integration; all 231 baseline cases retained), **225 frontend tests**, **8 mocked Chromium cases**, **24 real-backend Chromium cases** and **6 packaged-production HTTPS Chromium cases** passed. Typecheck (including packaged test sources), ESLint, production build and both npm audits passed; audits reported zero vulnerabilities. Packaged flows used the production JAR/system clock, actual PostgreSQL and local HTTPS without Vite. Axe WCAG A/AA scans reported zero violations on the scanned major screens and confirmation dialog; keyboard focus and desktop/mobile screenshots were reviewed. This is focused evidence, not accessibility certification. The runtime Docker image built successfully, repeated JAR packaging produced an identical checksum, and production-JAR/credential/artifact/whitespace checks passed. Missing TLS configuration was rejected at startup. Temporary browser resources were cleaned up. No unresolved critical/high/medium B6 finding remained; the prerequisites in the deployment guide still apply before public launch.


## Day 7 B3: AI Coach interface

`/app/coach` previews verified reporting metrics and offers explicit analysis only after a sharing acknowledgement. The page supports week/cutoff selection, server-owned recommendation cards, original-evidence replay, partial goal coverage and clear disabled/quota/timeout states. It never dispatches analysis on page load, automatically retries inference or changes product records. Uncertain recovery keeps the original submission UUID in private in-memory state. Reloading the page clears that browser state; no private analysis is persisted in localStorage.

Provider-disabled mode retains useful analytics previews. Groq Free-plan, privacy/ZDR and live-provider verification remain external requirements; public deployment is not complete. See [Coach design](docs/AI_COACH_DESIGN.md) and [deployment prerequisites](docs/DEPLOYMENT.md).


Verified Day 7 B3, November 20, 2026: Java 21.0.11/Node 24.22.1. Full production-profile Maven packaging/verification passed **287 tests (81 unit, 206 PostgreSQL integration)**, zero failures/errors/skips; all 286 B2 tests remain, plus one Candidate presentation/replay compatibility test. V1–V7 and Hibernate validation passed; no migration or dependency version changed. Frontend typecheck, ESLint, **255 tests** (all 225 baseline plus 30 Coach cases) and production build passed. Chromium passed **8 existing mocked cases, 6 Coach-specific mocked API cases, all 24 real-backend cases, and 8 packaged production HTTPS cases** (six retained plus two Coach cases). Scoped axe scans reported zero violations for result/disabled/error states and packaged workflows; this is not full assistive-technology or cross-browser certification. Desktop/mobile screenshots, keyboard behavior and 320/375/768 px overflow checks were reviewed.

The initial concurrent legacy browser runs reported two packaged expiry failures and one real historical-fixture failure because both suites matched the same database-name prefix. No application assertions or session/history mutations were bypassed. Separate prefixes/selectors corrected the test isolation; complete reruns passed. The final production JAR excludes local configuration, test launchers and fixture assets, and production JS contains no provider credentials, endpoint or test fixtures. Credential/artifact/whitespace/documentation checks passed. No unresolved critical/high/medium issue remained within B3 scope.

Recovery state is private in-memory state with a 24-hour cache retention setting; sign-out/account changes clear it and a full reload loses it. Browser cancellation is not proof that remote inference stopped. No live Groq inference, actual Free-plan/ZDR verification, public deployment or B4 work occurred. Enabled UI workflows were verified with deterministic API fixtures; real-backend Coach browser coverage used the disabled provider. B4 remains a separately approved final integration stage.
