package com.conxist.testing;

import java.util.ArrayList;
import java.util.List;
import com.conxist.platform.EventPublisher;

public class RecordedEvents implements EventPublisher {

    private final List<Object> published = new ArrayList<>();

    @Override
    public void publish(Object event) {
        published.add(event);
    }

    public <T> List<T> ofType(Class<T> type) {
        return published.stream().filter(type::isInstance).map(type::cast).toList();
    }

    public boolean isEmpty() {
        return published.isEmpty();
    }
}
