# Architecture

Status: backend foundation, authentication and profiles implemented; Google OIDC tested against a mock provider, with live browser verification pending. Goals and explicit progress are implemented in Day 3 Stage B; tasks are implemented in Stage C; habit persistence/calendar support and secure habit operations are implemented in Day 4 B1/B2; the authenticated analytics dashboard API is implemented in Day 5 B1; the React shell/API foundation is implemented in Day 6 B1 and account authentication/settings in Day 6 B2, goals/tasks in B3 and habits in B4; dashboard/historical analytics in B5; production SPA integration is implemented in B6; immutable finalization, coach and public deployment remain planned. This document describes the target MVP. See `DECISIONS.md` for tradeoffs and unresolved choices.

## System shape

Use a Java 21 Spring Boot modular monolith with a REST API and PostgreSQL. React, TypeScript and Vite live in a separate source directory. The production build packages React assets with Spring Boot, exposing one public HTTPS origin. Frontend development can use Vite's development proxy.

```text
Browser / React
    → Spring Security
    → REST controller / DTO validation
    → feature service / business rules / transaction
    → repository / PostgreSQL

Coach service → provider adapter → external AI API
```

React handles rendering, forms, accessibility, navigation and typed API calls. Java owns authorization, validation, calendar rules, analytics and provider access. Controllers stay small; services own transactions; repositories handle persistence. Do not expose JPA entities as API responses.

## Backend boundaries

| Package | Responsibility |
| --- | --- |
| `identity` | Users, external identities, authentication configuration and timezone profile |
| `goals` | Goal targets, weekly plans, explicit progress and archiving |
| `tasks` | Current task state and minimal reporting events |
| `habits` | Effective schedules, daily occurrences and completion |
| `analytics` | Deterministic calculations and closed-week summaries |
| `coach` | Summary preparation, quota reservations, provider adapter and response validation |
| `common` | Shared error handling and narrowly scoped configuration |

Use feature services for cross-feature operations. Avoid direct writes to another feature's repositories. Introduce interfaces only at useful boundaries, such as `CoachProvider`. No microservices, general event-sourcing framework, broker or Redis is required.

## Dependency direction

The proposed baseline is Java 21, Spring Boot 4.1.2, React 19.3 and PostgreSQL 17. Spring Boot manages compatible Spring Web, Data JPA, Security, OAuth2 Client, Validation and Flyway dependencies where provided by its dependency management. Include PostgreSQL-specific Flyway support when required by the selected release.

Before scaffolding, recheck support and pin compatible Maven, Vite, TypeScript, Node and test-tool versions. Use Maven Wrapper and a frontend lockfile. PostgreSQL development and test versions should match production's major version.

Version references checked during the architecture review: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [React versions](https://react.dev/versions), [Vite requirements](https://vite.dev/guide/) and [PostgreSQL support policy](https://www.postgresql.org/support/versioning/). Backend dependencies are managed by the Spring Boot parent; frontend versions are pinned in package.json and the npm lockfile.

## Data model

Use UUID primary keys, UTC instants for audit events, local dates for user calendar activities and decimal values for goal units. Distinguish original scheduling fields from current editable fields.

| Table | Important fields and rules |
| --- | --- |
| `users` | Normalized unique email, nullable password hash for Google-only users, IANA timezone, timestamps |
| `external_identities` | User FK, provider, provider subject; unique `(provider, subject)` |
| `goals` | Owner FK, title, positive total target, unit, optional deadline, archive timestamp |
| `goal_weekly_plans` | Goal FK, local week start, positive planned units; unique `(goal_id, week_start)` |
| `goal_progress_entries` | Goal FK, positive units, effective local date, recorded instant, optional void timestamp |
| `tasks` | Owner FK, optional goal FK, original/current due dates, optional positive duration in minutes, status, archive timestamp |
| `task_events` | Task FK, event type, recorded instant and required state/scheduling changes |
| `habits` | Owner FK, title, active/archive state and timestamps |
| `habit_schedule_versions` | Habit FK, effective local date, active weekdays and active flag |
| `habit_checkins` | Lazy occurrence, owner, local date, frozen reporting week and schedule version, current completion/rest flags; unique `(habit_id, local_date)` |
| `habit_checkin_events` | Append-only completion/undo/rest state, acceptance instant and occurrence sequence |
| `reporting_timezone_revisions` | Account reporting-timezone policy, effective Monday, acceptance instant and account sequence |
| `weekly_summary_snapshots` | Owner FK, week start, timezone, cutoff, raw counts/units, percentages and calculation version |
| `coach_requests` | Owner FK, quota date, status, reservation instant and minimal provider usage metadata |

Users own goals, tasks, habits, reports and coaching requests. Child records inherit ownership through their parent. Every access must be owner-scoped, including references between resources. Check that an associated goal belongs to the task owner. Database foreign keys protect relationships; service checks protect ownership.

Use check constraints for positive targets, progress and durations. Validate allowed states and weekday values. Index owner/date filters, task original due dates, `(task_id, recorded_at)`, habit/date lookups and goal progress dates. Use unique per-habit schedule sequences (multiple accepted revisions may share an effective date) and unique snapshot owner/week. Avoid indexing every field without a query need.

Use Spring Session JDBC tables for persistent server-side sessions when authentication is implemented. Configure their creation through reviewed migrations rather than unmanaged production schema initialization.

### Minimal history

Tasks keep their current state for ordinary screens. Append events only for creation, first due-date assignment, rescheduling, completion, reopening and archiving. Record changes atomically with current state. Retain the fields needed to reconstruct state at a cutoff; do not store every title edit or create an event replay application framework.

Archive instead of hard-deleting MVP activities. Archiving must not erase historical obligations. An incomplete archived task remains incomplete in its original reporting cohort. Previously scheduled habit days remain eligible.

Habit schedule edits and deactivation take effect no earlier than the following local day, so today's obligations cannot disappear after a partial completion. Creation may schedule today. Create occurrences lazily on actual mutations; reconstruct elapsed missing obligations from effective schedule versions without read-side inserts. Daily completion can be toggled only within the open reporting week and not for future days.

Goal progress corrections void and replace entries within the open week. Do not allow backdated edits to closed periods. A weekly plan becomes fixed when its week starts; a first plan created during the week is allowed, then fixed. Future plans remain editable. Goal units cannot change after progress or weekly plans exist. This preserves the meaning of historical values.

## Reporting contract

All modules and screens must use these definitions. Calculate with raw counts and decimal units; round displayed percentages to one decimal place. Preserve raw values in API responses and snapshots.

### Calendar and cutoff

- A week is Monday 00:00 through the next Monday 00:00, exclusive, using an IANA reporting timezone.
- Current-week cutoff is the current instant. Closed-week cutoff is the end boundary; events at that boundary belong to the next period.
- Resolve boundaries using timezone rules, not fixed 24-hour arithmetic, to handle daylight-saving changes.
- Lock the reporting timezone for a started week. A profile timezone change takes effect for reporting at the next week boundary in the existing reporting timezone. Show the pending change and use one consistent reporting timezone for scheduling until then.
- Store timezone and cutoff on closed snapshots. Planned finalization will build immutable closed snapshots from historical state through a separately authorized write path. Day 5 B1 creates no snapshots and labels closed results CLOSED_UNFINALIZED; corrections to closed weeks remain outside the MVP.

### Task completion

**Numerator:** tasks whose original due date is in the reporting period and whose completion state at the reporting cutoff is complete.

**Denominator:** all tasks whose original due date is in the reporting period.

Multiply numerator/denominator by 100. The original due date is the first date assigned, including when an initially undated task is scheduled later. Rescheduling changes only the current due date. Reconstruct completion state using task events before the cutoff. Reopening before cutoff makes the task incomplete; completion after cutoff does not repair a closed report. Archive actions do not remove tasks from the denominator.

For an open week, the denominator includes all tasks originally due that week, including later days; label the report provisional. An undated task is excluded. Reject a first due-date assignment into a closed reporting period. Optional duration has no effect on this count-based formula.

### Habit consistency

**Numerator:** eligible local days where every habit scheduled for the day is complete at cutoff.

**Denominator:** eligible local days, each with at least one scheduled habit.

Multiply numerator/denominator by 100. Rest days are excluded. In the current week, include only days that have started; today's result is provisional. Schedule versions and occurrences preserve prior obligations after edits or deactivation. This is a strict daily routine measure, not individual habit completions divided by opportunities.

### Weekly goal progress

For each goal, divide valid units recorded for the week by its planned weekly units, multiply by 100, and cap the percentage at 100%. Preserve actual over-target units. A progress entry has an explicit effective date and must be recorded before cutoff to count. Task completion does not generate goal units. Do not aggregate percentages across incompatible goal units.

### Empty and historical states

Zero denominators produce a `null` percentage and a reason such as `NO_SCHEDULED_ACTIVITY` or `NO_WEEKLY_TARGET`, never an invented 0% or 100%. New-user summaries return empty collections and useful guidance. Snapshot raw counts, per-goal units, timezone, cutoff and calculation version so results remain explainable after records are archived or rules evolve.

## REST API

Application routes use `/api/v1`, JSON DTOs and ISO dates/timestamps. Collection endpoints need bounded pagination. The table combines implemented backend routes with target MVP contracts; README identifies current capabilities and full habit contracts.

| Method and route | Responsibility |
| --- | --- |
| `GET /auth/csrf` | Obtain a CSRF token for browser mutations |
| `POST /auth/register`, `POST /auth/login` | Validate credentials and establish a session |
| `POST /auth/logout` | Invalidate the session |
| `GET /me`, `PATCH /me` | Read/update the authenticated profile and timezone |
| `GET /goals`, `POST /goals` | List/create owned goals |
| `GET /goals/{id}`, `PATCH /goals/{id}` | Read/edit an owned goal |
| `POST /goals/{id}/progress` | Record explicit goal units |
| `POST /goals/{id}/progress/{entryId}/void` | Correct an open-week entry without erasing its record |
| `PUT /goals/{id}/weekly-plans/{weekStart}` | Set a weekly target subject to plan-locking rules |
| `POST /goals/{id}/archive` | Archive a goal |
| `GET /tasks`, `POST /tasks` | List/create owned tasks |
| `GET /tasks/{id}`, `PATCH /tasks/{id}` | Read/edit task details; scheduling/state use explicit actions |
| `POST /tasks/{id}/complete`, `/reopen`, `/reschedule`, `/archive` | Apply a task action and retain required history |
| `GET /habits`, `POST /habits` | List/create owned habits |
| `GET /habits/{id}`, `PATCH /habits/{id}` | Read/edit metadata |
| `POST /habits/{id}/schedules` | Append a future schedule revision |
| `PUT /habits/{id}/rest-days/{date}`, `DELETE /habits/{id}/rest-days/{date}` | Plan/cancel prospective habit-specific rest |
| `GET /habits/{id}/history` | Bounded current-state or explicit-cutoff history |
| `PUT /habits/{id}/completions/{date}` | Idempotently complete an eligible occurrence |
| `DELETE /habits/{id}/completions/{date}` | Reopen an occurrence in an open week |
| `POST /habits/{id}/archive` | Stop future scheduling without erasing history |
| `GET /analytics/dashboard?weekStart=YYYY-MM-DD` | Return coherent task/habit counts, paginated goal progress and unfinalized report metadata |
| `POST /coach/analyses` | Analyze a selected accessible weekly summary within quota |

Google login uses Spring Security's `/oauth2/authorization/google` entry and `/login/oauth2/code/google` callback outside the versioned application API.

Only authentication entry points, CSRF bootstrap, public assets and restricted health responses are public. All private application endpoints require a session. Never accept owner IDs, authoritative percentages or audit fields from the browser.

Bean Validation checks required fields, lengths, positive values and date formats. Services check ownership, state transitions, timezone identifiers and calendar rules. Return RFC 9457-style problem responses with `type`, `title`, `status`, `detail`, `instance`, request ID and optional field errors. Use `400` for invalid input, `401` for missing authentication, `403` for CSRF failures, `404` for inaccessible resources, `409` for state conflicts and `429` for rate limits. Do not expose stack traces or provider credentials.

## Authentication and security

Use Spring Security's delegating password encoder with BCrypt, a calibrated work factor and an input limit compatible with BCrypt's byte limit. Never store plaintext passwords. Google-only users may have no password hash. Password reset and email verification are not part of the current approved feature list; assess that limitation before an unrestricted public launch.

Use OAuth2 authorization code flow with Spring Security's state handling and OpenID Connect identity validation. Identify Google accounts by provider subject, not mutable email. Never silently merge a Google identity into an existing password account merely because email addresses match. Linking requires an authenticated account and reauthentication; until that flow exists, reject collisions with safe guidance.

Use server-side sessions, session rotation on authentication, expiry and logout invalidation. Production cookies are Secure, HttpOnly and SameSite=Lax. Keep CSRF protection for cookie-authenticated mutations, including logout, and test token handling after login. Apply generic authentication errors and login throttling without logging passwords.

One production origin avoids routine CORS configuration. If direct cross-origin local development is necessary, permit only the exact development origin with credentials; never wildcard origins. Configure trusted proxy headers explicitly on deployment.

## AI request flow

1. Authenticate the user and atomically reserve a request against a persisted daily quota.
2. Build a minimal deterministic weekly summary from owned data; omit email and unnecessary free text.
3. Call `CoachProvider` with a timeout and bounded output tokens. Treat all user content as data, not instructions.
4. Validate the response schema, text lengths and suggestion count (maximum three). Do not accept generated metrics as verified analytics.
5. Return verified metrics in a separate response field from coaching text. Record request outcome and minimal usage metadata.

The provider has no tools or write access. Keep keys server-side. Provider timeouts/unavailability return a controlled `503`; invalid provider output returns `502`; quota exhaustion returns `429`. Avoid automatic retries in the MVP to bound cost. A failed provider attempt retains its reserved quota slot; requests rejected before provider dispatch do not consume a slot. Final limits and provider-wide spending controls remain to be selected.

## Testing and CI

- Unit tests: original due-date cohorts, state at cutoff, reopened/archived tasks, empty denominators, strict habit days, rest days, schedule changes, goal caps, timezone changes and daylight-saving boundaries.
- PostgreSQL integration tests with Testcontainers: Flyway migrations, constraints, owner-scoped queries, event/state atomicity and snapshot creation.
- Controller/security tests: session flows, CSRF, validation, identity collisions and cross-user resource/reference access.
- AI contract tests using a stub: valid output, extra suggestions, malformed data, timeout, provider failures, concurrent quota reservations and exhausted limits. Live provider calls are not a CI requirement.
- Frontend tests: forms, API errors, loading/empty states, accessibility and verified-metric/coaching separation.
- End-to-end tests: account flow, goal progress, task reschedule/reopen, habit rest days and stable closed-week results. Exercise Google configuration manually in a controlled smoke test rather than relying on a live provider in ordinary CI.

Future GitHub Actions gates: Maven verification, frontend type checking, linting, tests and production build. Prioritize meaningful edge cases over an arbitrary coverage target. Backend verification exists, including Stage C authentication, cookie and mock OIDC tests; the Stage B baseline passed 20 tests. Frontend and CI checks remain planned.

## Migration and deployment

Flyway versioned migrations own schema changes. Use Hibernate validation, not production auto-update. Review migrations before deployment; test them against PostgreSQL. Prefer forward fixes and backups over destructive rollback assumptions.

Local development will use Docker Compose for PostgreSQL, Maven for Spring Boot and Vite for React. Production will build React and package its assets into the Spring Boot artifact/container. Configure SPA fallback only for frontend routes, never missing API paths.

Use a managed container host and managed PostgreSQL with backups. Provider, region, domain, costs and retention are unresolved. Do not assume free-tier uptime, persistence or backup guarantees. External dependencies are Google identity, the AI provider, hosting, PostgreSQL hosting and HTTPS/domain configuration.

`.env.example` contains proposed empty configuration keys. Environment injection must be implemented explicitly; Spring Boot does not automatically read `.env`. Keep database, Google and AI credentials in local ignored files or hosting secrets. No secret belongs in a `VITE_*` value.

Register exact local and production OAuth callbacks, with the production callback at `https://<chosen-host>/login/oauth2/code/google`. Confirm the public URL, secure cookie/proxy settings, database connectivity, migrations, session persistence, backups and provider limits before release. Expose only minimal health information. Log request IDs and operational failures without credentials or raw coaching payloads.

## Planned repository layout

Day 1 created ten documentation/configuration files. Backend and Compose now exist; frontend and CI paths below remain a future implementation plan.

```text
backend/
  pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/
  src/main/java/com/belsenai/
    identity/, goals/, tasks/, habits/, analytics/, coach/, common/
  src/main/resources/db/migration/
  src/test/java/com/belsenai/
frontend/
  src/app/, features/, components/, api/, styles/
infra/
  compose.yaml
.github/workflows/
  ci.yml
docs/
  PRODUCT_VISION.md, ARCHITECTURE.md, ROADMAP.md, DECISIONS.md
```

Backend packages contain their own controllers, services, DTOs, repositories and entities as needed. Frontend features group user journeys; shared components hold reusable accessible UI. Infrastructure contains local runtime configuration; documentation records rules and decisions.

## Stage C implementation notes

The six authentication/profile API routes above are implemented. Other product APIs remain proposed. `AccountService` owns account transactions; controllers validate DTOs and call services. Profile IDs come only from the authenticated principal. Jackson rejects unknown fields. No new migrations were needed: V1/V2 remain unchanged.

Following [Spring Security session persistence guidance](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html), custom JSON authentication applies session-fixation and CSRF strategies, then explicitly saves the security context through `HttpSessionSecurityContextRepository`. The session principal contains the local UUID, not a JPA entity or password hash. Fetch a new CSRF token after authentication. Logout uses Spring Security to invalidate the JDBC session, clear authentication and expire the cookie. Request caching is disabled for API failures.

Google configuration is conditional (`BELSEN_GOOGLE_ENABLED`); enabled configuration requires `BELSEN_GOOGLE_CLIENT_ID` and `BELSEN_GOOGLE_CLIENT_SECRET`. Spring Security validates OIDC tokens before `GoogleAccountService` maps the provider subject. Returning subjects reuse the same local account even if the provider email changes. New identities require verified email and cannot merge with an existing email. Concurrent uniqueness conflicts fail safely; the user can retry. Google accounts initially use UTC. No account-linking route exists.

All other private routes remain denied by default. CSRF is enabled, including login/registration/logout. Passwords use delegating BCrypt with a 12-character registration minimum and 72-byte UTF-8 maximum. Unknown accounts and Google-only accounts perform a dummy password comparison before returning the same login error. Sessions expire after 30 minutes of inactivity by default.

Throttling is bounded to 10,000 keys and fails closed at capacity: 10 attempts/email and 50 attempts/address per 15 minutes across registration/login, with no success reset. It is synchronized within one instance; restart resets counters. Multi-instance limits, trusted proxy configuration, email verification and recovery are deployment decisions, not implemented protections.

Problem Details are shared by MVC and security filters. Validation responses expose field messages, never rejected values. Unexpected errors return a generic response and log only the request ID and exception class. Real Google sign-in remains unverified; see README for exact setup and CSRF usage.

## Day 3 goals implementation

V3 adds `goals`, `goal_progress_entries`, `goal_weekly_plans` and the narrow `reporting_weeks` calendar table. V1/V2 are unchanged. Calendar rows are unique by owner/Monday, contain an IANA timezone and stored UTC boundaries, and are never updated through the API. Plans and progress use composite foreign keys to enforce the same owner as their goal and calendar week. PostgreSQL also enforces positive numeric values, one plan per goal/week and one submission UUID per goal.

First progress or plan write reserves the current week even if it contains no progress; a future plan additionally reserves its future week. Active stored intervals take precedence over the current profile timezone. New rows use timezone-aware boundaries, clamped against adjacent reservations to avoid overlap. A profile timezone change never alters an existing row. A westward transition may leave the current profile date in a frozen, closed week; changes return `409` until the next eligible week. This is explicit fail-closed behavior, not a scheduler or historical recalculation.

Write transactions lock the account row first and then the owner-scoped goal row. This gives calendar first writes, archive/progress races and duplicate submissions one consistent lock order. The account lock serializes goal writes within one account; this favors a small, understandable MVP over maximum throughput. Goal/plan `@Version` fields and expected-version requests reject stale edits. Other accounts remain independent.

Progress is append-only except the explicit `voided_at` audit marker. Void and replacement are separate transactions: a failed replacement leaves the visible original voided, never silently restores or rewrites it. Closed weeks and archived goals reject correction. Submission retries compare quantity by numeric value and effective date under the parent lock; an identical retry returns the existing entry even after closure/archive/void, and conflicting reuse returns `409`. PostgreSQL uniqueness remains the final safeguard.

Goal GET/list responses provide lifetime totals and a separate weekly-plan collection with raw planned/completed units, percentage, version and frozen calendar metadata. Calculations are authoritative Java/BigDecimal values; only displayed percentages round. Collection ordering is creation time then UUID, with bounded page sizes. PATCH distinguishes omitted optional metadata from explicit null. Goal target updates do not rewrite weekly plans; unit changes are rejected once any progress or plan exists.

At the goals-stage checkpoint, no task tables, analytics snapshots, frontend or dependency were added. Tasks are introduced by Stage C below. See README for goal request contracts.

## Day 3 task implementation

V4 adds `tasks` and `task_events`. V1–V3 remain unchanged. Task owner/date and goal indexes support lists and future reporting. Composite task→goal and task/event→reporting-week foreign keys enforce ownership in PostgreSQL. State checks enforce completion timestamps, positive duration, and an original reporting week whenever an original due date exists.

Task writes follow the existing account-first lock order, then lock the owned task. Active goal association is validated through GoalService under that same account lock, coordinating safely with goal archive. Optimistic versions are checked before no-op decisions. Every actual change advances a per-task event sequence, so fixed-clock/equal-timestamp changes still increment the task version. No-op matching-version transitions emit no event; stale repeats return `409`.

A creation event captures the initial undated state; a dated creation additionally records DUE_ASSIGNED. Further events are RESCHEDULED, COMPLETED, REOPENED, ARCHIVED and metadata UPDATED. Each snapshot preserves completion/archive status, original/current due dates and original/event reporting-week references. Sequence is unique per task and breaks timestamp ties. Events expose no update/delete repository methods, use Hibernate immutable mappings and have a PostgreSQL trigger rejecting UPDATE/DELETE. All task state and event changes use one transaction; event failure rolls back even a flushed task UPDATE.

The original reporting cohort is frozen on first due assignment through ReportingCalendar. First assignment to a closed week fails; later planning-date changes preserve the cohort. Each operation uses the existing current calendar; `openAt` is a small extension accepting the captured event instant, while the old `open` behavior remains unchanged. Completion/archive timestamps equal event timestamps. Crossing a reporting boundary before capture returns a conflict rather than mixing calendar contexts. Cutoff reconstruction selects the last applicable event before the cutoff, using sequence to order ties. A later reopen/archive cannot change that earlier state. No analytics service is implemented.

Task state is OPEN or COMPLETED with a separate archive timestamp, preserving whether an archived task was complete. Reads are owner-scoped; lists default to non-archived tasks and fixed creation-time/UUID ordering. Mutation DTOs never accept ownership or internal state. Security authorizes only the implemented task methods/paths. There is no task-history HTTP API or goal-progress side effect. API contracts and filters are in README.

### Goal operation time and read snapshots

Goal recording, voiding and weekly planning capture one acceptance instant after acquiring the account and goal locks. That instant selects the frozen reporting week, validates eligibility and local dates, and stamps the entry, plan and goal. Persistence may finish after a boundary without changing the logical acceptance time. An operation accepted exactly at a week's exclusive end cannot modify that closed week. Identical submission retries still return the original entry before calendar eligibility checks.

Standalone GoalService get/list calls use read-only PostgreSQL REPEATABLE READ transactions through the Spring service proxy. REQUIRES_NEW prevents an enclosing READ COMMITTED transaction from silently overriding this guarantee; an enclosing transaction is suspended, so these reads intentionally cannot see its uncommitted changes and may require another pooled connection. Each response uses one snapshot, not necessarily the newest concurrent commit. Private view assembly does not rely on self-invoked transactional annotations. Other service calls retain their existing isolation.

Write methods returning goal views retain READ COMMITTED: update/archive hold the account and goal locks through view assembly, preventing concurrent goal progress/plan mutations; create returns a newly inserted goal invisible to other transactions until commit. These guarantees assume all application goal writers preserve the account-first locking protocol. Direct SQL writers bypassing it are outside this coordination contract.


## Day 4 B1: habit persistence and calendar foundation

At the B1 checkpoint, implemented only: V5 tables, JPA mappings/repositories and shared reporting-calendar support. No habit service, controller, public route, completion/rest operation or analytics API existed at that checkpoint; B2 below implements the habit workflows. V1–V4 are unchanged. The exact fields, PostgreSQL types, foreign keys and checks are defined in `backend/src/main/resources/db/migration/V5__create_habit_tables.sql`.

The five new tables are habits, habit_schedule_versions, habit_checkins, habit_checkin_events and reporting_timezone_revisions. UUID keys and composite foreign keys enforce same-owner relationships and ensure an occurrence's schedule belongs to that same habit. One occurrence is allowed per habit/local date. Positive sequences order revisions and events, including equal timestamps. Enabled weekday masks use bits 0–6 for ISO Monday–Sunday and must be 1–127; disabled schedules use zero. Date ranges are inclusive and ordered. Completed/rest flags are mutually exclusive. Rest-planned events require a nonblank reason. No audit foreign key cascades deletion. PostgreSQL triggers reject schedule/event/timezone-history UPDATE/DELETE and also protect previously frozen reporting-week rows.

Cross-row rules remain service responsibilities: schedule eligibility and date/week membership, prospective changes, valid IANA identifiers, state/event agreement, cutoff eligibility and timestamp/sequence ordering. B1 provides persistence, not these future habit workflows. Database constraints do not replace owner-scoped service authorization.

### Tracking initialization and timezone policy

The future habit-creation transaction must call ReportingCalendar.beginTracking with its server-derived account UUID. It locks the account, captures one acceptance instant, freezes the current week, and inserts a baseline policy using the account profile timezone and current Monday label. Repeated initialization is idempotent. The baseline does not claim knowledge of earlier missing weeks or pre-creation habit obligations. Already-stored weeks remain authoritative, including those preceding the baseline.

AccountService profile updates now acquire the same account lock. Accounts without a tracking baseline retain the existing goal/task calendar policy. Once tracking begins, a timezone change appends an immutable revision effective the next reporting Monday, in the same transaction as the profile update. Profile timezone changes immediately for profile display; reporting changes prospectively. Multiple accepted changes for that Monday retain every revision, with the greatest sequence winning. Backwards reporting-clock changes are rejected rather than backdating a policy.

If any frozen week on or after the proposed effective Monday has a different timezone, the transition returns 409 and rolls back the profile update. No existing reservation is rewritten and no immediate adoption is promised. Matching future reservations are preserved. With no new profile response fields in B1, this prospective rule and conflict response are the contract; B2 should expose effective reporting context in habit DTOs.

### Pure historical resolution

ReportingCalendar.resolveWeek first returns an existing row verbatim. Otherwise it selects the revision with the greatest applicable effective Monday and sequence. It never consults today's profile timezone for missing tracked weeks and returns no result for an absent pre-baseline week. A computed week has a null ID until a write explicitly reserves it. resolveDay intersects ZoneId-derived local midnight boundaries with the resolved week. Missing/skipped local days return no interval; DST days need not last 24 hours.

For missing weeks, preserve the previous policy week's natural end when an eastward change would overlap it, shortening the new week's beginning. Existing frozen neighbors further constrain the window. Conflicting empty weeks produce 409. Westward transitions may leave a gap until the next valid local Monday; current mutations then return a conflict rather than reopen a closed week. Week reservation uses this same calculation, keeping read reconstruction and persisted boundaries aligned.

Standalone resolver calls run in read-only REPEATABLE READ transactions. Calls joining an existing transaction inherit its isolation; a writer must hold the account lock, and a multi-query history service must establish its own consistent snapshot. resolveDay's internal call to resolveWeek shares its existing transaction; it does not depend on self-invoked transaction advice. Neither read method inserts calendar rows. Existing goal/task callers retain their method signatures and locking contract.

### Historical habit contract for B2

Schedule effective_from answers when a revision applies; recorded_at answers when it existed. As-of-cutoff selection must filter recorded_at strictly before cutoff before choosing the highest accepted sequence among revisions whose effective date has arrived. Daily events similarly filter accepted_at strictly before cutoff and select by sequence. local_date identifies the obligation, while reporting_week_id preserves its calendar context. Never use today's check-in flags as an historical cutoff result. Archive metadata must be coordinated with an effective disabled schedule revision, retaining earlier obligations.

Verification on November 4, 2026: full Maven verify passed 138 tests, zero failures/errors/skips, preserving all 116 existing tests and adding 22 PostgreSQL foundation/calendar tests. V1–V5 and Hibernate validation passed. No habit user-facing functionality is claimed by this checkpoint.


## Day 4 B2: secure routines and historical check-ins

HabitService owns transactions; HabitController derives the account UUID from the existing session principal and exposes DTOs only. Explicit security method/path matchers authorize the implemented habit routes and retain CSRF and deny-by-default. No authentication/session settings or migrations change. See README for the complete HTTP contract.

Every mutation locks account then owned habit and captures one logical acceptance instant, truncated to PostgreSQL microseconds. Habit creation shares that instant with the B1 tracking initialization overload, initial schedule and metadata. Calendar selection, day eligibility, timestamps and versioned state use that same instant even if persistence finishes after a boundary. Backwards logical clocks produce conflicts. Actual habit changes increment an audit sequence so Hibernate versions advance even under a fixed clock. The habit version is the common concurrency token; occurrence versions and event sequences are additional persistence safeguards. Matching-version no-ops add no events; stale retries conflict.

Schedule selection first filters by acceptance cutoff and then chooses the highest sequence among revisions effective on the requested date. This deliberately supersedes previously pending plans from the new revision's effective date onward. Archive appends a higher-sequence disabled revision effective tomorrow, preventing an older pending schedule from reactivating it. Metadata remains current; it does not drive historical eligibility. Current detail exposes the current schedule and latest still-applicable pending revision, not every future transition. History pages preserve all accepted versions.

Completion and undo require a begun, scheduled non-rest date in the current open frozen reporting week. Earlier dates within that week may be checked in; closed periods cannot change. Archive preserves pre-effective obligations, including open-week completion/undo. Rest planning and cancellation require acceptance before the target day starts. Rest is habit-specific; no full-day exclusion is implemented. Rest that becomes unscheduled remains auditable but inapplicable and can be cancelled prospectively. Actual future rest changes reserve their target reporting week; no-op cancellation does not. Original occurrence schedule references remain immutable context while day views show the schedule effective at the selected cutoff.

Each actual daily transition atomically saves the occurrence, advances its sequence, appends resulting-state history and advances the habit version. A failure after flushed SQL rolls back state, history and versions. Events retain their action reporting week separately from the occurrence's target reporting week. Same-account serialization coordinates schedule/archive, daily actions and profile timezone updates without additional infrastructure.

Get/list/history use read-only REPEATABLE READ with REQUIRES_NEW through the service proxy. Private helpers share the caller transaction; write responses remain protected by the account lock. Nested read calls need another pooled connection and cannot see outer uncommitted writes. History uses date ranges of at most 93 days, page sizes 1–100 and separate day/action/schedule pages. Queries are owner-scoped and bounded. Missing occurrences do not erase obligations and are never materialized by reads.

Without an explicit cutoff, history includes equal-timestamp committed actions. An explicit cutoff is exclusive for schedule recordedAt and event acceptedAt. Exclusive SQL bounds are ceiled to microseconds to preserve exact ordering against persisted microsecond ticks, while the API retains the original cutoff. Current occurrence flags and archive status never substitute for cutoff-filtered revisions/events. Unstarted dates are excluded from eligibility; explicit-cutoff responses omit their reportingDay rather than expose later timezone intentions. Frozen boundaries of begun dates remain authoritative. The response version is always the current optimistic token, not an as-of metadata revision.

HabitRules provides internal bounded consistency calculation over authoritative day evidence: group by date, remove habit rest/unscheduled/future rows, require every remaining habit complete, preserve integer counts and return a null percentage for no eligible dates. No public analytics API or closed-summary snapshot builder is implemented. A future snapshot builder must coordinate with in-flight accepted writes before freezing a closed result; a read-only snapshot only sees transactions committed when that snapshot was established.

Known tradeoffs: shared account locks serialize writes; history uses several queries per returned date/action and loads shared calendar policy history. Bounds limit each response, but workload-based query optimization remains future work. State toggles have no new rate limiter. Planned future rest can prevent a timezone change while incompatible frozen reservations remain. Standalone read snapshots are consistent but may omit newer concurrent commits.

Verified November 4, 2026: Maven verify passed 182 tests (42 unit, 140 PostgreSQL integration), zero failures/errors/skips. All 138 baseline cases remain, with one B1 route-availability assertion moved to an unsupported path; 44 new cases cover rules, workflows, ownership/CSRF, cutoff precision, concurrency, rollback and snapshot consistency. Flyway V1–V5 and Hibernate validation passed.


## Day 5 B1: read-only analytics dashboard

The implemented endpoint is GET /api/v1/analytics/dashboard. DashboardController derives the owner from the session principal, rejects unsupported/repeated/blank parameters and returns no-store. Security authorizes only that exact GET route. Existing mutations retain CSRF. README defines request parameters, DTO fields, no-data reasons and status codes.

DashboardService captures the clock once and runs through the Spring proxy in read-only REPEATABLE READ with REQUIRES_NEW, preventing an enclosing transaction from silently weakening isolation. DashboardReader uses MANDATORY propagation and the same transaction-bound JDBC connection; no metric opens a separate transaction or calls a resource GET/view service. A deterministic concurrent-commit test and JDBC transaction-setting assertions verify the boundary. Nested callers need another connection and cannot expose outer uncommitted writes. Response pages from separate requests have independent snapshots.

A period is either stored/FROZEN, resolved from reporting policy/RECONSTRUCTED, or a current-week UNFROZEN preview for an account with no calendar history. Bounded ReportingCalendar read methods use an exact week lookup, latest applicable policy, its preceding policy and indexed adjacent frozen weeks. They preserve the existing ordered, non-overlapping calendar invariant without loading entire histories or writing rows. Pure day clipping uses the shared calendar and IANA rules, including skipped dates and DST. Missing pre-baseline history is unavailable. A legacy account without tracking policy or a current reservation can receive a calendar conflict rather than a guessed preview.

All metrics use timestamps strictly before one cutoff C. Default C is now for an open period or end for a closed period. Explicit cutoffs require start < C <= min(now,end); default reads at the exact opening instant are valid. An exclusive SQL bound is ceiled to PostgreSQL microseconds, preserving the precise comparison against stored ticks. The original cutoff remains in the DTO. Date/interval and pagination validation happen before metric queries.

Task projections select current-table candidates by owner and immutable original date range, then choose the highest-sequence event before C for each task using an indexed lateral lookup. Only snapshots already carrying the selected original reporting-week ID count. Thus a first assignment after C is excluded even when the current task row is dated. The event action week is not a filter. Archive, reschedule and due-date removal preserve obligations; a last OPEN state after reopening is incomplete. Future dates inside the original week count once assigned. Raw counts produce a Java/BigDecimal percentage; zero cohort produces NO_SCHEDULED_TASKS.

Habit projections receive at most seven valid, started day contexts from Java. One set-based query selects the highest applicable schedule sequence accepted before C, applies date/weekday eligibility, and joins at most one occurrence and one latest event per habit/date. Current occurrence flags are never used as historical state. Habit-specific rest is removed before grouping counts by date. Archived habits retain earlier days; disabled revisions and acceptance timestamps determine their future obligations. HabitRules.consistencyFromDays reuses the strict rule from existing habit evidence: a positive remaining count makes the day eligible, and every remaining obligation must be complete. It returns integer day counts and a rounded display percentage, with NO_SCHEDULED_ACTIVITY for zero eligible days. The database returns at most seven aggregate rows rather than all habit/date pairs.

Goal count/page queries select goals created before C which were active at C or have a weekly plan/activity accepted before C. Relevant archived goals remain even if entries were later voided. For each bounded page, a separate lateral SUM includes entries for the selected owner/week/date range with created_at < C and (voided_at IS NULL OR voided_at >= C). Plans are unique per goal/week and must have existed before C. Aggregation occurs before any multiplicative history join; totals retain PostgreSQL aggregate NUMERIC/Java BigDecimal precision. Missing targets yield units and NO_WEEKLY_TARGET, not a percentage. Current goal titles are marked titleMetadata=CURRENT. Units come from current metadata, frozen once any planning/progress exists; earlier metadata versions and future-plan edits before week start are not reconstructed. In-period cutoffs are supported because weekly-plan edits are forbidden once the week starts.

B1 adds no snapshots, finalization path or V6. Report status depends on whether the reporting week has ended: PROVISIONAL or CLOSED_UNFINALIZED, including explicit earlier-cutoff queries. Snapshot consistency means one coherent committed database view, not an immutable historical publication. A write logically accepted before cutoff but committed after the read snapshot can change a later response. Future finalization must drain account writers before establishing its calculation snapshot and coordinate currently unlocked goal creation if it includes no-activity goals. It remains separately authorized work.

Performance review uses an isolated PostgreSQL fixture with roughly 4,000 tasks and 1,000 habits: native metric query count remains constant, and the task plan uses the existing task/sequence index for last-event lookups. The planner may choose a sequential scan when a large fraction of the small task table belongs to the cohort. No speculative indexes were added. This is query-shape evidence, not a production latency benchmark. Execution work still scales with relevant account records; long per-resource histories and large active-goal collections may need measured indexing/tuning later. Calendar result sets, metric result sets and goal responses are bounded.

Verified November 5, 2026: full Maven verify passed 231 tests (50 unit, 181 PostgreSQL integration), zero failures/errors/skips, retaining all 182 baseline cases. The 49 new cases include 8 focused rule tests and 41 dashboard integration tests. Flyway V1–V5 and Hibernate validation passed. Invalid stored timezone handling and default exact-week-opening behavior have regression coverage. No migration or dependency was introduced.


## Day 6 B1: React application foundation

The separate frontend uses strict TypeScript, React, Vite, React Router, TanStack Query and native fetch. Runtime additions are lossless-json for exact numeric JSON; no Redux, CSS-in-JS, component framework or extra HTTP client was added. The package and lockfile pin compatible versions; TypeScript 6 avoids the unsupported TypeScript 7/typescript-eslint pairing. Node 24.x is the chosen frontend runtime.

App owns routing/title behavior and an accessible responsive shell. Only auth has a feature module so far: a runtime-validated GET /me session query. Other pages are explicitly unimplemented placeholders shared through one component rather than empty feature scaffolding. Protected pages never assume authentication, never show private content on network errors and do not contain product fixtures. The public home page describes product direction and foundation status. System fonts, approved yellow/off-white/dark/white tokens and native semantic controls form the design system. AI Coach is informational only.

One API client accepts relative resources, uses same-origin credentials and rejects redirects. CSRF tokens/header names are memory-only, with a shared bootstrap and generation guard across authentication transitions. The next auth stage must explicitly invoke afterAuthentication after successful sign-in and clear on logout. Stale requests and bootstrap responses are discarded; 401 clears private query data without cancelling the session query's own error delivery. No blanket retry exists for mutation errors. HTTP errors expose safe Problem Details metadata and raw Retry-After; network errors do not log or disclose bodies. Runtime profile/CSRF decoders validate response shape before use.

The JSON codec preserves numeric lexemes through lossless-json. Versions and decimal form inputs never travel through unsafe Number conversion; explicit exact tokens serialize as JSON numbers. Quantity validation rejects excess precision instead of rounding. Null percentages remain null. The frontend does not own authoritative metric calculations.

Vite binds to localhost:5173 and proxies API/OAuth paths to the local backend, preserving Host. This is a development topology, not a production hostname in the bundle. Production asset copying, public static route authorization, SPA fallback and OAuth success/failure redirect adaptation remain future approved integration work. The backend and its Google/cookie/security configuration are unchanged.

Unit/component tests use isolated mocks. Playwright runs against the built preview with mock session responses and checks public/gated navigation at desktop/mobile widths. This is shell smoke coverage, not a claim of live backend/browser authentication. Existing backend verification remains separate and mandatory. Build output and browser reports are ignored; no service worker or persisted private cache exists.

Senior review corrected a stale-401 race: responses from a previous authentication generation are discarded before clearing newer session state. Bootstrap cancellation and concurrent callers have regression coverage. Session expiry clears private queries without cancelling the session query's own error delivery. Shared buttons default to type=button and input error descriptions preserve caller help text. The patched development glob dependency is pinned through a documented minimatch 11.0.1 override; the full and production dependency audits are clean.

Verified November 12, 2026: 42 frontend unit/component tests and 8 mocked-session Chromium smoke tests passed, alongside strict typecheck, ESLint and production build. Desktop/mobile screenshots were inspected. The browser tests include 320/375/768px overflow checks, but do not claim live backend end-to-end authentication. Backend Maven verification passed all 231 existing tests. No backend source or configuration changed.


## Day 6 B2: authentication and account settings

B2 supersedes B1 authentication placeholders and its deferred OAuth redirect adaptation. React now implements register/login, session restoration, protected routing, logout, Google callback handling and settings against the existing APIs. Initialization runs CSRF bootstrap then profile lookup. Authentication renews CSRF after session rotation, cancels old queries and clears account caches. Both navigation paths use the same fixed protected-route allowlist. Callback and failure redirects are fixed relative paths, with no provider diagnostics or client-supplied redirect destination.

The session query reconciles on focus, removes old-owner data when identity changes and refreshes security state. Generation checks discard obsolete HTTP responses; 401 clears authenticated state only for the active generation. Mutations are never blindly retried. 403 requires renewed security state and explicit user action; network errors do not imply logout. Settings invalidate reporting-dependent caches and retain the server's prospective timezone policy. No authoritative calendar calculation was moved into React.

Google still uses Spring Security state/nonce validation and provider-subject association. Only redirect handlers changed on the backend. Production Secure/HttpOnly/SameSite=Lax cookies, local HTTP exception, CSRF rules, JDBC persistence and deny-by-default remain unchanged. No new API, migration, forwarded-header trust or dependency was introduced. The public Google build flag must match backend enablement. Register localhost:5173/login/oauth2/code/google for the documented Vite journey. At the B2 checkpoint, production asset packaging and exact SPA delivery were deferred; B6 below implements both.

Isolated mocks cover errors/races. A separate Chromium suite reaches the actual packaged backend and disposable PostgreSQL, checking registration/login, rotation, CSRF rejection, profile persistence, logout, deep links and two-account isolation on desktop/mobile. The fixture copies the verified JAR to temporary storage and owns only its random container and loopback ports. Live Google and production HTTPS browser behavior are not established by these tests.


## Day 6 B3: goals and task workflows

React implements goal CRUD/archive, explicit progress/history/voids, weekly planning and task CRUD/actions/filtering through the existing APIs. Production never uses fixtures or local-only records. The existing shared API client, TanStack Query, native forms/dialogs and exact JSON codec suffice; no dependencies, infrastructure, migrations or backend behavior changed.

### Verified contract inventory

| Resource | Existing contract used by React |
| --- | --- |
| Goals | GET/POST `/goals`, GET/PATCH `/{id}`, POST `/{id}/archive`; edit/archive use returned goal version |
| Progress | POST/GET `/goals/{id}/progress`, POST `/{entryId}/void`; recording uses UUID + exact quantity + effectiveDate, not goal version |
| Plans | PUT `/goals/{id}/weekly-plans/{Monday}`; omit version on first creation, use the returned plan token on updates |
| Tasks | GET/POST `/tasks`, GET/PATCH `/{id}`, POST `/{id}/complete`, `/reopen`, `/reschedule`, `/archive`; each mutation uses returned task version |
| Filters | Active or archived selection; optional OPEN/COMPLETED, owned goal, exact current due date **or** inclusive from/to; bounded page/size |
| Reporting context | GET `/analytics/dashboard?goalsSize=1`; decode only period metadata, never derive authoritative metrics in React |

Paths are under `/api/v1`. Owner identity comes from the authenticated session. Request bodies never contain an owner ID. Private query keys begin with feature and account ID; query functions pass AbortSignal. Mutations synchronously reject duplicate operations, cancel prior product reads, accept authoritative responses and invalidate relevant goal/task/reporting/dashboard/analytics queries. Version comparison prevents an older mutation result replacing a newer cached entity. Query cancellation plus post-mutation refetch prevents stale reads replacing accepted results. No automatic mutation retries or optimistic completion are used.

Drafts retain their base version through a conflict. Refetched details show current metadata, dates, state and totals; a user must explicitly choose to retain the draft with the new token or replace it. Goal and plan tokens are never interchangeable. Current-week first plans are allowed; an existing started plan is fixed. Server context failures pause date-based writes, and calendar context is timestamped rather than represented as a continuously advancing browser clock.

Quantities and versions remain exact lexemes; only bounded pagination/duration values become safe integers. Total counts remain exact strings/BigInt for pagination. Date-only values remain ISO strings, with UTC civil arithmetic solely for Monday validation; server instants/timezones drive reporting-date defaults, including DST. Java owns totals, percentages, open-week validation and history.

Progress retry state contains the frozen submission UUID and exact payload in the private query cache. It survives component navigation, not reload/logout, and is never placed in browser storage. Network/5xx uncertainty permits only identical retries; conflicting reuse requires reviewing history before explicitly starting another entry. Rejected or late responses cannot recreate retry state after an account switch. Voids retain history; replacements are a separate request. A synchronous pre-allocation guard prevents two same-cycle submits from replacing the retained UUID.

Native dialogs provide focus trapping, Escape cancellation and focus restoration; focus moves to the detail heading if archive removes the trigger. Accessible labels, inline errors/summaries, responsive lists, background-refresh feedback and visible focus retain the existing design. Browser suites test desktop/mobile, keyboard controls, account isolation and real two-context conflicts. Local mock fixtures are restricted to isolated tests. Existing plan arrays are not paginated by the backend; their growth remains a documented API limitation.


B3 verification on November 15, 2026 passed 132 frontend tests (58 added to B2), 8 mocked shell tests, 12 actual PostgreSQL/Spring Boot Chromium cases and all 231 backend tests, plus typecheck, lint and build. Both npm audits were clean. Review corrections lock units during unknown progress outcomes, reject duplicate submits before UUID allocation, retain account isolation in late handlers, avoid false claims of fresh metrics after refresh errors and distinguish an optional goal filter from an unassigned-only filter (which the API does not provide). No unresolved critical/high/medium findings remained in scope. Live Google, full assistive-technology/cross-browser verification and production delivery remain separate gates.


## Day 6 B4: habit tracking through reconstructed evidence

The habit frontend uses the existing API only. GET `/habits?page&size&archived` is capped at five visible records; each visible row performs one cancellable, one-date GET `/{id}/history`. Selected detail, rest preview and paginated history are additional bounded reads, not an unbounded traversal of the account. There is no daily-board endpoint or client-computed recurrence/consistency/streak metric.

### Contract inventory

| Operation under `/api/v1/habits` | Contract |
| --- | --- |
| GET/POST `/`, GET/PATCH `/{id}` | Owned list/detail/create/metadata; metadata sends habit version |
| PUT `/{id}/completions/{date}` | Habit-wide version and day response's weekStart |
| DELETE `/{id}/completions/{date}` | Exact strong quoted If-Match; weekStart query parameter |
| PUT/DELETE `/{id}/rest-days/{date}` | PUT adds reason; DELETE uses quoted If-Match; both use the selected day/week and must precede day start |
| POST `/{id}/schedules` | Habit version, prospective effectiveFrom, distinct ISO weekdays and inclusive date bounds |
| POST `/{id}/archive` | Habit version; future disablement from tomorrow, no history removal or unarchive |
| GET `/{id}/history` | At most 93 inclusive dates; cutoff and independent page/actionPage/schedulePage/size |

Current and historical states are different contracts. Current daily/rest evidence supplies a cutoff, per-date schedule and reporting-day window; the frontend checks these for action availability and sends that window's weekStart. Historical cutoffs are exclusive and read-only; unstarted day context may be omitted. Returned DayView.version is the **current habit-wide token**, not an occurrence or as-of version. Present-day current/pending schedule annotations are never applied to explicit-cutoff schedule records. A day references its server-selected accepted revision; supersession is not reconstructed in JavaScript.

A WeakMap keyed by QueryClient holds synchronous owner/habit mutation locks, retained across component remounts until each request finishes. Locks reject overlap instead of queuing or replaying old intents. The rendered page additionally uses the existing pessimistic product action guard. A private query-cache version floor takes the maximum observed habit-wide version, with owner checks before publication; older day snapshots cannot enable mutations using a known stale token. Metadata and revision editors retain a separate draft base version and require explicit user review after conflict. Owner-scoped query cancellation/refetch and version-aware entity updates prevent old responses overwriting accepted writes.

No day is checked off optimistically. PUT returns a DayView confirmation; subsequent invalidation refreshes actual habit/history snapshots. Rest remains an exclusion, not completion. Obsolete rest decisions remain visible and may be cancelled prospectively when allowed. Archive hides metadata/schedule editing but retains eligible open-week check-ins, because the backend permits those historical obligations. Confirmation state is cleared before dispatch, so an old asynchronous result cannot dismiss a newer dialog; a failed day-preview refresh blocks confirmation and retains the reason. The checked-date controls retain their identity while the habit version floor advances before the day preview, keeping the reason draft mounted while actions wait for coherent evidence.

The reporting period is obtained from the existing dashboard metadata, without building an analytics screen. Initial date-dependent forms wait for that lookup to settle; later refreshes preserve drafts. No local-midnight or profile-timezone guess replaces stored week/day context. Unsupported browser timezone data pauses date-dependent daily controls rather than falling back to the device timezone. A future rest preview can use a prospective timezone revision while the current week remains frozen. If current context cannot be resolved, daily actions pause; creation may take an explicit user-supplied date and let Java validate it. Civil date arithmetic only validates bounds/ranges and proposes dates; Java decides eligibility.

The browser harness uses a separate `org.belsenfixture.BrowserFixtureApplication` compiled under test sources, loaded through PropertiesLauncher with a temporary copy of the verified JAR and fixture classes. Its primary Clock is anchored to a known Monday and advances normally, preserving meaningful exclusive-cutoff ordering. Production classes/configuration, default clock, migrations and dependency management are unchanged; the fixture is absent from the production JAR and has no control endpoint. Existing B2/B3 cases run through the same actual database/session/security implementation. Isolated DTO mocks remain separate from real Chromium evidence.

Remaining tradeoffs are five bounded day reads per board page, page-level serialization, manual refresh after time advances and independent snapshots across history pages. No report is labeled FINALIZED, and no new scheduler, calendar library, infrastructure or service was added. Analytics and coaching UI remain separate approved work.


B4 final verification on November 16, 2026 passed 177 frontend tests, 8 mocked smoke tests, 18 real PostgreSQL/Spring Boot Chromium tests and three extra desktop conflict repeats, plus typecheck, ESLint, production build and all 231 backend tests (50 unit, 181 integration; no failures/errors/skips). Flyway V1–V5 and Hibernate validation passed; both npm audits reported zero vulnerabilities. Review checked exact versions, account isolation, CSRF/session behavior, delayed responses, frozen/prospective timezone evidence and draft preservation. Focused keyboard/focus/overflow checks and desktop/mobile screenshot review passed. No unresolved critical/high/medium B4 finding remained; complete assistive-technology/cross-browser verification and live Google/production delivery remain separate gates.


## Day 6 B5: authoritative dashboard and historical analytics

Dashboard and Analytics share a small analytics feature: `model.ts` decodes the actual DashboardDtos/HabitDtos contract, `data.ts` owns one cancellable query, `Report.tsx` presents metrics, and `AnalyticsPage.tsx` handles current versus historical selection. No backend implementation or dependency changes are required. Existing resource views keep their own narrow reporting-context reads; the new report pages decode the entire response once rather than requesting calendar metadata separately.

Query keys include feature, authenticated account ID, requested Monday, exact cutoff and goal page. Both routes share the current-report cache; distinct route keys ensure a historical selection never carries into Dashboard. Previous-week results are not placeholder data for a new selection. One transient network retry is allowed; HTTP errors and mutations are not retried. AbortSignal cancellation and existing API session-generation guards reject stale responses. Existing product mutation invalidation already includes analytics; profile reconciliation clears private caches. The endpoint returns no-store and no owner parameter is accepted or sent.

All long counts and decimal quantities remain exact numeric strings decoded through lossless JSON. The only conversion to a JavaScript number is the already bounded 0–100 server percentage used for visual width and ARIA. Null percentages produce text guidance without bars. React does not recompute authoritative ratios, total goal percentages, recurrences or trends. Original task cohorts, cutoff-selected completion/reopening, strict all-obligations daily habit success and explicit non-voided weekly progress remain Java/PostgreSQL responsibilities.

Analytics validates civil Monday dates in UTC without selecting a device-local week. Optional cutoffs accept exact UTC instants with up to nine fractional digits, preserving the request text. Java enforces actual reporting bounds and future limits. Returned UTC boundaries/timezones and calendar/report statuses remain authoritative, including clipped/DST intervals. Report status follows week closure, not selected cutoff. FROZEN keeps stored context, RECONSTRUCTED uses persisted policy, and UNFROZEN is an honest read-only preview. CLOSED_UNFINALIZED is explicitly mutable after late commits; each goal page is its own read-only REPEATABLE READ response, not a finalized publication.

Weekly goal rows display the server's actual completed/planned units and capped percentage separately. NO_WEEKLY_TARGET preserves recorded units without inventing a denominator. Goal titles are labelled current metadata; archive state is not fabricated from a DTO that does not contain it. Errors distinguish unavailable history, calendar conflict, invalid selection and transport failure without rendering raw server diagnostics.

Native fields, labelled progress bars, visible text ratios, keyboard controls and existing focus/reduced-motion rules support accessible use. Pagination restores focus to the weekly-goal heading after the new page arrives; background refresh does not reset week/cutoff drafts. Responsive layouts wrap exact large units, timestamps and long titles. Desktop/mobile real tests exercise actual sessions, CSRF-protected resource writes, persistence, voiding, original cohorts, archived historical goals, cutoff nanoseconds and DST. Only the disposable browser database receives closed-week fixtures; no production clock rule or test control endpoint is added.

Remaining limits are manual refresh after inactivity, independent snapshots across requests, current rather than versioned metadata, unavailable pre-baseline history and reset-on-reload selection. No new API, migration, aggregation, cache infrastructure, chart dependency or finalization path is introduced.


B5 verification on November 16, 2026 passed 225 frontend tests (all 177 baseline plus 48 new), 8 mocked Chromium smoke cases, 24 real Spring Boot/PostgreSQL desktop/mobile cases (all 18 baseline plus six new), typecheck, ESLint and build. Maven passed all 231 backend tests (50 unit, 181 integration; no failures/errors/skips), including V1–V5 and Hibernate validation. Both npm audits and credential/generated-artifact/whitespace checks passed. Focused keyboard, pagination-focus, overflow and screenshot review passed, with no unresolved critical/high/medium B5 issue. Test fixtures are absent from production bundles/JAR; full assistive-technology/cross-browser and production delivery remain unverified.


## Day 6 B6: explicit production delivery

The Maven `spa` profile copies a verified Vite distribution into the application JAR and excludes local HTTP configuration. `scripts/build-production.sh` uses Node 24, the npm lockfile, clean Maven packaging and all unit/integration checks. Frontend/backend sources remain separate; no SPA source, test class or test clock enters the runtime artifact. The optional non-root Dockerfile pins its Java 21 base by digest and accepts only the combined JAR in its build context.

SpaController serves fixed public GET/HEAD document routes and a flat allowlist of hashed asset extensions with explicit MIME types. Generic static mappings are disabled. No request-path forwarding or wildcard HTML fallback exists. API, actuator, OAuth and private-file namespaces retain their security rules; missing asset requests receive safe JSON errors. HTML is no-store; immutable caching applies only to explicit hashed asset responses. This keeps Spring Security's existing cache protections for APIs and errors.

The production runtime profile requires an HTTPS origin, packaged HTML, validated database settings and secure HttpOnly/SameSite=Lax cookies. It rejects the local profile, invalid origin/port, callback origin mismatches and unsupported proxy settings. Direct TLS is default. TLS termination can be moved to an explicitly trusted native proxy only behind a private listener and a header-stripping/network boundary; forwarded headers are otherwise ignored. The Google callback is derived from the configured public origin rather than an untrusted Host, while local development keeps its existing template. HTTPS redirects, existing HSTS/frame/nosniff defaults, CSP and permissions restrictions apply. Inline CSS is deliberately allowed for existing dynamic progress widths; inline scripts are not.

Production suppresses Hibernate bind and SQL error diagnostic logging to avoid duplicate-email/SQL value disclosure. Application Problem Details keep safe request IDs and generic errors. Flyway V1–V5, Hibernate validate and JDBC-owned session schemas remain unchanged. Graceful shutdown allows 30 seconds; the pool defaults to maximum 10/minimum 2 with bounded acquisition waiting. The status-only public health endpoint is liveness, not ongoing database readiness or an actuator/configuration exposure.

Existing frontend and real-backend tests remain. A separate packaged Chromium suite launches `java -jar` with the production profile, local HTTPS, actual PostgreSQL and system UTC clock, without Vite or the test-only reporting-clock launcher. It derives dates from server metadata and expires only a fixture account's persisted last-access/expiry values, not production session policy. Automated axe scans cover the major screens. The scanner is a pinned test-only dependency, not production JavaScript. Browser requests are checked for the single HTTPS origin; upstream Router parser defaults are not rewritten.

Tradeoffs and deployment prerequisites are documented in DEPLOYMENT.md. No public hosting, real Google credentials, ingress provider, distributed throttle, full assistive-technology/cross-browser certification, CI pipeline, new infrastructure service or migration is claimed.


B6 verification on November 16, 2026 passed 248 backend tests (67 unit, 181 PostgreSQL integration), all 225 frontend tests, 8 mocked shell cases, 24 real-backend cases and 6 production-JAR HTTPS cases. Node 24 typecheck/lint/build, both npm audits, credential/artifact/whitespace checks and production-JAR inspection passed. Packaged axe scans covered authentication/registration, settings, goals, tasks, habits, dashboard, analytics and a confirmation dialog with no reported violations; focus/overflow/screenshots were reviewed. Runtime image build and identical repeat-package checksums passed. The production artifact excludes local config/test launchers, and missing TLS settings rejected startup. No unresolved critical/high/medium finding remained in B6 scope; public ingress, certificates, live Google, complete assistive-technology/cross-browser testing and release operations remain external gates.


## Day 7 B3: Coach interface and replay boundary

The authenticated `/app/coach` page reads provider status and a 20-goal dashboard preview, then uses the existing analysis endpoint only on an explicit consented click. Requests pin the preview's server week/cutoff and carry a client-generated submission UUID. Private owner-scoped in-memory state retains the exact request for explicit recovery; no automatic retries or persistent browser credentials/results are added. Session changes clear private state; aborted/obsolete responses cannot overwrite newer state. A reload clears in-memory recovery, with no claim that it cancels remote inference.

Cards display server-owned text and safe enum-mapped workflow links. Candidate gains derived read-only title/nextAction presentation properties with backward-compatible replay deserialization. The provider projection still omits those properties and all private source text. Metrics, eligibility, calendar rules, 24-hour replay and 32-day tombstones remain unchanged. Exact SPA allowlists and safe login returns now include Coach without opening API/OAuth/private-file fallbacks. Coach availability defaults disabled; live Groq/Free-plan/ZDR checks remain external prerequisites.


Verified Day 7 B3, November 20, 2026: Java 21.0.11/Node 24.22.1. Full production-profile Maven packaging/verification passed **287 tests (81 unit, 206 PostgreSQL integration)**, zero failures/errors/skips; all 286 B2 tests remain, plus one Candidate presentation/replay compatibility test. V1–V7 and Hibernate validation passed; no migration or dependency version changed. Frontend typecheck, ESLint, **255 tests** (all 225 baseline plus 30 Coach cases) and production build passed. Chromium passed **8 existing mocked cases, 6 Coach-specific mocked API cases, all 24 real-backend cases, and 8 packaged production HTTPS cases** (six retained plus two Coach cases). Scoped axe scans reported zero violations for result/disabled/error states and packaged workflows; this is not full assistive-technology or cross-browser certification. Desktop/mobile screenshots, keyboard behavior and 320/375/768 px overflow checks were reviewed.

The initial concurrent legacy browser runs reported two packaged expiry failures and one real historical-fixture failure because both suites matched the same database-name prefix. No application assertions or session/history mutations were bypassed. Separate prefixes/selectors corrected the test isolation; complete reruns passed. The final production JAR excludes local configuration, test launchers and fixture assets, and production JS contains no provider credentials, endpoint or test fixtures. Credential/artifact/whitespace/documentation checks passed. No unresolved critical/high/medium issue remained within B3 scope.

Recovery state is private in-memory state with a 24-hour cache retention setting; sign-out/account changes clear it and a full reload loses it. Browser cancellation is not proof that remote inference stopped. No live Groq inference, actual Free-plan/ZDR verification, public deployment or B4 work occurred. Enabled UI workflows were verified with deterministic API fixtures; real-backend Coach browser coverage used the disabled provider. B4 remains a separately approved final integration stage.
