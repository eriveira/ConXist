package com.conxist.sources.config;

import java.time.Clock;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.EventPublisher;
import com.conxist.platform.UnitOfWork;
import com.conxist.sources.application.AddSource;
import com.conxist.sources.application.ListSources;
import com.conxist.sources.application.SourceRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the sources use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
class SourcesConfiguration {

    @Bean
    AddSource addSource(SourceRepository sources, CurrentWorkspace workspace, EventPublisher events,
            UnitOfWork unitOfWork, Clock clock) {
        return new AddSource(sources, workspace, events, unitOfWork, clock);
    }

    @Bean
    ListSources listSources(SourceRepository sources, CurrentWorkspace workspace) {
        return new ListSources(sources, workspace);
    }
}
