# Seven-day roadmap

Target MVP date: **November 6, 2026**, using the project owner's Asia/Almaty calendar. This is an ambitious plan, not completed work. The Stage B backend foundation is implemented; verification evidence is recorded below. Authentication and profile code are implemented; live Google browser verification remains pending.

Each day has one main work package. Integrate small frontend slices from Day 3 onward so Day 6 is not the first time the browser meets the API. Review actual evidence before marking criteria complete.

| Day | Date | Main work package | Status |
| --- | --- | --- | --- |
| 1 | October 31 | Architecture and documentation foundation | Complete; owner confirmed |
| 2 | November 1 | Runtime and authentication | Stage B/C implemented; live Google verification pending |
| 3 | November 2 | Goals and tasks | Backend implemented and committed; browser slice remains planned |
| 4 | November 3 | Habits | B1/B2 backend implemented November 4; browser slice remains planned |
| 5 | November 4 | Analytics | B1 API implemented November 5; immutable finalization remains planned |
| 6 | November 5 | Frontend integration | B1 foundation implemented November 12; complete workflows and production integration planned |
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

### Day 5 B1 checkpoint

Implemented the authenticated dashboard GET endpoint with original-cohort task counts, strict daily habit consistency and paginated explicit weekly goal progress. All metrics share one exclusive cutoff and one read-only REPEATABLE READ snapshot. Owner isolation, no-data states, frozen/policy-derived calendar context and current-metadata labels are explicit. No reads create reporting weeks or occurrences. Closed weeks are CLOSED_UNFINALIZED; the immutable-snapshot acceptance criterion above remains pending. No new migration, finalization table, frontend, AI or dependency was added.

Verified November 5, 2026: Maven verify passed **231 tests** (50 unit, 181 PostgreSQL integration), zero failures/errors/skips. All 182 baseline cases remain; 8 rule and 41 dashboard integration tests were added. Tests cover source-event ordering, task cohorts, rest and schedule reconstruction, cutoff/void boundaries, aggregate precision, current previews, timezone/DST transitions, owner isolation, real JDBC isolation, concurrent commits and late unfinalized changes. Native metric query count stayed constant on a larger isolated fixture; V1–V5 and Hibernate validation passed. Immutable finalization remains unimplemented.

## Day 6: frontend integration

Acceptance criteria:

- Core account, goals, tasks, habits and weekly-summary journeys work against the backend.
- Responsive layouts use the approved yellow identity and readable contrast.
- Forms have labels, keyboard access, visible focus and useful validation errors.
- Loading, failure and empty states are present; planned features are not represented by fake data.
- Frontend type checking, linting, tests and production build pass; browser smoke checks cover the main journeys.
- React assets can be served by Spring Boot without swallowing API errors in SPA fallback.

Dependencies: stable enough API contracts and incremental browser integration from earlier days. Resolve deployment packaging now, not at the end of Day 7.

### Day 6 B1 checkpoint

Implemented the frontend foundation only: strict React/TypeScript tooling, pinned dependencies/lockfile, relative fetch/CSRF client, exact-number codec, shared UI styles/components, public placeholders and a real-session-gated responsive shell. At this checkpoint, authentication forms and product workflows were incomplete, and backend source/configuration were unchanged. OAuth redirect changes and later frontend slices required separate approval; B2 below records its authorized account work. Production SPA delivery remains deferred. The November 6 target is an unmet historical target, not a release claim.

Verified November 12, 2026: typecheck, ESLint, 42 frontend tests, production build and 8 mocked-session Chromium smoke tests passed. Both dependency audits are clean. All 231 backend tests still pass; source/configuration and V1–V5 are unchanged. Real backend/browser authentication, production SPA packaging and complete product workflows remain unverified/unimplemented.

### Day 6 B2 checkpoint

Implemented account interfaces only: email/password registration/login, session restoration and protected deep links, confirmed logout/cache clearing, profile display/name/timezone edits and configured Google navigation/callback handling. Fixed relative OAuth redirects are the only backend behavior change. Product workflows, production asset packaging and deployment remain outside B2. Live Google authentication is still unverified.

Acceptance evidence, November 14, 2026: strict typecheck, ESLint, 74 frontend unit/component tests, production build, 8 mocked-session Chromium smoke tests and 6 real-backend Chromium desktop/mobile tests passed. All 231 backend tests passed (50 unit, 181 PostgreSQL integration), zero failures/errors/skips, with V1–V5 and Hibernate validation. Both npm advisory audits reported zero vulnerabilities. Real-browser tests verified session rotation, stale-CSRF rejection, persisted settings, reloads, password login/logout and two-account isolation against disposable PostgreSQL. Keyboard, responsive overflow, focus/error labels and desktop/mobile screenshots were reviewed. This does not establish live Google or production HTTPS readiness.

### Day 6 B3 checkpoint

Goals and Tasks now replace their placeholders with real backend workflows. Accounts remain unchanged; habits, analytics, AI Coach and deployment are not part of this slice.

Acceptance criteria:

- Goals list/details/create/edit/archive work with exact values and returned versions; paginated history, explicit UUID-based progress and auditable voids preserve server reporting rules.
- Current-week first targets can be created; existing started plans stay fixed, and future edits use a distinct plan token.
- Tasks support owned goal association, metadata edits, completion/reopening, rescheduling/date removal, archival, filters and pagination. Original due dates remain visible.
- Uncertain progress retries reuse the frozen UUID/payload; same-cycle submits cannot replace that key. Reload/logout limitations are explicit.
- Goal, plan and task conflicts across real browser contexts preserve drafts, fetch latest state and require explicit user review without automatic replay.
- Real PostgreSQL/Spring Boot desktop/mobile tests cover the full workflows, response-loss fault injection, persisted reloads, cross-account rejection and keyboard confirmations; mocked smoke tests remain separately identified.
- Typecheck, lint, all frontend/backend tests, production build, npm audits and Git/credential/artifact checks pass before publication.

Verified November 15, 2026: 132 frontend tests, 8 mocked smoke tests and 12 real-backend Chromium tests passed, with all 231 backend tests passing and zero failures/skips. Typecheck, ESLint, production build, both npm audits, whitespace/credential/generated-file checks and isolated-fixture cleanup passed. Review regressions cover same-cycle UUID replacement, late account-switch responses, stale reads, unavailable reporting context, uncertain-unit edits, refresh failure messaging and filter semantics. No backend or dependency changes were needed.

The next frontend slice may implement habits and analytics after separate approval. B3 does not start it. Production delivery, live Google verification and coaching remain open release gates. Goal plan arrays and memory-only retry intents retain their documented limitations.

### Day 6 B4 checkpoint

Habits replace their placeholder with bounded daily checklists, creation and metadata editing, current-open-week completion/undo, prospective rest planning/cancellation, future schedule revisions, accepted history and archive. Dashboard/analytics, coaching and deployment are not part of B4.

Acceptance criteria:

- Weekday controls send distinct ISO 1–7 values; creation starts today or later and optional end dates are inclusive.
- Visible daily rows use reconstructed server evidence. Rest, unscheduled and unstarted dates never count as incomplete/completed obligations incorrectly.
- All dates share the habit-wide token. Synchronous locks survive remounts; DELETE actions use exact strong If-Match and the selected date's reporting week.
- Rest checks the future day window before confirmation; prospective timezone revisions and the frozen current week remain distinct.
- Metadata/revision drafts survive 409 responses. Completion, undo and rest are never automatically replayed; older responses cannot restore stale state or dismiss new confirmations.
- History stays within 93 dates, paginates three evidence streams independently and labels explicit-cutoff records without borrowing current schedule status.
- Archive preserves today/earlier obligations and immutable evidence; no unarchive or invented streak/consistency score is offered.
- Real PostgreSQL/Chromium desktop/mobile tests use a test-only deterministic reporting clock, verify two-context conflicts, frozen/prospective timezones, history and ownership, and clean up all resources.
- All frontend/backend quality gates, dependency/credential/artifact checks and review pass before publication. Production backend clock, validation, security and V1–V5 remain unchanged.

Verified November 16, 2026: 177 frontend tests, 8 mocked Chromium tests, 18 real Spring Boot/PostgreSQL desktop/mobile tests and three additional desktop concurrency repeats passed. Typecheck, ESLint, production build and Maven verification passed; backend totals remain 231 (50 unit, 181 integration), with no failures/errors/skips. V1–V5, Hibernate validation, both npm audits, credential/artifact checks and whitespace review passed. Focused keyboard, dialog, responsive overflow and screenshot review found no material issue. The test-only clock launcher is absent from the production JAR.

Next: an analytics/dashboard frontend may be proposed after separate approval. B4 does not begin analytics, AI Coach or deployment; live Google browser verification remains an external gate.

### Day 6 B5 checkpoint

Dashboard and Analytics replace their placeholders with the existing authoritative dashboard API. Both share current-week metrics and paginated weekly goal rows; Analytics adds Monday selection and an optional exact exclusive UTC cutoff.

Acceptance criteria:

- Show authenticated display name, server reporting timezone/boundaries and working links to core resource workflows.
- Preserve original task cohorts, strict eligible-day habit consistency and explicit non-voided weekly goal units. Never combine goal units or invent a score/trend.
- Keep null percentages distinct from zero, with no fake bars and useful empty-state actions. Show actual over-target units beside capped server percentages.
- Explain frozen/reconstructed/unfrozen calendars and provisional/closed-unfinalized reports. Never call mutable results finalized or invent missing historical metadata.
- Validate Monday/cutoff input, preserve exact fractional seconds, paginate only server goal rows, cancel obsolete reads and isolate account caches.
- Preserve selection drafts during refresh, reset history when returning to Dashboard, expose background refresh/error states and restore pagination focus.
- Retain all 177 frontend and 231 backend baseline tests. Add contract, precision, null/status, race, session/isolation, invalidation and accessibility tests plus actual PostgreSQL/browser workflows.
- Keep production backend, V1–V5 and dependency versions unchanged. Pass all quality/security/artifact checks before publication.

Verified November 16, 2026: 225 frontend tests (177 preserved plus 48 new), 8 mocked smoke tests, 24 real PostgreSQL/Chromium desktop/mobile tests (18 preserved plus six new), typecheck, ESLint and build passed. Full Maven verification passed 231 tests (50 unit, 181 integration), zero failures/errors/skips; V1–V5/Hibernate, both npm audits and credential/artifact/whitespace checks passed. Metric/security review and focused keyboard, pagination, overflow and screenshot review found no unresolved critical/high/medium issue. Temporary browser resources were cleaned up.

No B6, coaching, immutable snapshot finalization or production deployment is started. Full assistive-technology/cross-browser and live Google verification remain separate gates.

### Day 6 B6 checkpoint

Production integration packages the existing React app and REST API in one JAR. No AI Coach or public deployment is started.

Acceptance criteria:

- Exact SPA document routes support refresh, while APIs, OAuth, actuator and private files never receive catch-all HTML.
- HTML is no-store; hashed assets have explicit types and immutable caching. Local Vite proxy behavior remains available.
- Production startup rejects missing origin/assets/database configuration, the local profile, weakened cookies, callback-origin mismatch and invalid TLS/proxy combinations.
- Preserve session/CSRF/ownership/numeric/concurrency/reporting behavior, migrations V1–V5 and Hibernate validate; keep secrets and SQL values out of assets/logs.
- Produce a repeatable combined build under Node 24/Java 21, exclude local config/test launchers from the JAR and build the optional non-root runtime image.
- Retain 225 frontend tests and all 231 backend baseline tests. Pass all browser suites, including packaged production-profile HTTPS/PostgreSQL flows and axe scans across major pages.
- Document exact verification results, direct TLS, restricted proxy trust, environment variables, release prerequisites and remaining limitations before publication.

Verified November 16, 2026 for B6: 248 Maven tests (67 unit, 181 PostgreSQL integration; all 231 baseline retained), 225 frontend tests, 8 mocked Chromium tests, 24 real-backend Chromium tests and 6 production-JAR HTTPS tests passed. Typecheck, lint, build, both zero-vulnerability npm audits, JAR/artifact/credential/whitespace checks, runtime image build and repeat-package checksum comparison passed. Packaged axe scans reported no violations across scanned major pages/dialog, with focused keyboard, responsive and screenshot review. No unresolved critical/high/medium B6 finding remained. No public deployment, live Google or full accessibility/cross-browser certification is claimed.

## Day 7: coaching and release

Acceptance criteria:

- “Analyze my progress” uses a backend provider adapter and stored progress summaries.
- Output is validated, limited to three suggestions and visibly separate from verified metrics.
- Quotas, timeouts, invalid output and provider failures have tested behavior; AI cannot mutate user data.
- A public HTTPS origin serves the frontend and API with persistent PostgreSQL and sessions.
- Production OAuth redirects, cookie settings, migrations, backup configuration and secret handling are checked.
- CI passes and critical end-to-end smoke checks succeed; actual results and remaining limitations are recorded.

Dependencies: provider/model choice, funded usage limit, hosting access, production Google configuration and release-ready core features.

### Day 7A: AI Coach design checkpoint

The [AI Coach design](AI_COACH_DESIGN.md) proposes a user-triggered, evidence-backed prioritization assistant. It reuses one existing analytics snapshot, discloses bounded goal coverage and keeps verified metrics separate from AI prioritization. No Coach endpoint, provider integration, migration or UI is implemented by this checkpoint.

Proposed implementation sequence, pending approval:

- **B1:** typed contracts, trusted snapshot/candidates and reviewed request/quota persistence; PostgreSQL ownership, CSRF, precision, snapshot and concurrency tests.
- **B2:** one approved provider adapter, strict output validation, timeouts and durable spending safeguards; deterministic fake-provider tests with zero paid calls.
- **B3:** accessible Coach page, explicit analysis action, evidence cards and normal navigation to existing workflows; no automatic product changes or paid page-load calls.
- **B4:** real-backend and packaged HTTPS browser verification, accessibility/security review and final documentation; preserve all B6 regression tests.

Provider/model, privacy terms, funding, numerical limits and the proposed new migration/retention policy require approval. B1–B4 are not authorized by Day 7A. Public deployment, live Google verification and CI remain separate release requirements; this plan does not mark the broader Day 7 acceptance criteria complete.

## Release risks and response

The largest risks are analytics edge cases, OAuth configuration, provider access, deployment constraints and too much integration work arriving late. Obtain credentials and hosting access early. Keep visual polish small, use focused work packages and resolve integration failures as they appear.

All approved MVP areas remain release targets. Do not silently drop Google sign-in or coaching and call the full MVP complete. If acceptance criteria cannot be met by November 6, record the gap and request an explicit scope/date decision. Security, ownership checks and correct reporting are release gates.


### Historical Day 7 B1 interrupted checkpoint — recovered below

B1 backend evidence/API and operational persistence foundations are partially implemented and uncommitted. Status is NOT_CONFIGURED; no AI provider calls, generated advice or frontend exist. The owner selected 24-hour replay plus 32-day tombstones. See AI_COACH_DESIGN.md for actual scope and remaining gaps.

Fresh Maven verification compiled successfully and passed 69 unit tests, but all 11 PostgreSQL integration classes failed initialization because Docker Desktop's daemon/socket was unavailable. No migration/database/security integration success is claimed. Finish integration coverage and review, run full verification with Docker, and only then consider the authorized B1 commit/push. B1 is not complete; B2–B4 remain unstarted.


### Day 7 B1 recovered foundation

Implemented the authenticated NOT_CONFIGURED status, bounded evidence preparation and unavailable analysis contracts; Java-only candidate generation; V6 durable request/UTC quota foundations; canonical hashing; safe unavailable-request replay; 24-hour expiry and 32-day tombstones; database-atomic admission/reservations; and bounded cleanup preserving uncertain operations. No provider, paid request, generated recommendation text or Coach frontend exists.

B1 result replay covers safe unavailable outcomes and request identity. Completed AI result storage/replay, provider-priced token reservations, provider configuration and live billing safeguards remain B2 dependencies. Reservations are conservatively retained on abandonment. Operators must reconcile uncertain operations; automatic cleanup never deletes them. See AI_COACH_DESIGN.md for exact implemented endpoints, maintenance and limitations.

Final verification evidence follows the release check. B2–B4 remain unstarted and require separate authorization.


Verified B1 recovery, November 17, 2026: final `./backend/mvnw -B -ntp -f backend/pom.xml -Pspa clean verify` passed **267 tests: 71 unit and 196 PostgreSQL integration**, zero failures/errors/skips. All 248 B6 cases remain; four Coach unit tests and fifteen Coach integration tests were added. Fresh PostgreSQL 17.11 databases applied V1–V6, Flyway validation and Hibernate schema validation passed. Node 24 frontend typecheck, ESLint, 225 tests and production build passed. Browser suites were not rerun for this backend-only work; no new browser verification is claimed. Production JAR inspection found the SPA and no local configuration/test launcher/test fixtures. Credential-pattern, documentation-link and whitespace checks passed. No provider call or generated advice occurred. B1 is complete as a disabled backend foundation; B2 requires separate approval and provider/model/privacy/funding decisions.
