import type { AgentSummary } from './agents';
import {
  localSkillProblems, mentionedTools, type SkillSummary, skillMarkdown, skillRowProblems, skillRows, slug, sortSkills,
  usersOf,
} from './skills';

const TOOLS = [
  { id: 'LIST_MEETINGS', toolName: 'conxist.list_meetings' },
  { id: 'GET_TRANSCRIPT', toolName: 'conxist.get_transcript' },
];

function summary(change: Partial<SkillSummary>): SkillSummary {
  return {
    id: 's', name: 'Style', description: 'When writing.', source: 'YOURS', status: 'ACTIVE', publishedVersion: 1,
    draftPublished: true, needs: [], updatedAt: '2026-11-20T10:00:00Z', ...change,
  };
}

describe('skills', () => {
  it('finds tools named in the instructions by their full or short name only', () => {
    expect(mentionedTools('Use conxist.get_transcript, then list_meetings.', TOOLS)).toEqual(['LIST_MEETINGS', 'GET_TRANSCRIPT']);
    expect(mentionedTools('my_list_meetings or get_transcript_v2', TOOLS)).toEqual([]);
  });

  it('says what a draft still needs', () => {
    expect(localSkillProblems({ name: ' ', description: '', instructions: '', needs: [] }))
      .toEqual(['name-required', 'description-required', 'instructions-required']);
    expect(localSkillProblems({ name: 'A', description: 'When.', instructions: 'Do.', needs: [] })).toEqual([]);
  });

  it('writes a SKILL.md other tools can read', () => {
    expect(skillMarkdown({ name: 'Résumé de réunion', description: 'When "summing"\nup.', instructions: '# Steps  \n', needs: [] }))
      .toBe('---\nname: resume-de-reunion\ndescription: "When \\"summing\\" up."\n---\n\n# Steps\n');
    expect(slug('  ')).toBe('');
  });

  it('lists built-in skills first, then active ones, newest first', () => {
    const sorted = sortSkills([
      summary({ id: 'old', updatedAt: '2026-11-19T10:00:00Z' }),
      summary({ id: 'archived', status: 'ARCHIVED', updatedAt: '2026-11-21T10:00:00Z' }),
      summary({ id: 'new' }),
      summary({ id: 'built', source: 'BUILT_IN', updatedAt: '2026-11-01T00:00:00Z' }),
    ]);
    expect(sorted.map((skill) => skill.id)).toEqual(['built', 'new', 'old', 'archived']);
  });

  it('finds the agents that follow a skill', () => {
    const agent = { id: 'a', name: 'Analyst', skills: [{ skillId: 's', version: 2 }] } as AgentSummary;
    expect(usersOf('s', [agent, { ...agent, id: 'b', skills: [] }])).toEqual([{ agent, version: 2 }]);
  });

  it('describes each pin from what the server saved, or from the list when just added', () => {
    const saved = [{ skillId: 'a', version: 1, name: 'A v1', description: '', source: 'YOURS' as const, needs: ['GET_TRANSCRIPT'], newestVersion: 2 }];
    const rows = skillRows([{ skillId: 'a', version: 1 }, { skillId: 'b', version: 4 }, { skillId: 'c', version: 1 }], saved,
      [summary({ id: 'b', name: 'B', publishedVersion: 4, needs: ['LIST_MEETINGS'] })]);

    expect(rows.map((row) => row.skill?.name ?? null)).toEqual(['A v1', 'B', null]);
    expect(skillRowProblems(rows, ['GET_TRANSCRIPT'])).toEqual(['skill-missing', 'skill-needs-tool']);
    expect(skillRowProblems(rows.slice(0, 2), ['GET_TRANSCRIPT', 'LIST_MEETINGS'])).toEqual([]);
  });
});
