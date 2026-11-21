package com.conxist.agents.adapter.web;

import java.util.List;
import java.util.UUID;
import com.conxist.agents.application.AddSkill;
import com.conxist.agents.application.AgentUse;
import com.conxist.agents.application.ArchiveSkill;
import com.conxist.agents.application.DeleteSkill;
import com.conxist.agents.application.ListSkills;
import com.conxist.agents.application.PublishSkill;
import com.conxist.agents.application.ReadSkill;
import com.conxist.agents.application.RestoreSkillVersion;
import com.conxist.agents.application.SaveSkillDraft;
import com.conxist.agents.application.SkillSummary;
import com.conxist.agents.application.SkillView;
import com.conxist.agents.domain.AgentTool;
import com.conxist.agents.domain.SkillDefinition;
import com.conxist.agents.domain.SkillMarkdown;
import com.conxist.agents.domain.SkillVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Skills of the workspace the request names, and the built-in ones: list, add, edit, publish, restore, archive, delete. */
@RestController
@RequestMapping("/api/skills")
class SkillsController {

    /** Bounds what is read before the rules in SkillDefinition apply; those count characters, this UTF-16 units. */
    private static final int MAX_TEXT_INPUT = 50_000;
    private static final int MAX_LINE_INPUT = 3_000;
    private static final int MAX_LIST_INPUT = 50;

    private final ListSkills listSkills;
    private final AddSkill addSkill;
    private final ReadSkill readSkill;
    private final SaveSkillDraft saveDraft;
    private final PublishSkill publishSkill;
    private final RestoreSkillVersion restoreVersion;
    private final ArchiveSkill archiveSkill;
    private final DeleteSkill deleteSkill;

    SkillsController(ListSkills listSkills, AddSkill addSkill, ReadSkill readSkill, SaveSkillDraft saveDraft,
            PublishSkill publishSkill, RestoreSkillVersion restoreVersion, ArchiveSkill archiveSkill,
            DeleteSkill deleteSkill) {
        this.listSkills = listSkills;
        this.addSkill = addSkill;
        this.readSkill = readSkill;
        this.saveDraft = saveDraft;
        this.publishSkill = publishSkill;
        this.restoreVersion = restoreVersion;
        this.archiveSkill = archiveSkill;
        this.deleteSkill = deleteSkill;
    }

    @GetMapping
    List<SkillSummary> list() {
        return listSkills.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SkillView create(@Valid @RequestBody CreateRequest request) {
        return addSkill.create(request.name());
    }

    /** Reads a SKILL.md the person picked into a new draft. */
    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    SkillView importMarkdown(@Valid @RequestBody ImportRequest request) {
        return addSkill.importMarkdown(request.markdown());
    }

    @PostMapping("/{skillId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    SkillView copy(@PathVariable UUID skillId) {
        return addSkill.copy(skillId);
    }

    @GetMapping("/{skillId}")
    SkillView get(@PathVariable UUID skillId) {
        return readSkill.get(skillId);
    }

    @PutMapping("/{skillId}/draft")
    SkillView saveDraft(@PathVariable UUID skillId, @Valid @RequestBody DraftRequest request) {
        return saveDraft.save(skillId, request.revision(), request.draft().toDefinition());
    }

    @PostMapping("/{skillId}/publish")
    SkillView publish(@PathVariable UUID skillId, @Valid @RequestBody PublishRequest request) {
        return publishSkill.publish(skillId, request.revision(), request.note());
    }

    @GetMapping("/{skillId}/versions/{number}")
    SkillVersion version(@PathVariable UUID skillId, @PathVariable int number) {
        return readSkill.version(skillId, number);
    }

    @PostMapping("/{skillId}/versions/{number}/restore")
    SkillView restore(@PathVariable UUID skillId, @PathVariable int number,
            @Valid @RequestBody RevisionRequest request) {
        return restoreVersion.restore(skillId, number, request.revision());
    }

    @PostMapping("/{skillId}/archive")
    SkillView archive(@PathVariable UUID skillId, @Valid @RequestBody RevisionRequest request) {
        return archiveSkill.archive(skillId, request.revision());
    }

    @PostMapping("/{skillId}/unarchive")
    SkillView unarchive(@PathVariable UUID skillId, @Valid @RequestBody RevisionRequest request) {
        return archiveSkill.unarchive(skillId, request.revision());
    }

    /** The agents that follow the skill in their draft or live version; while any do, it can't be deleted. */
    @GetMapping("/{skillId}/uses")
    List<AgentUse> uses(@PathVariable UUID skillId) {
        return deleteSkill.uses(skillId);
    }

    /** Deletes the skill and its versions for good, if it is still at {@code revision} and no agent follows it. */
    @DeleteMapping("/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID skillId, @RequestParam long revision) {
        deleteSkill.delete(skillId, revision);
    }

    record CreateRequest(@Size(max = MAX_LINE_INPUT) String name) {
    }

    record ImportRequest(@NotNull @Size(max = SkillMarkdown.MAX_LENGTH) String markdown) {
    }

    record RevisionRequest(@NotNull Long revision) {
    }

    record PublishRequest(@NotNull Long revision, @Size(max = MAX_LINE_INPUT) String note) {
    }

    record DraftRequest(@NotNull Long revision, @NotNull @Valid DraftBody draft) {
    }

    record DraftBody(
            @Size(max = MAX_LINE_INPUT) String name,
            @Size(max = MAX_LINE_INPUT) String description,
            @Size(max = MAX_TEXT_INPUT) String instructions,
            @Size(max = MAX_LIST_INPUT) List<AgentTool> needs) {

        SkillDefinition toDefinition() {
            return new SkillDefinition(name, description, instructions, needs);
        }
    }
}
