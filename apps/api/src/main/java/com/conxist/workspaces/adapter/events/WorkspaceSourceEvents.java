package com.conxist.workspaces.adapter.events;

import com.conxist.sources.SourceAdded;
import com.conxist.workspaces.application.CountSources;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Listens for new sources. The JDBC event registry retries a listener that fails. */
@Component
class WorkspaceSourceEvents {

    private final CountSources countSources;

    WorkspaceSourceEvents(CountSources countSources) {
        this.countSources = countSources;
    }

    @ApplicationModuleListener
    void on(SourceAdded event) {
        countSources.sourceAdded(event);
    }
}
