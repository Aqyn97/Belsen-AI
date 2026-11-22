# Render/Neon experiment — archived, not approved

**Render Free is not approved for production use. Do not follow the historical deployment sequence below.** The current release route is the standalone [Cloudflare showcase](FREE_HOSTING.md); [Oracle ARM](ORACLE_ARM.md) is a separate proposal. No further Render tuning or deployment is part of the current work.

The preceding 1 CPU rehearsal with a 160 MiB heap, 512 MiB limit, PostgreSQL TLS and three connections passed basic workflows, restart and database recovery, but peaked at 488.35 MiB (4.6% headroom). The final 0.1 CPU run aborted about 13 minutes after container launch while binding `spring.datasource.hikari.connection`, with `java.net.SocketException: Broken pipe`. HTTP readiness did not pass. The underlying failure is unresolved; this is not evidence of a successful Render deployment. AMD64 was emulated on an ARM workstation.

Preserved files are `backend/src/main/java/com/belsenai/config/RenderConfig.java`, `backend/src/main/resources/application-render.yml`, `backend/src/test/java/com/belsenai/foundation/RenderConfigTest.java`, `infra/free/render.yaml.example` and `scripts/free/verify-memory.py`. The profile is opt-in and requires `production`; neither the Oracle overlay nor the unchanged Hetzner base activates it. The small-pool and PostgreSQL TLS tests remain useful independent checks. They do not prove a universal minimum connection count or free-platform suitability.

The old proposal is retained below to preserve the reviewed work, including Neon TLS and backup considerations. Its selection, launch commands and readiness assumptions have been superseded. Do not treat it as the current runbook.

---

# Historical proposal — superseded

Status: **local preparation, not deployed**. No cloud account, image publication, service, DNS change or provider request has been made by this work. Public deployment requires separate approval. The existing [Hetzner runbook](DEPLOYMENT.md#hetzner-controlled-portfolio-preview) and all its configuration remain available unchanged.

## Architecture and cost boundary

| Layer | Selected service | Content |
| --- | --- | --- |
| Public portfolio | Cloudflare Pages **Free**, `<project>.pages.dev` | `showcase/dist`: static HTML, CSS and genuine screenshots; no Functions |
| Interactive application | One Render Web Service **Free**, `<service>.onrender.com` | Existing Java 21 image: Spring Boot, bundled React, REST and JDBC sessions |
| Persistent data | One Neon **Free** project, explicitly PostgreSQL **17** | Direct certificate-verified JDBC endpoint; existing Flyway V1–V7 |
| Independent backup | Owner's existing workstation and separate existing storage | PostgreSQL 17 logical dump encrypted with age; no mandatory storage subscription |

Pages only links to Render. Authentication, cookies, CSRF and every authenticated request stay on the Render origin. No CORS, cross-origin authenticated frontend, database proxy, extra public server or keepalive service is added. The application continues to serve only its explicitly allowed SPA routes. The showcase includes `404.html` so Pages does not interpret unknown paths as application documents.

Do not attach a payment method, activate credits, select paid plans, purchase domains or enable optional paid products. No R2, Render Postgres, disks, cron jobs, workers, Neon Auth, Functions or AI Gateway are required. Existing optional Groq ranking, Google OIDC and public registration remain disabled; this profile refuses to enable them. Use the GitHub source and screenshots if a free service is suspended; do not silently upgrade.

### Limits checked November 22, 2026

Provider terms can change; recheck them before creating anything.

- Render: 512 MB / 0.1 CPU Free compute, sleeps after 15 minutes idle, ephemeral filesystem, 750 instance-hours shared per workspace per month. Cold requests can take minutes. It may restart at any time; no shell/one-off jobs on Free. Bandwidth/build allowance exhaustion without a payment method suspends services/builds; unusually high external-database traffic may also suspend service. Do not use it for an availability-sensitive production workload. [Free limitations](https://render.com/docs/free), [compute description](https://render.com/articles/from-side-project-to-production-scaling-your-first-app).
- Neon: Free currently includes 100 CU-hours per project per month, 1 GB database storage per project (20 GB account total), 5 GB public transfer per project and a six-hour restore window. Scale-to-zero after five minutes cannot be disabled on Free. Exhaustion restricts compute, transfer or writes; managed restore is not an independent backup. [Plans and exhaustion behavior](https://neon.com/docs/introduction/plans).
- Pages: Free permits 500 builds/month, one concurrent build, 20,000 files/site and 25 MiB per asset. This showcase needs no remote build, runtime packages or Functions. [Limits](https://developers.cloudflare.com/pages/platform/limits/).

The no-payment-method policy is intentional. If signup requires a card or offers only a trial in the owner's region/account, stop. Do not substitute a billable service. A public registry must also remain free without a payment method; otherwise stop before publishing an image.

## Local resource assessment

The free profile uses a 160 MiB heap, 128 MiB metaspace cap, 48 MiB code cache, 32 MiB direct-buffer cap, 512 KiB thread stacks and one JVM-visible processor. Heap is only part of container memory: native allocations, stacks, libraries and page cache also count. These caps cannot guarantee a total under 512 MiB under arbitrary load. OOM exits rather than continuing in an uncertain state. Render supplies the platform memory limit; do not assume a Blueprint can increase it while keeping Free.

Tomcat is limited to 20 request threads / two spare threads. Hikari has three connections, zero minimum idle, 30-second idle timeout, five-minute max lifetime and no keepalive. Ordinary session expiry remains 30 minutes. Daily JDBC-session cleanup replaces the minute-level default in this profile; authorization still rejects expired sessions immediately. Cleanup is best effort while the app is awake; infrequently running previews can retain expired rows until a later cleanup. Watch storage and perform approved maintenance rather than adding a worker or artificial wake traffic.

See [local verification evidence](FREE_HOSTING_VERIFICATION.md) for measured cold startup, memory, restart and fault-recovery results. A local ARM container is not proof of Render's AMD64 startup behavior, edge proxy contract, Neon wake latency or production capacity. Narrow headroom and slow cold starts are explicit portfolio limitations.

## Render configuration and HTTPS launch gate

Reference: `infra/free/render.yaml.example`. It is deliberately not an automatically applied root Blueprint. The image identity is a replacement marker. No remote creation or image push is part of this task.

Activate **`production,render`**, in that order. `render` alone fails. The additive profile binds `0.0.0.0:${PORT:10000}`, uses `RENDER_EXTERNAL_URL` as the HTTPS origin, and leaves Secure/HttpOnly/SameSite=Lax cookies, CSRF, private authorization, migrations and schema validation intact. Provider TLS ends at the ingress; only native forwarding from explicitly trusted exact IPv4 peers is accepted. No application or administrative port is separately published.

**Unresolved external gate: Render ingress trust.** No verified, stable ingress peer list or header-sanitization contract was available from this local task. Do not use `.*`, all RFC1918 networks, the entire internet, `ForwardedHeaderFilter`, arbitrary X-Forwarded-For's first entry or Cloudflare headers as a shortcut. `BELSEN_TRUSTED_PROXY_REGEX` is mandatory and accepts only exact IPv4 addresses, e.g. `10[.]1[.]2[.]3|10[.]1[.]2[.]4` (illustrative, never deploy these fixture addresses). An operator must obtain/verify actual last-hop peers, network isolation and header overwrite behavior with Render before launch. Peer changes require a reviewed configuration update. IPv6 ingress would require a separately reviewed extension; this template fails closed rather than guessing.

The ingress must overwrite client-controlled forwarded scheme/host headers, retain only a verifiable client-address chain and prevent direct bypass of its TLS boundary. Confirm hostile `Forwarded`, `X-Forwarded-Proto`, `X-Forwarded-Host` and `X-Forwarded-For` inputs cannot change request security, redirects or throttling identity. If Render cannot provide a safe stable contract, **this hosting option remains blocked**; do not weaken production security. Render's [proxy guidance](https://render.com/articles/how-to-proxy-analytics-through-your-own-domain) itself cautions that generic private ranges are illustrative, not verified ingress addresses.

Set these through Render's protected service environment UI, never a tracked `.env` or Docker build argument:

| Key | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `production,render` |
| `JAVA_TOOL_OPTIONS` | Exact bounded options from the reference YAML |
| `BELSEN_TRUSTED_PROXY_REGEX` | Operator-verified exact ingress peer expression |
| `SPRING_DATASOURCE_URL` | Direct Neon JDBC URL below, without credentials |
| `SPRING_DATASOURCE_USERNAME` | Dedicated application role |
| `SPRING_DATASOURCE_PASSWORD` | Secret for that role |
| `BELSEN_REGISTRATION_ENABLED` | `false` |
| `BELSEN_GOOGLE_ENABLED` | `false` |
| `BELSEN_COACH_ENABLED` | `false` |

Render supplies `PORT` and `RENDER_EXTERNAL_URL`; do not use the Pages domain as the application origin. Do not print environments or expose configuration through actuator endpoints. Protected environment variables are visible to authorized service operators; keep account access restricted. No application secret or password belongs in the image, public showcase or client bundle.

`/api/v1/health` returns only `{"status":"UP"}`. Startup waits for Flyway and Hibernate. The running health check deliberately does not query PostgreSQL, avoiding database wakeups from liveness probes. A healthy process does not prove database readiness; check an authenticated read separately. Render's [health contract](https://render.com/docs/health-checks) requires a response within five seconds and allows 15 minutes for a new deployment to become healthy. Do not return success before application initialization to hide a startup failure.

## Neon connection and role preparation

Choose PostgreSQL **17** explicitly; a provider's current default major version may differ. Pick a region near the chosen Render region. Use one project/database, with a dedicated non-superuser application login and a separate read-only backup login. Do not give the runtime the project owner's credential or `neon_superuser` membership.

Use the **direct** hostname (disable the pooled-connection toggle):

```text
jdbc:postgresql://<DIRECT_EP_HOST>.neon.tech:5432/<DATABASE>?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=15&socketTimeout=30&tcpKeepAlive=true
```

Replace the whole host with the console's `ep-...<region>...neon.tech` direct host. Database names in this narrow profile use letters, digits, underscores or hyphens. Keep the query exactly as shown: the startup guard rejects pooled hosts, URL credentials, TLS downgrades and extra/duplicate parameters. Credentials use separate variables. `DefaultJavaSSLFactory` uses the image's Java trust store; `verify-full` also checks the hostname. Never use `require`, `NonValidatingFactory` or a custom permissive hostname verifier. [pgJDBC SSL](https://jdbc.postgresql.org/documentation/ssl/).

One direct Hikari pool serves both application/JDBC sessions and Flyway, keeping connection usage bounded and preserving migration session/advisory-lock semantics. Neon transaction pooling is useful for connection-per-request/serverless workloads but has session-state limitations; migrations and pg_dump use direct connections. Splitting migration and runtime pools is unnecessary for this single-instance preview. [Neon pooling guidance](https://neon.com/docs/connect/connection-pooling).

The following is an **operator procedure after approval**, using a trusted PostgreSQL 17 `psql` workstation connection to the project owner, with certificate verification. Set passwords interactively with `\password`, never inline SQL or arguments. Confirm the managed owner has permission for these operations before proceeding; stop on errors. Use a new, empty dedicated database, not an existing user database.

```sql
CREATE ROLE belsen_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
CREATE ROLE belsen_backup LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
\password belsen_app
\password belsen_backup
CREATE DATABASE belsen OWNER belsen_app;
```

Connect as the project owner to the new `belsen` database, then:

```sql
ALTER SCHEMA public OWNER TO belsen_app;
REVOKE ALL ON DATABASE belsen FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE belsen TO belsen_backup;
GRANT USAGE ON SCHEMA public TO belsen_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE belsen_app IN SCHEMA public GRANT SELECT ON TABLES TO belsen_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE belsen_app IN SCHEMA public GRANT SELECT ON SEQUENCES TO belsen_backup;
```

Check both logins have no elevated memberships, role-creation/database-creation privileges or superuser flag. App owns its schema for Flyway, so it is not a read-only role. Use a project owner's private console only for role/database management. Flyway applies V1–V7 unchanged at app startup; Spring Session schema initialization is `never` and Hibernate remains `validate`. Verify seven successful migrations, no pending/failed migration and actual role isolation before admitting reviewers. Existing populated databases need a reviewed ownership/grant conversion, not this empty-database procedure.

Pool acquisition is bounded at 30 seconds; JDBC connect and socket deadlines are 15/30 seconds. An idle pool can empty without synthetic keepalives; a real next request establishes a connection and wakes Neon. Database outages may yield a safe 5xx or timeout, including when JDBC-session lookup fails. The browser must not interpret a failed write as success. Restore connectivity and refresh; inspect history before repeating a non-idempotent action. Do not add automatic mutation retries. Actual Neon suspend/resume latency and quota exhaustion remain external checks.

## Independent encrypted backup and restore

No Render disk or scheduled paid job is needed. Perform backups from an existing trusted workstation using PostgreSQL **17** clients, age and an existing separate storage device/location. Availability depends on that operator; there is no automatic off-site-backup claim. Run before each approved release and at an owner-selected recurring cadence while data matters. Keep at least two verified generations in separate existing locations, inspect timestamps and available capacity, and cap retention manually; do not let unattended dumps fill the disk.

Outside this repository create an owner-only directory (`0700`), `pg_service.conf` and `.pgpass` (`0600`). Service entries contain the **direct** host, port 5432, database, correct application/backup role, `sslmode=verify-full`, `sslrootcert=system` and `connect_timeout=15`. Use a current PostgreSQL 17 libpq build with system-root support, or an explicitly maintained trusted CA file. Passwords go only in `.pgpass`; escape its `:` and `\` characters per libpq rules. Set `PGSERVICEFILE` and `PGPASSFILE` to those private files. Never use a password-bearing URL, `PGPASSWORD`, shell tracing, committed file or command-line password. Services `belsen-backup` and `belsen-restore` must point respectively to the source read-only account and a **different empty disposable recovery database** with the restoration owner.

Generate an age identity interactively/offline; keep the recovery private key separately from ciphertext and the database. Only its public recipient is needed for backup. The following Bash example uses actual operator-selected private paths and placeholder filenames, not repository paths:

```bash
set -euo pipefail
umask 077
# Export PGSERVICEFILE/PGPASSFILE to private files first; no credentials in these values.
# Set a unique ABSOLUTE output path outside the repository and an age recipients file.
test ! -e "$BACKUP_FILE"
test ! -e "$BACKUP_FILE.partial"
pg_dump --dbname='service=belsen-backup' --format=custom --no-owner --no-acl --no-password \
  | age --recipients-file "$RECIPIENTS_FILE" > "$BACKUP_FILE.partial"
mv "$BACKUP_FILE.partial" "$BACKUP_FILE"
shasum -a 256 "$BACKUP_FILE"
```

On any failure delete only the uniquely named `.partial`, retain prior good backups and investigate. `pipefail` prevents accepting ciphertext from a failed dump. Copy ciphertext plus its checksum to the independent location, verify it there, and only then apply retention. No plaintext dump is stored. The backup contains password hashes and session data: encrypt it, restrict access and never publish it.

Test a real recovery, not just a successful upload/decrypt exit code:

```bash
set -euo pipefail
age --decrypt --identity "$RECOVERY_IDENTITY" "$BACKUP_FILE" \
  | pg_restore --dbname='service=belsen-restore' --no-owner --no-acl --exit-on-error --no-password
```

The target must be an empty isolated PostgreSQL 17 database, never the live service. Treat a partially restored target as unusable after **any** error; discard it and retry on a fresh target. Verify Flyway V1–V7, row counts, constraints, append-only protections, business queries and application schema validation. Recreate app/backup ownership and grants explicitly because `--no-owner --no-acl` intentionally omits them. Before a restored database becomes live, invalidate restored sessions (`TRUNCATE spring_session CASCADE` as an approved operator) and rotate credentials; backups can otherwise resurrect old sessions. Encrypted restore/corruption rejection is exercised locally by the disposable memory rehearsal. No actual Neon export, independent storage copy or disaster-recovery SLA is claimed.

## Approved individual demonstration accounts

Keep signup disabled. Render Free has no SSH/one-off shell. Run the existing offline Java helper on the operator's trusted workstation, following [the controlled-account procedure](DEPLOYMENT.md#hetzner-controlled-portfolio-preview):

```text
java -Dloader.main=com.belsenai.identity.tools.PreviewAccountSql -cp <VERIFIED_JAR> org.springframework.boot.loader.launch.PropertiesLauncher <PRIVATE_NEW_SQL_PATH>
```

The console prompts identity/timezone/password, validates the existing policy and creates new `0600` SQL with a salted BCrypt hash; it opens no application or database connection. Apply the reviewed SQL through a protected direct TLS `psql` application-role connection with `ON_ERROR_STOP=1`, then remove it. Do not place the SQL or passwords in Git, screenshots, arguments, Render logs or the landing page. Deliver individual credentials privately through an owner-approved channel. No default/shared admin account, registration bypass, public provisioning endpoint or automatic fictional-data seed is introduced.

Populate fictional goals/tasks/habits only through the existing authorized interface as that reviewer. Use no personal production data. Password recovery/email verification remain unimplemented; the owner must decide account revocation/reset and the private access-request process before inviting reviewers.

## Manual setup sequence — not executed

1. **Review and approve separately.** Confirm all three accounts offer the actual Free plan without a card, trial or automatic paid fallback. Resolve ingress trust and the account-access process. Keep the current local changes uncommitted until owner review.
2. **Build locally:** use Node 24, Java 21 and Docker; run `./scripts/build-production.sh`. Build the existing root Dockerfile for `linux/amd64` with `docker build --platform linux/amd64 -t <PUBLIC_REGISTRY_IMAGE:REVIEWED_TAG> .`. Verify its package and image. The current task builds locally only; image publication needs approval. [Render image requirements](https://render.com/docs/deploying-an-image).
3. **Neon console:** create one Free project with PostgreSQL 17, select a nearby region, create the dedicated database/roles above, retain scale-to-zero and obtain the direct endpoint. Store credentials privately. Do not add Auth, Functions, replicas, extra products or a paid plan. Confirm TLS/roles with a trusted workstation before starting the app.
4. **Public registry, only after approval:** publish the verified AMD64 image to an actually free public registry, record its immutable digest, and verify anonymous pull. Never put secrets in image layers. A free registry account/access check is still external; no push is performed here.
5. **Render dashboard:** New → Web Service → Existing Image. Paste the reviewed digest, select **Free**, one instance and a nearby region. Enter the protected environment values above; health path `/api/v1/health`. No disk, worker, cron, pre-deploy job or paid compute. Existing-image deployments are manual; do not add deploy hooks. Do not create the service until proxy-trust verification can be completed safely. Compare final UI settings with the reference YAML before approval. Creating a service starts deployment; this step requires the separate authorization.
6. **Verify Render before linking it:** HTTPS and exact origin, seven migrations, Hibernate validation, health under five seconds, Secure/HttpOnly/Lax cookie, closed signup, owner-safe login/session/CSRF, direct SPA refreshes, unknown route rejection, malicious forwarded-header behavior, no public administration/configuration and successful restart. Inspect memory/OOM events, actual cold start under the 15-minute readiness window, actual Neon suspend/wake, acquisition/socket timeout and expired-session recovery. No live Google/Groq verification is implied.
7. **Build showcase locally:** `node showcase/build.mjs` creates a no-live-app default. After the actual app passes launch gates, set only `BELSEN_APP_URL=https://<ACTUAL_SERVICE>.onrender.com` for this command. It accepts an exact HTTPS provider origin; no credentials, path or trailing slash. The output is `showcase/dist`, with no dependency installation or API calls. Inspect it before publication.
8. **Cloudflare dashboard:** Workers & Pages → Create application → Pages → **Direct Upload / Upload assets**. Choose a project name, upload the contents of `showcase/dist`, use the free `pages.dev` domain and no Functions/custom domain/billing integration. Do this only after separate publication approval. Direct Upload avoids Git-triggered deployments; it cannot later become a Git-integrated project without creating a different project. [Direct Upload instructions](https://developers.cloudflare.com/pages/get-started/direct-upload/).
9. **Post-publication review:** test desktop/mobile/keyboard, security headers and real image loading, ensure the launch link navigates to Render rather than calling its API, confirm Pages still loads with Render sleeping, verify explicit 404s and publish only the actual verified URLs in README. Maintain manual backups, quota checks and a written account-access policy. Do not run keepalives or promise uptime.

URL placeholders, intentionally not active links:

```text
Portfolio:   https://<ACTUAL_PROJECT>.pages.dev
Application: https://<ACTUAL_SERVICE>.onrender.com
```

No mandatory hosting payment is designed into this architecture. Continued free availability, account eligibility, resource headroom and provider contract verification remain conditions, not guarantees.
