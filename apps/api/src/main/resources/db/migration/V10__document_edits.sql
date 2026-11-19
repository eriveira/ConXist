-- A person can correct a recorded meeting in ConXist: its title, speaker names and transcript lines. The first edit keeps
-- the text and title as imported, sealed like the text itself, so "Restore original" can put them back. Plaud is never
-- changed.
alter table captured_documents add column edited_at timestamptz;
alter table captured_documents add column original_title text check (length(original_title) between 1 and 200);
alter table captured_documents add column original_sealed bytea;
alter table captured_documents add constraint captured_documents_original_with_edit
    check ((edited_at is null) = (original_sealed is null) and (original_sealed is null) = (original_title is null));
