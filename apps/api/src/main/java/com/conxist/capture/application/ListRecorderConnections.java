package com.conxist.capture.application;

import java.util.List;
import java.util.stream.Stream;
import com.conxist.platform.CurrentWorkspace;

/** The workspace's meeting recorder connections, for the Sources page. */
public final class ListRecorderConnections {

    private final PlaudTeamConnections teams;
    private final PlaudAccountConnections accounts;
    private final CurrentWorkspace workspace;

    public ListRecorderConnections(PlaudTeamConnections teams, PlaudAccountConnections accounts,
            CurrentWorkspace workspace) {
        this.teams = teams;
        this.accounts = accounts;
        this.workspace = workspace;
    }

    public List<ConnectionView> list() {
        return Stream.concat(
                teams.findAll(workspace.id()).stream().map(ConnectionView::of),
                accounts.findAll(workspace.id()).stream().map(ConnectionView::of)).toList();
    }
}
