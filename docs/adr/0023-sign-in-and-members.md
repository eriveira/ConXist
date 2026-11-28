# 0023. Sign-in, people and workspace members

- Status: Proposed
- Date: 2026-11-28
- Deciders: owner, Claude
- Closes: ADR 0002 "Open: auth provider"; ADR 0007's "workspaces are not a security boundary yet"
- Comes before: ADR 0022 (ConXist MCP for coding agents)
- Owner, 2026-11-28: "The ConXist MCP needs to be able to allow only authorized user to interact with authorized
  Workspaces"; "every workspace brings its own tools"; sign-in before the MCP; option 2 (ConXist's own sign-in
  server, Google and Microsoft first, Firebase possible later).

## Context

ConXist has no sign-in. Anyone who reaches the API on the machine can name any workspace (ADR 0007), and the API
listens on 127.0.0.1 only. The MCP for coding agents (ADR 0022) runs shared tools on a workspace's own sign-ins to
GitHub, Gmail and other services, so it must know who is calling and which workspaces they belong to. Coding agents
sign in with MCP's OAuth flow, which needs ConXist itself to issue tokens to outside clients.

## Decision

- **A `people` module** (people, their sign-ins, workspace members). Tables (next migration):
  - `people`: id, display name, email, email verified, created, last signed in.
  - `person_sign_ins`: person, provider (`GOOGLE`, `MICROSOFT`; later `FIREBASE` or others), the provider's subject
    id, unique per provider and subject. A person can have several; this is what lets a new provider be added later
    without moving anyone's data.
  - `workspace_members`: workspace, person, role (`OWNER`, `MEMBER`), added by, added at. A workspace always keeps
    at least one Owner.
  - `workspace_invites`: workspace, email, role, invited by, invited at. Sending email comes later; the invite
    waits for that email to sign in.
- **Sign in with Google or Microsoft** (OpenID Connect, Spring Security `oauth2Login`). ConXist asks only for
  `openid email profile`, never for mail or files (Gmail as a tool stays its own integration, ADR 0021). A sign-in
  is accepted only with a verified email. The first time a provider and subject is seen, ConXist links it to the
  person with that verified email, or creates one. ConXist stores no passwords.
- **ConXist issues its own tokens** with Spring Authorization Server, inside the API. The web app gets an HttpOnly,
  SameSite=Lax session cookie (8 hours idle, 30 days at most). Coding agents get OAuth tokens through ADR 0022's
  flow (dynamic client registration, PKCE, resource indicator), each for one person and one workspace. Whatever
  provider a person used, tokens come from ConXist, so adding Firebase later changes nothing for the MCP or the app.
- **Every workspace request checks membership.** `X-Workspace-Id` stays, and the platform's `CurrentWorkspace` now
  also checks that the signed-in person is a member of it: otherwise `workspace.not-found` (404), so a workspace's
  existence isn't revealed. The workspace list shows only the person's workspaces. Settings (LLM providers, MCPs,
  Integrations, Variables, Coding agents, Members) can be changed by Owners only; Members use agents, skills and
  sources and see Settings read-only. Background work (the recorder check) runs as the workspace, not as a person.
- **Settings › Members**: the list of members with their role and last sign-in; Owners invite by email, change a
  role, and remove a member (never the last Owner). Removing someone ends their sessions, MCP connections and
  access keys for that workspace at once.
- **Existing data.** The first person to sign in after this ships becomes Owner of every workspace that already
  exists, so the owner's Mac keeps its data. After that, a new person sees only workspaces they create or are
  invited to.
- **Local dev.** `mvn spring-boot:test-run` runs a fake sign-in provider with two people (`alex@acme.test`,
  `dana@acme.test`) so membership can be tried without Google or Microsoft. Real sign-in needs
  `CONXIST_GOOGLE_SIGNIN_CLIENT_ID/SECRET` and/or `CONXIST_MICROSOFT_CLIENT_ID/SECRET`; a provider without its
  settings isn't offered.

## Consequences

- Workspaces become a real boundary, which the MCP (ADR 0022) needs, and which hosting needs too. The API still
  listens on 127.0.0.1 until hosting is decided.
- Every API test signs in as a test person; the existing tests gain a member fixture.
- The product page can say "Sign in with Google or Microsoft" and "Invite your team" once this ships, not before.
- Later: Firebase or another provider as one more `person_sign_ins` provider; email for invites; SSO (SAML) for
  larger customers; two-step sign-in follows the provider's own.
