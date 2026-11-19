package com.conxist.capture.application;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.conxist.capture.application.RecordingPicks.Pick;
import com.conxist.capture.application.PlaudAccounts.SignIn;
import com.conxist.capture.application.PlaudAccounts.SignInStart;
import com.conxist.capture.application.PlaudAccounts.SignInStatus;
import com.conxist.capture.domain.PlaudAccountConnection;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCatalog;

/**
 * Connects a meeting recorder source to a person's own Plaud account: start a sign-in on Plaud's page, wait for the
 * person to finish it there, then link the account to the source. ConXist never sees the Plaud password and asks
 * Plaud only to read.
 */
public final class SignInToPlaud {

    private final CaptureSources sources;
    private final PlaudAccounts plaud;
    private final PlaudAccountConnections connections;
    private final PlaudTeamConnections teams;
    private final ListPlaudRecordings recordings;
    private final RecordingPicks picks;
    private final CaptureContext context;

    public SignInToPlaud(SourceCatalog catalog, PlaudAccounts plaud, PlaudAccountConnections connections,
            PlaudTeamConnections teams, ListPlaudRecordings recordings, RecordingPicks picks, CaptureContext context) {
        this.sources = new CaptureSources(catalog);
        this.plaud = plaud;
        this.connections = connections;
        this.teams = teams;
        this.recordings = recordings;
        this.picks = picks;
        this.context = context;
    }

    public SignInStart start() {
        try {
            return plaud.startSignIn(context.workspace().id());
        } catch (PlaudAccounts.Unavailable | PlaudAccounts.AccessDenied unavailable) {
            throw RecorderProblems.unavailable();
        }
    }

    /** Called when Plaud's page sends the person back. Never throws: the waiting dialog reads the outcome. */
    public void finish(String state, String code, String error) {
        plaud.finishSignIn(state, code, error);
    }

    public SignIn status(UUID signInId) {
        return plaud.signIn(context.workspace().id(), signInId)
                .orElseThrow(RecorderProblems::signInExpired);
    }

    /**
     * Links the signed-in account to the source, with the recordings the person picked; a sync imports them, a few at
     * a time.
     */
    public ConnectionView connect(UUID sourceId, AccountChoice choice) {
        sources.require(sourceId, Provider.PLAUD);
        UUID signInId = choice.signInId();
        boolean importNew = choice.importNew();
        Set<String> fileIds = new LinkedHashSet<>(choice.recordingIds());
        if (fileIds.size() > PlaudAccounts.MAX_RECENT_FILES) {
            throw RecorderProblems.tooManyPicks(PlaudAccounts.MAX_RECENT_FILES);
        }
        if (fileIds.isEmpty() && !importNew) {
            throw RecorderProblems.nothingPicked();
        }
        UUID workspaceId = context.workspace().id();
        if (connections.find(workspaceId, sourceId).isPresent() || teams.find(workspaceId, sourceId).isPresent()) {
            throw alreadyConnected();
        }
        SignIn signIn = signedIn(signInId);
        List<Pick> picks = fileIds.isEmpty() ? List.of() : recordings.resolve(workspaceId, signInId, fileIds);
        Instant now = context.clock().instant();
        PlaudAccountConnection connection = PlaudAccountConnection.connect(sourceId, workspaceId,
                signIn.accountLabel(), signIn.accountKey(), importNew, choice.includeSummaries(), now);
        context.unitOfWork().inTransaction(() -> {
            // Added before the tokens move, so two connects racing for one source cannot both win.
            if (!connections.add(connection)) {
                throw alreadyConnected();
            }
            plaud.attach(workspaceId, signInId, sourceId);
            this.picks.add(workspaceId, sourceId, picks, now);
            return null;
        });
        recordings.forget(signInId);
        return ConnectionView.of(connection);
    }

    /** After Plaud signed ConXist out: a new sign-in replaces the old one and the source is active again. */
    public ConnectionView reconnect(UUID sourceId, UUID signInId) {
        sources.require(sourceId, Provider.PLAUD);
        UUID workspaceId = context.workspace().id();
        PlaudAccountConnection connection = connections.find(workspaceId, sourceId)
                .orElseThrow(() -> new RuleViolation("plaud.not-connected", "Connect this source to Plaud first."));
        if (!connection.isAccount(signedIn(signInId).accountKey())) {
            // A different account would mix its meetings into this source and break "each meeting imported once".
            throw RecorderProblems.otherAccount();
        }
        PlaudAccountConnection active = connection.checked(connection.readThrough(), context.clock().instant());
        context.unitOfWork().inTransaction(() -> {
            plaud.attach(workspaceId, signInId, sourceId);
            connections.save(active);
            return null;
        });
        return ConnectionView.of(active);
    }

    private static RuleViolation alreadyConnected() {
        return new RuleViolation("plaud.already-connected", "This source is already connected to Plaud.");
    }

    private SignIn signedIn(UUID signInId) {
        SignIn signIn = status(signInId);
        if (signIn.status() != SignInStatus.SIGNED_IN) {
            throw RecorderProblems.signInNotFinished();
        }
        return signIn;
    }

    /**
     * What the person chose while connecting: {@code recordingIds} from {@link ListPlaudRecordings} to import now (may
     * be empty), and whether recordings made from now on come in as well.
     */
    public record AccountChoice(UUID signInId, List<String> recordingIds, boolean importNew, boolean includeSummaries) {

        public AccountChoice {
            recordingIds = List.copyOf(recordingIds);
        }
    }
}
