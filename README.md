# ConXist.ai

Your meetings already wrote the spec. ConXist captures what teams say and write,
turns it into requirements, and gives coding agents one shared memory.

- Rules for agents and people: [AGENTS.md](AGENTS.md)
- Standards (shared across WDTeams projects): [docs/standards/](docs/standards/)
- Decisions: [docs/adr/](docs/adr/)
- Product page: [apps/landing/](apps/landing/) (open `index.html`)
- Brand: [brand/](brand/) (copy of the shared Branding folder)

- API: [apps/api/](apps/api/) (Java 21, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL)
- Web app: [apps/web/](apps/web/) (React, TypeScript, Vite, React Flow)

Stack and reasons: [docs/adr/0002-stack.md](docs/adr/0002-stack.md).

## Run it locally

Needs Java 21, Maven, Node 22, pnpm 10 and Docker.

```sh
# API on http://127.0.0.1:8080 with a throwaway PostgreSQL in Docker
cd apps/api && mvn spring-boot:test-run

# Web app on http://127.0.0.1:5173 (forwards /api to the API)
cd apps/web && pnpm install && pnpm dev
```

There is no sign-in yet, so the API listens on this machine only and is not
deployable (ADR 0002). Plaud is a preview: it signs in to a sample account.

## Test it

```sh
cd apps/api && mvn verify          # unit, architecture and PostgreSQL integration tests
cd apps/web && pnpm test           # unit, component, architecture and brand-token tests
```
