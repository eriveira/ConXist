package com.conxist.hub.adapter.events;

import com.conxist.capture.DocumentCaptured;
import com.conxist.capture.DocumentDeleted;
import com.conxist.hub.application.RecordArrival;
import com.conxist.sources.SourceAdded;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Listens for arrivals from the other modules. The JDBC event registry retries a listener that fails. */
@Component
class HubArrivals {

    private final RecordArrival recordArrival;

    HubArrivals(RecordArrival recordArrival) {
        this.recordArrival = recordArrival;
    }

    @ApplicationModuleListener
    void on(SourceAdded event) {
        recordArrival.sourceAdded(event);
    }

    @ApplicationModuleListener
    void on(DocumentCaptured event) {
        recordArrival.documentCaptured(event);
    }

    @ApplicationModuleListener
    void on(DocumentDeleted event) {
        recordArrival.documentDeleted(event);
    }
}
