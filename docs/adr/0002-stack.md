# 0002. Application stack

- Status: **Proposed** (by Claude, 2026-11-17). Not accepted until the owner says so.
- Date: 2026-11-17
- Decided by: (pending)

## Context

The first screen is an n8n-style canvas where a team connects sources (GitHub,
Bitbucket, Plaud, S3/R2, Google Drive and others) to one ConXist hub. Behind it:
connectors that ingest content, a review queue, and an MCP server that coding
agents call. The standards require Clean Architecture with an enforced dependency
rule, strict tool schemas and evals.

## Decision (proposed)

One TypeScript monorepo (pnpm workspaces), Node 22:

| Part | Choice | Layer |
|---|---|---|
| `packages/domain` | Plain TypeScript entities and rules | Entities |
| `packages/application` | Use cases and the ports they own | Use cases |
| `packages/adapters` | GitHub, Bitbucket, Plaud, S3/R2, Drive… clients; Postgres repositories (Drizzle) | Adapters |
| `packages/ui` | React components built only on `--cx-*` tokens | Adapters (UI) |
| `apps/web` | React + Vite; React Flow (`@xyflow/react`) for the canvas; TanStack Query | Frameworks |
| `apps/api` | Fastify, Zod schemas shared with tools, OpenAPI | Frameworks |
| `apps/mcp` | MCP server (`@modelcontextprotocol/sdk`) calling the same use cases | Frameworks |
| `apps/worker` | Ingestion jobs on Postgres (pg-boss), no extra queue to run | Frameworks |
| `apps/landing` | The product page | – |

- Data: PostgreSQL; provider tokens encrypted at rest.
- Tests: Vitest (unit), Testcontainers (adapters against real Postgres),
  Playwright + axe (end-to-end and accessibility), an eval runner for tools.
- Enforcement: `dependency-cruiser` fails the build when a package imports
  outward; a brand-token test fails on raw colors and fonts.

## Why

- One language for canvas, API, MCP server and connectors; types and Zod schemas
  are shared between the API and the tools, so they cannot drift.
- React Flow is the standard library for node canvases like n8n's.
- The MCP and model SDKs are most complete in TypeScript.
- Packages map one-to-one onto Clean Architecture rings, so the dependency rule
  is checkable by a tool.

## Alternative

Spring Boot (Java 21) + React, as in C2Trade: the owner knows it and ArchUnit
enforces layers well, but it puts two languages in the codebase and the MCP
server and canvas would not share schemas with the backend.

## Open

- Hosting (Fly.io, Railway or the owner's own servers) and the managed Postgres.
- Auth provider for teams (for example Clerk, Auth0 or Supabase Auth).
