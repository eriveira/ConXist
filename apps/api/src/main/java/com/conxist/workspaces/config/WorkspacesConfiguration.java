package com.conxist.workspaces.config;

import java.time.Clock;
import java.util.List;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.UnitOfWork;
import com.conxist.platform.WorkspaceData;
import com.conxist.workspaces.application.CountSources;
import com.conxist.workspaces.application.CreateWorkspace;
import com.conxist.workspaces.application.DeleteWorkspace;
import com.conxist.workspaces.application.ListWorkspaces;
import com.conxist.workspaces.application.ManageVariables;
import com.conxist.workspaces.application.VariableRepository;
import com.conxist.workspaces.application.UpdateWorkspace;
import com.conxist.workspaces.application.WorkspaceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the workspaces use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
class WorkspacesConfiguration {

    @Bean
    CreateWorkspace createWorkspace(WorkspaceRepository workspaces, UnitOfWork unitOfWork, Clock clock) {
        return new CreateWorkspace(workspaces, unitOfWork, clock);
    }

    @Bean
    UpdateWorkspace updateWorkspace(WorkspaceRepository workspaces, UnitOfWork unitOfWork) {
        return new UpdateWorkspace(workspaces, unitOfWork);
    }

    @Bean
    DeleteWorkspace deleteWorkspace(WorkspaceRepository workspaces, List<WorkspaceData> data, UnitOfWork unitOfWork) {
        return new DeleteWorkspace(workspaces, data, unitOfWork);
    }

    @Bean
    ListWorkspaces listWorkspaces(WorkspaceRepository workspaces) {
        return new ListWorkspaces(workspaces);
    }

    @Bean
    ManageVariables manageVariables(VariableRepository variables, CurrentWorkspace workspace, UnitOfWork unitOfWork,
            Clock clock) {
        return new ManageVariables(variables, workspace, unitOfWork, clock);
    }

    @Bean
    CountSources countSources(WorkspaceRepository workspaces) {
        return new CountSources(workspaces);
    }
}
