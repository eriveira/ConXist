# ConXist.ai

Your meetings already wrote the spec. ConXist captures what teams say and write,
turns it into requirements, and gives coding agents one shared memory.

- Rules for agents and people: [AGENTS.md](AGENTS.md)
- Standards (shared across WDTeams projects): [docs/standards/](docs/standards/)
- Decisions: [docs/adr/](docs/adr/)
- Product page: [apps/landing/](apps/landing/) (open `index.html`)
- Brand: [brand/](brand/) (copy of the shared Branding folder)

- API: [apps/api/](apps/api/) (Java 21, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL)
- Web app: [apps/web/](apps/web/) (React, TypeScript, Vite)

Stack and reasons: [docs/adr/0002-stack.md](docs/adr/0002-stack.md).

## Run it locally

Needs Java 21, Maven, Node 22, pnpm 10 and Docker.

```sh
# API on http://127.0.0.1:8080 with a throwaway PostgreSQL in Docker
cd apps/api && mvn spring-boot:test-run

# Web app on http://127.0.0.1:5173 (forwards /api to the API)
cd apps/web && pnpm install && pnpm dev
```

There is no sign-in yet, so the API listens on this machine only, refuses to
start otherwise, and is not deployable (ADR 0002).

Plaud accounts connect through Sign in with Plaud on Plaud's own page (ADR 0005).
The Plaud Team path (ADR 0003) is on hold: its API stays, but the app no longer
offers it.
The API needs a key for the secrets it keeps:

```sh
export CONXIST_SECRETS_KEY=$(openssl rand -base64 32)
```

`mvn spring-boot:test-run` also starts a fake Plaud: press Sign in with Plaud (the
fake signs in as `alex@acme.test` at once) to see five sample meetings (three import, one is older
than 30 days, one is still being transcribed).

To connect your real personal Plaud account, run the API with plain
`mvn spring-boot:run` (with `CONXIST_SECRETS_KEY` set and PostgreSQL running) and
the web app, add a Meeting recorder, choose Sign in with Plaud and sign in on
Plaud's page. Plaud sends you back to the API at
`http://127.0.0.1:<port>/api/recorders/plaud/callback`, which follows
`CONXIST_API_PORT`; set `CONXIST_PLAUD_REDIRECT_URI` only if the API is reached
another way.

Uploaded files, captured text (including meeting transcripts) and recorder
private keys are encrypted at rest with `CONXIST_SECRETS_KEY` (ADR 0004). Keep
that key out of the database and its backups: without it the data cannot be
read, and losing it loses the data. The API has no sign-in yet and listens only
on this machine, so use real customer data on your own computer only.

## Test it

```sh
cd apps/api && mvn verify          # unit, architecture and PostgreSQL integration tests
cd apps/web && pnpm test           # unit, component, architecture and brand-token tests
```
