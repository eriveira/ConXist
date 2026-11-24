package com.conxist.workspaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.conxist.platform.NotFound;
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
    private final UpdateWorkspace update = new UpdateWorkspace(repository, new ImmediateUnitOfWork());

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
    void a_workspace_takes_a_new_name_and_description_and_keeps_its_sources() {
        WorkspaceView workspace = create.create("Acme", "");
        count.sourceAdded(new SourceAdded(UUID.randomUUID(), workspace.id(), Provider.UPLOAD, "Specs", TestClock.NOW));

        WorkspaceView updated = update.update(workspace.id(), " Acme Product ", "Checkout and payments");

        assertThat(updated.name()).isEqualTo("Acme Product");
        assertThat(updated.description()).isEqualTo("Checkout and payments");
        assertThat(updated.sourceCount()).isEqualTo(1);
        assertThat(updated.createdAt()).isEqualTo(workspace.createdAt());
        assertThat(list.list()).singleElement().extracting(WorkspaceView::name).isEqualTo("Acme Product");
    }

    @Test
    void a_workspace_may_keep_its_own_name_in_other_case_but_not_take_another_ones() {
        WorkspaceView acme = create.create("Acme", "");
        create.create("Globex", "");

        assertThat(update.update(acme.id(), "ACME", "").name()).isEqualTo("ACME");
        assertThatThrownBy(() -> update.update(acme.id(), "globex", ""))
                .isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("workspace.name-taken");
    }

    @Test
    void an_update_follows_the_same_rules_and_needs_the_workspace_to_exist() {
        WorkspaceView acme = create.create("Acme", "");

        assertThatThrownBy(() -> update.update(acme.id(), " ", ""))
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo("workspace.name-required");
        assertThatThrownBy(() -> update.update(UUID.randomUUID(), "Other", ""))
                .isInstanceOf(NotFound.class)
                .extracting(e -> ((NotFound) e).code()).isEqualTo("workspace.not-found");
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
        public boolean saveIfNameFree(Workspace workspace) {
            return !nameTaken(workspace.name()) && workspaces.add(workspace);
        }

        @Override
        public Optional<WorkspaceView> find(UUID workspaceId) {
            return findAll().stream().filter(w -> w.id().equals(workspaceId)).findFirst();
        }

        @Override
        public boolean updateIfNameFree(Workspace workspace) {
            if (workspaces.stream().anyMatch(w -> !w.id().equals(workspace.id())
                    && w.name().equalsIgnoreCase(workspace.name()))) {
                return false;
            }
            workspaces.replaceAll(w -> w.id().equals(workspace.id()) ? workspace : w);
            return true;
        }

        @Override
        public List<UUID> ids() {
            return workspaces.stream().map(Workspace::id).toList();
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
