# Public Belsen-AI showcase

A separate static portfolio, not the authenticated React application. Node 24 core modules only; no new dependency, JavaScript runtime in the page, external fonts, API requests, cookies or analytics trackers.

From the repository root:

```sh
unset BELSEN_APP_URL BELSEN_APP_VERIFIED BELSEN_SOURCE_PUBLIC
node showcase/build.mjs
node --test showcase/build.test.mjs
```

Output: ignored `showcase/dist`. Unset `BELSEN_APP_URL` displays “Interactive app not deployed.” Only set it to a public HTTPS DNS origin **after** separate backend verification, together with `BELSEN_APP_VERIFIED=true`; this operator attestation enables an ordinary `/auth` navigation link. Render origins are rejected. Leave both variables unset for the static-first release. Public source links also remain hidden unless `BELSEN_SOURCE_PUBLIC=true` is explicitly set after privacy and public-access approval. The configured repository stays `https://github.com/Aqyn97/Belsen-AI`; it is not substituted. Leave all three flags unset for this release. Never supply a secret or database URL. Cloudflare Pages Direct Upload instructions and external gates are in [FREE_HOSTING.md](../docs/FREE_HOSTING.md).

Browser/axe checks, with the repository's installed frontend test tools and Node 24:

```sh
cd frontend
npx playwright test --config playwright.showcase.config.ts
```

The local test server implements an explicit file list and Pages headers. It is not a hosting server or deployment tool. Desktop and mobile tests check screenshot loading, layout, keyboard navigation, WCAG 2.0/2.1/2.2 A/AA axe rules, no external requests and 404 behavior. Automated scans do not replace manual assistive-technology review.

## Screenshot provenance

These files are intentional public source assets, copied byte-for-byte from local real-backend browser captures at baseline `1322f44358d9961b0d1e84380646f05e2cdd399b`:

- `assets/dashboard.png`: `frontend/test-results/packaged/production-packaged-real-w-83443-CSRF-sessions-and-isolation-packaged-desktop/packaged-dashboard.png` (1280 × 1560). Java production JAR, local HTTPS, PostgreSQL and fictional “Packaged Owner” fixture.
- `assets/analytics.png`: `frontend/test-results/real/analytics-real-dashboard-f-a8f21-iding-and-account-isolation-real-desktop/analytics-current.png` (1280 × 2006). Real Java/PostgreSQL historical-cutoff test with fictional “Measured reading” fixture.

Both were inspected for credentials and personal data. Visible quantities and percentages are synthetic test fixtures, never adoption statistics. No live Google/Groq or cloud deployment is pictured. CSS clips the display frames; linked original PNGs remain unmodified. Generated Playwright captures/reports and build output remain ignored.
