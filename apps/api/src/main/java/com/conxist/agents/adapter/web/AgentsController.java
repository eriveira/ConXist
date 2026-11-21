package com.conxist.agents.adapter.web;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import com.conxist.agents.application.AgentSummary;
import com.conxist.agents.application.AgentView;
import com.conxist.agents.application.ArchiveAgent;
import com.conxist.agents.application.CreateAgent;
import com.conxist.agents.application.ListAgents;
import com.conxist.agents.application.PublishAgent;
import com.conxist.agents.application.ReadAgent;
import com.conxist.agents.application.RestoreAgentVersion;
import com.conxist.agents.application.SaveAgentDraft;
import com.conxist.agents.domain.AgentDefinition;
import com.conxist.agents.domain.AgentTemplate;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.AgentVersion;
import com.conxist.agents.domain.InputKind;
import com.conxist.agents.domain.Language;
import com.conxist.agents.domain.Limits;
import com.conxist.agents.domain.ModelPolicy;
import com.conxist.agents.domain.OutputKind;
import com.conxist.agents.domain.SkillPin;
import com.conxist.agents.domain.Style;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Agents of the workspace the request names: list, create, edit the draft, publish, restore and archive. */
@RestController
@RequestMapping("/api/agents")
class AgentsController {

    /** Bounds what is read before the rules in AgentDefinition apply; those count characters, this UTF-16 units. */
    private static final int MAX_TEXT_INPUT = 20_000;
    private static final int MAX_LINE_INPUT = 1_000;
    private static final int MAX_LIST_INPUT = 50;

    private final CreateAgent createAgent;
    private final ListAgents listAgents;
    private final ReadAgent readAgent;
    private final SaveAgentDraft saveDraft;
    private final PublishAgent publishAgent;
    private final RestoreAgentVersion restoreVersion;
    private final ArchiveAgent archiveAgent;

    AgentsController(CreateAgent createAgent, ListAgents listAgents, ReadAgent readAgent, SaveAgentDraft saveDraft,
            PublishAgent publishAgent, RestoreAgentVersion restoreVersion, ArchiveAgent archiveAgent) {
        this.createAgent = createAgent;
        this.listAgents = listAgents;
        this.readAgent = readAgent;
        this.saveDraft = saveDraft;
        this.publishAgent = publishAgent;
        this.restoreVersion = restoreVersion;
        this.archiveAgent = archiveAgent;
    }

    @GetMapping
    List<AgentSummary> list() {
        return listAgents.list();
    }

    /** The tools an agent may be allowed. They only read. */
    @GetMapping("/tools")
    List<ToolInfo> tools() {
        return Arrays.stream(AgentTool.values())
                .map(tool -> new ToolInfo(tool.name(), tool.toolName(), tool.readOnly()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AgentView create(@Valid @RequestBody CreateRequest request) {
        return createAgent.create(request.template(), request.name(), request.language());
    }

    @GetMapping("/{agentId}")
    AgentView get(@PathVariable UUID agentId) {
        return readAgent.get(agentId);
    }

    @PutMapping("/{agentId}/draft")
    AgentView saveDraft(@PathVariable UUID agentId, @Valid @RequestBody DraftRequest request) {
        return saveDraft.save(agentId, request.revision(), request.draft().toDefinition());
    }

    @PostMapping("/{agentId}/publish")
    AgentView publish(@PathVariable UUID agentId, @Valid @RequestBody RevisionRequest request) {
        return publishAgent.publish(agentId, request.revision());
    }

    @GetMapping("/{agentId}/versions/{number}")
    AgentVersion version(@PathVariable UUID agentId, @PathVariable int number) {
        return readAgent.version(agentId, number);
    }

    @PostMapping("/{agentId}/versions/{number}/restore")
    AgentView restore(@PathVariable UUID agentId, @PathVariable int number,
            @Valid @RequestBody RevisionRequest request) {
        return restoreVersion.restore(agentId, number, request.revision());
    }

    @PostMapping("/{agentId}/archive")
    AgentView archive(@PathVariable UUID agentId, @Valid @RequestBody RevisionRequest request) {
        return archiveAgent.archive(agentId, request.revision());
    }

    @PostMapping("/{agentId}/unarchive")
    AgentView unarchive(@PathVariable UUID agentId, @Valid @RequestBody RevisionRequest request) {
        return archiveAgent.unarchive(agentId, request.revision());
    }

    record ToolInfo(String id, String toolName, boolean readOnly) {
    }

    record CreateRequest(
            @NotNull AgentTemplate template,
            @Size(max = MAX_LINE_INPUT) String name,
            @NotNull Language language) {
    }

    record RevisionRequest(@NotNull Long revision) {
    }

    record DraftRequest(@NotNull Long revision, @NotNull @Valid DraftBody draft) {
    }

    record DraftBody(
            @Size(max = MAX_LINE_INPUT) String name,
            @Size(max = MAX_LINE_INPUT) String purpose,
            @Size(max = MAX_TEXT_INPUT) String instructions,
            @Size(max = MAX_LIST_INPUT) List<UUID> knowledgeSources,
            @Size(max = MAX_LIST_INPUT) List<@Valid SkillPinBody> skills,
            @Size(max = MAX_LIST_INPUT) List<AgentTool> tools,
            ModelPolicy model,
            Style style,
            @Size(max = MAX_LIST_INPUT) List<InputKind> accepts,
            OutputKind givesBack,
            @NotNull LimitsBody limits) {

        AgentDefinition toDefinition() {
            List<SkillPin> pins = skills == null ? null
                    : skills.stream().map(pin -> pin == null ? null : new SkillPin(pin.skillId(), pin.version()))
                            .toList();
            return new AgentDefinition(name, purpose, instructions, knowledgeSources, pins, tools, model, style,
                    accepts, givesBack, new Limits(limits.maxSteps(), limits.timeoutSeconds(), limits.maxToolCalls()));
        }
    }

    record SkillPinBody(@NotNull UUID skillId, int version) {
    }

    record LimitsBody(int maxSteps, int timeoutSeconds, int maxToolCalls) {
    }
}
