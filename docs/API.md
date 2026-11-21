# Backend API reference

These contracts describe the implemented session-authenticated API. See [Coach design](AI_COACH_DESIGN.md) for its evidence and analysis contracts. JSON mutation bodies are limited to 16 KiB; Coach bodies to 4 KiB. Both declared-length and chunked bodies are bounded before MVC decoding. Authentication/CSRF failures can be returned before body validation.

## Authentication API

| Method and route | Behavior |
| --- | --- |
| `GET /api/v1/auth/policy` | Public no-store `{registrationEnabled: boolean}`; false by default, local profile defaults true |
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

## Goals API

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


## Tasks API

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

Events are stored transactionally with task state, have per-task sequence numbers, and retain state after each action. PostgreSQL rejects event UPDATE/DELETE. Completion/archive timestamps match their event timestamp. If an operation crosses its frozen calendar boundary before recording, it fails with `409` for retry instead of storing mixed-week context. Task event-history screens are not implemented; analytics reconstructs event state at cutoff.


## Habits API

Habit routes require the existing session; every mutation requires CSRF. Foreign IDs return `404`. Owners and audit fields are server-derived. The authenticated dashboard API exposes consistency aggregates.

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


## Analytics dashboard API

`GET /api/v1/analytics/dashboard` requires the existing session and returns `Cache-Control: no-store`. The endpoint never creates reporting weeks, occurrences or finalized snapshots.

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

Missing pre-baseline timezone history is never fabricated. A pre-habit account with older frozen weeks but no policy/current reservation can receive a current-calendar conflict until an ordinary authorized activity reserves the current week. Shared calendar gaps and conflicting future reservations retain their existing behavior. Immutable closed-week finalization remains a separate future stage. Coach uses V6/V7; finalization has no migration.

Self-registration is server-controlled. With `BELSEN_REGISTRATION_ENABLED=false`, valid registration attempts return safe 403 before throttle allocation, password hashing or persistence. Existing password/Google identities can still sign in; creation of new Google accounts is also blocked. CSRF remains required. The public policy contains no account or credential data.
