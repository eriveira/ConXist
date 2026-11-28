-- Owner, 2026-11-28 (ADR 0027): Tuga, ConXist's assistant in the corner of every page. Each workspace may pick the
-- model Tuga answers on (or none: Tuga then answers where new agents start) and replace ConXist's prompt for Tuga with
-- its own version, after a warning, and restore it; an empty prompt means ConXist's current one. Nothing of a chat is
-- kept. No foreign key into the workspaces module (ADR 0002); a deleted workspace's row goes with its other runs data.

create table tuga_settings (
    workspace_id      uuid        primary key,
    provider          text,
    model             text,
    effort            text        check (effort in ('LOW', 'MEDIUM', 'HIGH')),
    prompt            text        not null default '' check (char_length(prompt) <= 4000), -- characters, as the app counts them
    prompt_changed_at timestamptz,
    updated_at        timestamptz not null,
    revision          bigint      not null,
    constraint tuga_model_all_or_none check ((provider is null) = (model is null) and (provider is null) = (effort is null)),
    constraint tuga_prompt_changed_at_with_edited_prompt check ((prompt = '') = (prompt_changed_at is null))
);
