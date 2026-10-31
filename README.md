# Belsen-AI

**Plan better. Stay consistent. Grow.**

Belsen-AI is a planned AI-assisted personal growth web application. It will help users set measurable goals, organize tasks, maintain habits, and understand their weekly progress.

This is a personal portfolio project focused on maintainable Java backend engineering and reliable analytics.

## Project status

**Foundation stage.** The architecture is approved and the documentation foundation is in place. There is no runnable application yet.

| Area | Status |
| --- | --- |
| Product scope and architecture | Documented |
| Email/password and Google sign-in | Planned |
| Goals and progress tracking | Planned |
| Tasks and reporting history | Planned |
| Habits and consistency tracking | Planned |
| Weekly analytics | Planned |
| AI coaching | Planned |
| Web interface, automated tests, CI and deployment | Planned |

The target for the first working MVP is **November 6, 2026**. The roadmap describes acceptance criteria; it is not a claim that those criteria have been met.

## Technical direction

- Java 21, Spring Boot, Maven, Spring Security and PostgreSQL.
- A modular monolith with feature-based Java packages and Flyway migrations.
- React, TypeScript and Vite in a separate frontend directory.
- One public HTTPS origin: Spring Boot will serve the React production assets and REST API.
- Verified analytics calculated in Java; optional AI suggestions displayed separately.

No setup commands are provided yet because the application has not been scaffolded. `.env.example` records proposed configuration names using empty values.

## Documentation

- [Product vision](docs/PRODUCT_VISION.md): users, MVP scope and reporting definitions.
- [Architecture](docs/ARCHITECTURE.md): modules, data, API, security and deployment.
- [Roadmap](docs/ROADMAP.md): seven development days and release criteria.
- [Decisions](docs/DECISIONS.md): accepted choices, tradeoffs and open questions.
- [Contributing](CONTRIBUTING.md): development and manual Git workflow.

The display identity is **belsen.** Primary yellow `#FACC15`, background `#FAFAF7`, dark text `#171717`, and white surfaces `#FFFFFF` define the planned visual direction. No interface has been implemented.
