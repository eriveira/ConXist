package com.conxist.capture.application;

import java.util.List;
import com.conxist.platform.CurrentWorkspace;

/** The workspace's meeting recorder connections, for the Sources page. */
public final class ListRecorderConnections {

    private final PlaudTeamConnections connections;
    private final CurrentWorkspace workspace;

    public ListRecorderConnections(PlaudTeamConnections connections, CurrentWorkspace workspace) {
        this.connections = connections;
        this.workspace = workspace;
    }

    public List<ConnectionView> list() {
        return connections.findAll(workspace.id()).stream().map(ConnectionView::of).toList();
    }
}
