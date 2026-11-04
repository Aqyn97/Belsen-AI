# Seven-day roadmap

Target MVP date: **November 6, 2026**, using the project owner's Asia/Almaty calendar. This is an ambitious plan, not completed work. The Stage B backend foundation is implemented; verification evidence is recorded below. Authentication and profile code are implemented; live Google browser verification remains pending.

Each day has one main work package. Integrate small frontend slices from Day 3 onward so Day 6 is not the first time the browser meets the API. Review actual evidence before marking criteria complete.

| Day | Date | Main work package | Status |
| --- | --- | --- | --- |
| 1 | October 31 | Architecture and documentation foundation | Complete; owner confirmed |
| 2 | November 1 | Runtime and authentication | Stage B/C implemented; live Google verification pending |
| 3 | November 2 | Goals and tasks | Backend implemented and committed; browser slice remains planned |
| 4 | November 3 | Habits | B1/B2 backend implemented November 4; browser slice remains planned |
| 5 | November 4 | Analytics | Planned |
| 6 | November 5 | Frontend integration | Planned |
| 7 | November 6 | AI coaching and release | Planned |

## Day 1: architecture and foundation

Acceptance criteria:

- The owner-approved architecture and refinements are recorded consistently.
- Exactly the ten approved foundation files are prepared, with accurate status labels and no application scaffolding.
- Reporting formulas, history boundaries, security decisions and deployment shape are documented.
- File scope, whitespace, documentation consistency and secret placeholders are reviewed.
- The owner reviews the files; Git staging, commit and push remain under manual control.

Owner review is complete, as confirmed in the Day 2 assignment.

## Day 2: runtime and authentication

Acceptance criteria:

- Java 21/Spring Boot/Maven scaffolding uses verified compatible versions and reproducible tooling. Frontend scaffolding is deferred from the original Day 2 plan.
- Local PostgreSQL starts through Compose; Flyway creates the initial schema.
- Email/password registration, login, logout and profile timezone work through secure sessions.
- Google login works with configured credentials; identity collisions cannot silently link accounts.
- Authentication, CSRF and unauthorized-access tests pass. CI is deferred from this controlled Stage B scope.
- Authentication behavior is verified through API/security tests; the browser account interface is deferred to frontend work.

Dependencies: approved implementation scope, Java/Node/Docker availability and Google client credentials. Verify production callback requirements early.

### Stage B checkpoint

Implemented: Java 21/Spring Boot 4.1.2, Maven Wrapper 3.9.12, isolated PostgreSQL Compose configuration, identity/session migrations, Hibernate validation, minimal health endpoint and deny-by-default security. Verified November 1, 2026: Maven `verify` passed with 20 tests, zero failures/errors/skips. PostgreSQL connectivity, both migrations, Hibernate validation and JDBC session persistence passed. Live HTTP checks and a separate Compose + packaged-JAR smoke test confirmed health `200`, private GET `401` and missing-CSRF mutation `403`. The smoke-test resources were cleaned up.

### Stage C checkpoint

Implemented: registration, JSON login, CSRF bootstrap, logout, owned profile updates, timezone/input validation, JDBC security-context persistence, session rotation, bounded throttling and conditional Google OIDC. Status statements from the Day 1 foundation are corrected. No frontend or other product module was added.

PostgreSQL and HTTP tests exercise password flows, cookies, profile ownership and a local signed-token OIDC provider. Verified November 1, 2026: Maven `verify` passed 51 tests (20 database-independent tests and 31 PostgreSQL integration tests), zero failures/errors/skips. Both migrations and Hibernate validation passed. A separate packaged-JAR + Compose HTTP smoke test passed registration, login, profile update, session continuity and logout invalidation; its resources were cleaned up. Real Google credentials and a successful browser flow are still needed to satisfy the live Google acceptance criterion. The full Day 2 package is not marked complete until that external verification is recorded.

## Day 3: goals and tasks

Acceptance criteria:

- Users can create/edit/archive goals, set weekly targets and record explicit progress.
- Users can create/edit/archive tasks, set optional duration, associate owned goals and complete/reopen/reschedule tasks.
- Original due dates are preserved; current state and required events update atomically.
- Ownership and cross-user reference tests pass; invalid targets/durations are rejected.
- A small frontend slice demonstrates goal and task operations against real data.

Dependency: working authentication and migrations. Avoid expanding history into a general event-sourcing system.

### Day 3 Stage B checkpoint

Goals, lifetime progress, weekly plans, frozen reporting calendar, auditable voids, idempotent submissions and ownership checks are implemented. Verified November 2, 2026: full Maven verification passed 83 tests, zero failures/errors/skips, including all 58 existing cases, 4 goal-rule tests and 21 goals integration tests. Flyway V1–V3 and Hibernate validation passed. At the Stage B checkpoint, tasks still required separate Stage C approval; the Stage C result is below.

### Day 3 Stage C checkpoint

Task CRUD/actions, owned active goal links, stable filtered lists, optimistic conflict handling and append-only ordered events are implemented. Reporting uses the Stage B frozen calendar. New tests cover transactional rollback after a flushed UPDATE, cutoff reconstruction, equal-time event ordering, ownership, CSRF, stale requests and concurrent actions. Verified November 2, 2026: full Maven verification passed 109 tests, zero failures/errors/skips, preserving all 83 previous cases. The 26 new cases comprise 23 task integration tests and 3 validation tests. Flyway V1–V4 and Hibernate mappings passed. No frontend, habits, analytics or AI work was added. At that checkpoint final owner review remained pending; Day 3 was subsequently committed as b132059.

## Day 4: habits

Acceptance criteria:

- Users can create/edit habits, select weekdays and record eligible daily completion.
- Rest days and future days cannot be falsely completed.
- Schedule edits/deactivation preserve elapsed obligations and follow the effective-date rule.
- Unique occurrences prevent duplicate daily records.
- Schedule, timezone, ownership and completion tests pass; a browser slice shows daily habits.

Dependency: authenticated users with reporting timezones. Occurrence reconstruction must work even when users skip opening the app for several days.

### Day 4 B2 checkpoint

Implemented the habit REST backend: owner-scoped creation/list/detail/metadata, immutable future schedules, versioned completion/undo, prospective habit-specific rest/cancellation, archive and bounded historical obligations/actions. Current-state and exclusive-cutoff history use consistent snapshots and preserve immutable records. Internal consistency rules use strict daily success and no-data results. Public analytics and frontend remain planned; the browser acceptance criterion above is not complete. V1–V5 remain unchanged by B2.

Verified November 4, 2026: full Maven verification passed **182 tests** (42 unit, 140 PostgreSQL integration), zero failures/errors/skips. All 138 previous tests remain, with the B1 unavailable-route assertion updated to retain deny-by-default coverage for an unsupported route. The 44 new cases comprise 9 rule tests and 35 habit integration tests. Flyway V1–V5 and Hibernate validation passed.

## Day 5: analytics

Acceptance criteria:

- Task metrics use original due dates and completion state at cutoff in every response.
- Habit metrics count fully completed eligible days, excluding rest days.
- Weekly goal metrics use explicit weekly targets, preserve actual units and cap percentages at 100%.
- Zero denominators return no-data states; counts and report timezone are visible.
- Closed-week snapshots remain stable after later activity and archiving.
- Tests cover cutoff boundaries, reopened/rescheduled tasks, schedule changes and daylight-saving behavior.

Dependencies: goals, tasks and habits with the required history. This is a correctness gate, not merely a dashboard styling task.

## Day 6: frontend integration

Acceptance criteria:

- Core account, goals, tasks, habits and weekly-summary journeys work against the backend.
- Responsive layouts use the approved yellow identity and readable contrast.
- Forms have labels, keyboard access, visible focus and useful validation errors.
- Loading, failure and empty states are present; planned features are not represented by fake data.
- Frontend type checking, linting, tests and production build pass; browser smoke checks cover the main journeys.
- React assets can be served by Spring Boot without swallowing API errors in SPA fallback.

Dependencies: stable enough API contracts and incremental browser integration from earlier days. Resolve deployment packaging now, not at the end of Day 7.

## Day 7: coaching and release

Acceptance criteria:

- “Analyze my progress” uses a backend provider adapter and stored progress summaries.
- Output is validated, limited to three suggestions and visibly separate from verified metrics.
- Quotas, timeouts, invalid output and provider failures have tested behavior; AI cannot mutate user data.
- A public HTTPS origin serves the frontend and API with persistent PostgreSQL and sessions.
- Production OAuth redirects, cookie settings, migrations, backup configuration and secret handling are checked.
- CI passes and critical end-to-end smoke checks succeed; actual results and remaining limitations are recorded.

Dependencies: provider/model choice, funded usage limit, hosting access, production Google configuration and release-ready core features.

## Release risks and response

The largest risks are analytics edge cases, OAuth configuration, provider access, deployment constraints and too much integration work arriving late. Obtain credentials and hosting access early. Keep visual polish small, use focused work packages and resolve integration failures as they appear.

All approved MVP areas remain release targets. Do not silently drop Google sign-in or coaching and call the full MVP complete. If acceptance criteria cannot be met by November 6, record the gap and request an explicit scope/date decision. Security, ownership checks and correct reporting are release gates.
