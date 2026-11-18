package com.conxist.sources.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceAdded;
import com.conxist.sources.SourceSummary;
import com.conxist.sources.domain.InvalidSource;
import com.conxist.sources.domain.SourceDraft;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.RecordedEvents;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class AddSourceTest {

    private final InMemorySources sources = new InMemorySources();
    private final RecordedEvents events = new RecordedEvents();
    private final ImmediateUnitOfWork unitOfWork = new ImmediateUnitOfWork();
    private final FixedWorkspace workspace = FixedWorkspace.standard();
    private final AddSource addSource = new AddSource(sources, workspace, events, unitOfWork, TestClock.fixed());
    private final ListSources listSources = new ListSources(sources, workspace);

    @Test
    void connects_the_source_and_announces_it_to_the_hub() {
        SourceSummary added = addSource.add(new SourceDraft(Provider.UPLOAD, "Product specs", null));

        assertThat(listSources.list()).containsExactly(added);
        assertThat(events.ofType(SourceAdded.class)).singleElement().satisfies(event -> {
            assertThat(event.sourceId()).isEqualTo(added.id());
            assertThat(event.workspaceId()).isEqualTo(workspace.id());
            assertThat(event.addedAt()).isEqualTo(TestClock.NOW);
        });
        assertThat(unitOfWork.transactions()).isOne();
    }

    @Test
    void rejects_a_second_source_with_the_same_name_ignoring_case() {
        addSource.add(new SourceDraft(Provider.UPLOAD, "Specs", null));

        assertThatThrownBy(() -> addSource.add(new SourceDraft(Provider.UPLOAD, "SPECS", null)))
                .isInstanceOf(InvalidSource.class);
        assertThat(events.ofType(SourceAdded.class)).hasSize(1);
    }

    @Test
    void finds_only_sources_of_the_current_workspace() {
        SourceSummary added = addSource.add(new SourceDraft(Provider.UPLOAD, "Specs", null));
        ListSources otherWorkspace = new ListSources(sources, new FixedWorkspace(UUID.randomUUID()));

        assertThat(listSources.find(added.id())).contains(added);
        assertThat(otherWorkspace.find(added.id())).isEmpty();
    }
}
