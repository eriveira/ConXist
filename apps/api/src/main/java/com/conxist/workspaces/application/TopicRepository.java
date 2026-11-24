package com.conxist.workspaces.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.workspaces.domain.Topic;

public interface TopicRepository {

    /** A workspace's topics, by name. */
    List<Topic> findAll(UUID workspaceId);

    Optional<Topic> find(UUID workspaceId, UUID topicId);

    int count(UUID workspaceId);

    /**
     * Saves a new topic or a topic's new text, unless another topic of the workspace has the name (ignoring case),
     * which can happen when two saves race.
     */
    boolean saveIfNameFree(Topic topic);

    boolean delete(UUID workspaceId, UUID topicId);
}
