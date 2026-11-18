package com.conxist.capture.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import com.conxist.capture.application.PlaudAccounts.AccountFile;
import com.conxist.capture.application.PlaudAccounts.FilePage;
import com.conxist.capture.application.PlaudTeam.Transcript;
import com.conxist.capture.application.SyncRecorder.SyncResult;
import com.conxist.capture.domain.PlaudAccountConnection;

/**
 * Reads a person's own Plaud account for recordings since the last sync (looking back a few days for transcripts
 * that were not ready) and imports each new one. Plaud's account API has no change feed, so the recordings list is
 * read by date and imported meetings are skipped by id.
 */
final class SyncPlaudAccount {

    /** A guard against a list that never ends: more pages than any account has in a few days. */
    static final int MAX_PAGES_PER_SYNC = 200;

    private final PlaudAccounts plaud;
    private final PlaudAccountConnections connections;
    private final MeetingImport meetings;
    private final CaptureContext context;

    SyncPlaudAccount(PlaudAccounts plaud, PlaudAccountConnections connections, MeetingImport meetings,
            CaptureContext context) {
        this.plaud = plaud;
        this.connections = connections;
        this.meetings = meetings;
        this.context = context;
    }

    SyncResult run(PlaudAccountConnection connection) {
        Instant startedAt = context.clock().instant();
        try {
            SyncResult result = read(connection);
            connections.save(connection.checked(startedAt, context.clock().instant()));
            return result;
        } catch (PlaudAccounts.AccessDenied denied) {
            connections.save(connection.paused(context.clock().instant()));
            throw RecorderProblems.signedOut();
        } catch (PlaudAccounts.Unavailable unavailable) {
            throw RecorderProblems.unavailable();
        }
    }

    private SyncResult read(PlaudAccountConnection connection) {
        // Plaud filters by calendar day in its own time zone, so ask from the day before and filter exactly here.
        LocalDate from = LocalDate.ofInstant(connection.readFrom(), ZoneOffset.UTC).minusDays(1);
        int imported = 0;
        int already = 0;
        int waiting = 0;
        FilePage page;
        int number = 0;
        do {
            if (++number > MAX_PAGES_PER_SYNC) {
                throw new PlaudAccounts.Unavailable("recordings list did not end");
            }
            page = plaud.files(connection.sourceId(), from, number);
            for (AccountFile file : page.files()) {
                if (!connection.wasRecordedInRange(file.recordedAt())) {
                    continue;
                }
                if (meetings.alreadyImported(connection.workspaceId(), connection.sourceId(), file.id())) {
                    already++;
                    continue;
                }
                Transcript transcript = plaud.transcript(connection.sourceId(), file.id()).orElse(null);
                if (transcript == null || transcript.lines().isEmpty()) {
                    waiting++;
                    continue;
                }
                List<String> notes = connection.includeSummaries()
                        ? plaud.notes(connection.sourceId(), file.id()) : List.of();
                if (meetings.save(connection.workspaceId(), connection.sourceId(), file.id(), file.title(),
                        file.recordedAt(), transcript, notes)) {
                    imported++;
                } else {
                    already++;
                }
            }
        } while (page.hasMore());
        return new SyncResult(imported, already, waiting);
    }
}
