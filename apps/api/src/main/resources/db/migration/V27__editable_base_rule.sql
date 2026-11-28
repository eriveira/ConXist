-- Owner, 2026-11-28 (ADR 0026): a workspace may replace ConXist's rule with its own version, after a warning, and
-- restore it; an empty base_rule means ConXist's current rule. It may also make the copy guard stricter: copy_words is
-- how many words in a row count as copying, from 6 to 11, or null for ConXist's 12.

alter table workspace_guardrails
    add column base_rule            text        not null default '' check (char_length(base_rule) <= 4000),
    add column base_rule_changed_at timestamptz,
    add column copy_words           integer     check (copy_words between 6 and 11),
    add constraint base_rule_changed_at_with_edited_rule check ((base_rule = '') = (base_rule_changed_at is null));

-- Each save names the revision it read, so a tab with stale values cannot quietly undo a stricter setting.
alter table workspace_guardrails add column revision bigint not null default 1;
