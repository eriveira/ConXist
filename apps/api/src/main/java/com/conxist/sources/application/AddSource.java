package com.conxist.sources.application;

import java.time.Clock;
import java.util.UUID;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.EventPublisher;
import com.conxist.platform.UnitOfWork;
import com.conxist.sources.SourceAdded;
import com.conxist.sources.SourceSummary;
import com.conxist.sources.domain.InvalidSource;
import com.conxist.sources.domain.Source;
import com.conxist.sources.domain.SourceDraft;

/** Connects a new source to the workspace's hub. */
public final class AddSource {

    private final SourceRepository sources;
    private final CurrentWorkspace workspace;
    private final EventPublisher events;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public AddSource(SourceRepository sources, CurrentWorkspace workspace, EventPublisher events,
            UnitOfWork unitOfWork, Clock clock) {
        this.sources = sources;
        this.workspace = workspace;
        this.events = events;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public SourceSummary add(SourceDraft draft) {
        Source source = Source.add(UUID.randomUUID(), workspace.id(), draft, clock.instant());
        return unitOfWork.inTransaction(() -> {
            if (sources.nameTaken(source.workspaceId(), source.name())) {
                throw new InvalidSource("name-taken", "A source named \"" + source.name() + "\" already exists.");
            }
            sources.save(source);
            events.publish(new SourceAdded(source.id(), source.workspaceId(), source.provider(), source.name(),
                    source.addedAt()));
            return source.toSummary();
        });
    }
}
