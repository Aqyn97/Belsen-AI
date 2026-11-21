# Production integration and deployment preparation

**Current work package: local Hetzner/Caddy/PostgreSQL preparation, November 21, 2026.** See [the target runbook](#hetzner-controlled-portfolio-preview) below. Nothing here authorizes a purchase, DNS change, remote login, deployment, provider enablement or Git publication. Older dated verification below is historical evidence, not proof of current cloud infrastructure.

The website and API ship in one Spring Boot JAR. Day 7 adds optional Coach ranking and its interface, disabled by default. No public deployment has been performed. Live Google/Groq verification, immutable report finalization and CI automation remain pending. Historical checkpoint results below retain their original dates; see RELEASE_READINESS.md for the current audit.

## Build the combined application

Use **Node 24**, Java 21, Docker for PostgreSQL integration tests, and the committed npm/Maven wrappers and lockfile. From the repository root:

```sh
./scripts/build-production.sh
```

The script checks Node's major version, runs `npm ci`, typecheck, ESLint, all frontend tests and the Vite build, then Maven `-Pspa clean verify`. The `spa` Maven build profile copies only `frontend/dist` into `BOOT-INF/classes/static`. It excludes `application-local.yml` from the production artifact. The test classpath retains local HTTP configuration for integration tests; test classes, fixtures and clock launchers do not enter the JAR. The ordinary backend build remains available for backend development, but it is not a production SPA package. Always use `clean` when switching packaging profiles so stale assets cannot survive in `target`.

Output: `backend/target/belsen-ai-0.0.1-SNAPSHOT.jar`. Do not commit it, `dist`, `target`, test results, environment files or certificates. The production JAR contains no Vite proxy. The frontend uses relative same-origin API paths. React Router includes upstream localhost URL-parsing defaults; they are not network destinations and are retained unchanged. Packaged browser tests check the actual page request origins.

`VITE_GOOGLE_ENABLED=true` is an optional **build-time public boolean**, not a credential. Build with it only when the backend Google configuration and registered callback are ready. Runtime environment changes cannot alter an already-built flag. Never place credentials in `VITE_*` variables.

## Direct HTTPS configuration

Keep configuration outside the checkout in a secret manager or a protected environment file. Spring Boot does not read the root `.env` automatically. Provide:

| Variable | Requirement |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `production`, never combined with `local` |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL; use provider-required TLS/CA verification for a remote database |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Dedicated database credentials; Flyway currently needs migration privileges |
| `BELSEN_PUBLIC_BASE_URL` | Exact HTTPS origin, for example `https://belsen.example`; no trailing slash, path, credentials, query or fragment |
| `BELSEN_TLS_KEY_STORE` | Mounted external PKCS12 file, for example `file:/secure/belsen/server.p12` |
| `BELSEN_TLS_KEY_STORE_PASSWORD` | Injected secret for that keystore |
| `BELSEN_SERVER_PORT` | Optional; defaults to `8081` |
| `BELSEN_BIND_ADDRESS` | Optional; production JAR defaults to loopback `127.0.0.1` |
| `BELSEN_SESSION_TIMEOUT` | Optional; defaults to `30m` |
| `BELSEN_DB_POOL_MAX` / `BELSEN_DB_POOL_MIN` | Optional; defaults to 10/2, with a 10-second connection acquisition timeout |

After injecting the environment, start:

```sh
java -jar backend/target/belsen-ai-0.0.1-SNAPSHOT.jar --spring.profiles.active=production
```

Direct TLS is the production default. Use a real certificate and hostname before exposing the service. The local packaged browser harness generates a short-lived self-signed certificate and ignores its trust error **only in Playwright**, not in the application. It does not test public certificate issuance/renewal.

Missing database settings, missing/invalid origin, absent SPA assets, TLS initialization failure, weakened cookies or an accidental `local` profile prevent production startup. Flyway runs before Hibernate schema validation; schema errors prevent successful startup. V1–V7 are applied without editing existing migrations, and Spring Session's schema initializer remains disabled. Do not use Hibernate schema updates or Flyway clean in production.

## Restricted reverse proxy option

Direct TLS needs no forwarded-header trust. The default `none` strategy ignores spoofed forwarding headers. If terminating TLS at a controlled proxy, explicitly set:

```text
BELSEN_TLS_ENABLED=false
BELSEN_FORWARD_HEADERS_STRATEGY=native
BELSEN_TRUSTED_PROXY_REGEX=<regex matching only the actual proxy peer addresses>
```

The proxy must strip incoming `Forwarded` and `X-Forwarded-*` headers and supply its own scheme, host, port and client address. Block direct access to the backend listener with network controls. Keep it on loopback for a same-host proxy or an isolated private container network. Do not trust arbitrary clients or use an all-address proxy regex. Startup rejects blank/common catch-all trust expressions; the operator must still review any custom regex and network boundary. The unrestricted framework forwarding filter is not supported by production validation.

Spring Security redirects non-HTTPS requests in production. Disabling direct TLS without native proxy handling is rejected. Wrong proxy settings can cause redirects or unavailable service; they are not a reason to weaken HTTPS/cookie enforcement. The Google callback is pinned to the configured public origin, independently of the request Host. Review a real proxy/domain configuration before public use; B6 exercises direct TLS and rejection of spoofed headers, not a chosen hosting provider's ingress.

References: [Spring Boot proxy/TLS configuration](https://docs.spring.io/spring-boot/how-to/webserver.html), [Spring Security HTTPS handling](https://docs.spring.io/spring-security/reference/features/exploits/http.html).

## Session, frontend and API security

The public HTML routes are exactly `/`, `/auth`, `/auth/callback`, `/app` and the seven implemented `/app/...` pages. They load the SPA; the existing React gate checks the session and every private API still authenticates the owner. Only GET/HEAD documents and single-segment hashed assets are public. There is no catch-all SPA fallback and no generic static-file mapping. Unknown API/actuator/private-file paths remain JSON errors. OAuth callback/authorization routes remain controlled by the existing Google-enabled condition, never mapped to HTML.

HTML is `no-store`; existing API/security no-store headers remain. Explicit hashed assets receive their MIME type and `public, max-age=31536000, immutable`. Source maps, nested asset paths, unhashed filenames and arbitrary files are not served. Asset hashes are deployment versioning, not authentication: deploy the JAR atomically and expect a long-open older tab to need a reload after an upgrade.

Cookies retain Secure, HttpOnly and SameSite=Lax. CSRF remains active on mutations; login rotates the session and CSRF state, logout invalidates the JDBC session, and account changes clear frontend private caches. All authoritative ownership, numeric precision, versions, idempotency and reporting rules remain in Java/PostgreSQL.

Spring Security retains HSTS on HTTPS, frame denial and `nosniff`. B6 adds CSP and permissions restrictions. Scripts/connections/fonts are same-origin; objects, frames and base-URI overrides are blocked. Inline styles are allowed for existing React progress widths, while inline scripts are not. Security's default cache protection remains; only explicit asset responses override it. See [Spring Security response headers](https://docs.spring.io/spring-security/reference/servlet/exploits/headers.html).

SQL bind/diagnostic logging is suppressed in production because Hibernate unique-constraint diagnostics can reveal email values. API errors retain safe Problem Details and request IDs. Do not enable request-body, SQL bind, authentication or TRACE logging around real credentials. Logs and backups still require access controls and retention limits.

## Google OIDC

Google is disabled by default. To enable it, supply `BELSEN_GOOGLE_ENABLED=true`, `BELSEN_GOOGLE_CLIENT_ID` and `BELSEN_GOOGLE_CLIENT_SECRET`, and build the frontend with its public boolean enabled. Register exactly:

```text
https://<your-authorized-domain>/login/oauth2/code/google
```

Production derives this callback from `BELSEN_PUBLIC_BASE_URL`; local development retains the browser-facing callback template. Success/failure redirect to fixed relative SPA routes. Existing state/nonce/token validation, verified claims, subject association and collision rejection remain unchanged. Live Google login is unverified without actual credentials and an authorized domain; local signed-provider tests are not a live-provider claim.

## Optional runtime container

The Dockerfile is a small, non-root Java 21 runtime image pinned by digest. Build the verified combined JAR first, then:

```sh
docker build -t belsen-ai:review .
```

The build context allowlist contains only that JAR. No npm/Maven download happens inside the runtime image. Use an external PostgreSQL 17 service with persistent storage, mounted certificates and injected secrets. For a private direct-TLS check, an operator can run:

```sh
docker run --rm --name belsen-production-check \
  --env-file /secure/belsen/production.env \
  -e BELSEN_BIND_ADDRESS=0.0.0.0 \
  -p 127.0.0.1:8081:8081 \
  -v /secure/belsen/tls:/run/secrets:ro \
  --read-only --tmpfs /tmp --cap-drop=ALL \
  --security-opt=no-new-privileges --stop-timeout=35 \
  belsen-ai:review
```

The environment file must set `BELSEN_TLS_KEY_STORE` to its container path and use a database address reachable from the container. Ensure UID 10001 can read the mounted keystore without making secrets public. This is a documented private run path, not a public deployment. Default local Compose remains a separately named, loopback-only Belsen PostgreSQL service; no TIR infrastructure is changed.

Stop via SIGTERM and allow at least 35 seconds for the configured 30-second graceful shutdown. The public `/api/v1/health` returns only `{"status":"UP"}` and is a liveness check, not continuous database readiness. Add protected external database/connection monitoring before operating publicly; do not expose configuration or actuator details to get a richer public check.

## Verification

From `frontend/` under Node 24:

```sh
npm run typecheck
npm run lint
npm test
npm run build
npm run e2e
npm run e2e:real
npm run e2e:packaged
npm audit
npm audit --omit=dev
```

Build the combined JAR with Maven `-Pspa clean verify` before browser commands. Ordinary real-backend tests keep the existing deterministic Monday test launcher and Vite proxy. Packaged tests use **`java -jar`**, production configuration, local HTTPS, the normal system clock and real disposable PostgreSQL. Their dates come from server period metadata, not the device calendar. They exercise authentication/security, resources, reporting, direct links, cache headers, forbidden files/routes, same-origin page traffic, and actual JDBC expiry without sleeps. Expiry fixtures update both last-access and expiry timestamps for exactly one account; they do not change application session rules.

Axe WCAG A/AA scans run on authentication, settings, goals, tasks, habits, Dashboard and Analytics in packaged desktop/mobile Chromium. This is automated evidence, not assistive-technology certification or a cross-browser claim. Existing mocked smoke tests remain separately labelled. Temporary databases, keystores and JAR copies are removed by the harness. Build/test output stays ignored.

## Before public release

Select and verify hosting/domain/TLS renewal, ingress trust and database TLS/backups/restore. Rotate/inject real secrets; decide database migration privileges and release/rollback procedures. Review availability monitoring, resource limits, CI automation and provider/OS/JVM security updates. The digest pin makes runtime selection repeatable; it also requires deliberate updates and vulnerability review.

The login throttle is single-process and bounded; it resets on restart and is not shared across replicas. A trusted proxy must preserve the correct client address or users share its address bucket. Do not add replicas and assume distributed protection. Public registration, email verification and password recovery policy remain unresolved launch decisions. Reports remain unfinalized and metadata may be current. No public hosting, real provider flow, production load test, complete screen-reader review or multi-browser certification has been performed.


## B6 verification evidence

November 16, 2026: Node 24.22.1 and Java 21 passed frontend typecheck, ESLint, 225 frontend tests and production build. Maven verification passed 248 tests (67 unit, 181 PostgreSQL integration), with no failures/errors/skips; V1–V5 and Hibernate validation passed. Chromium passed 8 mocked shell, 24 real-backend and 6 production-JAR HTTPS cases. Packaged cases include actual secure cookies, rotation, logout/expiry, CSRF, ownership, deep links, assets, unknown routes, same-origin requests and axe scans of authentication/registration, product screens, reporting, settings and a dialog. No violations were reported by those scans; this does not certify complete assistive-technology or cross-browser accessibility.

Both npm audits returned zero vulnerabilities. The pinned runtime image built successfully. Repackaging unchanged inputs produced the same JAR checksum; production inspection confirmed the SPA and absence of local configuration, test launchers and fixture assets. Credential/generated-artifact/whitespace checks passed. A launch without TLS settings exited unsuccessfully before application startup. Browser containers, processes and ephemeral certificate directories were cleaned up. The application was not publicly deployed; the optional image was built, while browser runtime verification used the JAR directly.

## Groq Free Coach setup

B2 defaults to disabled. Leave `BELSEN_COACH_ENABLED=false` until the actual account Free-plan/ZDR checks below are complete. Other application features require no Groq key.

After B2 verification, an operator must check the **actual Groq organization/project** in the console: confirm it is on the Free plan, no Developer/paid upgrade is active and no paid billing is enabled. Do not add a payment method or upgrade to resolve a rate limit. If these checks are unavailable or uncertain, keep Coach disabled. Application confirmations cannot detect a later account upgrade; no application quota guarantees zero charges on a paid account.

Review Groq Data Controls and enable Zero Data Retention for inference. Disable persistence features not needed by Belsen. Groq documents collection of usage metadata and possible reliability/abuse retention without ZDR; ZDR is not a claim of no metadata or absolute retention guarantees. See [Groq data controls](https://console.groq.com/docs/your-data) and [billing](https://console.groq.com/docs/billing-faqs).

Inject `BELSEN_COACH_API_KEY` through a protected backend environment/secret mechanism. Never place it in React, VITE variables, logs, command arguments or chat. Only after console review set `BELSEN_COACH_FREE_PLAN_CONFIRMED=true` and `BELSEN_COACH_PRIVACY_REVIEWED=true`. These are operator acknowledgements, not an account verification API. Model/endpoint overrides must equal the documented approved values in `.env.example`; other endpoints, HTTP, models and non-strict output are rejected.

Draft bounds are connect 3 seconds, provider total 15 seconds, complete outbound request 12 KiB, response envelope 64 KiB, decoded selection 2 KiB and 512 generated tokens. Byte limits are not a claimed exact tokenizer count. B1's 20-second request deadline can reject late completion conservatively. Free attempts consume daily quota even when the provider rejects them; uncertain operations retain concurrency capacity until operator review. There is no automatic retry, failover, paid service-tier selection or billing upgrade. Provider quotas are external and may change; a 429 is a safe failure, not an instruction to purchase capacity.

Set `BELSEN_COACH_ENABLED=false` and restart to disable inference. No provider request occurs from status/evidence reads, page loads or disabled analysis. Ordinary authenticated product workflows remain usable. Replay contains original evidence, not current product state. Payloads expire after 24 hours; tombstones remain at least 32 days, and unknown outcomes are never automatically deleted. Cleanup is bounded/lazy; backups need their own retention policy.

No live Groq request has been run. One optional synthetic smoke request needs separate explicit authorization, a securely injected key and confirmed Free-plan/ZDR/no-paid-billing conditions. Automated tests use local HTTP stubs and a deterministic fake provider only.


B2 protocol audit: `store:false` is omitted because it is undocumented for the selected chat endpoint. The request uses `include_reasoning:false` and `reasoning_effort:low`; no `reasoning_format` parameter is sent for GPT-OSS. Only final assistant content is validated. Do not interpret successful local protocol tests as live-provider, Free-plan or ZDR verification. The application validates exact endpoint/model configuration and follows no redirects. JVM HTTP wire/header debugging must remain disabled with real keys.

Unknown outcomes keep concurrency capacity indefinitely. HTTP errors are deliberately conservative; even a known rejected response does not automatically release a dispatched operation. A minimum Retry-After delay does not promise availability or authorize automatic redispatch. If capacity is exhausted by unknown operations, disable Coach, review the provider outcome and arrange an explicitly reviewed administrative recovery. Do not delete uncertain rows/tombstones or disable SQL guards to make another attempt. There is no automatic reconciliation API in B2.


Verified B2 recovery, November 20, 2026: Java 21.0.11 and Node 24.22.1. `./backend/mvnw -B -ntp -f backend/pom.xml -Pspa clean verify` passed **286 tests: 80 unit and 206 PostgreSQL integration**, zero failures/errors/skips. All 267 B1 cases remain; nine protocol tests and ten ranking integration tests were added. Fresh PostgreSQL 17.11 databases applied and validated V1–V7; Hibernate schema validation passed. Coverage includes separate-JVM replay, atomic completion rollback, concurrent duplicates, owner isolation, free-attempt quotas, retention and safe uncertain outcomes. Frontend typecheck, ESLint, 225 tests and production build passed. Chromium passed eight mocked shell cases and six real production-JAR HTTPS desktop/mobile cases, including scoped axe checks. The separate 24-case Vite/real-backend browser suite was not rerun; packaged HTTPS regressions cover the production delivery/configuration changes, while provider behavior is verified through Java local HTTP/PostgreSQL tests. No browser AI UI or live-provider verification is claimed.

Production artifact inspection confirmed SPA/V7 inclusion and absence of local configuration, tests and both test launchers. Credential/generated-artifact/whitespace checks and documentation-link review passed. Final source review found no unresolved critical/high/medium issue in B2 scope. Operator Free-plan/ZDR confirmations and a separately authorized synthetic live check remain external prerequisites; the feature stays disabled by default. Uncertain outcomes deliberately retain capacity and require reviewed operator recovery. No live paid/free inference, public deployment or B3 work occurred.


## Controlled deployment plan

Use one application instance and one PostgreSQL 17 service in the same region, behind a controlled HTTPS ingress or direct TLS. No separate public frontend server is needed. Keep PostgreSQL private, require certificate-verified database TLS for remote connections, and use provider-managed persistent storage. The application filesystem can be ephemeral; never keep backups or secrets there. Local Compose is development infrastructure, not a public database deployment.

Start with an explicitly budgeted 1–2 GiB memory allocation and a modest CPU allocation, then measure startup, heap/RSS, GC, report latency and pool saturation under representative data. These are sizing estimates, not measured capacity claims. Set container memory/CPU limits, Java heap headroom, ingress JSON/body/time/connection limits and an adequate startup grace period. Hikari defaults to ten connections per instance; reserve database connections for migration/administration and choose limits within the hosting plan. Do not add replicas without redesigning the in-memory auth throttle.

Use a secret manager or protected injected environment for database, TLS, Google and Coach credentials. Use a dedicated application database role, never a PostgreSQL superuser; the current single datasource also runs Flyway, so migration/DDL privileges remain a deliberate MVP compromise. Restrict its network and administrative access. Test role permissions, extension availability and migration execution on the selected provider before launch.

`/api/v1/health` is process liveness only. Successful startup proves initial database/migration validation, not continuous database readiness. Monitor synthetic private read journeys separately, startup failures, 5xx/latency, pool/DB capacity, backup age, TLS renewal, auth 429s and Coach unknown counts. Keep bounded access-controlled logs without bodies, SQL bind values, cookies or Authorization headers. Alert on any unknown Coach outcome. Let SIGTERM complete the 30-second graceful shutdown; give the host at least 35 seconds.

The prepared portfolio configuration disables self-registration while keeping the public landing page accessible. Existing approved accounts may sign in; password recovery/email verification/invites remain absent. The general single-origin configuration above is reusable, while the Hetzner runbook below is the selected preparation target. Do not enable unrestricted registration without separate approval.

### Hosting evaluation (reviewed November 20, 2026)

- **Render:** managed TLS and a Docker service fit the single-origin model. Its [free service documentation](https://render.com/docs/free) warns against production use: idle apps sleep and free PostgreSQL expires after 30 days. Use only a disposable preview, or select a durable budgeted plan with backups. Review actual proxy peer addresses before enabling native forwarding.
- **Neon:** [plan documentation](https://neon.com/docs/introduction/plans) offers managed PostgreSQL with free allowances, scale-to-zero and limited restore history. It does not host this Java application. Verify PostgreSQL 17 support, direct JDBC/TLS connectivity, connection caps, region, retention and current allowances; keep independent backups.
- **Fly.io:** [trial documentation](https://docs.fly.io/about/free-trial) describes time/credit limits and stopped machines when credits expire. It is a trial, not a permanent free hosting guarantee. A single machine can host the container, but durable database service, ingress trust and budget need explicit review.

Recommendation: one small managed container service plus durable managed PostgreSQL, with an owner-approved budget. A single VPS is an alternative with more patching, TLS and backup responsibility. No host/account was provisioned and no pricing or permanent-free promise is made.

## Backup, restore and rollback runbook

Assign an owner, encrypted off-host storage, access policy and retention before real data is accepted. Proposed controlled-demo objectives are RPO 24 hours and RTO two hours; these are targets requiring a timed provider-specific drill, not a tested SLA. Schedule daily logical backups and pre-migration backups; enable provider point-in-time recovery where available. Keep at least seven daily backups initially and review legal/privacy retention before expanding it. A backup on the database's own volume is insufficient. Monitor backup success and rehearse restore.

For this target use the encrypted streaming scripts and concrete procedure below; do not stage an unencrypted dump on the VPS. Keep service credentials in protected files, encrypt to an off-server age identity, verify the uploaded encrypted object and rehearse restoration into a new isolated PostgreSQL 17 database. Confirm Flyway V1–V7/Hibernate, ownership, representative rows and immutable-history guards before cutover. The original DatabaseRestoreIT remains complementary database-level evidence; it does not prove R2 permissions or production recovery time.

Invalidate restored sessions **before** permitting access: in the isolated restored database, remove `spring_session_attributes`, then `spring_session`, within one transaction. Do not restore previously active authenticated sessions to public service. This is intentional operator recovery work, never an automatic startup action. Keep Coach request/tombstone/usage records; a stale backup can omit a previously dispatched call. Therefore keep inference disabled until provider activity since the backup has been reconciled. Never redispatch because a restored key appears absent.

For an application-only rollback, retain the previous verified JAR/image digest and configuration, stop writes, back up the database, and confirm the previous application is compatible with the **current** schema in an isolated rehearsal. Prefer a forward fix. Flyway migrations have no down scripts; never edit applied SQL, use `repair` to hide a mismatch, or run `clean`. A schema/data rollback needs a separately approved restore/cutover, a measured write-loss window and explicit RPO acceptance. Keep the previous database isolated for investigation; do not delete it during rollback.

## Uncertain Coach operations: containment and recovery

Unknown outcomes are deliberately fail-closed: they retain one of two global slots and their account slot indefinitely. Ordinary replay never dispatches again. A Retry-After is a minimum delay, not a promise that capacity will recover. Known dispatched HTTP errors are also treated conservatively. This is an availability limitation, not a billing reconciliation implementation.

1. Disable Coach (`BELSEN_COACH_ENABLED=false`) and restart the single app; normal product workflows stay usable. If needed revoke the provider key in its console. Do not rely on a browser abort to cancel a provider request.
2. Take a protected database backup. With a read-only operator connection, inspect `owner_id`, `submission_id`, state, deadline and dispatch/finish instants from `coach_requests WHERE state='OUTCOME_UNKNOWN'`, plus current UTC usage. Treat UUIDs as private operational metadata and restrict incident records. Do not log prompts, keys or provider payloads.
3. Review actual Groq usage/billing and request records in the console. Record the evidence, affected keys, time range and whether the outcome can be established. Console uncertainty means **keep disabled**. No application flag verifies actual Free-plan or retention status.
4. There is **no supported in-place reconciliation command** in this release. V6's transition guard forbids reopening/retagging unknown rows. Do not delete rows/tombstones, disable triggers, repurpose COMPLETED/FAILED_PRE_DISPATCH or manufacture a result. Restoring a database to evade the ledger is unsafe.
5. If inference must resume after an unknown outcome, require a separately reviewed forward migration/tool with an auditable resolved state, preserved fingerprints/retention and tests proving no redispatch or double admission. Review the incident and provider evidence before enabling a fresh credential. Until then, ship the controlled demo with Coach disabled.

This containment procedure is safe but does not restore inference capacity. Automatic administrative recovery is intentionally absent. If enabled-Coach continuous availability is a release requirement, this limitation blocks that deployment configuration.

## Controlled launch sequence (separate approval required)

1. Owner approves host/region/budget, domain, license/visibility and restricted-audience policy.
2. Provision durable PostgreSQL, backup access and one privately reachable app; inject secrets outside Git. Keep Google and Coach disabled initially.
3. Rehearse migrations, full restore and rollback using the chosen provider and intended non-superuser role.
4. Configure domain, certificate renewal and exact HTTPS origin; validate proxy stripping/trust and firewall isolation.
5. Deploy the reviewed image/JAR only after separate public-deployment authorization; run secure-cookie/CSRF/session/ownership/deep-link/health smoke checks through the real ingress.
6. Enable Google only after exact callback/consent setup and a separately verified real sign-in. Enable Groq only after actual Free-plan/no-paid-billing/privacy review and separately authorized synthetic live verification, accepting the fail-closed recovery limitation above.
7. Monitor the restricted audience, backup age, auth abuse and database capacity. Expand registration only through a separate reviewed policy.


## Hetzner controlled portfolio preview

**Local preparation only.** Target one CX23 x86-64 VM, 2 shared vCPU, 4 GB RAM and 40 GB disk, in Germany or Helsinki, running supported Ubuntu 24.04 LTS. Confirm the actual offered image, stock, region and final price in the console before approving purchase. Compare measured latency from the owner/recruiter audience to available regions; no live server or regional benchmark was used here. Ubuntu 26.04 LTS is also supported upstream but was not selected or remotely tested by this preparation.

```text
Recruiter browser -- HTTPS --> Caddy -- private HTTP --> Spring Boot + packaged React
                                        app -- private PostgreSQL protocol --> PostgreSQL 17
Host backup timer -- pg_dump -> age encryption --> private R2 Standard bucket
Cloudflare Free: DNS-only initially; no Cloudflare HTTP proxy in this tested trust model
```

The initial DNS record must be **DNS-only (grey cloud)**. Caddy owns public certificate issuance and renewal; Cloudflare is not the TLS terminator. The origin IP remains visible and the Free HTTP proxy/WAF/DDoS features are not provided in this mode. [Cloudflare explains the distinction](https://developers.cloudflare.com/dns/proxy-status/). Do not turn on orange-cloud proxying without a separate review of client-IP provenance, Cloudflare Full (strict), origin certificate verification, allowed proxy ranges, cache bypass for private responses and origin firewall rules. Never use Flexible TLS.

### Cost and availability gate

Reviewed November 21, 2026. [Hetzner's published June 2026 price list](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/) lists EU CX23 at **€5.49 / $6.49** excluding IPv4/tax. Its [current public cloud page](https://www.hetzner.com/cloud/) marks cost-optimized service unavailable; this is not a reservation or an account/region stock check. Prices and stock must be reconfirmed at checkout. Account billing currency determines the applicable quote; the USD values below are provider USD prices, not an assumed exchange rate.

| Item | Monthly planning allowance | Treatment |
| --- | --- | --- |
| CX23 in Germany/Finland | $6.49 (€5.49) | Published server cap before tax; stock unconfirmed |
| Primary IPv4 | $0.60 (€0.50) | [Separately billed](https://docs.hetzner.com/cloud/servers/primary-ips/overview/); keep IPv4 for recruiter reachability |
| Cloudflare DNS Free | $0 | DNS hosting only; domain purchase is separate |
| Domain | $1–2 amortized | Budget assumption for a $12–24 annual renewal, **not a TLD quote**; first payment is annual and premium TLDs can exceed it |
| R2 Standard | $0 if account remains within included usage | Shared-account allowances; usage above them is billable |
| Optional whole-server snapshots/backups | $0 initially; separate quote before enabling | Not a replacement for encrypted logical database backups; retention/size must be budgeted |
| Traffic overages | $0 assumed within selected EU allowance | Verify included transfer and per-unit excess in the order; R2 uploads consume VPS outbound traffic |
| Monitoring | $0 local tools/operator checks | Any external alert/uptime plan needs a separate cost decision |
| Tax | Not determined | Depends on actual billing address/status; do not infer tax treatment from this runbook |

The base is **$7.09/month** before tax/domain. A $1–2 domain allowance gives **$8.09–9.09 before tax**. An illustrative 19% charge on VPS+IPv4 gives **$9.44–10.44 including that domain allowance**, before other charges; 19% is a scenario, not a statement of the owner's applicable tax. Thus the $5–10 target is conditional and the low end is not realistic with a new domain. Obtain explicit approval for the final all-in quote, renewal and overage budget.

If CX23 is unavailable, **do not silently substitute**: waiting is the budget-preserving choice. EU CPX22 is a same-architecture alternative, but the published list is **$22.99 (€19.49)** plus IPv4 before tax/domain, already outside budget. CAX is ARM and requires a separately approved architecture/build review. No instance was selected or purchased.

[R2 Standard pricing](https://developers.cloudflare.com/r2/pricing/) includes 10 GB-month storage, one million Class A and ten million Class B operations/month; Standard excess is $0.015/GB-month, $4.50/million Class A and $0.36/million Class B operations. Internet egress has no R2 charge. These are account-wide included amounts, not a hard spend cap; use the dedicated backup size/operation policy below and billing alerts. No permanent-free or zero-invoice guarantee is made.

### Ubuntu host setup (operator procedure, not executed)

1. Approve instance/region/domain/budget and administrator source CIDRs. Use supported Ubuntu LTS x86-64; retain console/rescue access and a verified recovery method before hardening SSH. Add only the administrator's **public** SSH key. Never share a private key with the application or in chat.
2. Create an individual sudo administrator account; verify key login in a second session before disabling root login. In a reviewed `/etc/ssh/sshd_config.d/` file set `PubkeyAuthentication yes`, `PasswordAuthentication no`, `KbdInteractiveAuthentication no`, and `PermitRootLogin no`. Check effective `sshd -T`, including cloud-init snippets and Match blocks, then `sshd -t` and reload SSH. Keep the original session open until the new path works; a lost key requires console/rescue, not a publicly exposed emergency login.
3. Configure **both** Hetzner Cloud firewall and host firewall for IPv4 and IPv6: SSH/TCP 22 only from approved admin CIDRs; public TCP 80 and 443 for Caddy; deny other inbound traffic. Do not publish 5432, 8081, 2019 or Docker APIs. HTTP/3 UDP is not required by the initial config. Preserve required ICMP/IPv6 neighbor/MTU behavior. With no approved IPv6 administration path, do not accidentally permit SSH from all IPv6.
4. Install Docker Engine/Compose from the [official Ubuntu instructions](https://docs.docker.com/engine/install/ubuntu/), checking signing key/repository and supported versions. Docker-published ports can bypass UFW; the absence of app/database port publication, upstream cloud firewall and Docker forwarding rules are essential. Do not assume UFW alone protects a published port. Do not disable Docker firewall management as a workaround. Verify actual nftables/iptables rules and exposure from an independent network before release.
5. Keep the Docker socket root-only and unmounted in every app service. Docker-group membership is root-equivalent; use sudo for the trusted operator, no web UI/remote Docker TCP listener, no unattended prune commands and no unrelated workloads on this VM. Review Docker/default seccomp/AppArmor rather than running privileged containers.
6. Apply security updates, verify `unattended-upgrades`/security origins and review `/var/run/reboot-required` on a maintenance schedule. Ubuntu archive automation does not necessarily patch Docker's external repository or containers; review/pin/rebuild those deliberately. Never auto-reboot during writes without an operator window. References: [Ubuntu security updates](https://documentation.ubuntu.com/security/security-updates/).
7. Enable/check time synchronization (`timedatectl status` and the selected NTP service), use UTC host logs, and set a bounded persistent journal (for example SystemMaxUse=200M). Do not dump environment files with `docker compose config` or `docker inspect` into public logs.
8. Keep packages minimal: Docker/Compose, OpenSSH, firewall/time/update tools, Python 3 if required by verification, age and AWS CLI v2 for backup transport. No Java/Node/Maven production build toolchain is needed. Install only reviewed packages/releases and verify checksums; host commands in this document are not installation authorization for a real server.

The dedicated production Compose configuration and environment template are under `infra/production/`. Keep secret files under a root-controlled directory outside Git (for example `/etc/belsen/` with directory 0700). Inject random database passwords as files; never put them in shell history, environment examples or command arguments. Use the exact Compose project and volume names in the prepared files; inspect for conflicts before first startup.

### Four-gigabyte resource and disk budget

The Compose limits are the source of truth. The intended allocation is app 1536 MiB, PostgreSQL 768 MiB and Caddy 128 MiB; reserve remaining RAM for Linux, Docker, page cache and an occasional bounded backup job. Java remains container-aware, uses a bounded heap and exits on OOM rather than remaining partially healthy. A small heap is not the process's full RSS: thread stacks, native buffers, code cache and metaspace also consume memory. Pool/thread/connection limits prevent default concurrency from consuming the reserve. These are initial settings, not capacity or uptime guarantees.

PostgreSQL begins with 128 MiB shared buffers, 4 MiB work_mem, 64 MiB maintenance_work_mem and 30 connections; each active sort/hash can allocate work_mem independently. App Hikari pool stays below database max, reserving headroom for Flyway, administration and backups. Do not tune from idle RSS alone. Measure on the actual CX23 with realistic synthetic data before widening access.

Use restart-on-failure/unless-stopped behavior only as recovery assistance. On JVM OOM, the process exits, in-flight requests fail and committed transactions remain in PostgreSQL; inspect memory events before restart loops. On database/container crash, let PostgreSQL recover WAL on its existing volume. Stop/reduce traffic and investigate instead of increasing limits beyond 4 GB. Never disable fsync/full-page writes or delete WAL to save memory/disk. A small encrypted-host swap allocation is an operator choice to protect administration during pressure, not extra application capacity; containers should have explicit swap policy.

Budget the 40 GB disk approximately: 8 GB OS/tools, 3 GB Docker/runtime images (retain current and one compatible rollback artifact), up to 16 GB PostgreSQL data/indexes/WAL, at most 2 GB encrypted staging, 1 GB bounded logs/journal and 10 GB reserve. These are soft operational thresholds, not volume quotas. Alert at 70% and 85% filesystem usage and on abnormal WAL/backup growth; review both `df` and `docker system df`. Keep at least 20–25% free. The default volume is not expandable without a storage/migration plan. No frontend/backend compilation or Maven/npm caches on the VM; prebuild the image externally and remove only explicitly obsolete release images after checking rollback needs. Never use `docker system prune --volumes`.

### Manual release and rollback gate

There is currently no committed GitHub Actions workflow. This preparation does not enable production automation or create GitHub secrets. The first release is manual; an optional future workflow should run tests and produce artifacts without deploy credentials, with a separate protected approval job for deployment.

1. Have the owner review/commit the prepared changes separately. Require clean source and record the full approved Git SHA; today's uncommitted local image is **not** an immutable release of the starting B4 SHA.
2. On a trusted build machine use Java 21, Node 24 and `./scripts/build-production.sh`, the browser suites, production-stack rehearsal and encrypted-restore test. Review advisory results, secret scans and `git diff --check`. Preserve exact reports; failed checks block release.
3. Build for Hetzner's architecture: `docker build --platform linux/amd64 -t belsen-ai:<approved-full-sha> .`. Inspect architecture, JAR assets/migrations and absence of local/test code; record JAR SHA-256 and Docker image ID. A tag alone is mutable; use a reviewed registry digest or verified local image ID. Registry publication or SSH/image transfer requires separate approval. Do not compile on the VPS.
4. Reconfirm cost/stock and obtain explicit provisioning/domain/DNS/remote-access/deployment approvals. Pin the reviewed app artifact identity in the protected environment. Verify secrets/volume ownership, role privileges, firewall and proxy network subnet conflicts. Back up before an upgrade; do not start Caddy publicly merely to test its configuration.
5. Start PostgreSQL and wait for authenticated readiness; start app, let Flyway apply approved migrations and Hibernate validate, and check database-backed readiness privately. Migration failure must stop the app/release: do not `clean`, `repair`, edit old migrations or restart with schema updates. Start/enable ingress only when readiness is verified. Run the smoke checklist below through the real origin before recording a successful deployment.
6. For rollback, stop ingress/writes, take a fresh protected backup and capture the failed release identity/log request IDs without private payloads. Rehearse the previous image against a copy of the current schema; switch only if compatible. Keep secure config/providers/registration disabled. A data restore requires a separately approved loss window and isolated new target, session invalidation and verification before cutover. Never use `down -v` or a database rewind to conceal migration failure. Preserve the old database for investigation.

PostgreSQL named volumes are host-local, **not high availability** and not an off-site backup. VM/disk/account failure interrupts all three services. Proposed objectives are RPO 24 hours after successful daily backup and RTO 2–4 hours if a replacement VM, keys and verified backup are available; measure and approve them in a timed off-site rehearsal. A whole-server snapshot helps rebuild OS/config but may be crash-consistent, is provider-local and may capture secrets; it does not replace an application-consistent encrypted PostgreSQL dump plus independently stored decryption identity.

### Production smoke and recruiter checklist

Use fictional individual accounts and non-destructive synthetic records. Preserve default public HTML allowlisting: loading a SPA document is public, but the React session gate and private API authorization still protect data. No server-rendered private data belongs in public HTML.

| Check | Evidence required before recording a deployment |
| --- | --- |
| TLS and DNS | Correct authorized hostname, real publicly trusted chain/SAN/expiry; HTTP redirects to same approved HTTPS origin; renewal/CAA/firewall tested |
| Public pages | Landing explains product/Java/PostgreSQL/security/optional AI and individual access request; sign-in usable; no signup available |
| Direct routes/assets | Refresh every allowed SPA route; unauthenticated private routes gate to login; unknown API/private-file paths stay JSON errors, never HTML |
| Authorization | Private API 401, other-account resource 404; no public actuator/configuration/database/app port |
| CSRF/session | Missing/stale CSRF mutations fail, login rotates session/token, logout invalidates JDBC session, Secure/HttpOnly/Lax cookies present |
| Registration | Runtime policy disabled; valid-CSRF direct register returns 403 without account/session creation; no hidden login bypass |
| Product data | Create/edit measurable goal/plan/progress, task complete/reopen, scheduled habit/rest, dashboard and historical cutoffs using synthetic records |
| Optional providers | Google hidden/disabled; Coach NOT_CONFIGURED with useful verified preview and zero outbound provider calls |
| Persistence | Restart only the app and confirm committed synthetic records and intended JDBC-session behavior survive; never remove the DB volume |
| Operations | Authenticated DB readiness, bounded logs, disk/RAM/process monitoring, successful encrypted upload and alert delivery, verified isolated restore |
| Portfolio screenshots | Genuine landing/auth, dashboard, goals, tasks, habits, analytics and disabled Coach; fixture inference labeled explicitly; no personal names/emails/secrets |

Choose a real recruiter access-request channel before public release; no email, domain, testimonial, live demo or provider result is invented here. Assign every approved reviewer an individual account, never shared administrator credentials. The technical controls do not create an email verification, invite or password reset service.

### Approval boundary and pending decisions

Local preparation can be reviewed without any cloud account. Separately approve: CX23 stock/region/all-in price or an explicitly priced alternative; Ubuntu image; domain and renewal cost; Cloudflare zone/DNS changes and DNS-only mode; SSH public keys/admin CIDRs/recovery; host/R2 creation and billing; R2 region/jurisdiction/private bucket/lifecycle/token scope; off-host encryption identity and alert destination; approved individual accounts/contact channel; remote provisioning; image publication/transfer and actual deployment. Google/Groq enablement, public signup, changes to architecture and Git commit/push each need separate approval.


### Concrete configuration, accounts and backup operation

Use `infra/production/compose.yaml`, `Caddyfile`, `proxy.caddy` and `environment.example`. The three password files are separate random admin/app/backup credentials. For Compose file secrets, container UIDs must be able to read the bind-mounted file: keep the **parent root-owned 0700**, with the individual mounted password files 0444 or reviewed UID-specific ownership. This does not make the files reachable through that parent by other host users. The app imports the uppercase config-tree key `SPRING_DATASOURCE_PASSWORD` so the existing Spring placeholder resolves the secret. Do not print `env`, `/proc` environments or expanded configuration. Secret rotation must update PostgreSQL roles as well as files; changing initialization files does not change existing roles/volumes.

Before first startup, initialize only Caddy's certificate-volume ownership (substitute the exact pinned Caddy image from Compose):

```sh
docker volume create belsen-ai-production_caddy_data
docker run --rm --network none --read-only --user 0:0 --cap-drop ALL --cap-add CHOWN \
  -v belsen-ai-production_caddy_data:/data <PINNED_CADDY_IMAGE> chown 10001:10001 /data
```

This is an operator command for later approval, not executed on a real server. PostgreSQL's normal entrypoint initializes its own data volume and non-superuser roles only when empty. App owns only the application database/schema (needed for this single-datasource Flyway design); backup has connect/usage/select plus default SELECT privileges on new app-owned public tables/sequences, not cluster-wide read or superuser rights. Never give the public app admin/bootstrap credentials. The PostgreSQL health check performs an authenticated SELECT; app TCP liveness is complemented by database-backed smoke checks. HTTPS terminates at Caddy on the same host; the private Docker links do not use TLS. Remote databases require a separate certificate-verified TLS design.

To approve an individual account without enabling signup, use the offline `com.belsenai.identity.tools.PreviewAccountSql` helper from the verified JAR on an administrator's trusted Java 21 workstation:

```sh
mkdir -m 700 /private/operator-chosen-directory
java -Dloader.main=com.belsenai.identity.tools.PreviewAccountSql -cp <VERIFIED_JAR> \
  org.springframework.boot.loader.launch.PropertiesLauncher /private/operator-chosen-directory/approved.sql
```

Choose an actual private path; the literal example is not a file to create automatically. The console prompts identity fields and password twice, validates the same registration policy and creates a new 0600 SQL file with a salted BCrypt hash. It opens no server/DB/network, and never changes the signup flag. Review/apply that file through protected PostgreSQL `psql -v ON_ERROR_STOP=1` stdin on the approved private connection, then delete it. It contains a hash and identity metadata, so treat it as sensitive. Give each reviewer an individual credential through an approved private channel, never a shared admin login or screenshots. The helper refuses overwrite/symlink output; no emergency login/backdoor exists. Reset/revocation procedures and an access-request contact channel still need owner decisions.

Backup host dependencies are Python 3, age and AWS CLI v2, from approved packages/releases. Keep the age **private identity off-server** with a separately secured recovery copy. `/etc/belsen/backup-recipients.txt` holds public recipients only. `/etc/belsen/r2-credentials` is root-owned 0600 with a `[belsen-r2]` profile; use a dedicated Object Read & Write token scoped to a single private bucket. Do not grant bucket/account administration or enable public URLs/r2.dev. Administer lifecycle separately with a different approved credential. No upload token is embedded in Compose.

Copy `infra/production/backup.env.example` outside Git to `/etc/belsen/backup.env`, install scripts at `/opt/belsen/scripts/production/`, create `/var/lib/belsen-backups` root-owned 0700 and review/copy the five systemd units from `infra/production/systemd/`. After a manually verified first upload and real restore, enable the daily backup and hourly freshness timers with `systemctl enable --now belsen-backup.timer belsen-backup-freshness.timer`. Do not enable them until the private R2 endpoint/bucket/credentials, lifecycle and alert delivery are approved.

`backup.py` streams PostgreSQL 17 custom-format dumps straight into age; it never stages a plaintext dump. The script checks child exit status, encrypted SHA-256/size, upload HEAD metadata and a complete encrypted download hash before marking success, then retains two local encrypted files. It fails closed at 512 MiB per dump, 2 GiB local staging budget, 16 objects or 8 GiB remote prefix budget. The UTC timer runs daily at 02:15 with up to 15 minutes jitter; freshness beyond 26 hours fails and journals an alert. Concurrent invocations lock. Failed uploads do not silently delete backup history. Journal alerts **do not constitute remote notification**: connect and test an owner-selected delivery path before operating real data.

Approve an **eight-day expiry** lifecycle for prefix `belsen/production/postgres/`, abort incomplete multipart uploads after one day, and monitor lifecycle deletion lag. At one dump/day and the 512 MiB ceiling, eight daily objects use at most 4 GiB (about 4.3 decimal GB) before lag/retries, leaving headroom within a dedicated 10 GB Standard allowance. Other account usage is shared. The explicit remote count/byte cap fails rather than buying space; it is not a Cloudflare billing cap. Review backup size/count every day and failed freshness hourly; increase retention/limits only after budget approval.

For restoration, download the encrypted object using protected credentials, compare SHA-256 against the retained local manifest or verified private-object HEAD metadata, and decrypt with the **off-host** age identity into `pg_restore` on a new isolated PostgreSQL 17 database. For example, `age --decrypt -i <PRIVATE_IDENTITY_FILE> <ENCRYPTED_DUMP> | pg_restore --dbname=service=belsen-restore --no-owner --no-acl --exit-on-error` with shell pipefail; never point this at an existing development/production database. An age authentication failure, bad checksum, nonzero dump/restore exit, missing migration or mismatch blocks cutover. Re-establish database owner/default backup grants (dumps exclude ACLs), validate V1–V7/Hibernate/constraints/history and representative reports, invalidate restored sessions, and keep Coach disabled for ledger reconciliation as described above. Measure off-site key retrieval and restore time; a successful local synthetic rehearsal is not proof of R2 permissions, bucket privacy, lifecycle or disaster recovery.

Local commands: after the combined build and `docker build -t belsen-ai:deployment-prep-local .`, run `python3 scripts/production/verify-local.py --image belsen-ai:deployment-prep-local --age-dir <DIRECTORY_WITH_AGE_AND_AGE_KEYGEN>` and `python3 -m unittest discover -s scripts/production -p 'test_*.py'`. The stack uses unique disposable names, loopback high ports, copied configuration and Caddy internal CA explicitly trusted by Python. No certificate-validation bypass, public ACME, DNS/R2 call or existing database is used. Test helpers also require htpasswd to generate a synthetic fixture hash; actual account provisioning uses the Java console helper. Existing browser suites separately retain their pre-existing self-signed test-certificate handling; do not confuse them with the new CA-verified Caddy check.

### Local preparation verification, November 21, 2026

Starting repository: clean `main` at `ecb26acea663017b226a9b913625e31233004211`. Changes remain uncommitted and unstaged; this is not a new published release. Java 21.0.11, Node 24.22.1 and Docker Desktop 29.9.2 were used. No cloud resources, domains, DNS, R2 credentials or live Google/Groq calls were used.

| Local gate | Actual result |
| --- | --- |
| Frontend typecheck / ESLint / build | Passed; locked `npm ci` unchanged dependency versions |
| Frontend tests | 267 passed, including registration-policy UI cases |
| Maven verify | 299 passed: 86 unit/security/configuration and 213 PostgreSQL integration; no failures/errors/skips in completed rerun |
| PostgreSQL | Fresh V1–V7, Hibernate validate, disabled signup/approved-account sessions and unchanged existing regressions passed |
| Existing Chromium suites | 8 mocked shell + 6 mocked Coach + 24 real backend + 8 packaged HTTPS + 6 deterministic-provider cases = 52 passed |
| Backup transport/freshness fixtures | 5 unit tests passed, including encrypted-upload roundtrip fixture, corruption/bad hash rejection, secret permissions, stderr suppression and stale/future freshness rejection |
| Production Caddyfile | Offline validation passed using pinned image and no network |
| Local topology | Unique disposable Caddy/app/PG stack, no app/DB published ports, trusted local CA/hostname verification, registration-off 403, login, CSRF, non-superuser app role and seven migrations passed |
| Backup restoration | Actual pg_dump17 → age stream → decryption → separate empty PG17 database restored representative goal/user data and all seven migration records; corrupt ciphertext rejected |
| Image preparation | Native local image and linux/amd64 image built; x86 image ID `sha256:dda2cc088710baef596f595d9e6c592a36de02e25750d13f74138625061b934b`; no registry publication |
| Dependencies | Both npm audits reported zero vulnerabilities; OSV returned no matches for 152 resolved Java runtime/test coordinates |
| Artifact review | SPA and seven migrations included; local HTTP config, test launchers and fixture assets absent; intentional offline account utility present |

The new topology check uses a CA-verified Python HTTPS client, not a browser that bypasses certificate validation. Its local high-port redirect fixture is distinct from production ACME; the production configuration separately validates automatic redirects. Existing browser suites keep their explicit test-only registration enablement and pre-existing self-signed-certificate harness; they do not prove live Cloudflare/Let's Encrypt/Hetzner behavior. Scoped axe checks belong to those browser suites, not a new accessibility certification.

Initial interrupted Maven initialization failed because Docker was stopped; the completed rerun above is the evidence. Local topology work found and corrected a file-secret name precedence issue, replaced the failing bind-mounted shell bootstrap with SQL, narrowed backup grants and fixed conflicting subprocess-capture options that prevented backup creation. Later fixture-only redirect/copy/name-collision errors were investigated; failed runs are not counted as passes. No authentication bypass, constraint removal, old migration edit or certificate-validation disablement was used to make checks pass.

A short native Docker Desktop rehearsal observed approximately 361 MiB app RSS/cgroup usage, 48 MiB PostgreSQL and 17 MiB Caddy after startup and a small synthetic workflow. This is a sample, not a peak/load test, x86 benchmark, VM capacity guarantee or production memory measurement. Container ceilings remain 1536/768/128 MiB with swap ceilings equal to memory limits. Recheck on the actual CX23 before accepting a larger audience.

The current JAR SHA-256 is `adce6455a126338de00f2e46ab045f5b5a370ed616085b8e0141ccb067fb22ed`. Local image identities describe uncommitted preparation inputs, not an immutable deployment of the old Git SHA. Scan/patch the selected runtime images and host packages before release; npm/OSV results do not establish absence of OS vulnerabilities.

Remaining external gates: instance stock and all-in quote/tax/domain approval; Ubuntu/SSH/cloud and host firewall rehearsal; public certificate issuance/renewal and DNS; true external port scan and client-IP behavior; R2 private access/token/lifecycle/overage checks; encrypted off-site download/restore with the separately held identity; actual alert delivery; timed recovery/rollback and approved reviewer accounts/contact channel. Linux systemd units were reviewed locally but not executed on an Ubuntu server. Until these pass, this is **local deployment preparation**, not a publicly deployed or infrastructure-verified service.


Final local rehearsal completed successfully: HTTP redirect, forwarded-header overwrite, CA-verified HTTPS, closed signup, authenticated CSRF/session flows, database-scoped roles, encrypted backup CLI, separate PostgreSQL restore, ciphertext corruption rejection, app restart persistence and logout. Disposable rehearsal containers/networks/volumes were removed. Credential-pattern/artifact checks, local documentation links and `git diff --check` passed. No migration or dependency version changed. The index is empty and HEAD remains the starting B4 commit.

Changed-file scope (38 paths; nothing staged):

```text
.env.example
.gitignore
README.md
backend/src/main/java/com/belsenai/config/SecurityConfig.java
backend/src/main/java/com/belsenai/identity/controller/AuthController.java
backend/src/main/java/com/belsenai/identity/service/AccountService.java
backend/src/main/java/com/belsenai/identity/service/GoogleAccountService.java
backend/src/main/resources/application-local.yml
backend/src/main/resources/application.yml
docs/API.md
docs/ARCHITECTURE.md
docs/DECISIONS.md
docs/DEPLOYMENT.md
docs/ROADMAP.md
frontend/scripts/browser-backend.mjs
frontend/src/app/App.tsx
frontend/src/features/auth/AuthPage.test.tsx
frontend/src/features/auth/AuthPage.tsx
backend/src/main/java/com/belsenai/identity/security/RegistrationPolicy.java
backend/src/main/java/com/belsenai/identity/tools/PreviewAccountSql.java
backend/src/test/java/com/belsenai/identity/RegistrationPolicyIT.java
backend/src/test/java/com/belsenai/identity/RegistrationPolicyTest.java
backend/src/test/java/com/belsenai/identity/tools/PreviewAccountSqlTest.java
infra/production/Caddyfile
infra/production/backup.env.example
infra/production/compose.yaml
infra/production/environment.example
infra/production/postgres-init.sql
infra/production/proxy.caddy
infra/production/systemd/belsen-backup-alert.service
infra/production/systemd/belsen-backup-freshness.service
infra/production/systemd/belsen-backup-freshness.timer
infra/production/systemd/belsen-backup.service
infra/production/systemd/belsen-backup.timer
scripts/production/backup.py
scripts/production/run-backup.sh
scripts/production/test_backup.py
scripts/production/verify-local.py
```
