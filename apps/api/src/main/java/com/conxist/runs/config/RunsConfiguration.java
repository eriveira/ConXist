package com.conxist.runs.config;

import java.time.Clock;
import java.util.List;
import com.conxist.agents.AgentBriefs;
import com.conxist.capture.MeetingLibrary;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.runs.adapter.model.ClaudeModel;
import com.conxist.runs.adapter.model.JsonAnswerReader;
import com.conxist.runs.application.AllowModel;
import com.conxist.runs.application.AllowedModelsRepository;
import com.conxist.runs.application.AnswerReader;
import com.conxist.runs.application.ModelPort;
import com.conxist.runs.application.LlmProviders;
import com.conxist.runs.application.ModelDefaultsRepository;
import com.conxist.runs.application.ProviderModelLists;
import com.conxist.runs.application.RunTools;
import com.conxist.runs.application.StartTestRun;
import com.conxist.sources.SourceCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires test runs and LLM providers: the read tools, Claude as the model, its model list, and the use cases. */
@Configuration(proxyBeanMethods = false)
class RunsConfiguration {

    @Bean
    @ConditionalOnMissingBean(ModelPort.class)
    ModelPort claudeModel(RunsProperties properties) {
        return new ClaudeModel(properties.anthropicApiKey(), properties.anthropicBaseUrl());
    }

    @Bean
    RunTools runTools(SourceCatalog sources, MeetingLibrary library) {
        return new RunTools(sources, library);
    }

    @Bean
    AnswerReader answerReader() {
        return new JsonAnswerReader();
    }

    @Bean
    ProviderModelLists providerModelLists(RunsProperties properties, Clock clock) {
        return new ProviderModelLists(properties.modelListKeptFor(), properties.modelListMinRefresh(),
                properties.modelListTimeout(), clock);
    }

    /** Also answers the agents module which models an agent may pick (AgentModels). */
    @Bean
    LlmProviders llmProviders(RunsProperties properties, List<ModelPort> ports, ModelDefaultsRepository defaults,
            AllowedModelsRepository allowed, ProviderModelLists lists, CurrentWorkspace workspace, Clock clock) {
        return new LlmProviders(properties.catalog(), ports, defaults, allowed, lists, workspace, clock);
    }

    @Bean
    AllowModel allowModel(LlmProviders providers, ProviderModelLists lists, AllowedModelsRepository allowed,
            AgentBriefs agents, CurrentWorkspace workspace, Clock clock) {
        return new AllowModel(providers, lists, allowed, agents, workspace, clock);
    }

    @Bean
    StartTestRun startTestRun(AgentBriefs agents, MeetingLibrary library, RunTools tools, LlmProviders providers,
            AnswerReader reader, CurrentWorkspace workspace, Clock clock) {
        return new StartTestRun(agents, library, tools, providers, reader, workspace, clock);
    }
}
