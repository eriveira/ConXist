package com.conxist.runs.config;

import java.time.Clock;
import java.util.List;
import com.conxist.agents.AgentBriefs;
import com.conxist.capture.MeetingLibrary;
import com.conxist.connectors.McpToolbox;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.WorkspaceVariables;
import com.conxist.runs.adapter.model.ClaudeModel;
import com.conxist.runs.adapter.model.OpenAiModel;
import com.conxist.runs.application.AllowModel;
import com.conxist.runs.application.AllowedModelsRepository;
import com.conxist.runs.application.ConnectProvider;
import com.conxist.runs.application.ProviderKeys;
import com.conxist.runs.application.ProviderKeysRepository;
import com.conxist.runs.application.ModelPort;
import com.conxist.runs.application.LlmProviders;
import com.conxist.runs.application.ModelDefaultsRepository;
import com.conxist.runs.application.ProviderModelLists;
import com.conxist.runs.application.RunTools;
import com.conxist.runs.application.RunLimitsRepository;
import com.conxist.runs.application.StartTestRun;
import com.conxist.runs.application.Tuga;
import com.conxist.runs.application.TugaSettingsRepository;
import com.conxist.runs.application.WorkspaceRunLimits;
import com.conxist.runs.application.GuardrailsRepository;
import com.conxist.runs.application.WorkspaceGuardrails;
import com.conxist.sources.SourceCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Wires test runs, Tuga and LLM providers: the read tools, Claude and OpenAI as the models, keys, model lists, and the use cases. */
@Configuration(proxyBeanMethods = false)
class RunsConfiguration {

    /** The providers, in the order Settings lists them (ADR 0014). */
    @Bean
    @Order(1)
    ModelPort claudeModel(RunsProperties properties) {
        return new ClaudeModel(properties.anthropicBaseUrl());
    }

    @Bean
    @Order(2)
    ModelPort openAiModel(RunsProperties properties) {
        return new OpenAiModel(properties.openaiBaseUrl());
    }

    /** A workspace's own key first; else the key set on this computer, when there is one. */
    @Bean
    ProviderKeys providerKeys(RunsProperties properties, ProviderKeysRepository stored, CurrentWorkspace workspace) {
        return new ProviderKeys(stored, properties.computerKeys(), workspace);
    }

    @Bean
    RunTools runTools(SourceCatalog sources, MeetingLibrary library, McpToolbox mcp) {
        return new RunTools(sources, library, mcp);
    }

    @Bean
    ProviderModelLists providerModelLists(RunsProperties properties, Clock clock) {
        return new ProviderModelLists(properties.modelListKeptFor(), properties.modelListMinRefresh(),
                properties.modelListTimeout(), clock);
    }

    /** Also answers the agents module which models an agent may pick (AgentModels). */
    @Bean
    LlmProviders llmProviders(RunsProperties properties, List<ModelPort> ports, ModelDefaultsRepository defaults,
            AllowedModelsRepository allowed, ProviderModelLists lists, ProviderKeys keys, CurrentWorkspace workspace,
            Clock clock) {
        return new LlmProviders(properties.catalog(), ports, defaults, allowed, lists, keys, workspace, clock);
    }

    @Bean
    ConnectProvider connectProvider(LlmProviders providers, ProviderModelLists lists, ProviderKeys keys,
            CurrentWorkspace workspace, Clock clock) {
        return new ConnectProvider(providers, lists, keys, workspace, clock);
    }

    @Bean
    AllowModel allowModel(LlmProviders providers, ProviderModelLists lists, AllowedModelsRepository allowed,
            AgentBriefs agents, CurrentWorkspace workspace, Clock clock) {
        return new AllowModel(providers, lists, allowed, agents, workspace, clock);
    }

    @Bean
    StartTestRun startTestRun(AgentBriefs agents, MeetingLibrary library, RunTools tools, LlmProviders providers,
            WorkspaceRunLimits limits, WorkspaceGuardrails guardrails, CurrentWorkspace workspace,
            WorkspaceVariables variables, Clock clock) {
        return new StartTestRun(agents, library, tools, providers, limits, guardrails, workspace, variables, clock);
    }

    @Bean
    Tuga tuga(TugaSettingsRepository settings, RunTools tools, LlmProviders providers, WorkspaceRunLimits limits,
            WorkspaceGuardrails guardrails, CurrentWorkspace workspace, WorkspaceVariables variables, Clock clock) {
        return new Tuga(settings, tools, providers, limits, guardrails, workspace, variables, clock);
    }

    @Bean
    WorkspaceGuardrails workspaceGuardrails(GuardrailsRepository guardrails, CurrentWorkspace workspace, Clock clock) {
        return new WorkspaceGuardrails(guardrails, workspace, clock);
    }

    @Bean
    WorkspaceRunLimits workspaceRunLimits(RunLimitsRepository limits, CurrentWorkspace workspace, Clock clock) {
        return new WorkspaceRunLimits(limits, workspace, clock);
    }
}
