package com.conxist.agents.config;

import java.time.Clock;
import com.conxist.agents.AgentBriefs;
import com.conxist.agents.AgentMcpTools;
import com.conxist.agents.AgentModels;
import com.conxist.agents.application.AddSkill;
import com.conxist.agents.application.AgentBook;
import com.conxist.agents.application.AgentRepository;
import com.conxist.agents.application.ArchiveAgent;
import com.conxist.agents.application.ArchiveSkill;
import com.conxist.agents.application.AgentUses;
import com.conxist.agents.application.CreateAgent;
import com.conxist.agents.application.DeleteAgent;
import com.conxist.agents.application.DeleteSkill;
import com.conxist.agents.application.ListAgents;
import com.conxist.agents.application.ListSkills;
import com.conxist.agents.application.PublishAgent;
import com.conxist.agents.application.PublishSkill;
import com.conxist.agents.application.ReadAgent;
import com.conxist.agents.application.ReadAgentBriefs;
import com.conxist.agents.application.ReadSkill;
import com.conxist.agents.application.RestoreAgentVersion;
import com.conxist.agents.application.RestoreSkillVersion;
import com.conxist.agents.application.SaveAgentDraft;
import com.conxist.agents.application.SaveSkillDraft;
import com.conxist.agents.application.SkillBook;
import com.conxist.agents.application.SkillRepository;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.UnitOfWork;
import com.conxist.sources.SourceCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the agents and skills use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
class AgentsConfiguration {

    @Bean
    SkillBook skillBook(SkillRepository skills, CurrentWorkspace workspace, UnitOfWork unitOfWork, Clock clock) {
        return new SkillBook(skills, workspace, unitOfWork, clock);
    }

    @Bean
    AgentBook agentBook(AgentRepository agents, CurrentWorkspace workspace, SourceCatalog sources,
            SkillBook skills, AgentModels models, AgentMcpTools mcpTools, UnitOfWork unitOfWork, Clock clock) {
        return new AgentBook(agents, workspace, sources, skills, models, mcpTools, unitOfWork, clock);
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

    @Bean
    ListSkills listSkills(SkillBook book) {
        return new ListSkills(book);
    }

    @Bean
    AddSkill addSkill(SkillBook book) {
        return new AddSkill(book);
    }

    @Bean
    ReadSkill readSkill(SkillBook book) {
        return new ReadSkill(book);
    }

    @Bean
    SaveSkillDraft saveSkillDraft(SkillBook book) {
        return new SaveSkillDraft(book);
    }

    @Bean
    PublishSkill publishSkill(SkillBook book) {
        return new PublishSkill(book);
    }

    @Bean
    RestoreSkillVersion restoreSkillVersion(SkillBook book) {
        return new RestoreSkillVersion(book);
    }

    @Bean
    ArchiveSkill archiveSkill(SkillBook book) {
        return new ArchiveSkill(book);
    }

    @Bean
    DeleteSkill deleteSkill(SkillBook skills, AgentBook agents) {
        return new DeleteSkill(skills, agents);
    }

    /** Nothing uses an agent until flows and schedules exist; they replace this bean with their uses. */
    @Bean
    AgentUses agentUses() {
        return AgentUses.none();
    }

    @Bean
    DeleteAgent deleteAgent(AgentBook book, AgentUses uses) {
        return new DeleteAgent(book, uses);
    }

    @Bean
    AgentBriefs agentBriefs(AgentBook book, SkillBook skills) {
        return new ReadAgentBriefs(book, skills);
    }
}
