package com.conxist.runs.config;

import java.time.Clock;
import com.conxist.agents.AgentBriefs;
import com.conxist.capture.MeetingLibrary;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.runs.adapter.model.ClaudeModel;
import com.conxist.runs.adapter.model.JsonAnswerReader;
import com.conxist.runs.application.AnswerReader;
import com.conxist.runs.application.ModelPort;
import com.conxist.runs.application.ModelSettings;
import com.conxist.runs.application.ModelSettingsRepository;
import com.conxist.runs.application.RunTools;
import com.conxist.runs.application.StartTestRun;
import com.conxist.sources.SourceCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires test runs and model settings: the read tools, Claude as the model, and the use cases. */
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
    ModelSettings modelSettings(RunsProperties properties, ModelSettingsRepository picks, ModelPort model,
            CurrentWorkspace workspace, Clock clock) {
        return new ModelSettings(properties.presets(), picks, model, workspace, clock);
    }

    @Bean
    StartTestRun startTestRun(AgentBriefs agents, MeetingLibrary library, RunTools tools, ModelPort model,
            ModelSettings models, AnswerReader reader, CurrentWorkspace workspace, Clock clock) {
        return new StartTestRun(agents, library, tools, model, models, reader, workspace, clock);
    }
}
