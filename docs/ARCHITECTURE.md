# Architecture

Status: backend foundation, authentication and profiles implemented; Google OIDC tested against a mock provider, with live browser verification pending. Goals and explicit progress are implemented in Day 3 Stage B; tasks are implemented in Stage C; habit persistence/calendar support and secure habit operations are implemented in Day 4 B1/B2; the authenticated analytics dashboard API is implemented in Day 5 B1; immutable finalization, coach and frontend remain planned. This document describes the target MVP. See `DECISIONS.md` for tradeoffs and unresolved choices.

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

Version references checked during the architecture review: [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html), [React versions](https://react.dev/versions), [Vite requirements](https://vite.dev/guide/) and [PostgreSQL support policy](https://www.postgresql.org/support/versioning/). Backend dependencies are managed by the Spring Boot parent; frontend dependencies are not installed.

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
