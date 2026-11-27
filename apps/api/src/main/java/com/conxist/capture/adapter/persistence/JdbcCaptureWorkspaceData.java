package com.conxist.capture.adapter.persistence;

import java.util.List;
import java.util.UUID;
import com.conxist.capture.application.FileStore;
import com.conxist.platform.WorkspaceData;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Everything Capture keeps for a deleted workspace: meetings and their text, Plaud sign-ins, connections and sealed
 * tokens, picks, syncs and recorder keys, before the sources module deletes the sources these point at; then, once
 * that has committed, the stored files.
 */
@Repository
class JdbcCaptureWorkspaceData implements WorkspaceData {

    /** Children before what they point at. */
    private static final List<String> TABLES = List.of("deleted_recordings", "plaud_recording_picks",
            "plaud_account_tokens", "plaud_account_connections", "plaud_team_connections", "plaud_sign_ins",
            "source_syncs", "captured_documents", "recorder_keys");

    private final JdbcClient jdbc;
    private final FileStore files;

    JdbcCaptureWorkspaceData(JdbcClient jdbc, FileStore files) {
        this.jdbc = jdbc;
        this.files = files;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void deleteAll(UUID workspaceId) {
        TABLES.forEach(table -> jdbc.sql("delete from " + table + " where workspace_id = :id")
                .param("id", workspaceId).update());
    }

    @Override
    public void afterDeleted(UUID workspaceId) {
        files.deleteWorkspace(workspaceId);
    }
}
