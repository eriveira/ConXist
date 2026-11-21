package com.conxist.agents.config;

import java.time.Clock;
import com.conxist.agents.application.AgentBook;
import com.conxist.agents.application.AgentRepository;
import com.conxist.agents.application.ArchiveAgent;
import com.conxist.agents.application.CreateAgent;
import com.conxist.agents.application.ListAgents;
import com.conxist.agents.application.PublishAgent;
import com.conxist.agents.application.ReadAgent;
import com.conxist.agents.application.RestoreAgentVersion;
import com.conxist.agents.application.SaveAgentDraft;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.UnitOfWork;
import com.conxist.sources.SourceCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the agents use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
class AgentsConfiguration {

    @Bean
    AgentBook agentBook(AgentRepository agents, CurrentWorkspace workspace, SourceCatalog sources,
            UnitOfWork unitOfWork, Clock clock) {
        return new AgentBook(agents, workspace, sources, unitOfWork, clock);
    }

    @Bean
    CreateAgent createAgent(AgentBook book) {
        return new CreateAgent(book);
    }

    @Bean
    ListAgents listAgents(AgentBook book) {
        return new ListAgents(book);
    }

    @Bean
    ReadAgent readAgent(AgentBook book) {
        return new ReadAgent(book);
    }

    @Bean
    SaveAgentDraft saveAgentDraft(AgentBook book) {
        return new SaveAgentDraft(book);
    }

    @Bean
    PublishAgent publishAgent(AgentBook book) {
        return new PublishAgent(book);
    }

    @Bean
    RestoreAgentVersion restoreAgentVersion(AgentBook book) {
        return new RestoreAgentVersion(book);
    }

    @Bean
    ArchiveAgent archiveAgent(AgentBook book) {
        return new ArchiveAgent(book);
    }
}
