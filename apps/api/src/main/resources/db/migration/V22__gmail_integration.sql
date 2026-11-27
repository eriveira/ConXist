-- Gmail joins as an integration (ADR 0021). Its sealed credential is the refresh token of the person's Google sign-in
-- (read-only Gmail); its "repositories" are the labels agents may read, by name, or ["*"] for all email but Spam and
-- Trash. Its account is the mailbox's address shortened (al…@acme.test). Nothing else changes.

alter table integrations drop constraint integrations_kind_check;
alter table integrations add constraint integrations_kind_check check (kind in ('GITHUB', 'BITBUCKET', 'GMAIL'));
