package com.conxist.workspaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.TestClock;
import com.conxist.workspaces.domain.Variable;
import org.junit.jupiter.api.Test;

class ManageVariablesTest {

    private static final FixedWorkspace OTHER = new FixedWorkspace(UUID.randomUUID());

    private final InMemoryVariables repository = new InMemoryVariables();
    private final ManageVariables variables = manage(FixedWorkspace.standard());

    private ManageVariables manage(FixedWorkspace workspace) {
        return new ManageVariables(repository, workspace, new ImmediateUnitOfWork(), TestClock.fixed());
    }

    @Test
    void added_variables_are_listed_by_name_with_their_value() {
        variables.add(" topics ", " Checkout, Payments ");
        variables.add("company_name", "Acme Inc.");

        assertThat(variables.list()).extracting(Variable::name).containsExactly("company_name", "topics");
        assertThat(variables.list().get(1).value()).isEqualTo("Checkout, Payments");
        assertThat(variables.list().get(0).createdAt()).isEqualTo(TestClock.NOW);
    }

    @Test
    void each_workspace_has_its_own_variables_and_cannot_reach_another_ones() {
        Variable company = variables.add("company_name", "Acme Inc.");
        ManageVariables other = manage(OTHER);

        other.add("company_name", "Their own");

        assertThat(other.list()).extracting(Variable::value).containsExactly("Their own");
        assertThatThrownBy(() -> other.change(company.id(), "mine_now", "x")).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> other.remove(company.id())).isInstanceOf(NotFound.class);
        assertThat(variables.list()).extracting(Variable::name).containsExactly("company_name");
    }

    @Test
    void names_are_unique_in_a_workspace_ignoring_case_but_a_variable_may_keep_its_own() {
        Variable company = variables.add("company_name", "Acme");
        variables.add("topics", "Checkout");

        assertThat(code(() -> variables.add("COMPANY_NAME", "x"))).isEqualTo("variable.name-taken");
        assertThat(code(() -> variables.change(company.id(), "Topics", "x"))).isEqualTo("variable.name-taken");
        assertThat(variables.change(company.id(), "Company_Name", "Acme Inc.").name()).isEqualTo("Company_Name");
    }

    @Test
    void a_variable_needs_a_short_name_made_for_braces_and_a_value() {
        assertThat(code(() -> variables.add("  ", "x"))).isEqualTo("variable.name-required");
        assertThat(code(() -> variables.add("a".repeat(Variable.MAX_NAME_LENGTH + 1), "x")))
                .isEqualTo("variable.name-too-long");
        assertThat(code(() -> variables.add("company name", "x"))).isEqualTo("variable.name-invalid");
        assertThat(code(() -> variables.add("2fa", "x"))).isEqualTo("variable.name-invalid");
        assertThat(code(() -> variables.add("a}}b", "x"))).isEqualTo("variable.name-invalid");
        assertThat(code(() -> variables.add("ok", "  "))).isEqualTo("variable.value-required");
        assertThat(code(() -> variables.add("ok", "x".repeat(Variable.MAX_VALUE_LENGTH + 1))))
                .isEqualTo("variable.value-too-long");
        assertThat(variables.add("ok", "😀".repeat(Variable.MAX_VALUE_LENGTH)).value())
                .hasSize(Variable.MAX_VALUE_LENGTH * 2);
    }

    @Test
    void a_workspace_keeps_up_to_a_hundred_variables() {
        for (int i = 0; i < Variable.MAX_PER_WORKSPACE; i++) {
            variables.add("variable_" + i, "x");
        }

        assertThat(code(() -> variables.add("one_more", "x"))).isEqualTo("variable.too-many");
    }

    @Test
    void runs_get_one_workspaces_values_keyed_by_lower_case_name() {
        variables.add("Company_Name", "Acme Inc.");
        manage(OTHER).add("topics", "Theirs");

        assertThat(variables.of(FixedWorkspace.standard().id())).isEqualTo(java.util.Map.of("company_name", "Acme Inc."));
        assertThat(variables.of(OTHER.id())).isEqualTo(java.util.Map.of("topics", "Theirs"));
    }

    @Test
    void a_removed_variable_is_gone() {
        Variable company = variables.add("company_name", "Acme");

        variables.remove(company.id());

        assertThat(variables.list()).isEmpty();
        assertThat(code(() -> variables.remove(company.id()))).isEqualTo("variable.not-found");
    }

    private static String code(Runnable action) {
        try {
            action.run();
        } catch (RuleViolation violation) {
            return violation.code();
        }
        throw new AssertionError("expected a rule violation");
    }

    private static final class InMemoryVariables implements VariableRepository {

        private final List<Variable> variables = new ArrayList<>();

        @Override
        public List<Variable> findAll(UUID workspaceId) {
            return variables.stream().filter(t -> t.workspaceId().equals(workspaceId))
                    .sorted(Comparator.comparing(t -> t.name().toLowerCase())).toList();
        }

        @Override
        public Optional<Variable> find(UUID workspaceId, UUID variableId) {
            return findAll(workspaceId).stream().filter(t -> t.id().equals(variableId)).findFirst();
        }

        @Override
        public int lockAndCount(UUID workspaceId) {
            return findAll(workspaceId).size();
        }

        @Override
        public boolean addIfNameFree(Variable variable) {
            return !nameTaken(variable) && variables.add(variable);
        }

        @Override
        public boolean updateIfNameFree(Variable variable) {
            if (find(variable.workspaceId(), variable.id()).isEmpty()) {
                throw new NotFound("variable.not-found", "gone");
            }
            if (nameTaken(variable)) {
                return false;
            }
            variables.replaceAll(t -> t.id().equals(variable.id()) ? variable : t);
            return true;
        }

        private boolean nameTaken(Variable variable) {
            return findAll(variable.workspaceId()).stream().anyMatch(t -> !t.id().equals(variable.id())
                    && t.name().equalsIgnoreCase(variable.name()));
        }

        @Override
        public boolean delete(UUID workspaceId, UUID variableId) {
            return variables.removeIf(t -> t.workspaceId().equals(workspaceId) && t.id().equals(variableId));
        }
    }
}
