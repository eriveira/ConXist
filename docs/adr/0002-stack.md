# 0002. Application stack

- Status: **Accepted**
- Date: 2026-11-17
- Decided by: Eugenio Riviera ("Back end we will build with Spring Boot using also Modulith", project thread, 2026-11-17)
- Supersedes: the earlier proposal in this file (one TypeScript monorepo with Fastify).

## Context

The first screen is an n8n-style canvas where a team adds sources (Capture,
Repo, Storage) and every source feeds one ConXist hub. Behind it: connectors
that ingest content, a review queue, and an MCP server that coding agents call.
The standards require Clean Architecture with an enforced dependency rule.

## Decision

| Part | Choice |
|---|---|
| Backend (`apps/api`) | Java 21, Spring Boot 4.1, **Spring Modulith 2.1**, Maven |
| Modules | One Modulith module per business capability (`sources`, `capture`, `hub`, later `memory`, `review`, `mcp`). Each module's root package is its public API; its `domain`, `application` and `adapter` sub-packages are internal |
| Inside a module | Clean Architecture: `domain` (plain Java) ← `application` (use cases + ports) ← `adapter` (web, persistence, connectors). Spring stays in `adapter` and the composition root |
| Between modules | Calls through the module API, and domain events (`@ApplicationModuleListener`) with the Modulith JDBC event publication registry |
| Data | PostgreSQL 16, Spring Data JDBC (`JdbcClient`), Flyway migrations |
| Text extraction | Apache Tika (PDF and Office), plain read for Markdown and text |
| MCP server | Spring AI MCP server, in its own module, calling the same use cases (later slice) |
| Frontend (`apps/web`) | React + TypeScript + Vite, React Flow (`@xyflow/react`) for the canvas, brand tokens only |
| Tests | JUnit 5 + AssertJ (unit), Testcontainers PostgreSQL (adapters, integration), `ApplicationModules.verify()` + ArchUnit (architecture), Vitest (web), Playwright later for end-to-end |

## Why

- The owner knows Spring Boot (C2Trade), and Spring Modulith makes module
  boundaries explicit and verifiable, which fits "screaming architecture".
- `ApplicationModules.verify()` fails the build on module cycles or access to
  another module's internals; ArchUnit fails it when a layer points outward.
- Domain events between modules keep `capture` and `hub` independent.
- A module may keep a read model fed by other modules' events (the hub's
  `hub_items`). It is denormalised on purpose (an exception to 3NF) and has no
  foreign key into another module's tables, so modules stay independent in the
  database too. Inside a module, child rows reference their parent by
  `(workspace_id, id)`, so a row cannot belong to another workspace's source.

## Consequences

- Two languages (Java backend, TypeScript canvas). The API contract lives in
  the backend's request and response records; the web client mirrors them.
- CI runs `mvn verify` for the API and `pnpm` for the web app.

## Open

- Hosting and the managed Postgres.
- Auth provider for teams. Until it is chosen, the API runs for one configured
  workspace and is **not deployable**; every table already carries
  `workspace_id` so tenant isolation can be enforced when auth lands. The API
  refuses to start on a non-loopback address. Do not put real customer data in
  it before encryption at rest and sign-in exist.
