# Production integration and deployment preparation

B6 packages the existing website and API into one Spring Boot JAR. No public deployment has been performed. AI Coach, live Google verification, immutable report finalization and CI automation remain outside this checkpoint.

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

Missing database settings, missing/invalid origin, absent SPA assets, TLS initialization failure, weakened cookies or an accidental `local` profile prevent production startup. Flyway runs before Hibernate schema validation; schema errors prevent successful startup. V1–V5 remain unchanged, and Spring Session's schema initializer remains disabled. Do not use Hibernate schema updates or Flyway clean in production.

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

The public HTML routes are exactly `/`, `/auth`, `/auth/callback`, `/app` and the six implemented `/app/...` pages. They load the SPA; the existing React gate checks the session and every private API still authenticates the owner. Only GET/HEAD documents and single-segment hashed assets are public. There is no catch-all SPA fallback and no generic static-file mapping. Unknown API/actuator/private-file paths remain JSON errors. OAuth callback/authorization routes remain controlled by the existing Google-enabled condition, never mapped to HTML.

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
