# Architecture decisions

The owner approved the architecture with refinements on October 31, 2026. The decisions describe the intended design. Stage B implements the backend foundation; Stage C adds authentication and profiles. Subsequent decisions record implemented goals, tasks, habits and the dashboard API; React product workflows and optional disabled Coach ranking are implemented; immutable report finalization and public application deployment remain planned, and live Google login is unverified. Detailed rules are in `ARCHITECTURE.md`.

## D01: modular monolith with feature packages

**Accepted.** Use one Spring Boot application with `identity`, `goals`, `tasks`, `habits`, `analytics` and `coach` packages.

This keeps transactions and deployment understandable for a seven-day portfolio MVP. Feature boundaries preserve room for growth without the operational cost of microservices. Services enforce boundaries; a separate service deployment is not needed.

## D02: separate source, single public origin

**Accepted.** Keep React and Spring Boot in separate directories, then package React production assets with Spring Boot.

One HTTPS origin simplifies cookie sessions, CSRF and OAuth redirects. The tradeoff is a coordinated frontend/backend deployment. Vite remains useful for local development.

## D03: server-side sessions and explicit identity linking

**Accepted.** Use Spring Security, BCrypt password hashing, Google authorization code/OpenID Connect login and persistent JDBC sessions.

Sessions avoid browser-managed bearer tokens and require CSRF protection. JDBC persistence adds database writes but avoids introducing Redis. A Google identity is keyed by provider subject. Matching emails never authorize automatic account merging; a collision must be rejected until secure authenticated linking is available.

## D04: minimal task history and immutable closed reports

**Accepted.** Store current task state plus the events required to reconstruct reporting state. Retain closed-week snapshots with counts, cutoff, timezone and calculation version.

Task completion is tasks originally due in the reporting period that are complete at cutoff, divided by all tasks originally due in that period. The original due date is the first assigned due date. Rescheduling cannot change that cohort; reopening before cutoff changes completion state. Later completion cannot rewrite a closed week.

This protects reporting accuracy without a general event-sourcing framework. The tradeoff is explicit cutoff reconstruction and snapshot logic. Archive actions retain obligations; hard deletion and edits to closed reporting periods are outside the MVP.

## D05: strict daily habit consistency

**Accepted.** An eligible day has at least one scheduled habit. It succeeds only if all habits scheduled for that day are complete at cutoff. Consistency is successful eligible days divided by eligible days.

This emphasizes complete routines and is stricter than counting individual checkmarks. Show raw day counts and explain the formula. Rest days are excluded. Effective schedule versions and daily occurrences preserve past obligations; edits take effect no earlier than the next local day.

## D06: explicit weekly goal plans and progress

**Accepted.** Weekly percentages require planned weekly units and explicit recorded progress. Task completion does not implicitly advance a goal. Report goals separately when their units differ.

This adds a weekly planning step but avoids arbitrary percentages. Lock started weekly plans after initial creation, preserve actual over-target units and cap the percentage at 100%. No target means no percentage. Prevent unit changes after progress or planning exists.

## D07: timezone-aware reporting and honest empty states

**Accepted.** Use IANA timezones, local calendar dates and UTC event instants. Weeks start Monday. Lock a started week's reporting timezone and apply profile timezone changes at the next boundary.

The delay avoids redefining an open week halfway through it, at the cost of a pending timezone state. Closed snapshots keep their original timezone. A zero denominator produces a null percentage and an explanation, never a fabricated score.

## D08: AI as a bounded external advisor

**Accepted.** Hide provider calls behind `CoachProvider`. Send minimal stored summaries, enforce persisted quotas, bound tokens/time, validate output and return at most three suggestions.

AI commentary is separate from verified analytics and has no write tools. This limits damage from unreliable output and bounds cost. Failures must not block core planning and reporting. Provider attempts consume reserved quota; pre-dispatch rejections do not. Provider and numerical limits remain open.

## D09: PostgreSQL and Flyway

**Accepted.** Use PostgreSQL with reviewed versioned Flyway migrations, Hibernate validation and PostgreSQL-based integration tests.

This avoids schema drift and database differences hidden by an in-memory test database. Testcontainers adds a Docker requirement. Backups and forward fixes are required before relying on production migrations.

## D10: clear status and restrained visual identity

**Accepted.** Label unimplemented features as planned. Keep the display identity `belsen.`, primary `#FACC15`, background `#FAFAF7`, dark text `#171717` and white surfaces `#FFFFFF`.

Use readable, accessible layouts and clear feedback. Day 1 creates documentation only: no interface, fabricated screenshots, demo links or claims of passing application tests.

## D11: Stage B foundation and configuration

**Accepted in the Stage B approval.** Java 21, Spring Boot 4.1.2 and Maven Wrapper 3.9.12 establish the build. Use Boot-managed dependency versions, including Spring Session JDBC 4.1.1 and Testcontainers 2.0.5. Pin PostgreSQL to `17.11-bookworm` for Compose and integration tests. No OAuth2 dependency or authentication implementation is added before Stage C.

The Compose project is `belsen-ai`, with a project-owned named volume and loopback port 5433. The backend defaults to port 8081. Both ports are configurable and must be rechecked before startup; other projects' infrastructure is untouched.

Flyway owns both identity and session tables. Hibernate `validate` checks real JPA mappings for the identity schema without changing it. The session migration is based on the selected Spring Session PostgreSQL schema. UUIDs are assigned by future application code; no UUID database extension is required.

Only GET `/api/v1/health` is public. Other routes are denied even for an authenticated principal during Stage B. CSRF remains enabled, and generated-user auto-configuration is excluded. Production defaults require Secure, HttpOnly, SameSite=Lax cookies; the explicitly selected local profile permits HTTP cookies on a loopback-bound server. Session inactivity defaults to 30 minutes.

Required database settings are validated; credentials are never hardcoded. MVC/security/configuration tests run with `test`; PostgreSQL integration tests run with `verify` and fail if Docker is unavailable. A partial test run does not establish database readiness.

## D12: controlled Day 2 scope and timezone staging

**Accepted in the Stage B approval.** The new backend-only Day 2 assignment supersedes the original roadmap's Day 2 frontend and browser-flow criteria. CI and frontend work remain outside Stage B. The owner confirmed Day 1 completion.

Stage C will validate and store profile timezones. There are no reporting periods in Stage B. The existing rule delaying reporting-timezone changes until a week boundary remains a later analytics requirement; this foundation does not implement or change it. Account linking remains out of scope.

## D13: Stage C authentication and identity

**Authorized by the Stage C assignment.** JSON registration/login establish a Spring Session JDBC session after explicit context persistence, session-ID rotation and CSRF-token invalidation. Logout invalidates the session and clears its cookie. Responses use DTOs; the session principal stores the local UUID. Profile ownership never comes from the request body. Unknown JSON fields are rejected.

Use delegating BCrypt (default cost 10), a 12-character minimum and a 72-byte UTF-8 maximum for registration passwords. Login errors are generic and missing-password/unknown accounts use a dummy hash comparison. Request DTO string representations redact credentials. Keep CSRF active for every cookie-authenticated mutation and refresh the token after authentication.

Add only the Boot-managed OAuth2 client starter. Google is disabled by default, with validated credentials when enabled. Spring validates provider tokens; local association uses the Google subject. Never merge on email. New accounts use UTC until profile update. At the Stage C checkpoint, callback failures returned safe Problem Details and success redirected to `/api/v1/me`. Day 6 B2 supersedes these browser destinations with fixed relative SPA redirects. Tests use a local signed-token mock provider, not real credentials.

Use a bounded synchronized in-memory throttle (10,000 keys; 10 attempts/email and 50/address per 15 minutes). It limits registration and login, counts successes and rejects new entries at capacity. The tradeoff is reset on restart and no coordination between instances. Trusted reverse-proxy handling must be configured before public hosting; supplied forwarding headers are not treated as authoritative today.

The original 20 foundation cases are retained. Two MVC cases now assert denial on an unimplemented route because login and profile are intentionally implemented. Cookie-jar HTTP tests cover local and production flags; PostgreSQL tests cover session persistence and invalidation. Full verification results are recorded in README/ROADMAP. A real Google browser flow still requires external verification.

## Unresolved decisions

| Decision | Needed by | Constraint |
| --- | --- | --- |
| Google project, consent configuration and credentials | Day 2 | Confirm local and production callback configuration and any testing restrictions |
| Hosting provider, region and domain | Before deployment integration on Day 6 | One HTTPS origin, persistent storage, backups and realistic budget |
| PostgreSQL hosting version and backup retention | Before production migrations | Match local/test major version; verify restore options |
| Actual Groq account, Free-plan/privacy confirmation and live protocol verification | Before enabling Coach | Fixed Groq GPT-OSS model and bounds are implemented; console/account checks remain external |
| Production throttle/proxy tuning | Before public hosting | Stage C implements single-instance limits and a 30-minute session timeout; deployment policy still needs review |
| Password recovery, email verification and public registration policy | Before unrestricted public launch | Not currently in approved MVP scope; document and resolve launch limitations |

The owner retains manual staging, commit and push control. Resolving an open technical choice does not authorize Git publication or work outside the agreed package.

## D14: frozen reporting-week calendar and goal transactions

**Authorized by Day 3 Stage B approval.** Add immutable per-account/Monday reporting-week rows to V3 alongside the three goal tables. First progress/plan writes reserve the current week; future planning reserves a future week without requiring progress. Store timezone and UTC bounds instead of deriving history from the latest profile timezone. Adjacent new intervals are shortened if needed to avoid overlap. An already-closed week never reopens after a timezone change; transient westward-boundary conflicts return `409`. Frozen reporting records are preserved without implementing analytics.

Use positive `NUMERIC(19,4)` values and Bean Validation rejecting excess scale/precision. Void entries rather than deleting them; corrections submit a new UUID after voiding. Lifetime and weekly percentages are separate. Metadata PATCH preserves omissions and clears only nullable fields when explicitly null.

Serialize writes through account-then-goal row locks. This protects calendar initialization, idempotency and archiving. Use `@Version` plus expected versions for goal metadata and future weekly-plan edits. New progress retries return the original record without double counting; conflicting reuse returns `409`. Unique keys and composite ownership foreign keys enforce invariants in PostgreSQL as well as services.

The tradeoffs are serialized writes per account, separate void/replacement actions and bounded paginated lists that still calculate totals per goal. Optimize only after actual workload evidence. No auth behavior or new infrastructure is introduced.

## D15: task state and append-only historical snapshots

**Authorized by Day 3 Stage C.** Keep OPEN/COMPLETED state plus a separate archive timestamp. Require an expected version for every mutation, checking it before idempotent no-op decisions. A matching-version repeat of complete/reopen/archive does not change the version or add history; a stale repeat returns `409`.

Use account-first locking from the goals calendar, then an owned task lock. Validate goal links through GoalService; the shared owner lock coordinates with goal archiving. Same-owner composite foreign keys are the database safeguard. No task operation writes goal progress.

Use a per-task sequence in addition to UTC event time. Store the resulting status, completion/archive timestamps, due dates and reporting contexts; do not create an event-sourcing framework. Metadata updates also leave an UPDATED marker. Task state and event insertion share a transaction. PostgreSQL rejects event mutation, and the application exposes no deletion or event-update API. The tradeoff is intentionally retaining history, not supporting hard deletion in this MVP.

Reuse the existing calendar for first due assignment and event context. Preserve original due date/week after rescheduling or removing the current date. The Calendar `openAt` addition validates the captured event instant without changing goal semantics. Completion/archive and their event use the same instant; a crossed boundary causes a safe retry conflict. No analytics calculations or task-history interface are added.

## D16: consistent goal acceptance time and response snapshots

Goal progress recording, voiding and weekly planning use one post-lock acceptance instant for calendar selection, validation and all mutation timestamps. Finishing persistence after a boundary does not invalidate a mutation accepted before it. Exclusive end boundaries and frozen timezones remain enforced.

Standalone goal get/list reads use read-only REPEATABLE READ with REQUIRES_NEW through the Spring proxy, ensuring lifetime and weekly aggregates share a snapshot even when a writer commits between queries. The tradeoffs are snapshot freshness and a separate connection when an outer transaction is suspended; uncommitted outer writes are not visible. Write responses retain existing account-level serialization rather than raising write isolation. No authentication, task behavior, schema or infrastructure changes are required.


## D17: habit persistence and prospective reporting policy

**Implemented in Day 4 B1.** V5 adds habits, immutable schedule versions, unique habit/date check-ins, append-only check-in events and immutable account timezone revisions. Composite foreign keys bind occurrences to schedules from the same habit and owner. SQL enforces weekday masks, date ranges, event states and positive unique sequences. Reporting weeks also gain UPDATE/DELETE protection without changing V1–V4. No habit routes or business service were implemented in B1; D18 records the B2 workflows.

Begin tracking explicitly inside future habit creation: freeze the current week and store a baseline from the account timezone. Existing frozen weeks always win. Missing post-baseline weeks use effective-Monday policy history, never the latest profile timezone; absent pre-baseline history is unavailable. Day resolution is read-only, uses IANA zone rules and clips local days to week intervals. Reservation uses the same calculation. This supports inactivity gaps without schedulers, generated future occurrences or a separate calendar architecture.

Serialize profile timezone updates with calendar writes using the existing account lock. After tracking begins, reporting changes no earlier than the next Monday. Reject incompatible frozen future reservations with 409 and roll back the profile update rather than overwrite history or silently delay adoption. Equal-effective-date revisions retain sequence-ordered history; backwards reporting-clock changes are rejected. Untracked accounts retain existing goal/task behavior.

Tradeoffs: reporting/profile timezones can temporarily differ; westward changes may leave safe conflict windows; reads load an account's calendar/revision history, and standalone resolver transactions use REPEATABLE READ while joined transactions inherit the caller's isolation. Database superusers can bypass triggers; these guards protect ordinary SQL mutations, not privileged administration. B2 must enforce workflow rules, owner authorization, bounded history queries and cutoff-filtered schedule/event selection, and return effective reporting context. No new dependencies or infrastructure were added.


## D18: versioned habit routines and cutoff-aware history

**Implemented in Day 4 B2.** Use the existing V5 persistence and account-first locking for habit CRUD/actions. Every mutation requires the habit's expected version. Actual changes advance its sequence/version; matching-version same-state requests are no-ops, while stale retries conflict. DELETE accepts exactly one strong quoted If-Match version. No occurrence, event or future reservation is added for a no-op rest cancellation.

Effective dates and acceptance times are independent. Filter schedules by acceptance cutoff, then select the highest sequence whose effective date has arrived. A newer revision supersedes older pending schedules from its effective date onward; immutable rows retain the audit trail. Archive adds a disabled revision tomorrow, preserving today's obligations and dominating older pending versions. Rest planning/cancellation must precede day start; obsolete rest remains visible as inapplicable history. Full-day exclusions are deferred.

Capture one post-lock acceptance instant at PostgreSQL microsecond precision, including initial tracking. Explicit history cutoffs are exclusive and handle sub-microsecond bounds exactly; default history includes equal-timestamp current committed actions. Use cutoff-filtered events instead of current flags, and omit not-yet-started day context in explicit historical responses to avoid showing later timezone intentions. Return current version tokens separately from historical meaning. REPEATABLE READ responses, bounded date ranges and independent pages keep reconstruction consistent without read-side writes.

No schema, dependency, login, OIDC or session changes were needed. At the B2 checkpoint, strict consistency rules were internal and the dashboard API was planned; D19 records its implementation. Frontend and coaching remain planned. Account serialization, bounded per-date queries, future-reservation timezone conflicts and committed-snapshot freshness are deliberate MVP limitations. Existing B1 coverage of deny-by-default was moved from the newly authorized habit POST route to an unsupported maintenance route; its security assertion remains intact.


## D19: coherent dashboard projections without finalization

**Implemented in Day 5 B1.** Add one authenticated, no-store dashboard GET with one exclusive cutoff and one read-only REPEATABLE READ transaction. Dedicated owner-scoped JDBC projections join that transaction through MANDATORY propagation. Do not reuse resource GET services with REQUIRES_NEW or run one habit history request per habit.

Task counts use original due-week membership present in the last event before cutoff. Habit counts group fully completed eligible days after effective-schedule and rest reconstruction; missing occurrences still imply potential obligations. Goal units use cutoff-aware creation/void predicates and existing weekly targets, with current metadata clearly identified. Native sums retain aggregate precision, and percentages remain Java calculations. One week, at most seven day aggregates and bounded stable goal pages keep responses small.

Extend the shared calendar with bounded exact/adjacent/policy lookups rather than a second calendar architecture. Never write during dashboard reads. New-account current previews are explicitly UNFROZEN; unavailable history and calendar gaps do not use fabricated timezones. Default requests exactly at a week opening are valid; explicit cutoffs must be inside the elapsed period.

Closed results remain CLOSED_UNFINALIZED. REPEATABLE READ prevents mixed commits within one response but cannot prevent a later commit accepted before week-end from changing a subsequent report. No finalization table, snapshot write or new migration is authorized here. Immutable finalization and goal-creation writer coordination remain future work. Other tradeoffs are independent snapshots across pages, current metadata labels, and query cost proportional to relevant records. Query-plan evidence did not justify additional indexes or dependencies.


## D20: browser accounts using existing JDBC sessions

**Implemented in Day 6 B2.** Keep the existing same-origin API and in-memory CSRF generation guard. Bootstrap CSRF before session restoration; rotate security state after password/OIDC authentication and cancel/remove private caches across account transitions. Logout clears client state after confirmed success; network failure is not proof of logout. Session reconciliation detects an account changed elsewhere and clears previous-owner caches. No browser credentials, passwords or tokens are stored in localStorage.

Use fixed relative OAuth destinations `/auth/callback` and `/auth?error=google`. Preserve Spring state/nonce/signature verification and reject email collisions. A public Vite build flag reveals the Google link only for configured deployments; secrets remain backend-only. Keep the development proxy Host and registered browser-facing callback consistent. This introduces no CORS, proxy trust, cookie or API authorization relaxation. Real Google verification remains external work.

Native forms and shared fields avoid additional dependencies. Validate Unicode code points and UTF-8 bytes without trimming passwords. Settings edit only existing name/timezone fields; prospective calendar conflicts remain server decisions. No production SPA packaging/fallback or product workflows are authorized in B2. Real PostgreSQL-backed Chromium journeys complement rather than replace mocked component/shell tests.


## D21: production SPA package and explicit transport boundary

**Implemented in Day 6 B6.** Keep one Spring Boot JAR, a small Maven `spa` resource profile and a root verification script rather than add a second public server. Serve only named frontend documents and controlled hashed assets; deny all other paths through the existing security boundary. HTML is no-store, immutable cache headers apply only to content-hashed assets, and default static-file scanning is disabled. Production packaging excludes local configuration and all test code.

Require a production HTTPS origin and secure session settings. Direct TLS is the default; native forwarding is opt-in for a controlled, privately reachable proxy and cannot replace TLS without explicit configuration. Keep existing CSRF, session rotation, ownership and historical metric rules. Pin production Google callbacks to the configured origin and preserve mock-provider versus live-provider evidence. Add CSP/permissions restrictions and suppress sensitive Hibernate SQL diagnostics in production. Use one pinned test-only axe dependency to check actual packaged workflows; do not add a charting/runtime dependency.

The optional runtime image is non-root and digest-pinned. Certificate/secret injection, real ingress, database backups, public registration/recovery policy, live Google, CI automation and provider vulnerability monitoring remain deployment prerequisites. Local self-signed HTTPS and automated accessibility scans are evidence for this package, not public deployment or certification.


## D22: controlled release evidence and operational limits

Day 7 B4 preserves the modular monolith, V1–V7, session/CSRF behavior and fixed free-tier Coach policy. Bound JSON mutation bodies centrally (16 KiB; Coach 4 KiB), including chunked transfer, without adding a dependency. Definite pre-dispatch selection/format failures can prepare a new explicit Coach submission; uncertain dispatch continues to retain the original UUID.

Complement mocked Coach UI tests with real HTTPS/Java/PostgreSQL tests using an external test-only ranking bean, and rehearse logical PostgreSQL backup/restore in a disposable database. These fixtures cannot establish live third-party behavior or production infrastructure recovery.

A controlled initial deployment keeps optional providers disabled until their external reviews pass. Unknown Coach operations retain capacity; safe incident containment disables inference, preserves the ledger and requires separately reviewed forward recovery before re-enabling it. No administrative recovery, CI, license selection or public deployment is implied. See DEPLOYMENT.md and RELEASE_READINESS.md for evidence and launch prerequisites.


## D23: local preparation for a controlled single-VPS preview

Keep three services on the proposed 4 GB x86-64 VPS: existing packaged Java/React image, PostgreSQL 17 and Caddy. Publish ingress only; keep app/database private, use exact-peer native forwarding and DNS-only Cloudflare initially. Non-root/read-only services, bounded RAM/logs and encrypted off-site dumps limit the operational surface. Bootstrap role creation is SQL on an empty volume; migrations V1–V7 remain unchanged. Application and backup credentials are separate and non-superuser, though Flyway still needs app-owned schema privileges.

Registration is disabled by default with explicit local opt-in behavior. Approved individual accounts are prepared offline with the existing validation/BCrypt encoder; no bypass login or public provisioning API is added. Backup scripts stream pg_dump to age and confirm encrypted uploads before marking success. Systemd journaling/freshness checks need a separately tested external alert channel.

All infrastructure, DNS, R2 access, publication and public deployment require separate approval. CX23 stock and the all-in budget are external gates; do not substitute a higher-cost or ARM server silently. See DEPLOYMENT.md for exact configuration, costs and remaining decisions.


## D24: standalone free portfolio first; full-stack hosting remains conditional

Select the dependency-free static showcase on Cloudflare Pages Free as the first public release route. It uses real fictional-fixture screenshots, no authenticated network requests and no application launch link by default. Future links require a verified public HTTPS origin and explicit operator attestation. Keep Render/Neon work isolated as unvalidated experimental configuration; Render Free is not approved for production after the failed 0.1 CPU run and 4.6% memory headroom in the passing 1 CPU run. Do not continue tuning it in this package.

Prepare Oracle Always Free ARM as a separate proposal: one A1 VM with 2 OCPUs and sufficient RAM, the existing Java/React ARM64 image, PostgreSQL 17 and Caddy. Reuse the unchanged Hetzner base through an ARM-specific Compose override, with a separate project/subnet and immutable image identities. Preserve secret files, private app/database ports, exact proxy trust and the normal production profile; do not import the experimental Render settings.

Local ARM build/startup, migrations, authentication/CSRF, backup/restore and restart are evidence of compatibility, not Oracle stock, cloud performance or availability. Oracle card verification, eligibility, idle reclamation, storage, public IP/HTTPS and independent recovery remain explicit gates. No resource creation, account, purchase, DNS change or website deployment is authorized here. The owner separately authorized the reviewed private Git commit and normal push, without changing visibility or history. The optional paid Hetzner route remains unchanged. See FREE_HOSTING.md, ORACLE_ARM.md and FREE_HOSTING_VERIFICATION.md.


## D25: public-source status, static Git delivery and read-only CI

Verified November 27, 2026: GitHub reports PUBLIC visibility at `62d2ad81a385064148b756a9b99d187159b35612`. Preserve the owner-accepted historical workstation-path disclosure and forward-only portable references; do not rewrite history or change visibility. Source visibility and permission to advertise source links are separate: the showcase keeps `BELSEN_SOURCE_PUBLIC` unset pending a separate privacy decision. No application launch destination is approved.

Prepare Cloudflare Pages Free Git integration with Node 24, repository-root build `node showcase/build.mjs` and output `showcase/dist`. Skip dependency installation because the static page uses Node core only. Connecting the repository or saving its first deployment requires separate approval. Git integration can publish future main pushes independently of CI; owner release controls must account for that.

Use minimal read-only GitHub Actions jobs on standard Ubuntu runners: static/React checks and Java 21 verification with real PostgreSQL Testcontainers. Pin action commits, avoid persisted Git credentials, provider secrets, paid runners, build artifact uploads and deployment steps. The focused stdlib publication guard recognizes common secret/dump signatures and generated paths but does not replace historical/manual privacy review or specialist monitoring. No hosted CI pass is claimed until the new workflow actually runs.
