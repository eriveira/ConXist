package com.conxist.capture.application;

import java.util.UUID;
import com.conxist.capture.application.PlaudTeam.Credential;
import com.conxist.capture.application.RecorderKeys.PublicKey;
import com.conxist.capture.domain.PlaudTeamConnection;
import com.conxist.capture.domain.StartFrom;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCatalog;

/**
 * Links a meeting recorder source to a Plaud Team: create a key for the admin to register, test the Client ID they
 * send back, then connect. Nothing is written to Plaud.
 */
public final class ConnectPlaudTeam {

    private final CaptureSources sources;
    private final RecorderKeys keys;
    private final PlaudTeam plaud;
    private final PlaudTeamConnections connections;
    private final PlaudAccountConnections accounts;
    private final CaptureContext context;

    public ConnectPlaudTeam(SourceCatalog catalog, RecorderKeys keys, PlaudTeam plaud,
            PlaudTeamConnections connections, PlaudAccountConnections accounts, CaptureContext context) {
        this.sources = new CaptureSources(catalog);
        this.keys = keys;
        this.plaud = plaud;
        this.connections = connections;
        this.accounts = accounts;
        this.context = context;
    }

    public PublicKey createKey() {
        return keys.create(context.workspace().id());
    }

    /** Signs in to Plaud with the key and answers which Team it reads. */
    public TeamCheck test(String clientId, String keyId) {
        return new TeamCheck(teamName(credential(clientId, keyId)));
    }

    public ConnectionView connect(UUID sourceId, String clientId, String keyId, StartFrom startFrom,
            boolean includeSummaries) {
        sources.require(sourceId, Provider.PLAUD);
        UUID workspaceId = context.workspace().id();
        if (connections.find(workspaceId, sourceId).isPresent() || accounts.find(workspaceId, sourceId).isPresent()) {
            throw new RuleViolation("plaud.already-connected", "This source is already connected to Plaud.");
        }
        Credential credential = credential(clientId, keyId);
        PlaudTeamConnection connection = PlaudTeamConnection.connect(sourceId, workspaceId, credential.clientId(),
                credential.keyId(), teamName(credential), startFrom, includeSummaries, context.clock().instant());
        connections.save(connection);
        return ConnectionView.of(connection);
    }

    private Credential credential(String clientId, String keyId) {
        if (keyId == null || !keys.exists(context.workspace().id(), keyId)) {
            throw new NotFound("plaud.key-not-found", "This key no longer exists. Close this and start again.");
        }
        return new Credential(context.workspace().id(), PlaudTeamConnection.cleanClientId(clientId), keyId);
    }

    private String teamName(Credential credential) {
        try {
            return plaud.teamName(credential);
        } catch (PlaudTeam.AccessDenied denied) {
            throw RecorderProblems.notAccepted();
        } catch (PlaudTeam.Unavailable unavailable) {
            throw RecorderProblems.unavailable();
        }
    }

    public record TeamCheck(String teamName) {
    }
}
