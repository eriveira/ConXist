package com.conxist.workspaces.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.workspaces.domain.Variable;

public interface VariableRepository {

    /** A workspace's variables, by name. */
    List<Variable> findAll(UUID workspaceId);

    Optional<Variable> find(UUID workspaceId, UUID variableId);

    /**
     * Holds the workspace's variable list until the transaction ends, so two adds at once cannot both pass the limit.
     * Returns how many variables the workspace has.
     */
    int lockAndCount(UUID workspaceId);

    /** Adds a variable unless another variable of the workspace has the name (ignoring case), as when two saves race. */
    boolean addIfNameFree(Variable variable);

    /**
     * Saves a variable's new text unless another variable of the workspace has the name.
     *
     * @throws com.conxist.platform.NotFound when the variable is no longer in its workspace
     */
    boolean updateIfNameFree(Variable variable);

    boolean delete(UUID workspaceId, UUID variableId);
}
