# Belsen-AI working rules

## Scope and current state

This repository has the Day 5 read-only analytics dashboard backend implemented. Authentication, profiles, goals, tasks, immutable task/habit history, frozen reporting weeks and prospective timezone policy are present. Habit schedules, daily completion/undo, prospective habit rest and bounded cutoff-aware history are implemented. The authenticated dashboard API uses strict consistency rules, original task cohorts and explicit weekly goal units. Closed reports are CLOSED_UNFINALIZED; React authentication/settings are implemented in Day 6 B2, goals/tasks in B3, habits in B4 and dashboard/historical analytics in B5; immutable finalization, coaching and deployment remain planned. Google OIDC has mock-provider coverage; live Google browser login remains unverified. Read `docs/PRODUCT_VISION.md`, `docs/ARCHITECTURE.md` and `docs/DECISIONS.md` before implementation.

The completed Day 1 foundation task was restricted to `README.md`, `CONTRIBUTING.md`, `.gitignore`, `.editorconfig`, `.env.example`, `AGENTS.md`, `docs/PRODUCT_VISION.md`, `docs/ARCHITECTURE.md`, `docs/ROADMAP.md` and `docs/DECISIONS.md`. Later implementation requires an authorized work package. Stage C does not authorize frontend or other product features.

## Manual Git control

- Inspect Git status before work and preserve the owner's changes.
- Do not stage, commit or push unless the owner explicitly authorizes that action. Default to manual owner Git control unless a specific work package authorizes agent commit/push after its verification gates.
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
- Habit mutations lock account then owned habit, capture one microsecond-precision acceptance instant, and require the habit's expected version. Retain immutable schedule/event records; never use current flags to answer historical cutoffs. Future schedule selection uses the highest applicable accepted revision sequence.
- Habit rest planning/cancellation must precede the reporting day start. Preserve obsolete rest events and previous obligations when archiving from tomorrow.
- Keep history bounded and snapshot-consistent. Never create occurrences or reporting weeks during reads. Preserve V1–V5; new schema changes require a new authorized migration.
- Analytics must use one read-only REPEATABLE READ transaction and one exclusive cutoff. Use owner-scoped projections, never resource view services that open separate transactions. Never label an unpersisted report FINALIZED or fabricate historical metadata/timezones.
- Explain dependency choices before installing them. Avoid unnecessary infrastructure and abstractions.

## Evidence and review

Keep changes small and reviewable. Update documentation when an accepted rule changes. Run applicable checks, report actual results, and identify checks that could not run. Do not invent tests, screenshots, deployments, working features or completed roadmap items. Finish with changed-file scope, validation evidence, remaining decisions and a suggested conventional commit message when requested.
