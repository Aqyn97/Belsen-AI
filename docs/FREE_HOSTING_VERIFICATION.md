# Portfolio release preparation — verification record

November 22, 2026. Baseline: `1322f44358d9961b0d1e84380646f05e2cdd399b`, branch `main`. **Local preparation only; nothing is publicly deployed.** The owner authorized a reviewed private Git commit and normal push with `feat(portfolio): finalize free Cloudflare showcase`. This authorizes Git publication only; no website deployment, account, purchase, DNS change, image publication or infrastructure provisioning is authorized or claimed.

## Current decisions

Cloudflare Pages Free is the standalone public portfolio target. Oracle Always Free ARM is a conditional full-stack proposal. Hetzner remains the unchanged optional paid route. Render Free is **not approved for production**; its experiment is preserved without further tuning or testing in this package.

The configured repository remains `https://github.com/Aqyn97/Belsen-AI`. It returned 404 to anonymous HTTP and the connected API. Per owner instruction, public source links are hidden; no different repository URL or visibility change was substituted. The static showcase can be published independently after owner approval. The application launch link also remains absent.

## Focused checks and evidence freshness

Final release checks used Node 24.22.1: the six-file static build and all seven build tests passed. The focused desktop/mobile Chromium suite was rerun: all six tests passed, including axe checks with zero reported violations, responsive widths, hidden links, screenshot loading and explicit 404 behavior. Frontend typecheck and ESLint also passed again. The approved forward-only correction replaces the two current workstation paths; the owner accepts the historical disclosure and keeps the repository private. No application behavior changed, so the completed application regression and ARM compatibility evidence below was reused rather than described as fresh.

### Completed checks

| Check | Actual result |
| --- | --- |
| Node 24 static build tests | **7 passed**, zero failed |
| Static production build | Passed; six files, 404,410 bytes total; preceding local timing 0.273 seconds; successful build reconfirmed on resume |
| Desktop/mobile Chromium | **6 passed**, zero failed |
| axe accessibility | Zero violations for the scanned WCAG 2.0/2.1/2.2 A/AA rules, both browser projects |
| Responsive behavior | No horizontal overflow at 320, 768 and 1440 pixels; desktop/mobile views visually inspected |
| Static isolation | No external requests during page load; no JavaScript, authenticated API connection, public GitHub link or Launch App link in the default build |
| Navigation and errors | Keyboard/skip navigation passed; unknown routes and API/private-file paths return explicit 404 pages |
| Screenshot review | Both original PNGs visually inspected; fictional fixtures, no private account data/credentials; PNG chunks contain only IHDR/IDAT/IEND |
| Frontend typecheck and ESLint | Passed, using Node 24 |
| Both npm audits | Zero reported vulnerabilities for runtime-only and full locked dependency sets |
| ARM64 application image build | Passed using the unchanged Dockerfile and existing verified production JAR |
| Java runtime | Native `aarch64`, Temurin Java 21.0.13 |
| Packaged native dependency review | 95 JAR libraries; no `.so`, `.dll`, `.dylib` or `.jnilib` entries found inside them |
| Final Oracle Compose model | Passed; three ARM64 images, correct isolated project/subnet, exact private proxy peer, normal production profile and private app/database ports |
| Python fixture syntax | Passed; the fixture also executed successfully |
| Git whitespace/artifact/credential review | Passed for intended worktree content, with the documented historical privacy finding below |

The browser checks use the local static server and actual generated files. They do not establish Cloudflare edge behavior, a public deployment, assistive-technology certification, Oracle capacity or live Google/Groq success. No dependency was added or version changed. A complete container-OS advisory scan was not performed; the installed Docker Scout requires an account login, and no account was created.

## Native local ARM64 rehearsal

Command used the separate `scripts/oracle/verify-local.py` fixture and a local `belsen-ai:oracle-arm64-local` image. Docker Compose was 5.6.1; the overlay requires at least 2.24.4. All three running service images were confirmed ARM64 through their actual image IDs. The final overlay pins the same PostgreSQL digest used by the successful rehearsal; final configuration and image identities were checked again after pinning.

- Cold topology readiness: **22.2 seconds**.
- Restart readiness: **7.8 seconds**; the same JDBC session and goal data remained valid.
- Application cold-start cgroup peak: **476,528,640 bytes / 454.45 MiB**, against a 1,536 MiB limit.
- Observed PostgreSQL peak: **209,760,256 bytes / 200.04 MiB**; Caddy peak: **75,816,960 bytes / 72.30 MiB**. These are separate service peaks, not a simultaneous host-memory measurement.
- No OOM kill or unexpected service restart was observed.
- CA/hostname-verified local HTTPS, forwarding-header overwrite, closed signup, login, private authenticated reads, CSRF rejection/acceptance and logout passed.
- Flyway V1–V7 and Hibernate startup validation passed using PostgreSQL 17.11. App role was non-superuser; app/database had no published ports.
- Real encrypted age dump, separate PostgreSQL restore, restored row/migration checks and corrupted-ciphertext rejection passed.
- Disposable test containers, networks and volumes were removed afterward.

The native ARM host was a local workstation, **not Oracle Ampere A1**. The test used the normal production profile, production JVM/resource settings and the Oracle overlay; no Render small-heap profile or third-party inference was involved. It supports ARM compatibility, not A1 latency, cloud firewall/certificate readiness, arbitrary workload capacity or uptime guarantees.

Local image manifest identity: `sha256:62d5b1328d56e97e0f49e9fde7636585c7265e51223c80dbd2fb6f6dfb0306ab`. It was not pushed. PostgreSQL and Caddy are digest-pinned in the selected model. Before any later publication/transfer, the owner must review the actual image identity and privacy/operational gates.

## Reused evidence — not rerun as fresh results

The preceding preparation recorded **307 Maven tests** (92 unit + 215 PostgreSQL integration), **267 frontend unit tests**, and **52 existing Chromium tests** (8 shell, 6 mocked Coach, 24 real Java/PostgreSQL, 8 packaged HTTPS, 6 real-backend Coach fixture). Those records were inspected and remain applicable to unchanged product behavior. They are not new passes from this focused package. No full regression lifecycle was repeated because authentication, domain logic, schemas and application UI behavior were not changed here.

The old Render profile's passing 1 CPU / 512 MiB rehearsal took 42.7 seconds cold and 32.1 seconds to restart, but peaked at 488.35 MiB: only 23.65 MiB (4.6%) headroom. Its final emulated-AMD64 0.1 CPU run aborted about 13 minutes into startup with `spring.datasource.hikari.connection` binding / `java.net.SocketException: Broken pipe`, before HTTP readiness. The failure was not resolved or retested in this task. Do not describe Render as approved or these results as live platform verification.

## Public-source audit and unresolved gates

[PUBLIC_SOURCE_AUDIT.md](PUBLIC_SOURCE_AUDIT.md) records the review of 243 baseline tracked paths, 17 baseline reachable commits, 418 distinct committed blobs, 77 additional unreachable local blobs, local review snapshots, the 33 reviewed release paths and commit metadata/messages. No confirmed real token, password, embedded private key or database dump was found. Literal candidates were reviewed as fixtures/autocomplete labels; author contacts use GitHub noreply addresses. Intentional infrastructure templates expose private topology/settings and secret paths, not actual cloud secrets or live public addresses.

**Accepted low historical disclosure:** the two workstation paths formerly at `docs/AI_COACH_DESIGN.md:280` and `:356` remain in the five historical commits enumerated in the audit. The owner explicitly accepted that risk and approved replacing both current references with `$HOME/.docker/run/docker.sock`. This forward-only documentation correction is included in this release; no history rewrite or visibility change is permitted. The repository stays private and both public GitHub links and the application launch link remain hidden. Remote-only GitHub content was inaccessible and is outside the local audit.

Oracle remains conditional on owner acceptance of card/identity verification, actual Always Free A1 availability, aggregate account allowances, public IPv4 eligibility, an approved free/existing HTTPS hostname, secure host/cloud firewall configuration and independent operational recovery. Idle reclamation remains a real risk. None was provisioned or claimed tested. [Manual steps](ORACLE_ARM.md) make those gates explicit. The [Pages publication steps](FREE_HOSTING.md#exact-manual-pages-publication-steps--not-executed) require separate owner approval; no cloud action is automated.

## Reviewed file scope and preservation

All 25 original uncommitted paths were reviewed and retained. The original Render/Neon prose was preserved in RENDER_EXPERIMENT.md before FREE_HOSTING.md became the static-first runbook. The experimental Java guard, profile, fixture and YAML receive explicit unvalidated/not-approved labels; their behavior was not optimized. The three backend foundation tests, genuine PNGs, static 404/headers, existing local static server and frontend test inclusion remain useful and preserved.

The unchanged root Dockerfile supports ARM64; no backend/frontend scaffold, product feature, dependency, historical migration or paid VPS configuration was rewritten. `infra/production/`, `scripts/production/`, backend dependency management and V1–V7 have no diff against HEAD. Source files were not discarded to obtain passing checks.

The reviewed release scope contains **33 paths** (six tracked modifications plus 27 new preparation files). Full inventory:

```text
README.md
backend/src/main/java/com/belsenai/config/RenderConfig.java
backend/src/main/resources/application-render.yml
backend/src/test/java/com/belsenai/foundation/FreePoolIT.java
backend/src/test/java/com/belsenai/foundation/PostgresTlsIT.java
backend/src/test/java/com/belsenai/foundation/RenderConfigTest.java
docs/AI_COACH_DESIGN.md
docs/ARCHITECTURE.md
docs/DECISIONS.md
docs/DEPLOYMENT.md
docs/FREE_HOSTING.md
docs/FREE_HOSTING_VERIFICATION.md
docs/ORACLE_ARM.md
docs/PUBLIC_SOURCE_AUDIT.md
docs/RENDER_EXPERIMENT.md
frontend/e2e-showcase/showcase.spec.ts
frontend/playwright.showcase.config.ts
frontend/scripts/showcase-server.mjs
frontend/tsconfig.json
infra/free/render.yaml.example
infra/oracle/compose.yaml
infra/oracle/environment.example
scripts/free/verify-memory.py
scripts/oracle/verify-local.py
showcase/404.html
showcase/README.md
showcase/_headers
showcase/assets/analytics.png
showcase/assets/dashboard.png
showcase/build.mjs
showcase/build.test.mjs
showcase/index.html
showcase/styles.css
```

Staging is limited to the reviewed manifest after the checks pass. The final commit identity and push result are reported separately; no self-referential commit SHA is embedded in this record. Generated `showcase/dist`, frontend test output, Java outputs, temporary credentials and certificates are excluded from Git; only the two intentionally reviewed PNGs are source assets. No GitHub, Oracle, Cloudflare, Google or Groq account/security setting was modified.
