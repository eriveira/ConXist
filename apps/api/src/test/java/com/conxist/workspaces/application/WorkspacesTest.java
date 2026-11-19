package com.conxist.workspaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceAdded;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.TestClock;
import com.conxist.workspaces.domain.Workspace;
import org.junit.jupiter.api.Test;

class WorkspacesTest {

    private final InMemoryWorkspaces repository = new InMemoryWorkspaces();
    private final CreateWorkspace create = new CreateWorkspace(repository, new ImmediateUnitOfWork(), TestClock.fixed());
    private final ListWorkspaces list = new ListWorkspaces(repository);
    private final CountSources count = new CountSources(repository);

    @Test
    void a_created_workspace_is_listed_with_no_sources() {
        WorkspaceView created = create.create("Acme Product", "Specs");

        assertThat(created.sourceCount()).isZero();
        assertThat(created.createdAt()).isEqualTo(TestClock.NOW);
        assertThat(list.list()).extracting(WorkspaceView::name).containsExactly("Acme Product");
        assertThat(list.exists(created.id())).isTrue();
        assertThat(list.exists(UUID.randomUUID())).isFalse();
        assertThat(list.ids()).containsExactly(created.id());
    }

    @Test
    void names_are_unique_ignoring_case() {
        create.create("Acme", "");

        assertThatThrownBy(() -> create.create(" ACME ", ""))
                .isInstanceOf(RuleViolation.class)
                .hasMessage("A workspace named \"ACME\" already exists.")
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("workspace.name-taken");
    }

    @Test
    void counts_each_source_once_even_when_the_event_comes_twice() {
        WorkspaceView workspace = create.create("Acme", "");
        SourceAdded added = new SourceAdded(UUID.randomUUID(), workspace.id(), Provider.UPLOAD, "Specs", TestClock.NOW);

        count.sourceAdded(added);
        count.sourceAdded(added);

        assertThat(list.list()).singleElement().extracting(WorkspaceView::sourceCount).isEqualTo(1);
    }

    private static final class InMemoryWorkspaces implements WorkspaceRepository {

        private final List<Workspace> workspaces = new ArrayList<>();
        private final Set<List<UUID>> sources = new HashSet<>();

        @Override
        public void save(Workspace workspace) {
            workspaces.add(workspace);
        }

        @Override
        public List<WorkspaceView> findAll() {
            return workspaces.stream().map(w -> new WorkspaceView(w.id(), w.name(), w.description(),
                    (int) sources.stream().filter(pair -> pair.get(0).equals(w.id())).count(), w.createdAt()))
                    .toList();
        }

        @Override
        public boolean exists(UUID workspaceId) {
            return workspaces.stream().anyMatch(w -> w.id().equals(workspaceId));
        }

        @Override
        public boolean nameTaken(String name) {
            return workspaces.stream().anyMatch(w -> w.name().equalsIgnoreCase(name));
        }

        @Override
        public void recordSource(UUID workspaceId, UUID sourceId) {
            sources.add(List.of(workspaceId, sourceId));
        }
    }
}
