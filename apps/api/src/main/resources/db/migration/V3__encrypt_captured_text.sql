-- Captured text is customer data and is stored encrypted from now on (AES-256-GCM, key from CONXIST_SECRETS_KEY,
-- bound to the document id). Rows written before this keep their plaintext only until the API starts and seals
-- them (SealPlaintextDocuments); a later migration drops text_content once every environment has run it.
alter table captured_documents alter column text_content drop not null;
alter table captured_documents add column text_sealed bytea;
alter table captured_documents add constraint captured_documents_text_once
    check ((text_content is null) <> (text_sealed is null));
