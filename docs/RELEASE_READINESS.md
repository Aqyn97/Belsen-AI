# Day 7 B4 release review

Reviewed November 20, 2026. Starting `main` and `origin/main`: `6f0981841274a3bfaac512afe0c7ee29cb3454ac` (`feat(coach): build AI Coach frontend`). The starting working tree was clean. No existing work was discarded.

**Decision: READY FOR CONTROLLED DEPLOYMENT**, with Google and Groq disabled initially and access restricted to an owner-approved audience. This is readiness of the verified local release candidate, subject to the external deployment prerequisites below. Enabled-Coach continuous availability still requires acceptance or correction of its fail-closed reconciliation limitation. No public deployment or live third-party verification is claimed.

## Findings and corrections

| Severity | Finding | Correction and regression evidence |
| --- | --- | --- |
| Medium, corrected | Only Coach JSON bodies were bounded before decoding. Public authentication and other JSON mutations could consume unbounded input before DTO validation. | Replace `coach/CoachBodyLimit.java` with `common/security/ApiBodyLimit.java:13–18`, wired at `config/SecurityConfig.java:81`. Limit general mutations to 16 KiB and Coach to 4 KiB, including unknown-length/chunked bodies. `identity/AuthenticationIT.java:28` proves oversized registration/login return safe 413 without authenticating. Existing Coach limits remain tested. |
| Medium, corrected | A definite Coach validation/history failure retained a submission key but offered no way to correct the selection and prepare a new request. | `frontend/src/features/coach/CoachPage.tsx:35–61` retains definite rejection state in the account-scoped memory cache and permits a new explicit submission for 400/404/410/413/415. Potential dispatch/transport errors retain same-key recovery. Ten added component cases cover fresh UUIDs, uncertain outcomes and navigation/remount. |
| Low, corrected | Several current documentation labels still called implemented frontend/Coach features planned; README mixed contracts with chronological checkpoint notes. | Concise current README; detailed existing contracts moved to API.md; current architecture/product/roadmap labels corrected. Historical dated checkpoints remain distinguishable from current verification. |
| Low, corrected | Coach preview did not explicitly distinguish current goal names/units from historical progress. | Add a current-metadata disclosure beside weekly goal preview. No historical labels or units are invented. |

No critical or high issue was identified in this review. No migration, dependency version, provider/model policy or authentication lifecycle was changed. Request-size limits bound individual input, not total traffic; ingress limits and monitoring remain necessary.

## Fresh final verification

Java **21.0.11**, Node **24.22.1**, Maven Wrapper **3.9.12**, PostgreSQL **17.11**. The Docker runtime uses Java **21.0.13**. All final suites completed with zero failures, errors or skips.

| Gate | Final result |
| --- | --- |
| `./scripts/build-production.sh` | Passed locked npm installation, typecheck, ESLint, 265 frontend tests, production build and Maven `-Pspa clean verify` |
| Backend unit/security/configuration | **81 passed** |
| PostgreSQL integration | **208 passed**, including fresh V1–V7/Flyway/Hibernate validation, concurrent mutations and disposable backup/restore |
| Total Maven tests | **289 passed**; all 287 B3 tests retained, plus request-limit and restore regressions |
| Frontend component/contract tests | **265 passed**; all 255 B3 tests retained, plus ten recovery regressions |
| Existing mocked Chromium | **8 passed**, desktop/mobile |
| Coach mocked API Chromium | **6 passed**, including explicit submission, disabled and uncertain states |
| Real Spring Boot/PostgreSQL Chromium | **24 passed**, no mocked product APIs |
| Normal production JAR HTTPS Chromium | **8 passed**, real PostgreSQL, production profile/system clock, no Vite |
| Real-backend Coach fixture HTTPS Chromium | **6 passed**, real Java/DB/session ledger with only a test-class ranking provider substituted |
| Total browser cases | **52 passed** |
| Scoped axe checks | No violations on scanned result/empty/disabled/uncertain states and packaged authentication/product/report/settings/dialog workflows |
| npm audits | Full and production-only audits: zero reported vulnerabilities |
| Java dependency advisory check | OSV querybatch: zero matches for **152 resolved runtime/test coordinates**; runtime-only check also returned zero matches for 107 coordinates |
| Runtime image | Built locally as `belsen-ai:b4-review`; digest-pinned Java 21 base and UID/GID 10001 |

The final suites ran sequentially after source edits stopped. Earlier exploratory real-browser runs are not counted as passes: one reloaded during a Vite configuration edit, and an overlapping invocation collided on the backend port/output directory and removed trace files. Trace/process review identified harness interference; the isolated full 24-case run passed without disabling assertions, changing test timeouts or adding retries.

Dependency audits report known advisory records at review time, not proof of absence of vulnerabilities. [OSV querybatch](https://google.github.io/osv.dev/post-v1-querybatch/) received package coordinates only. Docker Scout could not perform its optional base-OS advisory check without Docker login; no OS vulnerability-free claim or scanner success is made. Runtime image/JVM/OS advisory monitoring remains an operator responsibility. No new dependencies were added.

Commands after the combined build, from `frontend/`: `npm run e2e`, `npm run e2e:coach`, `npm run e2e:real`, `npm run e2e:packaged`, `npm run e2e:coach:real`. The last suite loads `org.belsenfixture.CoachBrowserFixtureApplication` from external compiled test classes; it is deliberately **not** the unmodified release launcher and proves neither Groq HTTP compatibility nor live inference. The normal eight-case packaged suite uses the unmodified production JAR. Provider wire/schema behavior is covered separately by local HTTP-stub Java tests.

Focused keyboard checks exercise Tab, Space, Enter, Escape, dialog focus restoration and weekday controls. Desktop/mobile screenshots and narrow-width overflow assertions were reviewed. Reduced-motion CSS and visible focus styles remain. This is scoped Chromium/axe evidence, not complete screen-reader, manual assistive-technology or cross-browser certification.

Repeated packaging of unchanged inputs produced the identical JAR SHA-256 `bdc8f0140e94f10a72d57ed85b3d9676359ce698705bbc4e488129ba7ed3761c`. The local image ID is `sha256:e32ffd7e7b3f17c8532a63152503def28e8235412ac0c3513df555786859733a`. These identify local artifacts, not a published image or deployment.

Credential-pattern inspection of reviewable source/documents found no key/private-key patterns; generated build outputs, environment files, certificates and browser evidence remain ignored. Production JavaScript contains no provider credential/endpoint or fixture marker. V1–V7 and dependency versions are unchanged. Whitespace and local documentation links pass. Temporary browser processes and disposable Belsen containers stopped normally; no existing volume or unrelated infrastructure was modified.

## Functional and technical audit

| Area | Evidence and limits |
| --- | --- |
| First-user accounts | Real registration, login, logout, invalid credentials/password validation, profile edits, session restoration/expiry and deep links. Backend tests check normalized concurrent registration, safe unique conflicts, BCrypt, session rotation and JDBC security-context persistence. |
| Goals | Real creation/editing, exact decimal units, weekly plans, idempotent/lost-response retries, auditable voids and optimistic conflicts. SQL ownership/precision constraints and archive-versus-progress coordination remain tested. |
| Tasks | Real create/complete/reopen/reschedule/archive, filtering and versions. Immutable ordered events preserve the original due-week cohort; archive/reschedule cannot remove obligations from reports. |
| Habits | Real schedules, completion/undo, prospective rest/cancellation, future revisions, frozen timezone context, history, stale conflicts and retained archived obligations. Browser tests exercise exact strong If-Match and draft preservation. |
| Analytics | Real weekly and historical reports, exact uncapped units, absent-target/no-data states and DST bounds. Java owns calculations; owner-scoped projections share one exclusive cutoff and read-only REPEATABLE READ transaction. Closed periods remain CLOSED_UNFINALIZED; separate pages use separate snapshots. |
| Security | Deny-by-default private routes, server-derived owners, foreign-resource 404, CSRF on mutations, session rotation, safe Problem Details and no sensitive response diagnostics. Production Secure/HttpOnly/Lax cookies, HTTPS, CSP/HSTS/frame/nosniff headers, default ignored forwarding headers and explicit SPA/asset routes remain intact. |
| PostgreSQL | Fresh PostgreSQL 17.11 databases apply/validate V1–V7, with Hibernate validate and Spring Session initialization disabled. Existing foreign keys, unique constraints, immutable-history triggers, rollback, concurrent writes and snapshot tests remain. No H2 substitution. |
| Packaging | Node 24 locked build and Java 21 Maven wrapper; one production JAR and non-root digest-pinned runtime image. The JAR contains the SPA and seven migrations, with no local HTTP config, fixture classes, test launchers, fixture assets or source maps. |

### AI Coach boundary

Java prepares verified metrics and eligible candidate IDs, explanations and destinations. Groq can rank only supplied IDs; malformed, duplicate or unknown IDs are rejected. A separate request whitelist excludes private titles, units, notes, owner/resource UUIDs and credentials. There is no AI write path into product data.

The adapter fixes the approved HTTPS endpoint and `openai/gpt-oss-20b`, strict JSON schema, no redirects, 3-second connection and 15-second total bounds, 12 KiB input, 64 KiB response envelope, 2 KiB decoded selection and 512 generated tokens. It parses only final assistant content. Keys remain backend-only; logs/errors do not include provider payloads or credentials. No automatic retry, provider fallback, paid upgrade or inference on page load is implemented.

PostgreSQL admission and owner-scoped UUID/fingerprint uniqueness coordinate duplicates and quotas. Tests cover concurrency, rollback, three free attempts/account/UTC day, global/account capacity, completed original-evidence replay, separate-JVM replay, 24-hour replay expiry and at least 32-day tombstones. Uncertain operations retain capacity and are never cleaned up or redispatched automatically.

The added enabled-Coach browser suite uses the production-profile HTTPS JAR plus an **external test-only ranking bean**, actual Java services and PostgreSQL. One candidate produces deterministic ranking; two select an explicit timeout fixture; empty evidence produces rules-only output. It verifies submission, persisted replay, CSRF, unauthenticated isolation and uncertain-ledger recovery. This is not the unmodified-JAR provider adapter or live Groq inference. The separate packaged suite runs the ordinary production JAR with Coach disabled; adapter protocol tests use local HTTP stubs.

## Final verification

Final serial verification results will be recorded here after all gates finish. Build and browser logs are local temporary evidence, not committed artifacts.

## Data safety and operations

DatabaseRestoreIT creates a user, goal, task/event, reporting context and JDBC session, uses PostgreSQL pg_dump custom format, and restores into a second disposable database. It compares representative row counts, all public constraint/trigger counts and V1–V7 history, and proves the restored task-event UPDATE guard still rejects mutation. It touches no existing database or persistent Compose volume.

The deployment runbook now covers encrypted off-host backups, restore to a new isolated database, session invalidation before access, preserving Coach ledgers, provider reconciliation after stale-backup recovery, compatible artifact rollback and separately approved data cutover. RPO/RTO are proposed objectives, not verified infrastructure guarantees. Actual provider PITR, role privileges, backup storage, renewal and timed recovery still require a rehearsal on the chosen host.

Unknown Coach outcomes deliberately consume capacity indefinitely; two can stop inference globally. There is no supported administrative state-transition/reconciliation command in this release. The safe response is disable Coach, preserve evidence/tombstones and review provider records. Do not delete rows, bypass guards or infer that timeout means no dispatch. Resuming inference after unknown outcomes needs separately reviewed forward recovery tooling/migration. This is a known availability limitation; the recommended initial controlled deployment keeps Coach disabled. Continuous enabled-Coach availability is not established.

## Accessibility and visual review

Chromium coverage includes desktop and mobile, axe scans of packaged account/product/reporting/settings pages and dialogs, Coach result/disabled/error/rules-only states, keyboard Tab/Space/Escape and focus behavior, 320/375/768-pixel overflow checks and preserved reduced-motion styles. Desktop/mobile application captures were visually reviewed for text wrapping, hierarchy, yellow contrast and navigation.

This is focused evidence, not screen-reader certification, complete WCAG compliance or multi-browser coverage. Mocked Coach screenshots use synthetic evidence (including synthetic replay dates); real Java fixture screenshots also use deterministic ranking. Neither represents live-provider output.

## Portfolio evidence

Keep screenshots of the genuine application using synthetic accounts only. Review names/emails, dates, UUIDs, browser chrome and private data before publishing. The current captures remain ignored verification output; no screenshot is inserted into the README as a live demo.

| Screen | Capture plan and caption |
| --- | --- |
| Landing/authentication | Desktop landing and mobile sign-in/validation; local application, synthetic account |
| Dashboard | Week/timezone, exact counts and goal units; real backend, seeded demonstration data |
| Goals | Measurable target, weekly plan and void history; real backend, synthetic data |
| Tasks | Original/current due dates and completion; real backend, synthetic data |
| Habits | Checklist, future rest and immutable history; real backend, deterministic reporting clock where used |
| Analytics | Closed-unfinalized DST week and missing-target state; real backend, seeded history |
| AI Coach | Evidence preview, result and unavailable/uncertain states; label **deterministic provider fixture — no live Groq inference** |

The repository has no committed GitHub Actions workflow or project license file. CI and license selection remain owner decisions; no license was selected or visibility changed. Both connected GitHub metadata lookup and unauthenticated REST lookup returned 404, so description/homepage/visibility could not be verified. Git transport can read origin/main. Suggested description: “Personal growth app with a Java/Spring Boot backend, PostgreSQL history, trustworthy analytics and optional evidence-based AI ranking.” Leave homepage unset until a real deployment exists.

From a backend reviewer’s perspective, the strongest evidence is database integrity, concurrency/idempotency, session security and cutoff-aware reporting. From a product perspective, the implemented planning/reporting journey is coherent, while external providers fail safely. For a Junior Java Backend portfolio, the README now leads with those concrete capabilities and separates implementation from unverified services. Future work should prioritize CI, recovery/verification policy and operational rehearsal before unrelated features.

## Deployment recommendation and external prerequisites

Use one app instance plus durable PostgreSQL 17 in one region, under one HTTPS origin. A small managed container service and managed database reduce operational work; a VPS transfers TLS, patches and backups to the operator. Resource sizing is an estimate requiring measurement, not a throughput claim. Free/trial services are appropriate only when their expiry, sleep, persistence and backup limits are acceptable. Current official hosting references and requirements are in [DEPLOYMENT.md](DEPLOYMENT.md#controlled-deployment-plan).

Before any public deployment, separately approve the host/region/budget, domain and restricted audience. Rehearse restore/rollback with the intended non-superuser role; configure database TLS, secrets, ingress stripping/trust, certificate renewal, limits and monitoring. Validate real-ingress cookie/CSRF/session/deep-link behavior. Unrestricted registration needs an explicit abuse/email-verification/password-recovery policy.

Google remains disabled until consent/client/callback configuration and a real provider login are verified. Groq remains disabled until actual Free-plan/no-paid-billing/privacy settings are reviewed and a separately authorized synthetic live check succeeds. Application acknowledgements cannot verify provider billing or retention. No real Google, live Groq, paid infrastructure or public deployment was performed.

## Changed-file scope

The release contains **23 files**: five backend paths (the relocated body limiter, security wiring, authentication regression, restore integration test and external provider fixture); seven frontend paths (Coach page/tests, browser harness, package script, TypeScript inclusion, real-Coach config and browser cases); and eleven environment/documentation paths. The latter are `.env.example`, AGENTS.md, README.md, API.md, AI_COACH_DESIGN.md, ARCHITECTURE.md, DECISIONS.md, DEPLOYMENT.md, PRODUCT_VISION.md, RELEASE_READINESS.md and ROADMAP.md. Existing migrations, dependency versions, Dockerfile and product features outside B4 are unchanged.
