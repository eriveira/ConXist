package com.conxist.hub.adapter.web;

import com.conxist.hub.application.HubOverview;
import com.conxist.hub.application.ReadHubOverview;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HubController {

    private final ReadHubOverview readHubOverview;

    HubController(ReadHubOverview readHubOverview) {
        this.readHubOverview = readHubOverview;
    }

    @GetMapping("/api/hub")
    HubOverview overview() {
        return readHubOverview.read();
    }
}
