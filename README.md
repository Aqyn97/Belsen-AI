# Belsen-AI

**Plan better. Stay consistent. Grow.**

Belsen-AI is a planned AI-assisted personal growth web application. It will help users set measurable goals, organize tasks, maintain habits, and understand their weekly progress.

This is a personal portfolio project focused on maintainable Java backend engineering and reliable analytics.

## Project status

**Day 2 backend.** Email/password authentication, JDBC sessions and private profiles are implemented. Google OIDC is implemented and tested with a local mock provider; live Google browser login awaits external verification. The web interface remains planned.

| Area | Status |
| --- | --- |
| Product scope and architecture | Documented |
| Backend configuration, migrations and health | Implemented; verification results below |
| Email/password, sessions and profiles | Implemented; PostgreSQL/HTTP tests |
| Google OIDC | Implemented; mock-provider tests; live verification pending |
| Goals and progress tracking | Planned |
| Tasks and reporting history | Planned |
| Habits and consistency tracking | Planned |
| Weekly analytics | Planned |
| AI coaching | Planned |
| Foundation automated tests | Implemented; verification results below |
| Web interface, CI and deployment | Planned |

The target for the first working MVP is **November 6, 2026**. The roadmap describes acceptance criteria; it is not a claim that those criteria have been met.

## Technical direction

- Java 21, Spring Boot, Maven, Spring Security and PostgreSQL.
- A modular monolith with feature-based Java packages and Flyway migrations.
- React, TypeScript and Vite in a separate frontend directory.
- One public HTTPS origin: Spring Boot will serve the React production assets and REST API.
- Verified analytics calculated in Java; optional AI suggestions displayed separately.

## Local backend setup

Requires Java 21, Docker with a running daemon, and network access for the first dependency/image download. Maven Wrapper pins Maven 3.9.12; a system Maven installation is not required.

Run from the repository root:

```sh
cp .env.example .env
```

Edit the ignored `.env`: fill the six required database values. Choose your own local password and shell-quote values containing special characters. Match both credential sets and use `jdbc:postgresql://127.0.0.1:5433/<your-database-name>` for the JDBC URL. If changing the database host port, update the URL too. Never commit `.env`.

```sh
docker compose --env-file .env -f infra/compose.yaml up -d --wait
set -a
. ./.env
set +a
SPRING_PROFILES_ACTIVE=local ./backend/mvnw -f backend/pom.xml spring-boot:run
```

Source only your trusted local `.env`. Compose reads that file explicitly; the backend needs the exported environment. The `local` profile binds the backend to loopback and permits HTTP cookies. Do not enable it in production. Without this profile, cookies require HTTPS.

Defaults are PostgreSQL `127.0.0.1:5433` and backend `127.0.0.1:8081`. Optional `BELSEN_POSTGRES_PORT` and `BELSEN_SERVER_PORT` overrides are documented in `.env.example`. Check ports again before starting. Compose uses project `belsen-ai` and volume `belsen-ai_postgres-data`; it does not manage TIR 2.0 resources.

In another terminal:

```sh
curl -i http://127.0.0.1:8081/api/v1/health
curl -i http://127.0.0.1:8081/api/v1/me
```

Expected responses are `200` with only `{"status":"UP"}` and `401` Problem Details, respectively. Health is a minimal process endpoint, not a continuous database-readiness probe. Startup requires working database configuration, migrations and schema validation. The unauthenticated `/me` request is rejected; authenticated sessions can read/update their own profile.

Stop the application with Ctrl-C. Stop only this project's database with `docker compose --env-file .env -f infra/compose.yaml stop`. The named volume persists; changing initialization credentials later does not reset an existing database.

## Verification

```sh
./backend/mvnw -f backend/pom.xml test
./backend/mvnw -f backend/pom.xml verify
```

`test` runs database-independent MVC/security/configuration tests. `verify` also packages the application and runs PostgreSQL Testcontainers integration tests (`*IT`). Docker is mandatory for full verification; database tests fail rather than silently skip when Docker is unavailable. Tests use a separate temporary database and do not require the local `.env` or a running Compose database.

Stage B passed its original 20 tests and a Compose + packaged-JAR smoke test. **Stage C verified November 1, 2026:** Maven `verify` passed **51 tests**, with zero failures/errors/skips, including the original 20 foundation cases. PostgreSQL/Flyway and Hibernate validation passed. A packaged-JAR + Compose HTTP smoke test passed registration, login, profile updates, persisted session requests and logout invalidation. Test resources were cleaned up. Live Google browser login and production deployment have not been verified.

## Authentication API

| Method and route | Behavior |
| --- | --- |
| `GET /api/v1/auth/csrf` | Return `headerName` and `token`; establish the browser session cookie |
| `POST /api/v1/auth/register` | Accept email, password, displayName and timezone; return `201` with a safe profile and sign in |
| `POST /api/v1/auth/login` | Accept email/password; return `200` with a safe profile and sign in |
| `POST /api/v1/auth/logout` | Require CSRF, invalidate the session and clear its cookie; return `204` |
| `GET /api/v1/me` | Return the current account's profile |
| `PATCH /api/v1/me` | Update displayName and/or IANA timezone only |

Use JSON and retain the cookie on subsequent requests. Before a mutation, fetch `/api/v1/auth/csrf` and send the returned token in the returned header (normally `X-CSRF-TOKEN`). **Fetch a fresh token after registration, login or Google login**, because authentication rotates the session ID and invalidates the old token. Logout also requires the token. Do not use localStorage tokens.

Emails are stripped and lowercased. Registration passwords need at least 12 characters and at most 72 UTF-8 bytes; passwords are never trimmed. Timezones must be named IANA identifiers such as `Asia/Almaty` or `UTC`, not numeric offsets. Unknown JSON fields, including `userId` and `accountId`, are rejected. Errors use Problem Details, with `fieldErrors` for validation.

## Google setup and verification status

Setup reference: [Google OpenID Connect documentation](https://developers.google.com/identity/openid-connect/openid-connect). Google is disabled unless `BELSEN_GOOGLE_ENABLED=true`. Password login works without Google credentials. To enable Google:

1. Create/select a Google Cloud project and configure Google Auth Platform branding, audience/consent and data access. Request only `openid`, `profile` and `email`; add your account as a test user when the consent application is in testing mode.
2. Create an OAuth client of type **Web application**.
3. Add the exact authorized redirect URI **`http://localhost:8081/login/oauth2/code/google`**. If the backend port changes, change this URI too. Production needs its separate exact HTTPS callback.
4. Put `BELSEN_GOOGLE_ENABLED`, `BELSEN_GOOGLE_CLIENT_ID` and `BELSEN_GOOGLE_CLIENT_SECRET` in your ignored `.env`, export them and restart. Missing credentials fail startup when enabled. No JavaScript origin is needed for this server-side authorization-code flow.
5. Open **`http://localhost:8081/oauth2/authorization/google`** in a browser. Use the same hostname throughout; `localhost` and `127.0.0.1` are different cookie hosts. Success redirects to `/api/v1/me` while no frontend exists.

Automated tests use a local mock provider, signed ID tokens and PostgreSQL. They verify state, nonce/signature rejection, account mapping and sessions without contacting Google. **A real Google browser login has not been run.** No account linking is implemented: an existing email with a different sign-in identity is rejected. New Google accounts start in `UTC`; update the profile afterward.

## Current limitations

Login/registration throttling uses a bounded in-memory store: 10 attempts per normalized email and 50 per client address per 15 minutes, including successful attempts. Counters reset on process restart and are not shared across instances. A reverse proxy may group clients under one address until trusted-proxy handling is configured; arbitrary forwarded headers are not trusted. Before public deployment, configure the trusted proxy, HTTPS, registration/email verification and password recovery policy. Registration conflicts can reveal whether an email exists; login errors stay generic.

No frontend, account linking, password reset, email verification, product modules, CI or public deployment exists yet. Cookie flags are verified over HTTP with a browser-style cookie jar; no visual browser automation was used.

## Documentation

- [Product vision](docs/PRODUCT_VISION.md): users, MVP scope and reporting definitions.
- [Architecture](docs/ARCHITECTURE.md): modules, data, API, security and deployment.
- [Roadmap](docs/ROADMAP.md): seven development days and release criteria.
- [Decisions](docs/DECISIONS.md): accepted choices, tradeoffs and open questions.
- [Contributing](CONTRIBUTING.md): development and manual Git workflow.

The display identity is **belsen.** Primary yellow `#FACC15`, background `#FAFAF7`, dark text `#171717`, and white surfaces `#FFFFFF` define the planned visual direction. No interface has been implemented.
