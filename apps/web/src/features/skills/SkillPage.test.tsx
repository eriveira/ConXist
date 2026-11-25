import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { AgentSummary } from '../../domain/agents';
import type { Skill, SkillDefinition } from '../../domain/skills';
import { stubApi } from '../../testing';
import { SkillPage } from './SkillPage';

const ID = '11111111-1111-1111-1111-111111111111';
const TOOLS = [
  { id: 'LIST_MEETINGS', toolName: 'conxist.list_meetings', readOnly: true },
  { id: 'GET_TRANSCRIPT', toolName: 'conxist.get_transcript', readOnly: true },
];
const DRAFT: SkillDefinition = {
  name: 'Acme style', description: 'When writing for customers.', instructions: 'Say customer, never user.', needs: [],
};
const SKILL: Skill = {
  id: ID, source: 'YOURS', status: 'ACTIVE', revision: 4, draft: DRAFT, publishedVersion: 1, draftPublished: false,
  problems: [], versions: [{ number: 1, note: 'First version', publishedAt: '2026-11-20T10:00:00Z' }],
  createdAt: '2026-11-20T10:00:00Z', updatedAt: '2026-11-20T10:00:00Z',
};
const AGENT = { id: '22222222-2222-2222-2222-222222222222', name: 'Analyst', skills: [{ skillId: ID, version: 1 }] } as AgentSummary;

function api(skill: Skill, routes: Parameters<typeof stubApi>[0] = {}) {
  return stubApi({
    [`GET /api/skills/${ID}`]: () => ({ body: skill }),
    'GET /api/agents/tools': () => ({ body: TOOLS }),
    'GET /api/agents': () => ({ body: [AGENT] }),
    [`PUT /api/skills/${ID}/draft`]: (init) => {
      const { draft } = JSON.parse(String(init?.body)) as { draft: SkillDefinition };
      return { body: { ...skill, revision: skill.revision + 1, draft } };
    },
    ...routes,
  });
}

const bodyOf = (calls: ReturnType<typeof stubApi>, key: string) =>
  JSON.parse(String(calls.filter((call) => call.key === key).at(-1)?.init?.body));

describe('SkillPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows the skill, who follows it and its SKILL.md, accessibly', async () => {
    api(SKILL);
    const { container } = render(<SkillPage skillId={ID} />);

    const heading = await screen.findByRole('heading', { name: 'Acme style', level: 1 });
    // The page moves focus once it has loaded; under a busy test run that can land a moment after the heading shows.
    await waitFor(() => expect(heading).toHaveFocus());
    expect(screen.getByText('v1 live · edited')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Analyst' })).toBeInTheDocument();
    expect(screen.getByText('Reference files').closest('section')).toHaveTextContent('Coming soon');
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.click(screen.getByRole('tab', { name: 'SKILL.md' }));
    expect(screen.getByRole('tabpanel')).toHaveTextContent('name: acme-style');
    expect(screen.getByRole('button', { name: 'Export' })).toBeInTheDocument();
  });

  it('saves the draft and offers to add the tools its instructions name', async () => {
    const calls = api(SKILL);
    render(<SkillPage skillId={ID} />);
    const instructions = await screen.findByRole('textbox', { name: 'Instructions' });

    await userEvent.type(instructions, ' Read it with get_transcript.');
    const mentioned = (await screen.findByText(/Your instructions name/)).closest('p') as HTMLElement;
    await userEvent.click(within(mentioned).getByRole('button', { name: 'Add' }));

    expect(screen.getByText('Added Read a transcript.')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('combobox', { name: 'Add a tool' })).toHaveFocus());
    await waitFor(() => expect(screen.getByText('Draft saved')).toBeInTheDocument(), { timeout: 3000 });
    const sent = bodyOf(calls, `PUT /api/skills/${ID}/draft`);
    expect(sent.revision).toBeGreaterThanOrEqual(4);
    expect(sent.draft.needs).toEqual(['GET_TRANSCRIPT']);
    expect(sent.draft.instructions).toContain('get_transcript');
  });

  it('publishes with a note and says the agents stay on their version', async () => {
    const calls = api(SKILL, {
      [`POST /api/skills/${ID}/publish`]: () => ({ body: { ...SKILL, revision: 5, publishedVersion: 2, draftPublished: true } }),
    });
    render(<SkillPage skillId={ID} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Publish' }));
    const dialog = screen.getByRole('dialog', { name: 'Publish v2?' });
    await waitFor(() => expect(dialog).toHaveTextContent('Analyst · stays on v1 and shows the update'));
    await userEvent.type(within(dialog).getByLabelText(/Note/), 'Shorter sentences');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Publish' }));

    expect(await screen.findByText('Published as v2.')).toBeInTheDocument();
    expect(bodyOf(calls, `POST /api/skills/${ID}/publish`)).toEqual({ revision: 4, note: 'Shorter sentences' });
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('keeps Publish off until it says when to use it', async () => {
    api({ ...SKILL, draft: { ...DRAFT, description: '' }, problems: ['description-required'] });
    render(<SkillPage skillId={ID} />);

    expect(await screen.findByRole('button', { name: 'Publish' })).toBeDisabled();
    expect(screen.getByText('Before you publish').parentElement).toHaveTextContent('Say when to use it');
  });

  it('does not delete a skill an agent follows: it names the agent and links there, accessibly', async () => {
    const calls = api(SKILL, {
      [`GET /api/skills/${ID}/uses`]: () => ({ body: [
        { kind: 'AGENT', id: AGENT.id, name: 'Analyst', where: 'BOTH', archived: false },
        { kind: 'AGENT', id: '66666666-6666-6666-6666-666666666666', name: 'Old digest', where: 'LIVE', archived: true },
      ] }),
    });
    render(<SkillPage skillId={ID} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    const dialog = await screen.findByRole('dialog', { name: 'Can’t delete Acme style yet' });
    expect(within(dialog).getByRole('heading', { name: '2 agents follow this skill.' })).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Analyst' })).toHaveAttribute('href', `#/agents/${AGENT.id}`);
    expect(within(dialog).getByText('Live · archived')).toBeInTheDocument();
    expect(within(dialog).queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    expect(await accessibilityViolations(dialog)).toEqual([]);
    expect(calls.some((call) => call.key.startsWith('DELETE'))).toBe(false);

    await userEvent.click(within(dialog).getByRole('button', { name: 'OK' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('deletes a skill nothing uses after asking, at the revision it read, and says so on the list', async () => {
    window.location.hash = `#/skills/${ID}`;
    const calls = api(SKILL, {
      [`GET /api/skills/${ID}/uses`]: () => ({ body: [] }),
      [`DELETE /api/skills/${ID}?revision=4`]: () => ({ status: 204, body: null }),
    });
    render(<SkillPage skillId={ID} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    const dialog = await screen.findByRole('dialog', { name: 'Delete Acme style?' });
    expect(within(dialog).getByText('Its draft and its 1 version are deleted for good.')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await waitFor(() => expect(window.location.hash).toBe('#/skills'));
    expect(calls.filter((call) => call.key.startsWith('DELETE'))).toHaveLength(1);
  });
});
