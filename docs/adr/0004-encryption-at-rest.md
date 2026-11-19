# 0004. Encrypt captured content at rest

- Status: **Accepted**
- Date: 2026-11-18
- Decided by: Eugenio Riviera (chose to connect a real Plaud account, 2026-11-18; encryption first was part of that plan)

## Context

ADR 0003 left meeting transcripts and uploads stored as plaintext, and the
README asked people not to use real customer data until that changed. The owner
now wants to connect a real Plaud account, so real meetings will be stored.
ENGINEERING_STANDARDS §5 asks for personal data to be encrypted at rest.

## Decision

- One application key, `CONXIST_SECRETS_KEY` (32 bytes, Base64, no default),
  seals everything ConXist keeps for a customer, with AES-256-GCM through
  `SecretBox` (`capture.adapter.secrets`):
  - recorder private keys (since ADR 0003), bound to the key id;
  - captured text in `captured_documents.text_sealed`, bound to the document id;
  - uploaded files on disk, bound to their storage key.
  Binding (GCM associated data) means a sealed value cannot be moved to another
  row or file and still open.
- Migration `V3__encrypt_captured_text.sql` adds `text_sealed` and lets
  `text_content` be empty, with a check that exactly one holds the text.
  `SealPlaintextDocuments` seals any older plaintext rows when the API starts.
  A later migration drops `text_content` once every environment has run it.
- Titles and account labels (a Plaud Team name, or the email of a personal Plaud
  account) stay plaintext: they are needed for lists and are short. Files uploaded
  before this change stay as they are on disk (local development only; nothing
  is deployed).

## Consequences

- Losing `CONXIST_SECRETS_KEY` loses the content. It must be kept apart from the
  database and its backups.
- The key cannot be rotated yet: the sealed format has no version or key id.
  Adding both is the next step before any shared deployment.
- Search over captured text will need to work on decrypted text in the API, not
  in SQL.
