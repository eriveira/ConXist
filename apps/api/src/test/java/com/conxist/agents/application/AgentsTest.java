package com.conxist.agents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.conxist.agents.domain.Agent;
import com.conxist.agents.AgentModels;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentModel;
import com.conxist.agents.domain.AgentStatus;
import com.conxist.agents.domain.AgentTemplate;
import com.conxist.agents.domain.AgentVersion;
import com.conxist.agents.domain.BuiltInSkill;
import com.conxist.agents.domain.InputKind;
import com.conxist.agents.domain.Language;
import com.conxist.agents.domain.SkillDefinition;
import com.conxist.agents.domain.SkillPin;
import com.conxist.agents.domain.SkillSource;
import com.conxist.platform.Conflict;
import com.conxist.platform.NotFound;
import com.conxist.platform.RuleViolation;
import com.conxist.sources.Provider;
import com.conxist.sources.SourceCategory;
import com.conxist.sources.SourceSummary;
import com.conxist.testing.FixedWorkspace;
import com.conxist.testing.ImmediateUnitOfWork;
import com.conxist.testing.TestClock;
import org.junit.jupiter.api.Test;

class AgentsTest {

    private final InMemoryAgents repository = new InMemoryAgents();
    private final Map<UUID, SourceSummary> sources = new HashMap<>();
    private final TestClock.Moving clock = TestClock.moving();
    private FixedWorkspace workspace = FixedWorkspace.standard();
    private final SkillBook skills = new SkillBook(new InMemorySkills(), () -> workspace.id(), () -> Language.EN,
            new ImmediateUnitOfWork(), clock);
    private final Models models = new Models();
    private final AgentBook book = new AgentBook(repository, () -> workspace.id(),
            id -> Optional.ofNullable(sources.get(id)), skills, models, new ImmediateUnitOfWork(), clock);
    private final AddSkill addSkill = new AddSkill(skills);
    private final SaveSkillDraft saveSkill = new SaveSkillDraft(skills);
    private final PublishSkill publishSkill = new PublishSkill(skills);
    private final CreateAgent create = new CreateAgent(book);
    private final ListAgents list = new ListAgents(book);
    private final ReadAgent read = new ReadAgent(book);
    private final SaveAgentDraft save = new SaveAgentDraft(book);
    private final PublishAgent publish = new PublishAgent(book);
    private final RestoreAgentVersion restore = new RestoreAgentVersion(book);
    private final ArchiveAgent archive = new ArchiveAgent(book);
    private final List<AgentUse> agentUses = new ArrayList<>();
    private final DeleteAgent deleteAgent = new DeleteAgent(book, (workspaceId, agentId) -> List.copyOf(agentUses));
    private final DeleteSkill deleteSkill = new DeleteSkill(skills, book);

    @Test
    void an_agent_starts_from_a_template_as_a_ready_draft() {
        AgentView created = create.create(AgentTemplate.REQUIREMENTS_ANALYST, " Requirements analyst ", Language.EN);

        assertThat(created.draft().name()).isEqualTo("Requirements analyst");
        assertThat(created.problems()).isEmpty();
        assertThat(created.versions()).isEmpty();
        assertThat(list.list()).singleElement().satisfies(summary -> {
            assertThat(summary.name()).isEqualTo("Requirements analyst");
            assertThat(summary.publishedVersion()).isZero();
        });
    }

    @Test
    void a_template_starts_on_its_tier_and_a_blank_agent_on_the_workspace_default() {
        assertThat(create.create(AgentTemplate.TOPIC_CLASSIFIER, "Topics", Language.EN).draft().model())
                .isEqualTo(new AgentModel("anthropic", "claude-haiku-5-5", AgentModel.Effort.LOW));
        assertThat(create.create(AgentTemplate.BLANK, "Mine", Language.EN).draft().model())
                .isEqualTo(new AgentModel("anthropic", "default-model", AgentModel.Effort.HIGH));
        assertThat(models.asked).containsExactly(Optional.of("FAST"), Optional.empty());
    }

    @Test
    void an_agent_without_a_model_or_on_a_model_that_cannot_run_is_not_published() {
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);
        AgentDefinition d = created.draft();

        AgentView none = save.save(created.id(), created.revision(), withModel(d, null));
        assertThat(none.problems()).containsExactly("model-required");

        AgentView retired = save.save(created.id(), none.revision(),
                withModel(d, new AgentModel("anthropic", "claude-retired", AgentModel.Effort.LOW)));
        assertThat(retired.problems()).containsExactly("model-unavailable");
        assertCode(() -> publish.publish(created.id(), retired.revision()), "agent.model-unavailable");

        AgentView back = save.save(created.id(), retired.revision(), withModel(d, SONNET));
        assertThat(back.problems()).isEmpty();
        models.connected = false;
        assertThat(read.get(created.id()).problems()).containsExactly("model-unavailable");
    }

    @Test
    void names_are_unique_in_a_workspace_ignoring_case() {
        AgentView first = create.create(AgentTemplate.BLANK, "Triage", Language.EN);
        AgentView second = create.create(AgentTemplate.BLANK, "Other", Language.EN);

        assertCode(() -> create.create(AgentTemplate.BLANK, "TRIAGE", Language.EN), "agent.name-taken");
        assertCode(() -> save.save(second.id(), second.revision(), renamed(second.draft(), "triage")),
                "agent.name-taken");
        workspace = new FixedWorkspace(UUID.randomUUID());
        assertThat(create.create(AgentTemplate.BLANK, "Triage", Language.EN).id()).isNotEqualTo(first.id());
    }

    @Test
    void edits_go_to_the_draft_and_publishing_makes_numbered_versions() {
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);
        AgentView v1 = publish.publish(created.id(), created.revision());
        clock.advance(Duration.ofMinutes(1));
        AgentView edited = save.save(v1.id(), v1.revision(), renamed(v1.draft(), "Weekly summary"));

        assertThat(v1.publishedVersion()).isEqualTo(1);
        assertThat(v1.draftPublished()).isTrue();
        assertThat(edited.draftPublished()).isFalse();
        assertThat(edited.publishedVersion()).isEqualTo(1);
        assertThat(read.version(created.id(), 1).definition().name()).isEqualTo("Summarizer");

        AgentView v2 = publish.publish(edited.id(), edited.revision());
        assertThat(v2.versions()).extracting(AgentView.VersionInfo::number).containsExactly(2, 1);
        assertThat(read.version(created.id(), 2).definition().name()).isEqualTo("Weekly summary");
    }

    @Test
    void a_stale_editor_cannot_overwrite_a_newer_draft() {
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);
        save.save(created.id(), created.revision(), renamed(created.draft(), "From tab one"));

        assertThatThrownBy(() -> save.save(created.id(), created.revision(), renamed(created.draft(), "Tab two")))
                .isInstanceOf(Conflict.class).hasMessageContaining("another tab");
        assertThat(read.get(created.id()).draft().name()).isEqualTo("From tab one");
    }

    @Test
    void saving_an_unchanged_draft_keeps_its_revision() {
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);

        assertThat(save.save(created.id(), created.revision(), created.draft()).revision())
                .isEqualTo(created.revision());
    }

    @Test
    void restoring_copies_a_version_into_the_draft() {
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);
        AgentView v1 = publish.publish(created.id(), created.revision());
        AgentView edited = save.save(v1.id(), v1.revision(), renamed(v1.draft(), "Changed"));
        AgentView v2 = publish.publish(edited.id(), edited.revision());

        AgentView restored = restore.restore(v2.id(), 1, v2.revision());

        assertThat(restored.draft().name()).isEqualTo("Summarizer");
        assertThat(restored.publishedVersion()).isEqualTo(2);
        assertThat(restored.draftPublished()).isFalse();
        assertCode(() -> restore.restore(v2.id(), 9, restored.revision()), "agent.version-not-found");
    }

    @Test
    void an_agent_that_reads_a_removed_source_cannot_be_published() {
        UUID kept = UUID.randomUUID();
        sources.put(kept, new SourceSummary(kept, SourceCategory.CAPTURE, Provider.UPLOAD, "Specs", null,
                Instant.EPOCH));
        UUID removed = UUID.randomUUID();
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);
        AgentView reading = save.save(created.id(), created.revision(),
                withSources(created.draft(), List.of(kept, removed)));

        assertThat(reading.problems()).containsExactly("source-missing");
        assertCode(() -> publish.publish(reading.id(), reading.revision()), "agent.source-missing");

        AgentView fixed = save.save(reading.id(), reading.revision(), withSources(reading.draft(), List.of(kept)));
        assertThat(publish.publish(fixed.id(), fixed.revision()).publishedVersion()).isEqualTo(1);
    }

    @Test
    void an_agent_follows_a_skill_at_the_version_it_is_pinned_to() {
        SkillView skill = addSkill.create("Acme style");
        SkillView drafted = saveSkill.save(skill.id(), skill.revision(), new SkillDefinition("Acme style",
                "When writing for customers.", "Say customer, never user.", List.of()));
        SkillView v1 = publishSkill.publish(drafted.id(), drafted.revision(), "First version");
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);

        AgentView following = save.save(created.id(), created.revision(),
                withSkills(created.draft(), List.of(new SkillPin(skill.id(), 1))));
        SkillView edited = saveSkill.save(v1.id(), v1.revision(), new SkillDefinition("Acme style",
                "When writing for customers.", "Say customer. Short sentences.", List.of()));
        publishSkill.publish(edited.id(), edited.revision(), "");

        assertThat(read.get(following.id()).skills()).singleElement().satisfies(pinned -> {
            assertThat(pinned.version()).isEqualTo(1);
            assertThat(pinned.newestVersion()).isEqualTo(2);
            assertThat(pinned.source()).isEqualTo(SkillSource.YOURS);
        });
        assertThat(list.list()).singleElement().extracting(AgentSummary::skills)
                .isEqualTo(List.of(new SkillPin(skill.id(), 1)));
        assertThat(publish.publish(following.id(), following.revision()).publishedVersion()).isEqualTo(1);
    }

    @Test
    void a_skill_that_needs_a_tool_the_agent_lacks_blocks_publishing() {
        AgentView created = create.create(AgentTemplate.BLANK, "Writer", Language.EN);
        AgentView ready = save.save(created.id(), created.revision(), withSkills(
                new AgentDefinition("Writer", "", "Write.", List.of(), List.of(), List.of(), SONNET, null,
                        List.of(InputKind.TOPIC), null, null),
                List.of(new SkillPin(BuiltInSkill.MEETING_SUMMARY.id(), 1))));

        assertThat(ready.problems()).containsExactly("skill-needs-tool");
        assertThat(ready.skills()).singleElement().extracting(PinnedSkill::name).isEqualTo("Meeting summary");
        assertCode(() -> publish.publish(ready.id(), ready.revision()), "agent.skill-needs-tool");
    }

    @Test
    void a_pin_to_a_skill_this_workspace_cannot_use_is_missing() {
        SkillView other = addSkill.create("Elsewhere");
        SkillView drafted = saveSkill.save(other.id(), other.revision(),
                new SkillDefinition("Elsewhere", "When in another workspace.", "Do it.", List.of()));
        publishSkill.publish(drafted.id(), drafted.revision(), "");
        workspace = new FixedWorkspace(UUID.randomUUID());
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);

        AgentView pinned = save.save(created.id(), created.revision(), withSkills(created.draft(), List.of(
                new SkillPin(other.id(), 1), new SkillPin(BuiltInSkill.PLAIN_WRITING.id(), 9))));

        assertThat(pinned.skills()).isEmpty();
        assertThat(pinned.problems()).containsExactly("skill-missing");
        assertCode(() -> publish.publish(pinned.id(), pinned.revision()), "agent.skill-missing");
    }

    @Test
    void archived_agents_stay_listed_and_can_come_back() {
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);
        AgentView archived = archive.archive(created.id(), created.revision());

        assertThat(list.list()).singleElement().extracting(AgentSummary::status).isEqualTo(AgentStatus.ARCHIVED);
        assertCode(() -> publish.publish(archived.id(), archived.revision()), "agent.archived");
        assertThat(archive.unarchive(archived.id(), archived.revision()).status()).isEqualTo(AgentStatus.ACTIVE);
    }

    @Test
    void an_agent_is_deleted_with_its_versions_unless_something_uses_it_or_it_changed() {
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);
        AgentView published = publish.publish(created.id(), created.revision());

        assertCode(() -> deleteAgent.delete(published.id(), created.revision()), "agent.changed");
        agentUses.add(new AgentUse("FLOW", UUID.randomUUID(), "Triage", "LIVE", false));
        assertThat(deleteAgent.uses(published.id())).hasSize(1);
        assertCode(() -> deleteAgent.delete(published.id(), published.revision()), "agent.in-use");
        agentUses.clear();

        deleteAgent.delete(published.id(), published.revision());

        assertThat(list.list()).isEmpty();
        assertThatThrownBy(() -> read.get(published.id())).isInstanceOf(NotFound.class);
        assertThat(repository.versions).isEmpty();
    }

    @Test
    void a_skill_an_agent_follows_in_its_draft_or_live_version_is_not_deleted() {
        SkillView skill = addSkill.create("Acme style");
        SkillView drafted = saveSkill.save(skill.id(), skill.revision(), new SkillDefinition("Acme style",
                "When writing for customers.", "Say customer, never user.", List.of()));
        SkillView v1 = publishSkill.publish(drafted.id(), drafted.revision(), "");
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);
        AgentView following = save.save(created.id(), created.revision(),
                withSkills(created.draft(), List.of(new SkillPin(skill.id(), 1))));
        AgentView live = publish.publish(following.id(), following.revision());

        assertThat(deleteSkill.uses(skill.id())).singleElement().satisfies(use -> {
            assertThat(use.name()).isEqualTo("Summarizer");
            assertThat(use.where()).isEqualTo("BOTH");
        });
        assertCode(() -> deleteSkill.delete(skill.id(), v1.revision()), "skill.in-use");

        // Out of the draft but still in the live version: still in use, and an archived agent still counts.
        AgentView dropped = save.save(live.id(), live.revision(), withSkills(live.draft(), List.of()));
        AgentView archived = archive.archive(dropped.id(), dropped.revision());
        assertThat(deleteSkill.uses(skill.id())).singleElement().satisfies(use -> {
            assertThat(use.where()).isEqualTo("LIVE");
            assertThat(use.archived()).isTrue();
        });

        AgentView back = archive.unarchive(archived.id(), archived.revision());
        publish.publish(back.id(), back.revision());
        assertThat(deleteSkill.uses(skill.id())).isEmpty();
        deleteSkill.delete(skill.id(), v1.revision());
        assertThatThrownBy(() -> deleteSkill.uses(skill.id())).isInstanceOf(NotFound.class);
    }

    @Test
    void a_built_in_skill_is_not_deleted() {
        assertCode(() -> deleteSkill.delete(BuiltInSkill.PLAIN_WRITING.id(), 1), "skill.built-in");
    }

    @Test
    void another_workspaces_agent_is_not_found() {
        AgentView created = create.create(AgentTemplate.MEETING_SUMMARIZER, "Summarizer", Language.EN);
        workspace = new FixedWorkspace(UUID.randomUUID());

        assertThatThrownBy(() -> read.get(created.id())).isInstanceOf(NotFound.class);
        assertThat(list.list()).isEmpty();
    }

    private static AgentDefinition renamed(AgentDefinition d, String name) {
        return new AgentDefinition(name, d.purpose(), d.instructions(), d.knowledgeSources(), d.skills(), d.tools(),
                d.model(), d.style(), d.accepts(), d.givesBack(), d.limits());
    }

    private static AgentDefinition withModel(AgentDefinition d, AgentModel model) {
        return new AgentDefinition(d.name(), d.purpose(), d.instructions(), d.knowledgeSources(), d.skills(), d.tools(),
                model, d.style(), d.accepts(), d.givesBack(), d.limits());
    }

    private static AgentDefinition withSources(AgentDefinition d, List<UUID> sources) {
        return new AgentDefinition(d.name(), d.purpose(), d.instructions(), sources, d.skills(), d.tools(),
                d.model(), d.style(), d.accepts(), d.givesBack(), d.limits());
    }

    private static AgentDefinition withSkills(AgentDefinition d, List<SkillPin> skills) {
        return new AgentDefinition(d.name(), d.purpose(), d.instructions(), d.knowledgeSources(), skills, d.tools(),
                d.model(), d.style(), d.accepts(), d.givesBack(), d.limits());
    }

    private static final AgentModel SONNET = new AgentModel("anthropic", "claude-sonnet-5-5", AgentModel.Effort.MEDIUM);

    /** Anthropic with its three models; a blank agent starts on a made-up workspace default. */
    private static final class Models implements AgentModels {
        private final List<Optional<String>> asked = new ArrayList<>();
        private boolean connected = true;

        @Override
        public Start start(Optional<String> tier) {
            asked.add(tier);
            return tier.map(name -> switch (name) {
                case "FAST" -> new Start("anthropic", "claude-haiku-5-5", "LOW");
                case "THOROUGH" -> new Start("anthropic", "claude-opus-5-5", "HIGH");
                default -> new Start("anthropic", "claude-sonnet-5-5", "MEDIUM");
            }).orElse(new Start("anthropic", "default-model", "HIGH"));
        }

        @Override
        public boolean available(String provider, String model) {
            return connected && provider.equals("anthropic") && List.of("claude-haiku-5-5", "claude-sonnet-5-5",
                    "claude-opus-5-5", "default-model").contains(model);
        }
    }

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(RuleViolation.class)
                .extracting(e -> ((RuleViolation) e).code()).isEqualTo(code);
    }

    private static final class InMemoryAgents implements AgentRepository {

        private final Map<UUID, Agent> agents = new HashMap<>();
        private final List<AgentVersion> versions = new ArrayList<>();

        @Override
        public boolean insertIfNameFree(Agent agent) {
            if (nameTaken(agent.workspaceId(), agent.draft().name(), agent.id())) {
                return false;
            }
            agents.put(agent.id(), agent);
            return true;
        }

        @Override
        public boolean update(Agent agent, long expectedRevision) {
            Agent current = agents.get(agent.id());
            if (current == null || current.revision() != expectedRevision) {
                return false;
            }
            agents.put(agent.id(), agent);
            return true;
        }

        @Override
        public boolean delete(UUID workspaceId, UUID agentId, long expectedRevision) {
            Agent current = agents.get(agentId);
            if (current == null || !current.workspaceId().equals(workspaceId) || current.revision() != expectedRevision) {
                return false;
            }
            agents.remove(agentId);
            versions.removeIf(version -> version.agentId().equals(agentId));
            return true;
        }

        @Override
        public Optional<Agent> find(UUID workspaceId, UUID agentId) {
            return Optional.ofNullable(agents.get(agentId)).filter(a -> a.workspaceId().equals(workspaceId));
        }

        @Override
        public List<Agent> findAll(UUID workspaceId) {
            return agents.values().stream().filter(a -> a.workspaceId().equals(workspaceId))
                    .sorted(Comparator.comparing(Agent::updatedAt).reversed()).toList();
        }

        @Override
        public boolean nameTaken(UUID workspaceId, String name, UUID except) {
            return agents.values().stream().anyMatch(a -> a.workspaceId().equals(workspaceId)
                    && !a.id().equals(except) && a.draft().name().equalsIgnoreCase(name));
        }

        @Override
        public void addVersion(AgentVersion version) {
            versions.add(version);
        }

        @Override
        public List<AgentView.VersionInfo> versionInfos(UUID workspaceId, UUID agentId) {
            return versions.stream().filter(v -> v.agentId().equals(agentId) && find(workspaceId, agentId).isPresent())
                    .sorted(Comparator.comparingInt(AgentVersion::number).reversed())
                    .map(v -> new AgentView.VersionInfo(v.number(), v.publishedAt())).toList();
        }

        @Override
        public Optional<AgentVersion> version(UUID workspaceId, UUID agentId, int number) {
            return versions.stream().filter(v -> v.agentId().equals(agentId) && v.number() == number
                    && find(workspaceId, agentId).isPresent()).findFirst();
        }
    }
}
