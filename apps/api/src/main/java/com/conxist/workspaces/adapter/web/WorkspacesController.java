package com.conxist.workspaces.adapter.web;

import java.util.List;
import com.conxist.workspaces.application.CreateWorkspace;
import com.conxist.workspaces.application.ListWorkspaces;
import com.conxist.workspaces.application.WorkspaceView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lists and creates workspaces. These requests name no workspace. */
@RestController
@RequestMapping("/api/workspaces")
class WorkspacesController {

    /** Bounds what is read before the rules in Workspace apply; those count characters, this counts UTF-16 units. */
    private static final int MAX_INPUT = 2_000;

    private final CreateWorkspace createWorkspace;
    private final ListWorkspaces listWorkspaces;

    WorkspacesController(CreateWorkspace createWorkspace, ListWorkspaces listWorkspaces) {
        this.createWorkspace = createWorkspace;
        this.listWorkspaces = listWorkspaces;
    }

    @GetMapping
    List<WorkspaceView> list() {
        return listWorkspaces.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    WorkspaceView create(@Valid @RequestBody CreateRequest request) {
        return createWorkspace.create(request.name(), request.description());
    }

    record CreateRequest(@Size(max = MAX_INPUT) String name, @Size(max = MAX_INPUT) String description) {
    }
}
