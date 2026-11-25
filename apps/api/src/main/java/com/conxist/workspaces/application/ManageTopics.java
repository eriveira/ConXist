package com.conxist.workspaces.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import com.conxist.platform.CurrentWorkspace;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.platform.UnitOfWork;
import com.conxist.workspaces.domain.Topic;

/** The current workspace's topics (ADR 0014): list, add, change and remove them. */
public final class ManageTopics {

    private final TopicRepository topics;
    private final CurrentWorkspace workspace;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ManageTopics(TopicRepository topics, CurrentWorkspace workspace, UnitOfWork unitOfWork, Clock clock) {
        this.topics = topics;
        this.workspace = workspace;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    public List<Topic> list() {
        return topics.findAll(workspace.id());
    }

    public Topic add(String name, String description) {
        UUID workspaceId = workspace.id();
        Topic topic = Topic.create(UUID.randomUUID(), workspaceId, name, description, clock.instant());
        return unitOfWork.inTransaction(() -> {
            if (topics.lockAndCount(workspaceId) >= Topic.MAX_PER_WORKSPACE) {
                throw new RuleViolation("topic.too-many",
                        "A workspace keeps up to " + Topic.MAX_PER_WORKSPACE + " topics. Remove one first.");
            }
            return nameFree(topics.addIfNameFree(topic), topic);
        });
    }

    public Topic change(UUID topicId, String name, String description) {
        UUID workspaceId = workspace.id();
        return unitOfWork.inTransaction(() -> {
            Topic topic = existing(workspaceId, topicId).withText(name, description);
            return nameFree(topics.updateIfNameFree(topic), topic);
        });
    }

    public void remove(UUID topicId) {
        UUID workspaceId = workspace.id();
        if (!topics.delete(workspaceId, topicId)) {
            throw notFound();
        }
    }

    private Topic existing(UUID workspaceId, UUID topicId) {
        return topics.find(workspaceId, topicId).orElseThrow(ManageTopics::notFound);
    }

    private static Topic nameFree(boolean saved, Topic topic) {
        if (!saved) {
            throw new RuleViolation("topic.name-taken", "There is already a topic named \"" + topic.name() + "\".");
        }
        return topic;
    }

    private static NotFound notFound() {
        return new NotFound("topic.not-found", "This topic no longer exists.");
    }
}
