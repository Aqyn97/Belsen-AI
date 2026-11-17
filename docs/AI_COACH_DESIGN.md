# AI Coach: Day 7A design and implementation plan

Status: **Day 7A design preserved; B1 backend foundation implemented, final verification below; no paid use authorized**. Reviewed November 17, 2026 against clean `main` at `713640d0cb07a8b1e0f64d872f919c63843f5f30`. Day 7A changes documentation only. No provider credentials, dependencies, migrations, application code or deployment are added. B1–B4 each require authorization.

## 1. Repository findings and reusable components

| Actual component | Reuse and boundary |
| --- | --- |
| `analytics/DashboardService.java` | One public `dashboard(owner, week, cutoff, page, size)` call through the Spring proxy; read-only REPEATABLE READ, REQUIRES_NEW. Captures one clock instant and resolves the calendar. |
| `analytics/DashboardReader.java` | MANDATORY transaction, owner-bound JDBC projections. Task cohorts, habit days and goal totals already share the dashboard snapshot. Do not copy its SQL into Coach. |
| `analytics/DashboardDtos.java`, `DashboardRules.java` | Exact counts, BigDecimal values, explicit reasons, cutoff and calendar/report status. Monday years 1900–9998; page size up to 100. |
| `goals/GoalService.java`, `ReportingCalendar.java` | Explicit units, void corrections, frozen weeks and prospective timezone policy. Their resource views are not extra Coach data sources: separate read transactions would break snapshot consistency. |
| `tasks/TaskService.java` | Expected-version operations, original cohort retention and ordered immutable events. Current task lists are not historical evidence. |
| `habits/HabitService.java`, `HabitRules.java` | Immutable schedules/rest/check-in history and all-obligations daily consistency. Dashboard already reuses these rules; Coach does not reconstruct habits again. |
| `identity/security/SessionLogin.java`, `config/SecurityConfig.java` | Principal is the account UUID; explicit JDBC security-context save, session/CSRF rotation, deny-by-default routes and protected mutations. |
| `common/error/ProblemResponses.java`, `ApiExceptionHandler.java` | Safe Problem Details and request identifiers. Existing generic 429 handling always sets 900 seconds: Coach needs a scoped handler with its actual retry delay, without changing login behavior. |
| `frontend/src/shared/api/client.ts` | Relative same-origin requests, CSRF bootstrap, no mutation retries, session generation guards and cancellation. |
| `frontend/src/features/analytics/` | Period validation, exact numeric decoding, honest no-data/status rendering; reuse contracts without recomputing metrics in React. |
| `frontend/src/app/App.tsx`, shared UI/styles | Session gate, owner-scoped private cache clearing, responsive shell and accessible controls. Six current workspace sections; no Coach route exists. |
| `web/SpaController.java`, production configuration | Explicit document route allowlist, hashed assets, HTTPS and secure cookies. A future Coach route needs both SPA and security allowlist updates. No wildcard fallback. |

V1–V5 are the only migrations. V1 owns identity; V2 owns JDBC sessions; V3 goals, progress, plans and reporting weeks; V4 tasks/events; V5 habits and immutable calendar/history protection. Coach request/quota tables and immutable finalized reports do not exist. Preserve all five applied migrations and Hibernate validation.

The backend uses Java 21 / Spring Boot 4.1.2 with Boot-managed dependencies. React 19.3.1, TypeScript 6.0.3, Vite 8.3.8, React Query and lossless-json are already installed/pinned; Node 24 is required. No AI SDK/framework exists or is needed for the proposed single HTTP adapter. There is no `.github` CI workflow. Build/test scripts and PostgreSQL/browser harnesses exist; do not describe local scripts as CI.

Recorded B6 evidence is 248 backend tests, 225 frontend tests, 8 mocked Chromium, 24 real-backend Chromium and 6 packaged HTTPS Chromium tests with scoped axe checks. These are the existing checkpoint, **not fresh Day 7A test results**. Production packaging excludes local configuration and test launchers. Public deployment and live Google login remain unverified.

Older documentation contains historical checkpoint statements such as “analytics not implemented” and the original planned snapshots table. The later Day 5/B6 sections and actual code establish today's behavior. This design does not treat earlier target architecture as implemented functionality or rewrite unrelated historical documentation.

## 2. Product: a short decision aid, not a chatbot

The question is: **“What should I focus on next, based on my actual progress?”**

A signed-in user selects a reporting week, optionally an exact historical cutoff, reads what will be shared and clicks **Analyze my progress**. Return the verified period summary plus zero to three ordered recommendation cards. No chat box, conversation memory, background analysis, paid page-load calls, predictions, combined score or write tools.

Recommended MVP: **AI-assisted prioritization of server-defined, evidence-backed actions**. Java builds eligible candidates; a model chooses and orders candidate IDs. Java renders the factual explanation and practical action from reviewed templates and trusted values. Do not display arbitrary generated narrative. This deliberately limits expressive personalization but makes unsupported numbers, invented intentions and psychological assertions impossible in the displayed contract. Explain this clearly: “AI helps prioritize these suggestions. Metrics and explanations come from your recorded data.” If this constrained approach is not approved, free-form advice requires a separate safety design; JSON structure alone does not establish truth.

The summary always comes from Java, not the model. A recommendation has a concise title, explanation, next action, evidence references and an explicit limitation where applicable. Its priority is an AI suggestion, not a measured probability or verified optimum. There is no assertion that one metric or unit is comparable with another.

Candidate examples (conditions are exact, not invented performance thresholds):

| Candidate | Eligibility | Practical action and limitation |
| --- | --- | --- |
| Review remaining task commitments | `tasks.incomplete > 0` | Open Tasks and review current commitments. Evidence shows completed/scheduled in the original cohort; do not call all remaining items overdue or identify a particular task. |
| Review routine feasibility | `habits.eligibleDays > habits.successfulDays` | Review Habits and consider a manageable future schedule. Do not erase elapsed obligations, propose retroactive rest or label the user inconsistent. Today's incomplete routine may still be in progress. |
| Make goal progress measurable | Included goal has `plannedUnits == null` | Review Goals and choose a suitable weekly target through the normal workflow. Show recorded units and “No weekly target”; never infer zero achievement. |
| Choose a next goal step | Included goal has a positive target and units below it | Review that goal and choose a feasible next action. Show actual units/target; do not prescribe a fabricated daily pace, hours available or deadline. |
| Review the next plan | Included goal meets/exceeds its target | Review the next open planning period; do not automatically increase targets or hide over-target units. |

Historical reports describe that period and link to current planning pages; they never invite editing a closed week. Archived goal metadata is not exposed by the dashboard DTO, so suggestions say “review” rather than promise the goal is editable. No inferred trends, causes, streaks, motivation, health diagnosis or “improvement since last week”: only one period is analyzed.

Empty report: return `INSUFFICIENT_DATA`, verified null percentages and deterministic setup guidance, with **zero provider calls**. An old week with no evidence says “No data in this period,” not “You have no goals.” If there are no eligible candidates, return `NO_ACTION_NEEDED` with an honest summary and no call. Core pages remain fully usable when Coach is disabled or unavailable.

## 3. Trusted context and historical semantics

Call `DashboardService.dashboard(owner, requestedWeek, requestedCutoff, 0, 20)` exactly once. Coach orchestration has **no surrounding database transaction**. The dashboard proxy opens and closes its own snapshot; copy its immutable DTO to trusted context, then release the connection before any provider call. Never call GoalService views, TaskService lists or habit-history endpoints to enrich this result. No read creates weeks, occurrences or progress.

The first 20 relevant goals follow the existing stable creation/UUID ordering, not “most important goals.” Return coverage `{includedGoals, totalGoals, truncated}`. If more than 20 exist, prominently label partial goal coverage. Task/habit aggregates remain account-wide for the period. Do not fetch more pages, silently truncate without disclosure, or claim a global best goal. At most 20 goal candidates plus two aggregate candidates enter ranking; this bounds provider input. A future broader selection is separate work.

Keep the exact dashboard period, counts and decimals in the public response. Do not narrow aggregate NUMERIC to entry precision, round units or combine different goal units. The provider receives opaque request-local IDs such as `g1`, server-owned action enums, numerical evidence and limitations. Do not send database UUIDs, titles, unit labels, descriptions, habit names, rest reasons, profile fields or raw events. Local UI may show the dashboard's current goal title/unit as escaped text, labelled current metadata. Numeric values remain exact strings in the provider payload.

Preserve these existing meanings:

- Tasks count the original due-date cohort and last event strictly before cutoff. Archive/reschedule/due-date removal do not erase obligations; reopened tasks may be incomplete. Future due dates within the selected week are included. The aggregate cannot identify which task needs work, distinguish every overdue item or explain why work was missed.
- Habits count successful eligible days, not checkmarks. All remaining scheduled habits must be complete; habit-specific rest removes only its obligation. Unstarted days are excluded; today is provisional. Schedule, archive and rest history are cutoff-aware. The DTO does not name a missed habit/day, so Coach cannot either.
- Weekly goals use explicit entries, excluding voids effective before cutoff; a void at cutoff remains outside the exclusive boundary. Missing targets mean null percentage, with actual units preserved. No lifetime totals or deadlines are available in this DTO.
- Keep frozen/reconstructed/unfrozen calendar status, timezone and actual UTC boundaries, including DST and shortened intervals. Do not use the device timezone or recompute using the current profile.
- Report status remains `PROVISIONAL` or `CLOSED_UNFINALIZED`, based on week closure, even for an earlier explicit cutoff. Never invent `FINALIZED`. Late commits may change a later reconstruction. Current titles and historically unversioned metadata are not as-of facts.

Store `preparedAt` separately from the reporting cutoff. One database snapshot means coherent committed data, not that all writes logically accepted before the cutoff have committed. A replay uses its original response and period; it never silently recomputes using a new snapshot. A new explicit analysis is needed for refreshed evidence.

## 4. Proposed backend contract and flow

Feature package: `com.belsenai.coach`. Narrow responsibilities: controller/DTOs, nontransactional orchestration, context/candidate preparation, provider boundary and validation, short request/quota transactions. No agent framework, vector store, embeddings, Redis, queue, streaming or background worker.

### Endpoints

| Endpoint | Contract |
| --- | --- |
| `GET /api/v1/coach/status` | Authenticated, no-store; enabled/disabled, public provider label, privacy notice version and public limits. No keys, endpoint URLs or diagnostics. No provider call, reservation or analytics writes. |
| `POST /api/v1/coach/analyses` | Authenticated and CSRF-protected; bounded JSON with `submissionId` UUID, optional `weekStart`, optional `cutoff`, required accepted `noticeVersion`. Reject unknown properties, owner IDs, model/provider overrides, prompts and supplied metric values. |

POST is appropriate because analysis can incur cost and reserve usage, although it cannot mutate product records. The owner comes only from Authentication. Use an exact method/path matcher, not a broad public Coach matcher. Default body limit 4 KiB, enforced while reading even for chunked requests. Reuse dashboard date/cutoff validation; `cutoff` requires `weekStart`. No version token or If-Match is needed: no goal/task/habit mutation occurs.

First successful response and successful replay: `200`, `Cache-Control: no-store`. Response fields:

```text
schemaVersion: 1
submissionId: UUID
status: READY | INSUFFICIENT_DATA | NO_ACTION_NEEDED
preparedAt: ISO instant
period: unchanged DashboardDtos.Period
verifiedMetrics: { tasks, habits, goals } // original exact values, page metadata
coverage: { includedGoals, totalGoals, truncated }
summary: reviewed server-rendered text
recommendations: [ // 0..3, ordered
  { id, priority, title, explanation, nextAction, evidenceIds,
    limitation, destination: GOALS | TASKS | HABITS | ANALYTICS }
]
evidence: server-built typed entries referencing the trusted snapshot
analysis: { source: AI_PRIORITIZED | RULES_ONLY, modelLabel: nullable,
            promptVersion, expiresAt }
limitations: server-owned codes/text
```

This is a proposed schema, not fabricated production output. URLs are never model-generated: the frontend maps destination enums to existing pages. A goal evidence ID resolves only within this response's owned snapshot. No invented per-goal deep-link route. The verified summary may exist even when the model fails; keep the last verified preview visible but do not label it a new successful analysis.

### Error mapping

Use RFC 9457 with stable Coach-specific `code`, safe detail and requestId; never echo provider content, database diagnostics, prompt, URL or credentials. Add a scoped Coach exception handler so the existing authentication 429 policy is unchanged.

| Status | Cases and UI behavior |
| --- | --- |
| 400 / 413 / 415 | Invalid dates/cutoff/UUID/notice version or unsupported fields, oversized body, wrong media type; no dispatch |
| 401 / 403 | Session unavailable / CSRF denied; existing client expiry and token behavior |
| 404 / 409 | Unavailable historical calendar / timezone gap; forward safe dashboard semantics |
| 409 | Conflicting UUID reuse; identical pending request returns `ANALYSIS_IN_PROGRESS`; uncertain prior dispatch returns `ANALYSIS_OUTCOME_UNKNOWN` |
| 410 | Same key after cached response expiry while its retained tombstone exists; request a new explicit analysis |
| 429 | Per-account or global admission/quota/budget limit; accurate `Retry-After` for the relevant UTC quota/cooldown |
| 502 | Invalid, refused, truncated or unsupported provider result; discard the entire output |
| 503 / 504 | Disabled/unconfigured/unavailable provider or bounded capacity / provider deadline exceeded |

This proposal refines the older planned architecture flow: prepare evidence before reserving paid quota so empty reports cost nothing, and distinguish a provider deadline as 504 rather than the earlier generic 503. Neither changes an implemented Coach contract; approval is required. No automatic provider retry, automatic paid failover or automatic “repair the JSON” call. A failed analysis is not relabelled as AI success with fabricated fallback. Manual retry of the same submission retrieves the existing outcome; a fresh paid attempt needs a new explicit click and UUID.

## 5. Duplicate requests, durable limits and cancellation

Recommended new migration, **only after B1 approval**: `V6__create_coach_request_tables.sql`. Existing D08 already calls for persisted quotas. Two small tables suffice: `coach_requests` and `coach_daily_usage`; no conversation/history product.

`coach_requests`: owner FK, UUID submission key with unique `(owner_id, submission_id)`, canonical request hash (including null/default selection and notice version), status, preparation/dispatch/deadline/completion instants, reserved cost, bounded sanitized result or safe error code, response expiry and metadata retention time. Index owner/key and expiry. No raw prompt/provider body or credential storage. Validate status/cost/timestamp invariants in SQL. Hashing is for equality, not anonymization.

`coach_daily_usage`: UTC date plus quota scope (global or owned account), counters and reserved USD amount as NUMERIC. A checked scope representation and unique key must enforce one global row and one row per account/day; no nullable-key uniqueness loophole. This state is operational spending control, never a second analytics store. Retain 32 days of minimal metadata; response JSON expires after 24 hours. Lazy bounded cleanup on admission plus a documented operator cleanup command handles idle installations; expiry denies reads immediately but physical deletion is not promised at exactly 24 hours without operator cleanup. Backup retention is separate.

Admission order and crash policy:

1. Authenticate, validate CSRF/body/selection, configuration and privacy notice. Acquire a bounded application permit before expensive work (two analyses globally, one per owner). Reject overflow, do not queue unbounded work or grow a user-key map without a cap.
2. Claim the unique owner/submission row in a short transaction. Identical completed requests replay, conflicting bodies return 409, existing pending requests do not dispatch. Inspect duplicates before rejecting for an already-used account permit where practical; they must never create a second provider call.
3. Prepare one dashboard snapshot and candidates outside the ledger transaction. No-data/no-candidate outcomes persist as rules-only results without charging paid quota. Cheap request throttling still applies to prevent database abuse: propose 10 new analyses/account/minute and 100 global/minute, including rules-only results. Use bounded in-memory admission state (maximum 1,000 active account keys, reject new keys at capacity, expire idle entries); PostgreSQL remains authoritative for paid limits. Cap retained request rows at 100,000 and reject new admissions at capacity after bounded cleanup, never evict live idempotency records. The row-cap check and claim must serialize through the same short global admission lock. These operational limits require B1 tests and approval.
4. Immediately before dispatch, reserve the maximum request cost and request count atomically: lock global daily usage then owner daily usage, then the owned request row, in the same order on every path. Never hold these locks during analytics or HTTP. Check all caps before increment; rollback all reservations on known pre-dispatch rejection.
5. Commit a `DISPATCHED` marker **before** making the one outbound call. Dispatched attempts consume quota even on timeout, refusal or uncertain network failure. Do not refund uncertain attempts or redispatch a marked request after restart. A crash between marker and actual send can conservatively consume unused quota; this is safer than a possible duplicate charge.
6. Validate and persist the bounded response in a short transaction. Timeout or crash leaves a terminal failure/unknown outcome; later requests can recognize stale pending deadlines without a worker. Unknown dispatched outcomes keep their worst-case reservation. Release process permits in finally; network deadline also bounds orphaned work.

Cache lookup and all transitions are owner-scoped. Never deduplicate across accounts or by shared metric hashes. Idempotency is guaranteed during the 32-day metadata retention window; a 24-hour cached-result window is not permanent idempotency. After tombstone deletion, old keys may be new work: clients generate a new UUID for every deliberate fresh attempt and never queue offline replay. This limitation must be public in the contract. An expired cached response returns 410, not a new billable call.

For cancellation, React uses AbortSignal, clears late results on logout/account/selection changes, and explains that stopping the browser wait may not stop provider processing or charges. The bounded synchronous MVP does not promise reliable servlet disconnect detection. Server interruption/timeout cancels the outbound future where possible; never release the global concurrency permit while its local HTTP operation remains active. No dedicated cancellation endpoint or provider job is proposed.

## 6. Provider choice, transport and cost

Recommendation: implement a small `CoachProvider` Java interface and **one** hosted adapter using Java 21 HttpClient and existing JSON support. Start with an OpenAI structured-output adapter **subject to explicit approval of a supported low-cost model, account billing and privacy terms**. Build B1 with deterministic test fixtures first. OpenAI is a proposal, not selected/enabled configuration. No Spring AI/LangChain dependency, provider-switching framework or self-hosted inference service is justified for one constrained ranking request.

| Option | Cost/complexity/reliability tradeoff | Privacy and output considerations |
| --- | --- | --- |
| OpenAI API | Token-based cost; one HTTPS adapter; structured-output support and explicit refusal/incomplete handling. Small model is sufficient only if fixture evaluation passes. | Set `store:false`; no conversation/tools. This does not itself mean zero retention. Review the selected account/endpoint's abuse retention and region terms. |
| Anthropic Claude API | Same narrow HTTP boundary, model-dependent token pricing; schema support must be checked for the chosen model. A larger model is not automatically useful for this task. | Structured outputs supported on documented models; retention eligibility has conditions. Independently review account terms. |
| Google Gemini API | Token-priced paid tiers and model-specific quotas; structured output supports a JSON Schema subset. | Published pricing distinguishes free-tier product-improvement use from paid-tier treatment. Do not send personal activity to a free tier merely to avoid billing. |
| Local model | No hosted per-call bill, but memory/hardware, serving, updates and quality testing are additional operational costs. | Data can stay local, but adds infrastructure outside this focused MVP; not recommended now. |
| No provider | No provider spend or data transfer; deterministic summary/setup guidance remains possible. | Must be labelled rules-only or unavailable, never presented as working AI. |

Official references inspected for this design (terms/prices must be rechecked when a model is approved): [OpenAI structured outputs](https://developers.openai.com/api/docs/guides/structured-outputs), [OpenAI data controls](https://developers.openai.com/api/docs/guides/your-data), [OpenAI pricing](https://developers.openai.com/api/docs/pricing), [Claude structured outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs), [Claude retention](https://platform.claude.com/docs/en/build-with-claude/api-and-data-retention), [Claude pricing](https://platform.claude.com/docs/en/about-claude/pricing), [Gemini structured outputs](https://ai.google.dev/gemini-api/docs/structured-output), [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing). Structured output constrains syntax, not recommendation quality; the local validator remains mandatory. A ChatGPT subscription is not assumed to provide API entitlement or spending authorization.

Proposed pilot limits, all pending approval:

| Guard | Proposed value |
| --- | --- |
| Feature default | Disabled; no credential requirement while disabled |
| Account paid attempts | 3 per UTC day, at least 60 seconds between new dispatches |
| Global paid attempts | 100 per UTC day |
| Global reserved spend | USD 1 per UTC day and USD 10 per UTC calendar month; deny when either would be exceeded |
| Concurrency | Two HTTP analyses globally, one per account, zero waiting queue |
| Input | At most 20 goals, 22 candidates; serialized provider data at most 12 KiB, complete input budget at most 4,000 tokens including instructions/schema |
| Output | Maximum 512 billable output tokens including any reasoning; at most three IDs; response envelope at most 64 KiB, decoded selection at most 2 KiB |
| Deadlines | Connect 3 seconds, provider total 15 seconds, application analysis total 20 seconds; database statement/lock waits and HTTP timeout clipped to the remaining total deadline |
| Attempt policy | One provider call; no retries/failover/tools/streaming |

The daily ledger also enforces the monthly cap by summing locked global daily reservations for that month while serializing global admissions through a single fixed PostgreSQL advisory transaction lock. The lock key is server-owned and used consistently across month/day rollover; no long-held lock during HTTP. Alternatively a dedicated monthly budget row is acceptable if it makes B1 clearer, but do not implement an unlocked SUM/check/increment race. Monetary controls use USD decimal arithmetic, never floating-point.

Provider configuration is backend-only: proposed `BELSEN_COACH_ENABLED`, `BELSEN_COACH_PROVIDER`, `BELSEN_COACH_MODEL`, `BELSEN_COACH_API_KEY`, quota/budget settings and approved pricing configuration. Do not add them to environment files until implementation is authorized. Enabled mode must fail configuration validation on absent key/model/budgets or unsupported output/token limits. Disabled mode must not break ordinary app startup. No VITE credential or provider call. The production adapter fixes its HTTPS hostname/path, disallows redirects and does not accept browser endpoint overrides. A loopback fake transport belongs only in test configuration, absent from the JAR.

Reserve `inputLimit × approvedInputPrice + outputLimit × approvedOutputPrice`, divided by one million for per-million prices, including any additional billable categories. Reserve conservatively when usage is unknown. A 12 KiB body alone is not proof of a 4,000-token limit: B2 must establish an exact supported tokenizer or a documented conservative upper bound including schema overhead; reject excess rather than silently shorten evidence. Reject a model with unbounded hidden reasoning charges. Keep maximum reservations charged for the day/month for simplicity; actual usage is informational, not a reason to refund uncertain work. Prices must be pinned/reviewed before enabling and changed when provider rates change. App caps bound approved-price reservations, not a guaranteed invoice under unannounced pricing changes; use a dedicated provider project/key and available provider-side limits too. No automatic top-up.

## 7. Privacy, prompt injection and output validation

System instructions are static, versioned and separate from serialized evidence. There is no user prompt field. All source strings, including units, are untrusted; use a whitelist mapper that omits them from outbound data, rather than regex-sanitizing malicious instructions. Use request-local aliases; retain the alias-to-owned-evidence mapping only within the response context.

Provider result schema: an object containing only an ordered list of up to three unique candidate IDs from this request. Reject additional fields, unknown IDs, duplicate IDs, invalid types, refusals, partial responses, excessive nesting/bytes and incomplete finish states. Java validates eligibility and builds the full cards; no provider numbers, evidence references, URLs or narratives are accepted. An empty valid list is allowed and shown as no additional suggestion, not a fabricated failure. Do not log rejected bodies.

No provider tools, database access, browsing, retrieval, code execution, memory or function calls. Even a malicious selection can only reorder actions already deemed safe. Prompt injection can still affect ranking if a future change sends free text; that would require new review. Tests must assert the outbound payload excludes injected source fields, not merely that a fake model happened to ignore them.

Do not send email, display name, account/resource UUID, IP, credentials, cookies, session/CSRF tokens, goal descriptions, private reasons or raw history. Aggregate activity is still personal information, not anonymous data. The page must disclose the provider and payload categories before analysis, with an unchecked acknowledgement tied to a versioned notice. Selection of a provider is not user consent. No durable profiling or cross-user memory.

Logs include only server requestId, outcome enum, duration bucket, model identifier, token totals and reserved cost. No request/response bodies, headers, aliases tied to account data, provider exception text or account IDs in routine logs/metric labels. Owner references exist only in the restricted database for isolation/quota enforcement. Use low-cardinality operational counters without adding a public telemetry endpoint or new monitoring infrastructure. The private cached response is personal data: access control, retention and backup policy apply.

## 8. Frontend experience

Proposed route `/app/coach`, named **AI Coach** in the existing navigation. Add exact GET/HEAD document entries to SpaController and SecurityConfig in B3, preserving every forbidden-path test. API calls remain same-origin through the existing client; CSP needs no external connect/script allowance.

Page order: title/introduction; period controls; compact sharing notice and provider status; Analyze button; live loading status; verified summary; ordered cards; coverage/time/metadata limitations. Use the existing analytics date validation and returned reporting context. GET status/dashboard previews may run on page load, but must never dispatch a provider call. Freeze the selected input when submitting; changes hide/cancel pending old selections, and late results cannot replace a newer selection.

Each card shows a short title, one factual explanation, the exact supporting metric, a feasible next action and a normal navigation link. Links open existing workflows and perform no mutation. Reuse white surfaces, off-white background, charcoal text and yellow actions; dark text on yellow, visible focus, no color-only status. No avatar conversation UI, decorative graph or animated typing.

Reuse React Query for safe status/preview reads and an explicit non-retrying mutation for analysis. State is owned by the signed-in account and selection; use existing session-generation guards plus component request generation. Clear results on logout/account change, do not persist to localStorage or service-worker caches. Disable duplicate submits locally; durable server deduplication covers multiple tabs. Never auto-analyze on mount, refocus, invalidation or route return. A manual fresh analysis creates a new submission ID; recovering an uncertain response reuses the original key and cannot create another charge.

Use semantic heading order, a form with labels/described errors, keyboard-operable controls, `aria-busy` and a polite live status. Focus the result heading on explicit success without interrupting navigation. Keep current error/summary readable; safe retry is an explicit button, no endless spinner. Handle disabled, empty, partial goal coverage, timeout, quota delay, expired result, stale session and historical-calendar errors distinctly. At 320 px use one column with wrapped exact values; respect reduced motion. Plain React text only: no raw HTML, Markdown parser or model-created links.

## 9. Acceptance tests and deterministic fixtures

Tests never contact a real paid provider. `FakeCoachProvider` is test-only, deterministic and records call counts with latches and a controlled Clock. Test the real HTTP adapter separately against a loopback stub with canned responses, not just an interface mock. CI/test configuration must reject live endpoints and dummy credentials must not be valid provider secrets. No fake provider may silently activate in production.

| Boundary | Required acceptance evidence |
| --- | --- |
| Authentication/CSRF | Anonymous GET/POST rejected; missing/stale CSRF prevents dispatch; login/logout/expiry unchanged; unknown methods/routes denied. |
| Ownership | Two PostgreSQL-backed accounts with distinct metrics, reused identical UUIDs and simultaneous requests; no evidence/cache/quota collision; reject owner fields and foreign result lookup. |
| Snapshot | Barrier-controlled commit between dashboard queries yields one snapshot; zero product writes; verify RR/read-only settings and no open analytics transaction during fake-provider wait. |
| Reporting | Zero data, no target, over-target units, void before/equal/after cutoff, reopen/reschedule/archive, rest/all-habits rule, historical gap, frozen/pending timezones, DST and sub-microsecond cutoff; compare unchanged dashboard output. |
| Bounds | 21+ goals clearly marked partial, 20/100-page boundary unaffected, huge exact sums retain precision, maliciously long input/HTTP envelopes fail safely. |
| Provider validation | Good selection, empty selection, duplicate/foreign ID, invalid JSON, extra fields, refusal, truncation, oversized/chunked body, wrong content type, timeout and outage; no partial output and exactly one dispatch at most. |
| Prompt injection | Goal/title/unit/description/rest payload contains instructions, credential requests and HTML; outbound capture excludes text/IDs/secrets, display remains text, destinations remain enums. |
| Quotas/concurrency | Concurrent last-slot/budget requests, daily/monthly UTC rollover, bounded permits, rollback before dispatch, restart after dispatch marker, unknown usage and no refunds, cleanup and cache expiry. PostgreSQL uniqueness/locks tested with real simultaneous transactions. |
| Idempotency | Same key/body replay is byte-equivalent evidence with original cutoff; conflict returns 409; pending/timeout/restart do not redispatch; cached expiry 410; same key across accounts remains isolated. |
| Frontend | Mount/refocus/selection/GET/retry rendering makes zero paid calls; one click one logical request; network loss uses same key; session/selection races discard stale output; no optimistic product mutation. |
| Browser/accessibility | Desktop/mobile real sessions and PostgreSQL with fake provider: analyze, empty state, 429, outage, logout midflight, historical report, links/manual actions, refresh/deep link; packaged HTTPS routes/assets/cookies/CSP, keyboard focus, axe and narrow layout. |
| Regression/packaging | Preserve B6 tests and V1–V5 checksums; no local HTTP config, provider test fixture, key or launcher in production JAR/JS; audit logs for sensitive payload; both npm audits. |

Do not use sleeps to prove concurrency. Use latches/barriers/controlled clocks. Evaluation fixtures should also show that selected suggestions are useful on representative data; a schema-valid answer alone does not demonstrate product value. A live-provider quality check, if later authorized, uses synthetic data with an explicit small spend limit and is reported separately from automated tests.

## 10. Implementation stages and completion conditions

All paths below are expected future work, not files created in Day 7A. No new frontend dependency is proposed. Re-evaluate exact class splits while keeping responsibilities small.

### B1 — Backend contracts and trusted data preparation

Scope: Coach DTOs/controller/status, one snapshot adapter, deterministic summary/candidates, request fingerprinting, durable request/quota schema and repository boundary. Define the full typed contracts before provider code. No live adapter. Disabled is the production default; rules-only fixtures belong to tests, not fake production AI.

Expected files: `backend/src/main/java/com/belsenai/coach/{CoachController,CoachDtos,CoachService,CoachContextFactory,CoachCandidates,CoachRequestStore,CoachLimits}.java`, `coach/CoachProvider.java`; V6 above; focused rules tests and `identity/CoachIT.java` using existing PostgreSQL HTTP support; exact SecurityConfig API matchers and scoped Coach error handling. Configuration examples may be added with empty placeholders after approval. No changes to V1–V5 or analytics formulas.

Dependency: design/retention/migration approval. Risk: accidental mixed snapshots, ledger lock order and incomplete goal coverage. Acceptance: exact dashboard parity, no product writes, principal-derived ownership, 20-goal disclosure, invalid input/CSRF zero dispatch, true concurrent duplicate/cap tests. Verification: `./backend/mvnw -B -ntp -f backend/pom.xml verify`, `git diff --check`; inspect Flyway/Hibernate output and source diff. Completion: all 248 existing backend tests plus new tests pass; no enabled provider or paid calls; reviewer approves contracts and migration.

### B2 — Provider adapter and operational safeguards

Scope: one approved provider adapter, strict selection parser, immutable prompt/schema version, HTTP byte/token/deadline limits, durable spend admission, safe telemetry, disabled/enabled config validation and recovery semantics. Wire to B1 stores; no new product data queries.

Expected files: `coach/provider/{HttpCoachProvider,CoachOutputValidator}.java`, `coach/CoachConfig.java`, narrowly scoped application configuration and `.env.example`, test-only stub fixtures and provider/config/quota tests; operational sections in deployment docs. Prefer existing JDK HTTP and JSON libraries. A tokenizer dependency is conditional on an explained need and approval; do not guess token counts.

Dependency: named provider/model, verified current schema/token accounting, privacy acceptance and approved spend ceilings. Risk: unknown dispatch outcome, hidden billable tokens, provider rate/retention changes. Acceptance: one outbound attempt; invalid outputs rejected; reserved caps survive restart; timeout does not strand permits; key/payload never reaches browser/logs; all automated tests have zero live calls. Verification: full Maven verify and credential/config review, captured HTTP contract tests including chunked size/deadline failures. Completion: B1 tests remain green and every failure/cost gate has deterministic evidence. No paid smoke check without separate authorization.

### B3 — Frontend Coach experience

Scope: accessible page, status/privacy notice, period preview/selection, explicit analysis mutation, trusted cards/evidence, honest empty/error/retry states and existing workflow links. Add exact production route allowlists; no chat, auto-analysis, write shortcuts or AI animations.

Expected files: `frontend/src/features/coach/{CoachPage,RecommendationCard}.tsx`, `{model,data}.ts`, focused tests; `app/App.tsx`, minimal shared styles if needed; `SpaController.java`, `SecurityConfig.java` and route-security tests. Reuse existing API, numbers, analytics and UI helpers. No provider SDK in the frontend.

Dependency: stable B1/B2 contracts. Risk: stale owner/selection results, accidental query-triggered costs and unsafe links. Acceptance: zero provider calls on mount/refocus; preserved drafts; same-key recovery; all states usable by keyboard and at 320 px; no auto-mutation. Verification from `frontend/` with Node 24: `npm run typecheck`, `npm run lint`, `npm test`, `npm run build`, `npm run e2e`; backend verify for allowlist tests. Completion: all 225 existing frontend tests plus new tests and 8 mocked browser cases remain passing; no generated artifacts tracked.

### B4 — End-to-end verification and security review

Scope: real Spring Boot/PostgreSQL + test-provider browser workflows, packaged HTTPS Coach tests, axe scans, independent review and final truthful docs. Fake adapter is test infrastructure only. Keep two explicit browser modes: the unmodified release JAR under `java -jar` verifies disabled Coach behavior, routes and transport security; a separately assembled test application using the same production SPA/configuration and external test-only provider bean verifies enabled workflows over HTTPS. Label the latter as a test-enabled packaged application, not proof of a live provider or the byte-identical release JAR. Never weaken production hostname validation to enable a stub, and inspect the release JAR independently for absence of that bean. Test the actual HTTP adapter against a loopback stub at the Java integration boundary. A release-JAR live-provider browser test remains optional, separately funded work.

Expected files: `frontend/e2e-real/` and `e2e-packaged/` suites (use the verified existing harness directories), test-only provider harness under backend test sources, focused README/architecture/roadmap/deployment updates. No CI platform, hosting or public deployment added by implication.

Dependency: B1–B3 accepted; Java 21, Node 24, Docker/PostgreSQL and Chromium available. Risk: fake-provider success mistaken for live-provider verification. Acceptance: all old and new tests pass, no critical/high/medium defect remains; browser evidence explicitly separates mocked UI, real backend with fake provider and any separately authorized live-provider checks. Verify no test provider/key/local config in artifacts and no application request to a provider from React.

Full gates: root `./scripts/build-production.sh`; frontend `npm run e2e`, `npm run e2e:real`, `npm run e2e:packaged`, `npm audit`, `npm audit --omit=dev`; `git diff --check`; inspect full Git diff/status, JAR contents, credentials and generated outputs. The script includes npm ci/typecheck/lint/unit/build and Maven `-Pspa clean verify`. Preserve baseline 248 backend, 225 frontend, 8 mocked, 24 real and 6 packaged cases; report exact new totals, failures and skips. Completion requires fresh results and a reviewed limitation list, not inherited success claims. Commit/push/public deployment require explicit authorization in that work package.

## 11. Approval decisions and limitations

Approve or revise before B1: constrained AI ranking with server-rendered explanations; first-20 goal coverage; API and two-table V6 design; 24-hour response/32-day metadata retention and its idempotency/cleanup limits. Before B2: provider/account/model, data region/retention terms, explicit API funding and proposed numerical quotas/budgets/deadlines. Keep live mode disabled until these are settled. A subscription is not evidence of an API key or spending approval.

Known limits: one period, no trends/lifetime/deadline-aware advice, aggregate task/habit guidance, current goal metadata, partial large-account goal coverage, no finalized reports, no personal-text understanding, no guaranteed optimal prioritization, best-effort cancellation after dispatch, conservative quota consumption and no provider-independent exactly-once billing guarantee. Single-instance process concurrency is intentional; adding replicas needs a new concurrency design even though PostgreSQL budget reservations are durable. No public launch, real Google verification, payment feature, recovery/email-verification feature or AI-driven domain mutation is included.


## Historical interrupted B1 checkpoint — superseded by recovery below

The owner confirmed 24-hour replay and 32-day tombstones. The working tree now contains a bounded metadata-free evidence mapper, authenticated status/evidence/analysis contracts, a 4 KiB body limit, V6 operational tables and an internal transactional request/reservation store. `GET /api/v1/coach/status` reports NOT_CONFIGURED. `POST /api/v1/coach/evidence` prepares evidence only; it is not an analysis or idempotent paid operation. `POST /api/v1/coach/analyses` validates selection and returns 503 without dispatch or request persistence. No provider interface implementation, generated recommendation text or Coach frontend exists.

Evidence preparation invokes the existing dashboard once with page zero/size 20; stable goal ordering is its existing created_at/UUID order. Only period metadata, exact numeric facts, server-owned candidates and coverage leave the mapper. Original goal UUIDs, titles and unit labels are omitted. Candidate order follows tasks, habits, then the bounded goal order. Evidence reads do not create domain records.

The internal ledger has ACCEPTED, RESERVED, PROCESSING, COMPLETED, FAILED_PRE_DISPATCH and OUTCOME_UNKNOWN states. Claims are unique by owner/submission UUID and compare a supplied SHA-256 fingerprint. Wiring canonical request hashing and replay response storage into the future paid orchestration is still required; B1 stores no result text. SQL guards immutable identity and allowed transitions. A fixed transaction-scoped PostgreSQL advisory lock serializes short admission/reservation/transition operations, including UTC daily/monthly aggregate checks and database concurrency checks (two global, one per owner). No network operation exists. Defaults are three account attempts/day, USD 1 daily and USD 10 monthly reservations, with internal positive maximum-cost input. These are reservation foundations, not an enforced provider invoice cap. Pricing/token validation and configuration remain B2 work.

Stale undispatched requests become FAILED_PRE_DISPATCH; stale dispatched requests become OUTCOME_UNKNOWN. No automatic redispatch occurs. Current conservative accounting retains all reservations, including abandoned pre-dispatch reservations; it does not yet implement the proposed pre-dispatch refund path. Requests have a 20-second deadline, 24-hour replay expiry and 32-day metadata retention. Bounded lazy deletion removes up to 100 expired request rows during new claims; idle-installation and usage-row cleanup still need an operator procedure. The 100,000-row admission cap rejects new requests instead of evicting protected keys. Cheap per-minute request admission controls, provider-priced reservations and scoped dynamic Retry-After handling remain incomplete; the unavailable analysis API prevents paid dispatch in the meantime.

Fresh verification: Java compilation and 69 unit tests (67 baseline plus two evidence tests) passed. Full Maven verify failed before PostgreSQL test execution: all 11 integration test classes reported Docker-environment initialization errors. `docker info` confirms the desktop-linux socket `/Users/yeraassyltl/.docker/run/docker.sock` does not exist. V6 application, Hibernate startup and all integration assertions therefore remain unverified. No backend release or security sign-off is claimed. Six new PostgreSQL test methods cover API security/unavailability, bounded evidence/isolation, validation/body limits, duplicate/expiry behavior, concurrent capacity/rollback and stale/UTC rollover behavior, but they have not executed. Additional high-value tests for monthly ceilings, database transition constraints, chunked input and snapshot/precision parity must be completed before declaring B1 done.

No frontend source or dependency changed. Frontend/browser checks were not rerun. The migration-count assertion was updated from five to six; V1–V5 are unchanged. No staging, commit or push occurred. Resume with Docker available, finish missing coverage/review, run full verification and inspect artifacts before publication. This checkpoint supersedes no Day 7A product decisions and does not authorize B2.


## B1 recovery and implemented contract

Docker Desktop connectivity recovered without resetting Docker, changing contexts or deleting volumes. B1 preserves the original Day 7A design; the following describes its narrower implemented contract. Provider generation, pricing/token accounting, recommendation-result caching and frontend work remain B2–B4 proposals.

### API and evidence

- Authenticated `GET /api/v1/coach/status` returns schemaVersion 1, NOT_CONFIGURED, noticeVersion 1 and goalLimit 20. It makes no provider call.
- Authenticated, CSRF-protected `POST /api/v1/coach/evidence` accepts submissionId, optional weekStart/cutoff and noticeVersion 1. It returns an immutable evidence snapshot with exact numeric strings, server reason codes, deterministic eligible candidates and goal coverage. It prepares evidence only; submissionId does not promise idempotent snapshot retrieval at this endpoint.
- Authenticated, CSRF-protected `POST /api/v1/coach/analyses` validates the reporting selection with the authoritative dashboard, then tracks a unique logical request and marks a new request FAILED_PRE_DISPATCH. It returns safe 503 NOT_CONFIGURED behavior, never invented recommendations. An identical failed request repeats that safe unavailable response without a new request row or quota reservation. A pending request returns 409; an uncertain dispatched request returns 409 without retry; conflicting payload returns 409 and replay expiry returns 410. The response's ordinary requestId is per HTTP response, not cached.

Each evidence preparation uses one existing read-only REPEATABLE READ dashboard call, never extra resource views or metric calculations. No ledger transaction surrounds analytics. Revalidation of a repeated unavailable analysis can perform another read; **B1 does not cache or replay historical analytics snapshots or completed model results**. B2 must persist/replay its sanitized completed result before enabling any provider. Current B1 replay orchestration concerns request identity and safe failure outcomes only.

Canonical hashing uses a versioned UTF-8 field representation: parsed Monday, normalized Instant and notice version. Field order, JSON whitespace and equivalent Instant spellings do not change the hash. Submission UUID is the owner-scoped key, not part of the content hash. Omitted/null period values both mean relative current selection; an explicit Monday is deliberately a different request. Hashes never contain titles or credentials and are not logged or returned to clients.

Body size is bounded while reading, including chunked bodies; excess is 413. Source titles, units, account/resource UUIDs, private text and credentials are absent from the future-provider evidence projection. Null percentages retain explicit server reason codes. Evidence is not a finalized publication. No-data evidence contains empty candidates and no fabricated ratio.

### Durable admission, quotas and recovery

All ledger methods use short REQUIRES_NEW transactions, a fixed PostgreSQL advisory transaction lock and bounded lock/statement waits. Claims and reservations are serialized across application processes. No database transaction spans future HTTP work. Unique owner/submission keys and SQL transition/identity guards provide database integrity. V1–V5 are unchanged; V6 was only applied to disposable test databases during this work, so its uncommitted index/constraint refinements were verified through fresh migrations rather than rewriting a persistent environment.

New requests are limited to 10/account and 100/global per rolling 60 seconds, atomically from indexed database timestamps. Duplicates do not consume new admission slots. Total retained requests are capped at 100,000; capacity rejects new work instead of evicting protected identities. Evidence-only reads have no paid admission or reservation, like the existing analytics read API. Admission Retry-After is 60 seconds; capacity is 20 seconds; reservation limits report the next UTC day or month. These responses use a dedicated handler and do not alter login throttling.

Internal reservation operations enforce three account attempts per UTC day, two global reserved/processing requests, one per account, USD 1 reserved/day and USD 10 reserved/calendar month. Daily scope uniqueness handles a null global owner explicitly. Both counter scopes and request state update in one transaction; rejection rolls back every update. Decimal arithmetic is exact. The B1 public API never invokes paid reservation or PROCESSING. No API key, model, outbound transport or provider configuration is installed. These are hard-coded conservative foundation defaults, not live-provider spending authorization or an invoice guarantee; B2 must validate model pricing, token ceilings and operational configuration before enabling dispatch.

State transitions are ACCEPTED → RESERVED → PROCESSING → COMPLETED; undispatched acceptance/reservation can become FAILED_PRE_DISPATCH, and stale processing becomes OUTCOME_UNKNOWN. Staleness is reconciled on ledger operations using the controlled 20-second deadline. A PROCESSING marker must commit before any future dispatch; it is never automatically retried. Unknown operations require operator/provider reconciliation, never automatic recovery. B2 must bound actual network lifetime and shutdown behavior so a ledger deadline cannot be mistaken for proof that a remote operation stopped.

Reservations remain conservatively charged even on known pre-dispatch abandonment. This explicitly defers the Day 7A refund optimization: it can reduce available quota but cannot increase spend or enable duplicate dispatch. B2 can add a separately tested refund for proven undispatched work. Stale/dispatched reservations are never automatically refunded.

### Retention and operator maintenance

Replay is accepted strictly before 24 hours from claim; from that boundary the key returns 410 while its tombstone exists. Metadata remains for at least 32 days. Up to 100 expired COMPLETED/FAILED_PRE_DISPATCH records are removed per new admission or internal `CoachRequests.cleanup()` invocation. Active and OUTCOME_UNKNOWN rows are never automatically deleted. Usage rows older than 32 UTC days are also deleted in batches of 100; this cannot remove the current month's budget. Lazy cleanup gives eventual deletion during use, not an exact wall-clock deletion promise. Backups need a separate retention policy. Unknown rows can exhaust the bounded store and require manual review; capacity must not silently allow new dispatch.

For an idle installation, an operator may run bounded terminal-row cleanup with the same admission lock. Example maintenance SQL (connect using approved external PostgreSQL credentials, never a committed password):

```sql
BEGIN;
SET LOCAL lock_timeout = '3s';
SELECT pg_advisory_xact_lock(72610401);
DELETE FROM coach_requests
WHERE (owner_id, submission_id) IN (
  SELECT owner_id, submission_id FROM coach_requests
  WHERE retain_until <= CURRENT_TIMESTAMP
    AND state IN ('COMPLETED', 'FAILED_PRE_DISPATCH')
  ORDER BY retain_until LIMIT 100
);
COMMIT;
```

Do not modify immutable identity/deadline fields or delete uncertain records to make retries possible. Confirm the provider outcome and maintain an audit trail before any manual reconciliation; B1 has no provider outcome to reconcile. After a safe terminal tombstone is actually removed, an old key could be new work, so clients must not queue indefinite offline retries. Each deliberate fresh analysis uses a new UUID.

### Review and verification

The recovered first suite applied V1–V6 and validated Hibernate, but found a test-isolation assertion that counted other accounts' request rows. The assertion is now owner-scoped. Focused tests then passed, and final full-suite results are recorded after the release run. New tests cover deterministic/exact evidence, canonical hashes, one dashboard call, HTTP ownership/CSRF/validation/body limits, unavailable replay/conflicts, simultaneous duplicates, capacity and quota rollback, UTC rollover, SQL transitions and safe cleanup. Existing dashboard tests retain snapshot/concurrent-commit, timezone/DST, void and original-cohort coverage.

Senior review also corrected monthly Retry-After, indexed rolling admissions, added finite reservation bounds, kept uncertain rows out of automatic cleanup and used the decoded servlet path for the body-limit filter. No real provider was used. The design remains explicit about partial goal coverage, current metadata, mutable unfinalized reports, conservative reservations and B2 completion-result replay requirements.


Verified B1 recovery, November 17, 2026: final `./backend/mvnw -B -ntp -f backend/pom.xml -Pspa clean verify` passed **267 tests: 71 unit and 196 PostgreSQL integration**, zero failures/errors/skips. All 248 B6 cases remain; four Coach unit tests and fifteen Coach integration tests were added. Fresh PostgreSQL 17.11 databases applied V1–V6, Flyway validation and Hibernate schema validation passed. Node 24 frontend typecheck, ESLint, 225 tests and production build passed. Browser suites were not rerun for this backend-only work; no new browser verification is claimed. Production JAR inspection found the SPA and no local configuration/test launcher/test fixtures. Credential-pattern, documentation-link and whitespace checks passed. No provider call or generated advice occurred. B1 is complete as a disabled backend foundation; B2 requires separate approval and provider/model/privacy/funding decisions.
