package com.conxist.workspaces.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.workspaces.domain.Topic;

public interface TopicRepository {

    /** A workspace's topics, by name. */
    List<Topic> findAll(UUID workspaceId);

    Optional<Topic> find(UUID workspaceId, UUID topicId);

    /**
     * Holds the workspace's topic list until the transaction ends, so two adds at once cannot both pass the limit.
     * Returns how many topics the workspace has.
     */
    int lockAndCount(UUID workspaceId);

    /** Adds a topic unless another topic of the workspace has the name (ignoring case), as when two saves race. */
    boolean addIfNameFree(Topic topic);

    /**
     * Saves a topic's new text unless another topic of the workspace has the name.
     *
     * @throws com.conxist.platform.NotFound when the topic is no longer in its workspace
     */
    boolean updateIfNameFree(Topic topic);

    boolean delete(UUID workspaceId, UUID topicId);
}
