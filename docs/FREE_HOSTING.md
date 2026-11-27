# Free portfolio publication

Status: **locally prepared, not published**. The first release is the static showcase on **Cloudflare Pages Free**. It works without Java, PostgreSQL, accounts, AI providers or an application URL. No external account, deployment, DNS change, purchase, image push is authorized by this preparation. GitHub source is public as verified November 27, 2026. The current preparation authorizes no commit, push or deployment; source links still require separate privacy approval.

## Separate hosting options

| Route | Purpose and status |
| --- | --- |
| Cloudflare Pages Free | Selected public portfolio route: `showcase/dist`, free `pages.dev` domain, no Functions or authenticated requests |
| Oracle Always Free ARM | Proposed full Java/React + PostgreSQL 17 + Caddy route; local ARM compatibility passes, but eligibility, capacity and public HTTPS are unverified |
| Hetzner | Existing optional **paid** VPS route; configuration is unchanged |
| Render/Neon experiment | Preserved, **unvalidated and not approved for production**; no further optimization in this task |

See [Oracle ARM preparation](ORACLE_ARM.md), [the existing VPS runbook](DEPLOYMENT.md#hetzner-controlled-portfolio-preview), [the Render archive](RENDER_EXPERIMENT.md) and [verification evidence](FREE_HOSTING_VERIFICATION.md). No mandatory monthly hosting charge is designed into the static route. Cloud account eligibility and current Free limits still need owner confirmation; do not select paid plans or trial-dependent resources.

## Static build and launch policy

Use Node **24** from the repository root; no install or framework is required:

```sh
unset BELSEN_APP_URL BELSEN_APP_VERIFIED BELSEN_SOURCE_PUBLIC
node --test showcase/build.test.mjs
node showcase/build.mjs
```

Output is ignored `showcase/dist/`. Upload that directory's contents only: `index.html`, `404.html`, `styles.css`, `_headers` and the two genuine screenshot PNGs. No Java archive, repository source, test report, environment file, credential or account data belongs in the upload. The screenshots show fictional local test fixtures; their [provenance](../showcase/README.md#screenshot-provenance) is explicit.

Leave `BELSEN_APP_URL`, `BELSEN_APP_VERIFIED` and `BELSEN_SOURCE_PUBLIC` unset for this release. The page shows “Interactive app not deployed”; it contains no working Launch App link. There is no script, cross-origin API call, login, tracker, external font or dependency on a sleeping backend. `404.html` prevents an implicit SPA fallback. `_headers` denies scripts, connections, frames and forms while allowing local styles/images.

A future, separately approved backend may enable an ordinary navigation link only after an operator verifies its public HTTPS origin, authentication/CSRF, private ownership, restart, secrets, backup/restore and actual availability. Set `BELSEN_APP_URL` to the exact public HTTPS DNS origin with no port, path, credentials or trailing slash, and `BELSEN_APP_VERIFIED=true` for the build. This flag is an operator attestation, not automatic verification. Render domains remain rejected. The static site still never calls authenticated APIs; all application traffic stays on that application's own HTTPS origin.

## Cloudflare Pages Git settings

Verified against Cloudflare documentation on November 27, 2026. These are prepared settings, not an existing Cloudflare project or successful cloud build.

| Setting | Value |
| --- | --- |
| Product / plan | Pages / Free; static assets only, no Functions |
| Git provider / repository | GitHub / `Aqyn97/Belsen-AI` |
| Production branch | `main` |
| Framework preset | None |
| Root directory | Repository root; leave the field empty |
| Build command | `node showcase/build.mjs` |
| Build output directory | `showcase/dist` |
| Build system | v3 |
| Build environment | `NODE_VERSION=24`, `SKIP_DEPENDENCY_INSTALL=1` |
| Application/source variables | **Absent**: `BELSEN_APP_URL`, `BELSEN_APP_VERIFIED`, `BELSEN_SOURCE_PUBLIC` |
| Preview branches | Disable automatic preview builds initially |
| Public address | Generated `https://<project>.pages.dev`; no verified URL yet |

The [build image documentation](https://developers.cloudflare.com/pages/configuration/build-image/) supports explicit numeric Node versions via `NODE_VERSION`. Do not rely on its default Node version or `frontend/package.json` engines detection. Skipping automatic dependency installation is intentional: the static builder uses Node core modules and needs neither frontend dependencies nor Java. Apply these environment settings to production and any future preview environment; never add database passwords, Google/Groq keys or backend environment files.

The [Git integration guide](https://developers.cloudflare.com/pages/get-started/git-integration/) confirms repository-root builds and automatic deployments after connected pushes. Git integration replaces the earlier Direct Upload proposal. A Direct Upload project cannot be converted in place to Git integration. No such project is known to exist, and none is created here. The separate [build configuration guide](https://developers.cloudflare.com/pages/configuration/build-configuration/) defines output paths.

## Exact manual Pages publication steps — not executed

1. Obtain separate approval for Git publication of this preparation and, later, for website publication. Review the [privacy audit](PUBLIC_SOURCE_AUDIT.md): the configured repository is already public, and no visibility change is requested. Source links stay hidden until a separate privacy decision; do not set `BELSEN_SOURCE_PUBLIC` just because GitHub responds successfully.
2. Run the Node 24 build/tests above and the desktop/mobile checks in `showcase/README.md`. Inspect the six-file output, genuine fictional-fixture screenshots and absence of GitHub/Launch links. Commit/push only after the owner separately authorizes it.
3. **Only after website publication approval**, open Cloudflare **Workers & Pages → Create application → Pages → Connect to Git**. Grant the Cloudflare GitHub app access to this repository only. Select the repository and settings above. Connecting and saving can trigger the first public deployment: do not perform this during local preparation. Do not select Workers, paid plans, R2 or a custom domain.
4. Once approved and published, verify the actual `pages.dev` address on desktop/mobile: images, internal links, keyboard focus, CSP/security headers, no external requests, and explicit 404 responses for `/unknown`, `/api/v1/me` and `/.env`. Confirm the application link is absent. Local tests cannot prove Cloudflare edge responses.
5. Record only the tested public URL in README. Configure build branch controls deliberately; future `main` pushes can publish automatically once connected. Pages builds are not gated by the separate GitHub Actions workflow by default: require review and passing CI before merging publishing changes, or disable automatic production builds when manual release control is needed. Retain the previous successful deployment for rollback. Do not promise uptime.

[Pages Free limits](https://developers.cloudflare.com/pages/platform/limits/) include 500 builds/month, 20,000 files per site and 25 MiB per asset. The six-file bundle is well below those limits. Recheck account eligibility and current allowances before publication; no paid infrastructure is required by this static design. Do not put secrets in the repository merely because Cloudflare uploads only `showcase/dist`: Git integration still grants the build service repository access.

## Backend demonstration and access

Oracle is a feasibility proposal, not a deployed fallback. It requires a separately approved account/card-verification decision, available ARM capacity, a verified public IP/HTTPS hostname, tested operational recovery and independent encrypted backups. Do not create infrastructure merely because the local image works.

For either future full-stack route, keep registration, Google OIDC and Groq disabled. Use the existing offline `PreviewAccountSql` console helper on a trusted operator workstation to prepare individual approved accounts. Apply the private SQL through the protected database/operator path, then remove it; never publish passwords or offer an authentication bypass. Populate only fictional demonstration data through the normal authenticated UI. The public showcase needs no account.
