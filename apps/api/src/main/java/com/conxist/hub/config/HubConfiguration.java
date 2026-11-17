package com.conxist.hub.config;

import com.conxist.hub.application.HubItemRepository;
import com.conxist.hub.application.ReadHubOverview;
import com.conxist.hub.application.RecordArrival;
import com.conxist.platform.CurrentWorkspace;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the hub use cases to their adapters. */
@Configuration(proxyBeanMethods = false)
class HubConfiguration {

    @Bean
    RecordArrival recordArrival(HubItemRepository items) {
        return new RecordArrival(items);
    }

    @Bean
    ReadHubOverview readHubOverview(HubItemRepository items, CurrentWorkspace workspace) {
        return new ReadHubOverview(items, workspace);
    }
}
