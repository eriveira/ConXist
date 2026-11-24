package com.conxist.workspaces.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.TestClock;
import com.conxist.workspaces.domain.Topic;
import org.junit.jupiter.api.Test;

class ManageTopicsTest {

    private static final FixedWorkspace OTHER = new FixedWorkspace(UUID.randomUUID());

    private final InMemoryTopics repository = new InMemoryTopics();
    private final ManageTopics topics = manage(FixedWorkspace.standard());

    private ManageTopics manage(FixedWorkspace workspace) {
        return new ManageTopics(repository, workspace, new ImmediateUnitOfWork(), TestClock.fixed());
    }

    @Test
    void added_topics_are_listed_by_name_with_their_description() {
        topics.add(" Payments ", "Billing, refunds and invoices");
        topics.add("Checkout", "");

        assertThat(topics.list()).extracting(Topic::name).containsExactly("Checkout", "Payments");
        assertThat(topics.list().get(1).description()).isEqualTo("Billing, refunds and invoices");
        assertThat(topics.list().get(0).createdAt()).isEqualTo(TestClock.NOW);
    }

    @Test
    void each_workspace_has_its_own_topics_and_cannot_reach_another_ones() {
        Topic checkout = topics.add("Checkout", "");
        ManageTopics other = manage(OTHER);

        other.add("Checkout", "Their own");

        assertThat(other.list()).extracting(Topic::description).containsExactly("Their own");
        assertThatThrownBy(() -> other.change(checkout.id(), "Mine now", "")).isInstanceOf(NotFound.class);
        assertThatThrownBy(() -> other.remove(checkout.id())).isInstanceOf(NotFound.class);
        assertThat(topics.list()).extracting(Topic::name).containsExactly("Checkout");
    }

    @Test
    void names_are_unique_in_a_workspace_ignoring_case_but_a_topic_may_keep_its_own() {
        Topic checkout = topics.add("Checkout", "");
        topics.add("Hiring", "");

        assertThat(code(() -> topics.add("CHECKOUT", ""))).isEqualTo("topic.name-taken");
        assertThat(code(() -> topics.change(checkout.id(), "hiring", ""))).isEqualTo("topic.name-taken");
        assertThat(topics.change(checkout.id(), "checkout", "Cart and payment").name()).isEqualTo("checkout");
    }

    @Test
    void a_topic_needs_a_short_name_and_description() {
        assertThat(code(() -> topics.add("  ", ""))).isEqualTo("topic.name-required");
        assertThat(code(() -> topics.add("x".repeat(Topic.MAX_NAME_LENGTH + 1), ""))).isEqualTo("topic.name-too-long");
        assertThat(code(() -> topics.add("Ok", "x".repeat(Topic.MAX_DESCRIPTION_LENGTH + 1))))
                .isEqualTo("topic.description-too-long");
        assertThat(topics.add("😀".repeat(Topic.MAX_NAME_LENGTH), "").name()).hasSize(Topic.MAX_NAME_LENGTH * 2);
    }

    @Test
    void a_workspace_keeps_up_to_fifty_topics() {
        for (int i = 0; i < Topic.MAX_PER_WORKSPACE; i++) {
            topics.add("Topic " + i, "");
        }

        assertThat(code(() -> topics.add("One more", ""))).isEqualTo("topic.too-many");
    }

    @Test
    void a_removed_topic_is_gone() {
        Topic checkout = topics.add("Checkout", "");

        topics.remove(checkout.id());

        assertThat(topics.list()).isEmpty();
        assertThat(code(() -> topics.remove(checkout.id()))).isEqualTo("topic.not-found");
    }

    private static String code(Runnable action) {
        try {
            action.run();
        } catch (RuleViolation violation) {
            return violation.code();
        }
        throw new AssertionError("expected a rule violation");
    }

    private static final class InMemoryTopics implements TopicRepository {

        private final List<Topic> topics = new ArrayList<>();

        @Override
        public List<Topic> findAll(UUID workspaceId) {
            return topics.stream().filter(t -> t.workspaceId().equals(workspaceId))
                    .sorted(Comparator.comparing(t -> t.name().toLowerCase())).toList();
        }

        @Override
        public Optional<Topic> find(UUID workspaceId, UUID topicId) {
            return findAll(workspaceId).stream().filter(t -> t.id().equals(topicId)).findFirst();
        }

        @Override
        public int count(UUID workspaceId) {
            return findAll(workspaceId).size();
        }

        @Override
        public boolean saveIfNameFree(Topic topic) {
            if (findAll(topic.workspaceId()).stream().anyMatch(t -> !t.id().equals(topic.id())
                    && t.name().equalsIgnoreCase(topic.name()))) {
                return false;
            }
            topics.removeIf(t -> t.id().equals(topic.id()));
            return topics.add(topic);
        }

        @Override
        public boolean delete(UUID workspaceId, UUID topicId) {
            return topics.removeIf(t -> t.workspaceId().equals(workspaceId) && t.id().equals(topicId));
        }
    }
}
