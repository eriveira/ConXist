package com.conxist.capture.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import com.conxist.capture.application.PlaudAccounts.AccountFile;
import com.conxist.capture.application.PlaudAccounts.FilePage;
import com.conxist.capture.application.PlaudTeam.Transcript;
import com.conxist.capture.application.RecordingPicks.Pick;
import com.conxist.capture.application.SyncRecorder.SyncResult;
import com.conxist.capture.domain.PlaudAccountConnection;

/**
 * Imports from a person's own Plaud account: first some of the recordings they picked, then (if they chose to) every
 * new recording since the last sync, looking back a while for transcripts that were not ready. Plaud's account API
 * has no change feed, so recordings are listed by date and imported meetings are skipped by id.
 */
final class SyncPlaudAccount {

    /** A guard against a list that never ends: more pages than any account has in a few days. */
    static final int MAX_PAGES_PER_SYNC = 200;
    /** Picked recordings imported per sync, so one sync stays short; the rest follow every few minutes. */
    static final int PICKS_PER_SYNC = 10;
    /**
     * How long a pick may wait for its transcript. Plaud only transcribes a recording when someone asks it to, so a
     * pick can wait forever; after this it is dropped rather than checked every few minutes.
     */
    static final Duration GIVE_UP_AFTER = Duration.ofDays(7);

    private final PlaudAccounts plaud;
    private final PlaudAccountConnections connections;
    private final RecordingPicks picks;
    private final MeetingImport meetings;
    private final CaptureContext context;

    SyncPlaudAccount(PlaudAccounts plaud, PlaudAccountConnections connections, RecordingPicks picks,
            MeetingImport meetings, CaptureContext context) {
        this.plaud = plaud;
        this.connections = connections;
        this.picks = picks;
        this.meetings = meetings;
        this.context = context;
    }

    SyncResult run(PlaudAccountConnection connection) {
        Instant startedAt = context.clock().instant();
        try {
            Tally tally = new Tally();
            importPicks(connection, tally);
            if (connection.importNew()) {
                importNew(connection, tally);
            }
            connections.save(connection.checked(startedAt, context.clock().instant()));
            return tally.result(picks.count(connection.sourceId()));
        } catch (PlaudAccounts.AccessDenied denied) {
            connections.save(connection.paused(context.clock().instant()));
            throw RecorderProblems.signedOut();
        } catch (PlaudAccounts.Unavailable unavailable) {
            throw RecorderProblems.unavailable();
        }
    }

    /**
     * The next few picks. Their titles are read from Plaud's list for the days they were recorded (one list per day),
     * so ConXist never stores a title before the meeting itself is imported.
     */
    private void importPicks(PlaudAccountConnection connection, Tally tally) {
        UUID sourceId = connection.sourceId();
        picks.giveUp(sourceId, context.clock().instant().minus(GIVE_UP_AFTER));
        List<Pick> next = picks.next(sourceId, PICKS_PER_SYNC);
        Map<LocalDate, Map<String, AccountFile>> byDay = new TreeMap<>();
        for (Pick pick : next) {
            LocalDate day = LocalDate.ofInstant(pick.recordedAt(), ZoneOffset.UTC);
            if (!byDay.containsKey(day)) {
                // Plaud dates recordings in its own time zone, so ask one day either side.
                byDay.put(day, listed(sourceId, day.minusDays(1), day.plusDays(1)));
            }
            AccountFile file = byDay.get(day).get(pick.fileId());
            if (file == null) {
                picks.remove(sourceId, pick.fileId()); // Deleted in Plaud since it was picked.
                continue;
            }
            Outcome outcome = importFile(connection, file);
            if (outcome == Outcome.WAITING) {
                picks.tried(sourceId, pick.fileId(), context.clock().instant());
                tally.waitingPicks++;
            } else {
                picks.remove(sourceId, pick.fileId());
            }
            tally.add(outcome);
        }
    }

    private Map<String, AccountFile> listed(UUID sourceId, LocalDate from, LocalDate to) {
        Map<String, AccountFile> files = new HashMap<>();
        readPages(sourceId, from, to, page -> page.files().forEach(file -> files.put(file.id(), file)));
        return files;
    }

    private void importNew(PlaudAccountConnection connection, Tally tally) {
        // Plaud filters by calendar day in its own time zone, so ask from the day before and filter exactly here.
        LocalDate from = LocalDate.ofInstant(connection.readFrom(), ZoneOffset.UTC).minusDays(1);
        readPages(connection.sourceId(), from, null, page -> {
            for (AccountFile file : page.files()) {
                if (connection.wasRecordedInRange(file.recordedAt())) {
                    tally.add(importFile(connection, file));
                }
            }
        });
    }

    private void readPages(UUID sourceId, LocalDate from, LocalDate to, Consumer<FilePage> each) {
        FilePage page;
        int number = 0;
        do {
            if (++number > MAX_PAGES_PER_SYNC) {
                throw new PlaudAccounts.Unavailable("recordings list did not end");
            }
            page = plaud.files(sourceId, from, to, number);
            each.accept(page);
        } while (page.hasMore());
    }

    private Outcome importFile(PlaudAccountConnection connection, AccountFile file) {
        if (meetings.alreadyImported(connection.workspaceId(), connection.sourceId(), file.id())) {
            return Outcome.ALREADY;
        }
        Transcript transcript = plaud.transcript(connection.sourceId(), file.id()).orElse(null);
        if (transcript == null || transcript.lines().isEmpty()) {
            return Outcome.WAITING;
        }
        List<String> notes = connection.includeSummaries() ? plaud.notes(connection.sourceId(), file.id()) : List.of();
        return meetings.save(connection.workspaceId(), connection.sourceId(), file.id(), file.title(),
                file.recordedAt(), transcript, notes) ? Outcome.IMPORTED : Outcome.ALREADY;
    }

    private enum Outcome { IMPORTED, ALREADY, WAITING }

    private static final class Tally {
        private int imported;
        private int already;
        private int waiting;
        private int waitingPicks;

        void add(Outcome outcome) {
            switch (outcome) {
                case IMPORTED -> imported++;
                case ALREADY -> already++;
                case WAITING -> waiting++;
            }
        }

        /** {@code picksLeft} counts picks still waiting for a transcript too; those are reported as waiting. */
        SyncResult result(int picksLeft) {
            return new SyncResult(imported, already, waiting, Math.max(0, picksLeft - waitingPicks));
        }
    }
}
