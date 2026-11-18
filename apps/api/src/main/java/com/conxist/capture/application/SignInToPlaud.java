package com.conxist.capture.application;

import java.util.UUID;
import com.conxist.capture.application.PlaudAccounts.SignIn;
import com.conxist.capture.application.PlaudAccounts.SignInStart;
import com.conxist.capture.application.PlaudAccounts.SignInStatus;
import com.conxist.capture.domain.PlaudAccountConnection;
import com.conxist.capture.domain.StartFrom;
import com.conxist.platform.NotFound;
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
    private final CaptureContext context;

    public SignInToPlaud(SourceCatalog catalog, PlaudAccounts plaud, PlaudAccountConnections connections,
            PlaudTeamConnections teams, CaptureContext context) {
        this.sources = new CaptureSources(catalog);
        this.plaud = plaud;
        this.connections = connections;
        this.teams = teams;
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
                .orElseThrow(() -> new NotFound("plaud.sign-in-not-found", "This sign-in expired. Press Sign in again."));
    }

    public ConnectionView connect(UUID sourceId, UUID signInId, StartFrom startFrom, boolean includeSummaries) {
        sources.require(sourceId, Provider.PLAUD);
        UUID workspaceId = context.workspace().id();
        if (connections.find(workspaceId, sourceId).isPresent() || teams.find(workspaceId, sourceId).isPresent()) {
            throw new RuleViolation("plaud.already-connected", "This source is already connected to Plaud.");
        }
        SignIn signIn = signedIn(signInId);
        PlaudAccountConnection connection = PlaudAccountConnection.connect(sourceId, workspaceId,
                signIn.accountLabel(), startFrom, includeSummaries, context.clock().instant());
        context.unitOfWork().inTransaction(() -> {
            plaud.attach(workspaceId, signInId, sourceId);
            connections.save(connection);
            return null;
        });
        return ConnectionView.of(connection);
    }

    /** After Plaud signed ConXist out: a new sign-in replaces the old one and the source is active again. */
    public ConnectionView reconnect(UUID sourceId, UUID signInId) {
        sources.require(sourceId, Provider.PLAUD);
        UUID workspaceId = context.workspace().id();
        PlaudAccountConnection connection = connections.find(workspaceId, sourceId)
                .orElseThrow(() -> new RuleViolation("plaud.not-connected", "Connect this source to Plaud first."));
        if (!connection.accountLabel().equalsIgnoreCase(signedIn(signInId).accountLabel())) {
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

    private SignIn signedIn(UUID signInId) {
        SignIn signIn = status(signInId);
        if (signIn.status() != SignInStatus.SIGNED_IN) {
            throw RecorderProblems.signInNotFinished();
        }
        return signIn;
    }
}
