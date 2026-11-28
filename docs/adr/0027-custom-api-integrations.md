# 0027. Integrations are custom APIs

- Status: Proposed
- Date: 2026-11-28
- Deciders: owner, Claude
- Supersedes: ADR 0019 (GitHub), ADR 0020 (Bitbucket), ADR 0021 (Gmail)
- Mock: `App/mocks/custom-api-v1` (v3, approved by the owner on 2026-11-28)

## Context

Settings › Integrations had three services ConXist knew by heart: GitHub, Bitbucket and Gmail, each with its own
sign-in, its own five read tools and its own adapter. The owner asked for the first iteration of Integrations to be
**any API a person adds**, with templates for specific services later. Asked how ConXist would know how to use an API
it has never seen, the owner chose: import its OpenAPI description when it has one, write tools by hand when it
doesn't, list every endpoint grouped by the API's own sections (at most 200), and let the workspace tick which ones
agents may use. Each agent then picks its own among the ticked ones, so no agent ever gets the whole list. Logos can
be set by hand, by address or by uploading a file, for MCP servers too.

## Decision

1. **Remove GitHub, Bitbucket and Gmail fully.** Their code, fakes and rows go. V28 drops the `integrations` table and
   makes it again for custom APIs. Agent drafts and versions that picked their tools keep the pick as written; the
   agent page shows it as a tool that is no longer available, and runs skip it, as with any removed MCP tool. They
   come back later as templates of a custom API.
2. **An API is an address and a sign-in.** The address is https (loopback http only for tests), on a public network,
   with no query; requests go only under it and never follow redirects, so a secret never reaches another host. The
   sign-in is none, an API key in a header or the query (its name given), a bearer token, or Basic (username and
   password). The secret is sealed with `SecretBox` (ADR 0004), bound to the workspace and the row, and only its last 4
   characters show. Adding an API checks the sign-in with one GET of its check path (or its address): a refused key is
   not saved. OAuth 2.0 is a later step.
3. **Tools from a description, or by hand.** ConXist reads OpenAPI 3 and Swagger 2, JSON or YAML (YAML read safely),
   fetched from an address through the same address guard, or uploaded, up to 2 MB. Each operation under the API's
   address becomes a tool: its name from the operation id in snake_case, its section from its first tag, its inputs
   from path, query and header parameters and the properties of a JSON body. Local `$ref`s are followed and schemas
   are kept small. What ConXist can't call (files, forms, cookies, another host, a path outside the address, past 200)
   is listed as left out, with why. A person can also write a tool: method, path, section, description and inputs.
   Importing again replaces the imported tools and keeps the ticks, the tools written by hand, and any tool an agent
   uses.
4. **Reads and changes.** A GET always only reads and a DELETE never does; a POST, PUT or PATCH changes things unless
   a person says it only reads (a search, say). On the first import the tools that only read come ticked; tools that
   change things start unticked and warn before they are ticked (ADR 0022). Re-imported tools that are new start off.
5. **Agents use them as MCP tools.** An agent picks an API tool like an MCP tool (`mcpTools`, the API's id as
   `serverId`), and the `McpToolbox` port offers and calls both. The model sees one object of inputs with nothing else
   allowed; an unknown input, a missing one, a path value of `.` or `..`, or a header value that is not one short line
   is refused before anything is sent. Path values are encoded, so they can't climb out of the path. The run's copy
   guard and size cap (ADR 0025) apply as to every outside call. The answer is the API's data, untrusted, cut at
   20,000 characters (2,000 for an error); a 401 marks the API so a person replaces the secret; files are not read.
6. **Try it.** A person can send one real call of a tool, saved or not, from the tool editor. Nothing of it is kept,
   and the address shown never includes the query, so a key in the query never shows.
7. **In use stays.** A tool an agent uses can't be unticked, removed, renamed or turned into one that changes things;
   an API whose tools agents use can't be removed.
8. **Logos by hand.** An API, or an MCP server, shows the icon ConXist finds for its site, or a logo a person sets by
   its image address (fetched once, as in ADR 0022) or by uploading a PNG, JPG, SVG, WebP or ICO up to 100 KB whose
   first bytes match its type. The app shows logos only as images.
9. **Test run.** `mvn spring-boot:test-run` runs a fake API, "Northwind CRM", on this computer: it takes any
   `X-Api-Key` starting with `nw_fake` and publishes its description at `.../v1/openapi.json`.

## Consequences

- Any API with a key or token works now, with no code per service. Templates for GitHub, Bitbucket, Gmail and others
  become a prefilled address, sign-in and description.
- Gmail and Bitbucket-style "pick what agents may read" (labels, repositories) is gone with them; a custom API's limit
  is the tools ticked and what its key reaches.
- OAuth 2.0 sign-ins, XML and form bodies, and file uploads are not covered yet.
