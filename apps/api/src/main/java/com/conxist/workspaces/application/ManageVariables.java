package com.conxist.workspaces.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.platform.UnitOfWork;
import com.conxist.platform.WorkspaceVariables;
import com.conxist.workspaces.domain.Variable;

/** The current workspace's variables (ADR 0018): list, add, change and remove them. */
public final class ManageVariables implements WorkspaceVariables {

    private final VariableRepository variables;
    private final CurrentWorkspace workspace;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ManageVariables(VariableRepository variables, CurrentWorkspace workspace, UnitOfWork unitOfWork, Clock clock) {
        this.variables = variables;
        this.workspace = workspace;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public List<Variable> list() {
        return variables.findAll(workspace.id());
    }

    @Override
    public Map<String, String> of(UUID workspaceId) {
        Map<String, String> values = new LinkedHashMap<>();
        variables.findAll(workspaceId).forEach(variable -> values.put(variable.name().toLowerCase(Locale.ROOT),
                variable.value()));
        return Map.copyOf(values);
    }

    public Variable add(String name, String value) {
        UUID workspaceId = workspace.id();
        Variable variable = Variable.create(UUID.randomUUID(), workspaceId, name, value, clock.instant());
        return unitOfWork.inTransaction(() -> {
            if (variables.lockAndCount(workspaceId) >= Variable.MAX_PER_WORKSPACE) {
                throw new RuleViolation("variable.too-many",
                        "A workspace keeps up to " + Variable.MAX_PER_WORKSPACE + " variables. Remove one first.");
            }
            return nameFree(variables.addIfNameFree(variable), variable);
        });
    }

    public Variable change(UUID variableId, String name, String value) {
        UUID workspaceId = workspace.id();
        return unitOfWork.inTransaction(() -> {
            Variable variable = existing(workspaceId, variableId).withText(name, value);
            return nameFree(variables.updateIfNameFree(variable), variable);
        });
    }

    public void remove(UUID variableId) {
        UUID workspaceId = workspace.id();
        if (!variables.delete(workspaceId, variableId)) {
            throw notFound();
        }
    }

    private Variable existing(UUID workspaceId, UUID variableId) {
        return variables.find(workspaceId, variableId).orElseThrow(ManageVariables::notFound);
    }

    private static Variable nameFree(boolean saved, Variable variable) {
        if (!saved) {
            throw new RuleViolation("variable.name-taken", "This workspace already has a variable called " + variable.name() + ".");
        }
        return variable;
    }

    private static NotFound notFound() {
        return new NotFound("variable.not-found", "This variable no longer exists.");
    }
}
