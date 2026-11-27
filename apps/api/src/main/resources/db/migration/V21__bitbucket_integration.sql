-- Bitbucket joins GitHub as an integration (ADR 0020). Its sealed credential holds the person's Atlassian email and
-- API token together; its repositories are the ones the person picked. Nothing else changes.

alter table integrations drop constraint integrations_kind_check;
alter table integrations add constraint integrations_kind_check check (kind in ('GITHUB', 'BITBUCKET'));
