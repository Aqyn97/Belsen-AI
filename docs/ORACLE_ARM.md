# Proposed Oracle Always Free ARM demonstration

Status: **ARM64 compatibility verified locally; Oracle deployment not authorized or verified**. The selected public portfolio is the independent [Cloudflare Pages showcase](FREE_HOSTING.md). This proposal does not require Oracle to publish that static site. No Oracle account, resource, public IP, DNS record or certificate request was created.

## Eligibility and billing gates

As checked November 22, 2026, Oracle's [Always Free resource page](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm) specifies **1,500 OCPU-hours and 9,000 GB-hours/month**, equivalent to **2 A1 OCPUs / 12 GB RAM** for a Free tenancy. Resources must be in the home region. ARM capacity can be unavailable. Combined boot/block storage is 200 GB, with five volume backups. Over seven days, idle reclamation considers CPU 95th-percentile, network and A1 memory utilization below 20%. A lightly used portfolio is exposed to reclamation; do not generate artificial load to evade it.

Some older or paid-account documentation still describes 4 OCPUs / 24 GB or larger allowances. Use the current Free-tenancy limit and the actual account's Always Free eligibility, not those larger figures. The [Free Tier lifecycle documentation](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier.htm) warns that excess trial A1 instances can be disabled and later deleted. No trial credits are needed or permitted by this proposal.

**Oracle generally requires phone and credit/debit-card verification.** Its [FAQ](https://www.oracle.com/cloud/free/faq/) describes temporary authorization holds and account eligibility rules. This is not a cardless hosting option. The owner must decide whether to accept verification before a separate account task; no card information is requested or handled here. Keep the account on Free; do not upgrade to Pay As You Go to solve capacity shortages. If an Always Free resource, IPv4 address or required feature is unavailable at $0, stop rather than substitute paid infrastructure.

Proposed initial allocation, subject to actual eligibility: one **VM.Standard.A1.Flex, 2 OCPUs, 8 GB RAM**, an Always Free-eligible Ubuntu 24.04 ARM64 image, and one 50 GB boot volume. A 31-day month uses 1,488 OCPU-hours and 5,952 GB-hours. Aggregate all other tenancy resources before checking allowances. Eight GB is a working recommendation, not a reservation, stock claim or guarantee. Do not choose the 1 GB AMD micro shape for this Java/PostgreSQL topology.

Use compartment quotas, least-privilege IAM, MFA and billing/usage notifications; notifications are not hard spending caps. Inspect every console cost estimate and resource label before approval. Avoid paid marketplace images, load balancers, NAT gateways, extra volumes, cross-region copies, excess backups, extra public addresses and optional services. Do not rely on automatic billing recovery or upgrades. Account abandonment and reclamation can interrupt access; keep recoverable copies outside Oracle.

## ARM64 package and dependencies

The existing root Dockerfile already supports ARM64 through its digest-pinned Temurin multi-platform base. No Dockerfile or application rewrite was needed:

```sh
docker build --platform linux/arm64 -t belsen-ai:oracle-arm64-local .
docker run --rm --platform linux/arm64 --entrypoint java belsen-ai:oracle-arm64-local -version
```

The build uses the existing verified production JAR, whose bytecode targets Java 21. The local image reports Java 21.0.13 and `os.arch=aarch64`. Its 95 packaged dependency JARs contain no `.so`, `.dll`, `.dylib` or `.jnilib` entries; native code comes from the architecture-specific JVM/OS image. Spring, Hibernate, Flyway and the PostgreSQL JDBC driver are used without an x86-only application library. Full startup, seven migrations, Hibernate validation and authenticated queries passed locally on ARM64. This does not establish every possible workload's capacity or a complete vulnerability clearance.

The Oracle override pins the same tested PostgreSQL 17.11 image identity; the existing Caddy 2.11.9 digest also resolves to ARM64. The rehearsal verified each running image's architecture rather than assuming a tag was multi-platform. [Exact results](FREE_HOSTING_VERIFICATION.md) distinguish native local ARM from untested Oracle A1 hardware.

## Separate Compose model

`infra/oracle/compose.yaml` is an override, **not a standalone stack**. Merge it after the unchanged production base. It selects `linux/arm64` for all three services, uses project `belsen-ai-oracle-preview`, changes the proxy subnet to `172.30.50.0/28` and reduces the PostgreSQL CPU cap. `!override` replaces the old subnet instead of appending it; require [Docker Compose 2.24.4 or later](https://docs.docker.com/reference/compose-file/merge/).

| Service | CPU cap | Memory / swap cap | Public exposure |
| --- | --- | --- | --- |
| Java/React app | 1.25 | 1,536 MiB / same | None |
| PostgreSQL 17 | 0.5 | 768 MiB / same | None |
| Caddy | 0.25 | 128 MiB / same | TCP 80/443 only |

Caps total two CPUs and 2,432 MiB, leaving space within the proposed 8 GB VM for the host, filesystem cache and maintenance. Limits are ceilings, not capacity reservations. The JVM retains the production 768 MiB heap and two visible processors; no Render settings, small-heap workaround or disabled feature is applied. Verify actual latency, storage growth, OOM history and host headroom after any separately approved deployment; the local smoke workload is not a load test.

The existing production policies remain: one application HTTPS origin, Secure/HttpOnly/Lax cookies, session rotation and CSRF, explicit SPA routes, private owner-scoped APIs, Flyway-owned V1–V7, Hibernate `validate`, closed registration and disabled Google/Groq. No authentication bypass or public provisioning endpoint is added.

## Network and secret boundary

A directly reachable VM requires a [public IP address](https://docs.oracle.com/en-us/iaas/Content/Network/Tasks/managingpublicIPs.htm), an appropriate public subnet/internet-gateway route, OCI ingress rules and host firewall rules. Explicitly verify IPv4 assignment and its $0 eligibility; a public subnet alone is insufficient. Restrict TCP 22 to the operator's address; allow 80/443 to Caddy. Do not open 5432, 8081, Docker's API or Caddy's administration port. Do not assume UFW alone restricts Docker-published ports. Review IPv6 separately before assigning it or adding AAAA records.

Only Caddy joins the public Docker network. App/database have no published ports; the internal data network is host-local. Caddy overwrites forwarded headers, and the app trusts only its fixed `172.30.50.2` peer. Database traffic stays inside the same VM's internal Docker network, as in the approved VPS design; moving PostgreSQL off-host requires a separately verified TLS design. Never apply the external Neon/Render profile to this stack.

**Public HTTPS hostname is unresolved.** Oracle does not provide this application's free public DNS hostname. Use an owner-controlled existing or approved free DNS hostname without purchasing a domain, and verify it points to the actual public IP before public ACME use. The `pages.dev` showcase is not an application reverse proxy. Bare IP/local Caddy certificates are not accepted as public browser trust; do not ask reviewers to bypass certificate warnings. A hostname/certificate plan must be approved before deployment. Address changes require DNS and certificate re-verification. [Caddy HTTPS requirements](https://caddyserver.com/docs/automatic-https).

Use `infra/oracle/environment.example` outside the checkout, e.g. `/etc/belsen/oracle.env` with mode `0600`. It contains public settings and secret **file paths**, never inline passwords. Retain the root-owned `0700` parent and designated-container-UID readability rules in the [existing secret runbook](DEPLOYMENT.md#hetzner-controlled-portfolio-preview). Compose secrets are bind mounts, not encrypted secret storage. The app imports the password through `configtree:/run/secrets/`; bootstrap and read-only backup roles use separate passwords. Do not log expanded environments or upload secret files to Pages. Generate/transfer operator secrets only during a separately approved provisioning task.

## Manual full-stack sequence — future approval only

1. Resolve account/card verification, home-region A1 capacity, aggregate Free allowances, public IPv4 eligibility and the free HTTPS hostname. Keep the standalone showcase usable if any gate fails. Do not create accounts or resources as part of this local preparation.
2. After separate approval, the owner selects the eligible ARM VM/image/allocation above, reviews the final $0 estimate, configures its network/firewalls and SSH access, and installs Docker Engine/Compose for ARM64 using official distribution instructions. Do not select an unreviewed substitute or paid upgrade. No provisioning automation is included.
3. Build/test the production JAR locally using Java 21 / Node 24 when application changes require it. Build for `linux/arm64`; record and review the image identity. Transfer the verified image through the approved private SSH/registry process. Do not place private code in a public container registry while source publication is unapproved; use an approved private SSH image transfer. Image push/transfer is not performed here. Select an immutable ARM64 image identity in the protected Oracle environment.
4. Prepare the protected environment and password files using the existing production rules, but select the Oracle project and image. Inspect the **merged** model before starting anything:

```sh
docker compose --env-file /etc/belsen/oracle.env \
  -f infra/production/compose.yaml -f infra/oracle/compose.yaml config --quiet
```

5. On the future approved host, render that same model into a root-owned `0600` file outside the checkout (e.g. `/etc/belsen/oracle.compose.yaml`) under `umask 077`. This preserves resolved bind paths and gives existing single-file backup tooling a stable input. Never publish the rendered file or use the production-only model by accident. After explicit deployment approval, the owner starts:

```sh
docker compose -p belsen-ai-oracle-preview --env-file /etc/belsen/oracle.env \
  -f /etc/belsen/oracle.compose.yaml up -d --wait
```

6. Confirm public certificate/hostname trust, restricted ports, forwarding-header overwrite, seven migrations and Hibernate validation. Verify signup closed, individual login/CSRF, private ownership, goals/tasks/habits/analytics, disabled providers, direct SPA refreshes, unknown paths, restart/session behavior and actual A1 resource usage. A health `UP` is process liveness, not continuous database readiness. No public URL becomes a showcase link until these checks pass.
7. Use the existing offline `PreviewAccountSql` helper and private operator connection for approved individual accounts; populate fictional data through normal authenticated workflows. Decide a private account-request/revocation/reset process. Password recovery and email verification are still absent.
8. Use the existing `scripts/production/backup.py create` with `--compose-file /etc/belsen/oracle.compose.yaml`, the protected env file and `--project belsen-ai-oracle-preview`. It streams the read-only role's PostgreSQL dump into age. Copy only encrypted output and checksum through approved SSH to existing independent storage; verify a real PostgreSQL 17 restore in an isolated database. Keep recovery identity separate, retain verified generations and monitor disk/freshness. The local `create` step alone does not prove off-site success. Do not enable R2 upload/systemd cloud-backup paths by default or add a paid storage service.
9. Treat VM loss, reclamation, IP changes and boot-volume loss as recoverable failures, not unlikely exceptions. Keep encrypted logical backups outside the tenancy; volume snapshots alone are insufficient. Recreate only within fresh approval and available Free capacity. On recovery, reapply roles/grants, validate schema/history, invalidate restored sessions and rotate credentials before admitting users.
10. After verified public backend acceptance, the owner can build the showcase with the verified origin and explicit attestation described in FREE_HOSTING.md. Otherwise retain its disabled application state. No public uptime guarantee or Oracle SLA is claimed.

## Local reproduction

Requires the existing Docker/Compose, age, age-keygen, htpasswd and a built ARM image; no package installation is done automatically:

```sh
python3 scripts/oracle/verify-local.py --image belsen-ai:oracle-arm64-local --age-dir <DIRECTORY_CONTAINING_AGE_AND_AGE_KEYGEN>
```

This uses three disposable local containers, high loopback ports 18447/18087, generated credentials, local CA-verified HTTPS and isolated database volumes. It performs no public ACME, cloud, DNS, R2 or live-provider operation and removes its test resources afterward. It does not alter the production base. Do not run it over an existing production stack or treat it as a deployment command.
