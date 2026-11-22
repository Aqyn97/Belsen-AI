# Free portfolio publication

Status: **locally prepared, not published**. The first release is the static showcase on **Cloudflare Pages Free**. It works without Java, PostgreSQL, accounts, AI providers or an application URL. No external account, deployment, DNS change, purchase, image push is authorized by this preparation. The owner separately authorized the reviewed private Git commit and normal push; this does not authorize website deployment.

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

## Exact manual Pages publication steps — not executed

1. Review the changes and verification report. Keep the configured repository URL `https://github.com/Aqyn97/Belsen-AI`; do not substitute another repository or change its visibility/permissions. It returned 404 to anonymous and connected checks. Public GitHub and repository-document links are hidden by default, so static publication does not depend on resolving source access.
2. Read [the public-source privacy audit](PUBLIC_SOURCE_AUDIT.md) before considering repository visibility. The owner accepted the low-risk historical workstation paths; the two current references are now portable. The repository stays private. Public source access requires a separate owner decision; it is not a prerequisite for the static showcase and is not performed here. Only after privacy approval and actual public-access verification may a future build set `BELSEN_SOURCE_PUBLIC=true`, which reveals links to the same configured repository. Publish referenced documentation through the owner's manual Git process before enabling its public links.
3. On a trusted Node 24 workstation, leave all three optional visibility/launch variables unset, run the two commands above, and inspect `showcase/dist/index.html`. Confirm the disabled application state and absence of real secrets/private information. Open the local desktop/mobile preview using the test command in `showcase/README.md` if needed.
4. After separate account/publication approval, sign into the owner's Cloudflare account. Use **Workers & Pages → Create application → Pages → Direct Upload / Upload assets**. Choose the **Free** plan, name the project and upload the **contents of `showcase/dist`** (or that folder through the dashboard's folder upload). Use the generated `https://<project>.pages.dev` URL. Do not enable Functions, paid Workers, R2, custom domains, Git-triggered deployments or billing upgrades.
5. Check the public page on desktop and mobile: keyboard navigation, real image loading, hidden GitHub/documentation links while source access is unavailable, no horizontal overflow, and no Launch App link. Check `/unknown` and `/api/v1/me` are 404 pages; inspect CSP and other response headers. Confirm there are no requests to a Java backend or third-party API.
6. Record only the actual verified Pages URL in README. Keep the application status “not deployed” until a separate backend release passes its gates. Retain the previous reviewed static bundle for manual rollback. Do not promise uptime.

[Cloudflare Direct Upload](https://developers.cloudflare.com/pages/get-started/direct-upload/) accepts prebuilt assets and avoids automatic Git deployments. A Direct Upload project cannot be converted to Git integration later without creating a different project. Its dashboard drag-and-drop upload is limited to 1,000 files; this build has only six. [Pages Free limits](https://developers.cloudflare.com/pages/platform/limits/) currently allow 500 builds/month, 20,000 files per site and 25 MiB per asset. Recheck eligibility and limits before publication; no payment method or paid product is required by this design.

## Backend demonstration and access

Oracle is a feasibility proposal, not a deployed fallback. It requires a separately approved account/card-verification decision, available ARM capacity, a verified public IP/HTTPS hostname, tested operational recovery and independent encrypted backups. Do not create infrastructure merely because the local image works.

For either future full-stack route, keep registration, Google OIDC and Groq disabled. Use the existing offline `PreviewAccountSql` console helper on a trusted operator workstation to prepare individual approved accounts. Apply the private SQL through the protected database/operator path, then remove it; never publish passwords or offer an authentication bypass. Populate only fictional demonstration data through the normal authenticated UI. The public showcase needs no account.
