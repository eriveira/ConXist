package com.conxist.workspaces.adapter.web;

import java.util.List;
import java.util.UUID;
import com.conxist.workspaces.application.CreateWorkspace;
import com.conxist.workspaces.application.DeleteWorkspace;
import com.conxist.workspaces.application.ListWorkspaces;
import com.conxist.workspaces.application.UpdateWorkspace;
import com.conxist.workspaces.application.WorkspaceView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lists, creates, renames and deletes workspaces. These requests name no workspace in a header. */
@RestController
@RequestMapping("/api/workspaces")
class WorkspacesController {

    /** Bounds what is read before the rules in Workspace apply; those count characters, this counts UTF-16 units. */
    private static final int MAX_INPUT = 2_000;

    private final CreateWorkspace createWorkspace;
    private final ListWorkspaces listWorkspaces;
    private final UpdateWorkspace updateWorkspace;
    private final DeleteWorkspace deleteWorkspace;

    WorkspacesController(CreateWorkspace createWorkspace, ListWorkspaces listWorkspaces,
            UpdateWorkspace updateWorkspace, DeleteWorkspace deleteWorkspace) {
        this.createWorkspace = createWorkspace;
        this.listWorkspaces = listWorkspaces;
        this.updateWorkspace = updateWorkspace;
        this.deleteWorkspace = deleteWorkspace;
    }

    @GetMapping
    List<WorkspaceView> list() {
        return listWorkspaces.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WorkspaceView create(@Valid @RequestBody WorkspaceRequest request) {
        return createWorkspace.create(request.name(), request.description());
    }

    @PutMapping("/{id}")
    WorkspaceView update(@PathVariable UUID id, @Valid @RequestBody WorkspaceRequest request) {
        return updateWorkspace.update(id, request.name(), request.description());
    }

    /** Deletes the workspace and everything in it, when {@code name} is its name exactly. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @Valid @RequestBody DeleteRequest request) {
        deleteWorkspace.delete(id, request.name());
    }

    record DeleteRequest(@Size(max = MAX_INPUT) String name) {
    }

    /** A new workspace, or a workspace's new name and description. */
    record WorkspaceRequest(@Size(max = MAX_INPUT) String name, @Size(max = MAX_INPUT) String description) {
    }
}
