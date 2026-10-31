# Contributing to Belsen-AI

Belsen-AI is currently in the documentation foundation stage. Backend, frontend, database migrations, tests and CI will be introduced in later work packages.

## Working process

1. Read `AGENTS.md`, the product vision and the relevant architecture decisions.
2. Agree on a small work package and its acceptance criteria before implementation.
3. Inspect the current files and Git state. Preserve unrelated work.
4. Implement only the agreed scope and explain material tradeoffs.
5. Run the checks that actually exist and report their results, including failures or unavailable tooling.
6. Present the changes for review. The repository owner controls staging, commits and pushes manually unless explicitly authorized otherwise.

Do not rewrite Git history or create a pull request without explicit permission. Never include credentials, local environment files, build outputs or personal data in a commit.

## Engineering conventions

- Use clear English and names that match the product domain.
- Keep Java packages organized by feature. Put business rules in Java services, not React components or controllers.
- Use request/response DTOs rather than exposing persistence entities.
- Enforce ownership for every private resource and relationship.
- Use Flyway for schema changes and preserve historical reporting semantics.
- Keep dependencies justified and explain the reason before installing them.
- Prefer small changes that the owner can understand and explain in an interview.

## Verification

For documentation changes, check links, status claims, formulas and configuration examples. Run `git diff --check` for tracked changes; new untracked files need separate inspection because ordinary Git diffs omit them.

Application checks will be documented when the corresponding build tools and tests exist. Planned gates are Maven verification, PostgreSQL integration tests, frontend type checking, linting, tests and a production build. Do not claim those checks passed before they have been executed successfully.

Use conventional commit messages such as `docs: add Day 1 project foundation`. A suggested message is not authorization to commit.
