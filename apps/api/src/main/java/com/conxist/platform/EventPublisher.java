package com.conxist.platform;

/** Publishes a domain event to the other modules. */
public interface EventPublisher {

    void publish(Object event);
}
