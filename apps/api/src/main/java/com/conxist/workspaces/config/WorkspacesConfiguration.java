package com.conxist.workspaces.config;

import java.time.Clock;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.UnitOfWork;
import com.conxist.workspaces.application.CountSources;
import com.conxist.workspaces.application.CreateWorkspace;
import com.conxist.workspaces.application.ListWorkspaces;
import com.conxist.workspaces.application.ManageTopics;
import com.conxist.workspaces.application.TopicRepository;
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
    ListWorkspaces listWorkspaces(WorkspaceRepository workspaces) {
        return new ListWorkspaces(workspaces);
    }

    @Bean
    ManageTopics manageTopics(TopicRepository topics, CurrentWorkspace workspace, UnitOfWork unitOfWork,
            Clock clock) {
        return new ManageTopics(topics, workspace, unitOfWork, clock);
    }

    @Bean
    CountSources countSources(WorkspaceRepository workspaces) {
        return new CountSources(workspaces);
    }
}
