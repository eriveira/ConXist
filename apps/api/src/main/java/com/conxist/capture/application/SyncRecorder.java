package com.conxist.capture.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.UUID;
import com.conxist.capture.application.PlaudTeam.ChangePage;
import com.conxist.capture.application.PlaudTeam.Credential;
import com.conxist.capture.application.PlaudTeam.TeamFile;
import com.conxist.capture.application.PlaudTeam.Transcript;
import com.conxist.capture.application.SyncHistory.Outcome;
import com.conxist.capture.application.SyncHistory.StartedBy;
import com.conxist.capture.application.SyncHistory.SyncRun;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.PlaudAccountConnection;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCatalog;

/**
 * Syncs a meeting recorder source: a person's own Plaud account through {@link SyncPlaudAccount}, or a Plaud Team.
 * For a Team it reads the change feed from where the last sync stopped and imports each new meeting's transcript (and
 * summaries, if chosen) as a document waiting for review. Meetings already imported, recorded before the chosen start,
 * deleted, or still being transcribed are skipped; a meeting whose transcript arrives later shows up again in the feed.
 * The checkpoint is saved only after the whole feed was read, so an interrupted sync repeats safely.
 */
public final class SyncRecorder {

    /** A guard against a feed that never ends: more pages than any real Team has in one sync. */
    static final int MAX_PAGES_PER_SYNC = 1000;
    private static final System.Logger log = System.getLogger(SyncRecorder.class.getName());

    private final CaptureSources sources;
    private final PlaudTeam plaud;
    private final PlaudTeamConnections connections;
    private final CaptureContext context;
    private final MeetingImport meetings;
    private final PlaudAccountConnections accounts;
    private final SyncPlaudAccount accountSync;
    private final SyncHistory history;

    public SyncRecorder(SourceCatalog catalog, PlaudTeam plaud, PlaudTeamConnections connections,
            PlaudAccounts plaudAccounts, PlaudAccountConnections accounts, RecordingPicks picks,
            CapturedDocumentRepository documents, SyncHistory history, CaptureContext context, int maxTextCharacters) {
        this.history = history;
        this.meetings = new MeetingImport(documents, context, maxTextCharacters);
        this.accounts = accounts;
        this.accountSync = new SyncPlaudAccount(plaudAccounts, accounts, picks, meetings, context);
        this.sources = new CaptureSources(catalog);
        this.plaud = plaud;
        this.connections = connections;
        this.context = context;
    }

    public SyncResult sync(UUID sourceId) {
        sources.require(sourceId, Provider.PLAUD);
        UUID workspaceId = context.workspace().id();
        Optional<PlaudTeamConnection> team = connections.find(workspaceId, sourceId);
        if (team.isPresent()) {
            return recorded(workspaceId, sourceId, StartedBy.PERSON, () -> run(team.get()));
        }
        PlaudAccountConnection account = accounts.find(workspaceId, sourceId)
                .orElseThrow(() -> new RuleViolation("plaud.not-connected", "Connect this source to Plaud first."));
        return recorded(workspaceId, sourceId, StartedBy.PERSON, () -> accountSync.run(account));
    }

    /**
     * The scheduled check: every active connection of one workspace. It runs outside a request, so it is told the
     * workspace. One failing connection does not stop the others.
     */
    public List<SourceSync> syncAll(UUID workspaceId) {
        safely("forget old import history", () -> history.forgetBefore(workspaceId, context.clock().instant().minus(SyncHistory.KEEP)));
        Stream<Map.Entry<UUID, Supplier<SyncResult>>> teams = connections.findAll(workspaceId).stream()
                .filter(connection -> connection.status() == ConnectionStatus.ACTIVE)
                .map(connection -> Map.entry(connection.sourceId(), () -> run(connection)));
        Stream<Map.Entry<UUID, Supplier<SyncResult>>> personal = accounts.findAll(workspaceId).stream()
                .filter(connection -> connection.status() == ConnectionStatus.ACTIVE)
                .map(connection -> Map.entry(connection.sourceId(), () -> accountSync.run(connection)));
        return Stream.concat(teams, personal)
                .map(entry -> {
                    UUID sourceId = entry.getKey();
                    try {
                        return new SourceSync(sourceId, recorded(workspaceId, sourceId, StartedBy.SCHEDULE, entry.getValue()), null);
                    } catch (RuleViolation problem) {
                        return new SourceSync(sourceId, null, problem.code());
                    } catch (RuntimeException failure) {
                        log.log(System.Logger.Level.WARNING, "Recorder source {0} failed: {1}",
                                sourceId, failure.getClass().getSimpleName());
                        return new SourceSync(sourceId, null, SYNC_FAILED);
                    }
                })
                .toList();
    }

    /** Runs one check and writes it to the import history, failed or not; a failure is then passed on. */
    private SyncResult recorded(UUID workspaceId, UUID sourceId, StartedBy startedBy, Supplier<SyncResult> check) {
        Instant startedAt = context.clock().instant();
        SyncResult result;
        try {
            result = check.get();
        } catch (RuleViolation problem) {
            remember(failed(workspaceId, sourceId, startedBy, startedAt, problem.code()));
            throw problem;
        } catch (RuntimeException failure) {
            remember(failed(workspaceId, sourceId, startedBy, startedAt, SYNC_FAILED));
            throw failure;
        }
        remember(new SyncRun(UUID.randomUUID(), workspaceId, sourceId, startedAt,
                context.clock().instant(), startedBy, result.imported() > 0 ? Outcome.IMPORTED : Outcome.UP_TO_DATE,
                result.imported(), result.alreadyImported(), result.waitingForTranscript(), result.queued(), null));
        return result;
    }

    /** The history only reports on a check: failing to write it never changes what the check itself did. */
    private void remember(SyncRun run) {
        safely("record a check in the import history", () -> history.record(run));
    }

    private static void safely(String what, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            log.log(System.Logger.Level.WARNING, "Could not {0}: {1}", what, failure.getClass().getSimpleName());
        }
    }

    private SyncRun failed(UUID workspaceId, UUID sourceId, StartedBy startedBy, Instant startedAt, String problemCode) {
        return new SyncRun(UUID.randomUUID(), workspaceId, sourceId, startedAt, context.clock().instant(),
                startedBy, Outcome.FAILED, 0, 0, 0, 0, problemCode);
    }

    /** Reads from the saved checkpoint; if Plaud says it expired, reads once more from the start. */
    private SyncResult run(PlaudTeamConnection connection) {
        Credential credential = new Credential(connection.workspaceId(), connection.clientId(), connection.keyId());
        try {
            try {
                return readFeed(connection, credential);
            } catch (PlaudTeam.CheckpointExpired expired) {
                return readFeed(connection.withoutCheckpoint(), credential);
            }
        } catch (PlaudTeam.CheckpointExpired expiredAgain) {
            throw RecorderProblems.unavailable();
        } catch (PlaudTeam.AccessDenied denied) {
            connections.save(connection.paused(context.clock().instant()));
            throw RecorderProblems.accessLost();
        } catch (PlaudTeam.Unavailable unavailable) {
            throw RecorderProblems.unavailable();
        }
    }

    private SyncResult readFeed(PlaudTeamConnection connection, Credential credential) {
        Tally tally = new Tally();
        String cursor = null;
        ChangePage page;
        int pages = 0;
        do {
            if (++pages > MAX_PAGES_PER_SYNC) {
                throw new PlaudTeam.Unavailable("feed did not end");
            }
            page = plaud.changes(credential, connection.checkpoint(), cursor);
            for (TeamFile file : page.files()) {
                importFile(connection, credential, file, tally);
            }
            if (page.hasMore() && (page.nextCursor() == null || page.nextCursor().equals(cursor))) {
                throw new PlaudTeam.Unavailable("feed cursor did not move");
            }
            cursor = page.nextCursor();
        } while (page.hasMore());
        String checkpoint = page.nextCheckpoint() != null ? page.nextCheckpoint() : connection.checkpoint();
        connections.save(connection.checked(checkpoint, context.clock().instant()));
        return tally.result();
    }

    private void importFile(PlaudTeamConnection connection, Credential credential, TeamFile file, Tally tally) {
        if (file.deleted() || !connection.wasRecordedInRange(file.recordedAt())) {
            return;
        }
        if (!file.hasTranscript()) {
            tally.waiting++;
            return;
        }
        if (meetings.alreadyImported(connection.workspaceId(), connection.sourceId(), file.id())) {
            tally.alreadyImported++;
            return;
        }
        Transcript transcript = plaud.transcript(credential, file.id()).orElse(null);
        if (transcript == null) {
            return;
        }
        List<String> summaries = connection.includeSummaries() && file.hasSummary()
                ? plaud.summaries(credential, file.id()) : List.of();
        if (meetings.save(connection.workspaceId(), connection.sourceId(), file.id(), file.title(),
                file.recordedAt(), transcript, summaries)) {
            tally.imported++;
        } else {
            tally.alreadyImported++;
        }
    }

    private static final class Tally {
        int imported;
        int alreadyImported;
        int waiting;

        SyncResult result() {
            return new SyncResult(imported, alreadyImported, waiting);
        }
    }

    /**
     * {@code waitingForTranscript}: recordings Plaud is still transcribing; they arrive in a later sync.
     * {@code queued}: picked recordings not reached yet; later syncs import them, a few at a time.
     */
    public record SyncResult(int imported, int alreadyImported, int waitingForTranscript, int queued) {

        public SyncResult(int imported, int alreadyImported, int waitingForTranscript) {
            this(imported, alreadyImported, waitingForTranscript, 0);
        }
    }

    /** {@code problemCode} is set when the sync failed; {@link #SYNC_FAILED} for a failure ConXist did not expect. */
    public record SourceSync(UUID sourceId, SyncResult result, String problemCode) {
    }

    public static final String SYNC_FAILED = "plaud.sync-failed";
}
