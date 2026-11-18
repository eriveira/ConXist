package com.conxist.platform.adapter;

import com.conxist.platform.EventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
class SpringEventPublisher implements EventPublisher {

    private final ApplicationEventPublisher events;

    SpringEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    @Override
    public void publish(Object event) {
        events.publishEvent(event);
    }
}
