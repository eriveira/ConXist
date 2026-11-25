package com.conxist.workspaces.adapter.web;

import java.util.List;
import java.util.UUID;
import com.conxist.workspaces.application.ManageTopics;
import com.conxist.workspaces.domain.Topic;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The topics of the workspace the request names (ADR 0014). */
@RestController
@RequestMapping("/api/topics")
class TopicsController {

    /** Bounds what is read before the rules in Topic apply; those count characters, this counts UTF-16 units. */
    private static final int MAX_INPUT = 2_000;

    private final ManageTopics topics;

    TopicsController(ManageTopics topics) {
        this.topics = topics;
    }

    @GetMapping
    List<TopicView> list() {
        return topics.list().stream().map(TopicView::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TopicView add(@Valid @RequestBody TopicRequest request) {
        return TopicView.of(topics.add(request.name(), request.description()));
    }

    @PutMapping("/{id}")
    TopicView change(@PathVariable UUID id, @Valid @RequestBody TopicRequest request) {
        return TopicView.of(topics.change(id, request.name(), request.description()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID id) {
        topics.remove(id);
    }

    record TopicRequest(@Size(max = MAX_INPUT) String name, @Size(max = MAX_INPUT) String description) {
    }

    /** A topic as the app shows it; the workspace is implied by the request. */
    record TopicView(UUID id, String name, String description) {

        static TopicView of(Topic topic) {
            return new TopicView(topic.id(), topic.name(), topic.description());
        }
    }
}
