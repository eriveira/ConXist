package com.conxist.capture.application;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import com.conxist.capture.DocumentOrigin;
import com.conxist.capture.application.PlaudTeam.ChangePage;
import com.conxist.capture.application.PlaudTeam.Credential;
import com.conxist.capture.application.PlaudTeam.TeamFile;
import com.conxist.capture.application.PlaudTeam.Transcript;
import com.conxist.capture.domain.CapturedDocument;
import com.conxist.capture.domain.ConnectionStatus;
import com.conxist.capture.domain.DocumentTitle;
import com.conxist.capture.domain.MeetingText;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCatalog;

/**
 * Reads a Plaud Team's change feed from where the last sync stopped and imports each new meeting's transcript (and
 * summaries, if chosen) as a document waiting for review. Meetings already imported, recorded before the chosen start,
 * deleted, or still being transcribed are skipped; a meeting whose transcript arrives later shows up again in the feed.
 * The checkpoint is saved only after the whole feed was read, so an interrupted sync repeats safely.
 */
public final class SyncRecorder {

    private final CaptureSources sources;
    private final PlaudTeam plaud;
    private final PlaudTeamConnections connections;
    private final CapturedDocumentRepository documents;
    private final CaptureContext context;

    public SyncRecorder(SourceCatalog catalog, PlaudTeam plaud, PlaudTeamConnections connections,
            CapturedDocumentRepository documents, CaptureContext context) {
        this.sources = new CaptureSources(catalog);
        this.plaud = plaud;
        this.connections = connections;
        this.documents = documents;
        this.context = context;
    }

    public SyncResult sync(UUID sourceId) {
        sources.require(sourceId, Provider.PLAUD);
        PlaudTeamConnection connection = connections.find(context.workspace().id(), sourceId)
                .orElseThrow(() -> new RuleViolation("plaud.not-connected", "Connect this source to Plaud first."));
        return run(connection);
    }

    /** The scheduled check: every active connection of the workspace. One failing Team does not stop the others. */
    public List<SourceSync> syncAll() {
        return connections.findAll(context.workspace().id()).stream()
                .filter(connection -> connection.status() == ConnectionStatus.ACTIVE)
                .map(connection -> {
                    try {
                        return new SourceSync(connection.sourceId(), run(connection), null);
                    } catch (RuleViolation problem) {
                        return new SourceSync(connection.sourceId(), null, problem.code());
                    }
                })
                .toList();
    }

    private SyncResult run(PlaudTeamConnection connection) {
        Credential credential = new Credential(connection.clientId(), connection.keyId());
        try {
            return readFeed(connection, credential);
        } catch (PlaudTeam.CheckpointExpired expired) {
            return readFeed(connection.withoutCheckpoint(), credential);
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
        do {
            page = plaud.changes(credential, connection.checkpoint(), cursor);
            for (TeamFile file : page.files()) {
                importFile(connection, credential, file, tally);
            }
            cursor = page.nextCursor();
        } while (page.hasMore());
        connections.save(connection.checked(page.nextCheckpoint(), context.clock().instant()));
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
        if (documents.exists(connection.workspaceId(), connection.sourceId(), file.id())) {
            tally.alreadyImported++;
            return;
        }
        Transcript transcript = plaud.transcript(credential, file.id()).orElse(null);
        if (transcript == null) {
            return;
        }
        List<String> summaries = connection.includeSummaries() && file.hasSummary()
                ? plaud.summaries(credential, file.id()) : List.of();
        DocumentTitle title = DocumentTitle.ofMeeting(file.title());
        String text = MeetingText.compose(title.value(), file.recordedAt(), transcript.language(), summaries,
                transcript.lines());
        CapturedDocument document = new CapturedDocument(UUID.randomUUID(), connection.workspaceId(),
                connection.sourceId(), title, MeetingText.MEDIA_TYPE, text.getBytes(StandardCharsets.UTF_8).length,
                text, DocumentOrigin.RECORDING, file.id(), context.clock().instant());
        if (context.saveAndAnnounce(documents, document)) {
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

    /** {@code waitingForTranscript}: recordings Plaud is still transcribing; they arrive in a later sync. */
    public record SyncResult(int imported, int alreadyImported, int waitingForTranscript) {
    }

    public record SourceSync(UUID sourceId, SyncResult result, String problemCode) {
    }
}
