# Belsen-AI working rules

## Scope and current state

This repository is in Day 2 backend development. Stage B provides Spring Boot, PostgreSQL/Flyway, JDBC sessions, security and foundation tests. Stage C implements authentication, Google OIDC, profiles and security tests. Live Google browser login remains unverified. No frontend or deployment exists. Read `docs/PRODUCT_VISION.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md` before implementation.

The completed Day 1 foundation task was restricted to `README.md`, `CONTRIBUTING.md`, `.gitignore`, `.editorconfig`, `.env.example`, `AGENTS.md`, `docs/PRODUCT_VISION.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` and `docs/DECISIONS.md`. Later implementation requires an authorized work package. Stage C does not authorize frontend or other product features.

## Manual Git control

- Inspect Git status before work and preserve the owner's changes.
- Do not stage, commit or push unless the owner explicitly authorizes that action. The owner currently intends to commit and push personally.
- Never rewrite Git history. Do not create pull requests without permission.
- A proposed commit message is not permission to execute it.

## Product and engineering constraints

- Keep a Java 21 modular monolith, feature-based packages, REST, PostgreSQL and Flyway.
- Keep frontend and backend source separate. Serve React production assets through Spring Boot under one public HTTPS origin.
- Keep business rules and authoritative analytics in Java.
- Preserve only the task history and closed-week snapshots needed for accurate analytics. Do not introduce a general event-sourcing framework.
- Task reports use original due dates and completion state at the reporting cutoff. Rescheduling must not move historical obligations out of a report.
- Habit consistency counts successful eligible days, where every habit scheduled for that day is complete. Rest days are excluded.
- Return no-data states instead of fabricated percentages. Keep AI suggestions separate from verified metrics; AI must never modify user data.
- Enforce resource ownership, input validation, session security and CSRF protection. Keep all credentials and AI keys server-side and out of source control.
- Retain the visual identity: primary `#FACC15`, background `#FAFAF7`, dark text `#171717`, white surfaces `#FFFFFF`.
- Explain dependency choices before installing them. Avoid unnecessary infrastructure and abstractions.

## Evidence and review

Keep changes small and reviewable. Update documentation when an accepted rule changes. Run applicable checks, report actual results, and identify checks that could not run. Do not invent tests, screenshots, deployments, working features or completed roadmap items. Finish with changed-file scope, validation evidence, remaining decisions and a suggested conventional commit message when requested.
