import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { Agent, AgentDefinition } from '../../domain/agents';
import type { SkillSummary } from '../../domain/skills';
import { LLM_PROVIDERS, stubApi } from '../../testing';
import { AgentPage } from './AgentPage';

const ID = '11111111-1111-1111-1111-111111111111';
const SOURCE = { id: '33333333-3333-3333-3333-333333333333', category: 'CAPTURE', provider: 'UPLOAD', name: 'Specs', accountLabel: null, addedAt: '2026-11-01T00:00:00Z' };
const TOOLS = [
  { id: 'LIST_MEETINGS', toolName: 'conxist.list_meetings', readOnly: true },
  { id: 'GET_TRANSCRIPT', toolName: 'conxist.get_transcript', readOnly: true },
];
const STYLE_ID = '44444444-4444-4444-4444-444444444444';
const SUMMARY_ID = '55555555-5555-5555-5555-555555555555';
const SKILLS: SkillSummary[] = [
  { id: SUMMARY_ID, name: 'Meeting summary', description: 'When summarizing a meeting.', source: 'BUILT_IN', status: 'ACTIVE',
    publishedVersion: 1, draftPublished: true, needs: ['LIST_MEETINGS'], updatedAt: '2026-11-21T00:00:00Z' },
  { id: STYLE_ID, name: 'Acme style', description: 'When writing for customers.', source: 'YOURS', status: 'ACTIVE',
    publishedVersion: 3, draftPublished: true, needs: [], updatedAt: '2026-11-21T00:00:00Z' },
];
const DRAFT: AgentDefinition = {
  name: 'Summarizer', purpose: 'Summarizes meetings.', instructions: 'Summarize the meeting.', knowledgeSources: [], skills: [],
  tools: ['GET_TRANSCRIPT'], model: { provider: 'anthropic', model: 'claude-haiku-5-5', effort: 'LOW' }, style: 'PRECISE', accepts: ['TRANSCRIPT'], givesBack: 'SUMMARY',
  limits: { maxSteps: 6, timeoutSeconds: 120, maxToolCalls: 10 },
};
const AGENT: Agent = {
  id: ID, status: 'ACTIVE', revision: 1, draft: DRAFT, publishedVersion: 0, draftPublished: false, problems: [], skills: [],
  versions: [], createdAt: '2026-11-20T10:00:00Z', updatedAt: '2026-11-20T10:00:00Z',
};

function api(agent: Agent, routes: Parameters<typeof stubApi>[0] = {}) {
  return stubApi({
    [`GET /api/agents/${ID}`]: () => ({ body: agent }),
    'GET /api/sources': () => ({ body: [SOURCE] }),
    'GET /api/agents/tools': () => ({ body: TOOLS }),
    'GET /api/skills': () => ({ body: SKILLS }),
    'GET /api/llm-providers': () => ({ body: LLM_PROVIDERS }),
    'GET /api/test-runs/status': () => ({ body: { ready: true } }),
    [`GET /api/sources/${SOURCE.id}/documents`]: () => ({ body: [] }),
    ...routes,
  });
}

const bodyOf = (calls: ReturnType<typeof stubApi>, key: string) =>
  JSON.parse(String(calls.filter((call) => call.key === key).at(-1)?.init?.body));

describe('AgentPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows every part of the agent, accessibly', async () => {
    api(AGENT);
    const { container } = render(<AgentPage agentId={ID} />);

    const heading = await screen.findByRole('heading', { name: 'Summarizer', level: 1 });
    // The page moves focus once it has loaded; under a busy test run that can land a moment after the heading shows.
    await waitFor(() => expect(heading).toHaveFocus());
    expect(screen.getByText('Draft, not published')).toBeInTheDocument();
    for (const section of ['What it is', 'Instructions', 'Takes in and gives back', 'Knowledge', 'Skills', 'Tools', 'How it thinks', 'Limits']) {
      expect(screen.getByRole('region', { name: new RegExp(section) })).toBeInTheDocument();
    }
    expect(screen.getByRole('checkbox', { name: /Read a transcript/ })).toBeChecked();
    expect(await screen.findByRole('checkbox', { name: /Specs/ })).not.toBeChecked();
    expect(await screen.findByRole('combobox', { name: 'Model' })).toHaveDisplayValue('Claude Haiku 5.5');
    expect(screen.getByRole('radio', { name: /Low/ })).toBeChecked();
    expect(screen.getByRole('button', { name: 'History' })).toBeDisabled();
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('saves changes as a draft after a pause, based on the revision it read', async () => {
    const calls = api(AGENT, {
      [`PUT /api/agents/${ID}/draft`]: (init) => {
        const { draft } = JSON.parse(String(init?.body)) as { draft: AgentDefinition };
        return { body: { ...AGENT, revision: 2, draft } };
      },
    });
    render(<AgentPage agentId={ID} />);
    await screen.findByRole('heading', { name: 'Summarizer', level: 1 });

    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Model' }), 'claude-opus-5-5');
    await userEvent.click(screen.getByRole('radio', { name: /High/ }));
    await userEvent.click(await screen.findByRole('checkbox', { name: /Specs/ }));

    await waitFor(() => expect(screen.getByText('Draft saved')).toBeInTheDocument(), { timeout: 3000 });
    const sent = bodyOf(calls, `PUT /api/agents/${ID}/draft`);
    expect(sent.revision).toBe(1);
    expect(sent.draft.model).toEqual({ provider: 'anthropic', model: 'claude-opus-5-5', effort: 'HIGH' });
    expect(sent.draft.knowledgeSources).toEqual([SOURCE.id]);
    expect(calls.filter((call) => call.key === `PUT /api/agents/${ID}/draft`)).toHaveLength(1);
  });

  it('keeps Publish off and says why until the draft is ready', async () => {
    api({ ...AGENT, draft: { ...DRAFT, instructions: '' }, problems: ['instructions-required'] });
    render(<AgentPage agentId={ID} />);

    expect(await screen.findByRole('button', { name: 'Publish' })).toBeDisabled();
    expect(screen.getByText('Before you publish').parentElement).toHaveTextContent('Write the instructions.');
  });

  it('marks a model that can no longer run and clears the problem once another is picked', async () => {
    const calls = api({ ...AGENT, draft: { ...DRAFT, model: { provider: 'anthropic', model: 'claude-retired', effort: 'LOW' } },
      problems: ['model-unavailable'] }, {
      [`PUT /api/agents/${ID}/draft`]: (init) => ({ body: { ...AGENT, revision: 2, draft: JSON.parse(String(init?.body)).draft } }),
    });
    render(<AgentPage agentId={ID} />);

    const model = await screen.findByRole('combobox', { name: 'Model' });
    expect(model).toHaveDisplayValue('claude-retired (can’t run now)');
    expect(model).toHaveAccessibleDescription(/Its model can’t run here now/);
    expect(screen.getByText('Before you publish').parentElement).toHaveTextContent('Its model can’t run here now.');
    expect(screen.getByRole('button', { name: 'Publish' })).toBeDisabled();

    await userEvent.selectOptions(model, 'claude-haiku-5-5');
    expect(screen.queryByText('Before you publish')).not.toBeInTheDocument();
    await waitFor(() => expect(bodyOf(calls, `PUT /api/agents/${ID}/draft`).draft.model)
      .toEqual({ provider: 'anthropic', model: 'claude-haiku-5-5', effort: 'LOW' }), { timeout: 3000 });
  });

  it('does not save an empty name, and says the draft is waiting on it', async () => {
    const calls = api(AGENT);
    render(<AgentPage agentId={ID} />);
    const name = await screen.findByLabelText('Name');

    await userEvent.clear(name);

    await waitFor(() => expect(screen.getByText('Not saved: fix the marked field')).toBeInTheDocument());
    await new Promise((resolve) => setTimeout(resolve, 900));
    expect(calls.some((call) => call.key === `PUT /api/agents/${ID}/draft`)).toBe(false);
    expect(screen.getByRole('button', { name: 'Publish' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Archive' })).toBeDisabled();
    expect(name).toHaveAccessibleDescription(/Give your agent a name\./);

    await userEvent.type(name, 'Summarizer');
    await waitFor(() => expect(screen.getByText('Draft saved')).toBeInTheDocument(), { timeout: 3000 });
    expect(calls.some((call) => call.key === `PUT /api/agents/${ID}/draft`)).toBe(false);
  });

  it('publishes the next version', async () => {
    const calls = api(AGENT, {
      [`POST /api/agents/${ID}/publish`]: () => ({ body: { ...AGENT, revision: 2, publishedVersion: 1, draftPublished: true, versions: [{ number: 1, publishedAt: '2026-11-20T11:00:00Z' }] } }),
    });
    render(<AgentPage agentId={ID} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Publish' }));

    expect(await screen.findByText('Published as v1.')).toBeInTheDocument();
    expect(bodyOf(calls, `POST /api/agents/${ID}/publish`)).toEqual({ revision: 1 });
    expect(screen.getByText('Live v1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publish' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'History (1)' })).toBeEnabled();
  });

  it('stops editing and offers Reload when the agent changed in another tab', async () => {
    api(AGENT, { [`PUT /api/agents/${ID}/draft`]: () => ({ status: 409, body: { code: 'agent.changed' } }) });
    render(<AgentPage agentId={ID} />);

    await userEvent.type(await screen.findByLabelText('Purpose'), ' Fast.');

    expect(await screen.findByText('Changed in another tab.', {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument();
    expect(screen.getByLabelText('Purpose')).toBeDisabled();
  });

  it('puts an earlier version back in the draft after asking', async () => {
    const published = {
      ...AGENT, revision: 5, publishedVersion: 2, draftPublished: true,
      versions: [{ number: 2, publishedAt: '2026-11-20T12:00:00Z' }, { number: 1, publishedAt: '2026-11-20T11:00:00Z' }],
    };
    const calls = api(published, {
      [`GET /api/agents/${ID}/versions/1`]: () => ({ body: { agentId: ID, number: 1, publishedAt: '2026-11-20T11:00:00Z', definition: { ...DRAFT, name: 'First summarizer' } } }),
      [`POST /api/agents/${ID}/versions/1/restore`]: () => ({ body: { ...published, revision: 6, draftPublished: false, draft: { ...DRAFT, name: 'First summarizer' } } }),
    });
    render(<AgentPage agentId={ID} />);

    await userEvent.click(await screen.findByRole('button', { name: 'History (2)' }));
    const dialog = screen.getByRole('dialog', { name: 'History' });
    const first = within(dialog).getAllByRole('listitem')[1];
    await userEvent.click(within(first).getByRole('button', { name: 'View v1' }));
    expect(await within(first).findByText('First summarizer')).toBeInTheDocument();
    await userEvent.click(within(first).getByRole('button', { name: 'Restore v1' }));
    expect(within(first).getByText('Put v1 in your draft? Nothing changes until you publish.')).toBeInTheDocument();
    expect(await accessibilityViolations(dialog)).toEqual([]);
    await userEvent.click(within(within(first).getByRole('group')).getByRole('button', { name: 'Restore' }));

    expect(await screen.findByText('v1 is in your draft. Publish to use it again.')).toBeInTheDocument();
    expect(bodyOf(calls, `POST /api/agents/${ID}/versions/1/restore`)).toEqual({ revision: 5 });
    expect(screen.getByRole('heading', { name: 'First summarizer', level: 1 })).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('shows an archived agent read-only with a way to bring it back', async () => {
    const calls = api({ ...AGENT, status: 'ARCHIVED', publishedVersion: 1, draftPublished: true, versions: [{ number: 1, publishedAt: '2026-11-20T11:00:00Z' }] }, {
      [`POST /api/agents/${ID}/unarchive`]: () => ({ body: { ...AGENT, revision: 3 } }),
    });
    render(<AgentPage agentId={ID} />);

    expect(await screen.findByLabelText('Name')).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'History (1)' }));
    expect(screen.getByRole('dialog', { name: 'History' })).toHaveTextContent('This agent is archived. Bring it back to restore a version.');
    expect(screen.queryByRole('button', { name: /Restore|Discard/ })).not.toBeInTheDocument();
    await userEvent.click(within(screen.getByRole('dialog')).getAllByRole('button', { name: 'Close' })[0]);
    await userEvent.click(screen.getByRole('button', { name: 'Bring back' }));

    await waitFor(() => expect(screen.getByLabelText('Name')).toBeEnabled());
    expect(bodyOf(calls, `POST /api/agents/${ID}/unarchive`)).toEqual({ revision: 1 });
  });

  it('says when sources cannot load instead of calling them removed, and retries', async () => {
    let fail = true;
    api({ ...AGENT, draft: { ...DRAFT, knowledgeSources: [SOURCE.id] } }, {
      'GET /api/sources': () => (fail ? { status: 500, body: {} } : { body: [SOURCE] }),
    });
    render(<AgentPage agentId={ID} />);

    const alerts = await screen.findAllByText('We could not load your sources, skills, tools and models. Nothing was changed.');
    expect(alerts).toHaveLength(4);
    expect(screen.queryByText('A source it reads was removed from the workspace.')).not.toBeInTheDocument();
    fail = false;
    await userEvent.click(screen.getAllByRole('button', { name: 'Retry' })[0]);
    expect(await screen.findByRole('checkbox', { name: /Specs/ })).toBeChecked();
  });

  it('adds a skill at its newest version and turns on the tools it needs', async () => {
    const calls = api(AGENT, {
      [`PUT /api/agents/${ID}/draft`]: (init) => {
        const { draft } = JSON.parse(String(init?.body)) as { draft: AgentDefinition };
        return { body: { ...AGENT, revision: 2, draft } };
      },
    });
    const { container } = render(<AgentPage agentId={ID} />);
    const skills = await screen.findByRole('region', { name: /Skills/ });

    await userEvent.click(await within(skills).findByRole('button', { name: 'Add' }));
    expect(await accessibilityViolations(container)).toEqual([]);
    await userEvent.click(within(skills).getByRole('button', { name: /Meeting summary/ }));

    expect(within(skills).getByText('Added Meeting summary. Turned on List meetings, which it needs.')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /List meetings/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /List meetings/ })).toHaveAccessibleName(/Needed by Meeting summary/);
    await waitFor(() => expect(screen.getByText('Draft saved')).toBeInTheDocument(), { timeout: 3000 });
    const sent = bodyOf(calls, `PUT /api/agents/${ID}/draft`);
    expect(sent.draft.skills).toEqual([{ skillId: SUMMARY_ID, version: 1 }]);
    expect(sent.draft.tools).toEqual(['LIST_MEETINGS', 'GET_TRANSCRIPT']);
  });

  it('never turns on a tool that changes something when a skill is added', async () => {
    api(AGENT, { 'GET /api/agents/tools': () => ({ body: TOOLS.map((tool) => ({ ...tool, readOnly: false })) }) });
    render(<AgentPage agentId={ID} />);
    const skills = await screen.findByRole('region', { name: /Skills/ });

    await userEvent.click(await within(skills).findByRole('button', { name: 'Add' }));
    await userEvent.click(within(skills).getByRole('button', { name: /Meeting summary/ }));

    expect(within(skills).getByText('Added Meeting summary.')).toBeInTheDocument();
    expect(within(skills).getByText('Needs List meetings, which is off.')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /List meetings/ })).not.toBeChecked();
    await userEvent.click(within(skills).getByRole('button', { name: 'Turn on List meetings' }));
    expect(screen.getByRole('checkbox', { name: /List meetings/ })).toBeChecked();
    await waitFor(() => expect(within(skills).getByRole('button', { name: 'Add' })).toHaveFocus());
  });

  it('blocks publishing while a skill needs a tool that is off, and offers to turn it on', async () => {
    api({
      ...AGENT, draft: { ...DRAFT, skills: [{ skillId: SUMMARY_ID, version: 1 }] }, problems: ['skill-needs-tool'],
      skills: [{ skillId: SUMMARY_ID, version: 1, name: 'Meeting summary', description: 'When summarizing.', source: 'BUILT_IN', needs: ['LIST_MEETINGS'], newestVersion: 1 }],
    });
    render(<AgentPage agentId={ID} />);

    expect(await screen.findByText('Needs List meetings, which is off.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publish' })).toBeDisabled();
    expect(screen.getByText('Before you publish').parentElement).toHaveTextContent('Turn on the tools its skills need');
    await userEvent.click(screen.getByRole('button', { name: 'Turn on List meetings' }));

    expect(screen.getByRole('checkbox', { name: /List meetings/ })).toBeChecked();
    expect(screen.queryByText('Before you publish')).not.toBeInTheDocument();
  });

  it('shows a newer version of a skill as an update to take, and a removed one to remove', async () => {
    const gone = '66666666-6666-6666-6666-666666666666';
    const calls = api({
      ...AGENT, draft: { ...DRAFT, skills: [{ skillId: STYLE_ID, version: 2 }, { skillId: gone, version: 1 }] },
      problems: ['skill-missing'],
      skills: [{ skillId: STYLE_ID, version: 2, name: 'Acme style', description: 'When writing.', source: 'YOURS', needs: [], newestVersion: 3 }],
    }, {
      [`PUT /api/agents/${ID}/draft`]: (init) => {
        const { draft } = JSON.parse(String(init?.body)) as { draft: AgentDefinition };
        return { body: { ...AGENT, revision: 2, draft } };
      },
    });
    render(<AgentPage agentId={ID} />);

    expect(await screen.findByText('v3 available')).toBeInTheDocument();
    expect(screen.getByText('A skill it follows is no longer available.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Update Acme style to v3' }));
    await userEvent.click(screen.getByRole('button', { name: 'Remove the missing skill' }));

    await waitFor(() => expect(screen.getByText('Draft saved')).toBeInTheDocument(), { timeout: 3000 });
    expect(bodyOf(calls, `PUT /api/agents/${ID}/draft`).draft.skills).toEqual([{ skillId: STYLE_ID, version: 3 }]);
    expect(screen.getByRole('button', { name: 'Publish' })).toBeEnabled();
  });

  it('says why a save was refused', async () => {
    api(AGENT, { [`PUT /api/agents/${ID}/draft`]: () => ({ status: 422, body: { code: 'agent.name-taken' } }) });
    render(<AgentPage agentId={ID} />);

    await userEvent.type(await screen.findByLabelText('Name'), ' 2');

    expect(await screen.findByText(/Not saved\. Another agent in this workspace has this name\./, {}, { timeout: 3000 })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publish' })).toBeDisabled();
  });

  it('says when the agent no longer exists', async () => {
    stubApi({
      [`GET /api/agents/${ID}`]: () => ({ status: 404, body: { code: 'agent.not-found' } }),
      'GET /api/sources': () => ({ body: [] }),
      'GET /api/agents/tools': () => ({ body: [] }),
    });
    render(<AgentPage agentId={ID} />);

    expect(await screen.findByRole('heading', { name: 'This agent no longer exists.' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Agents' })).toHaveAttribute('href', '#/agents');
  });

  it('shows what started using the agent when the server refuses the delete, and deletes nothing', async () => {
    let checks = 0;
    const calls = api(AGENT, {
      [`GET /api/agents/${ID}/uses`]: () => {
        checks += 1;
        return { body: checks === 1 ? [] : [{ kind: 'AGENT', id: ID, name: 'Weekly flow', where: 'DRAFT', archived: false }] };
      },
      [`DELETE /api/agents/${ID}?revision=1`]: () => ({ status: 409, body: { code: 'agent.in-use' } }),
    });
    render(<AgentPage agentId={ID} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }));

    const confirm = await screen.findByRole('dialog', { name: `Delete ${DRAFT.name}?` });
    expect(within(confirm).getByText('Its draft is deleted for good.')).toBeInTheDocument();
    await userEvent.click(within(confirm).getByRole('button', { name: 'Delete' }));

    const blocked = await screen.findByRole('dialog', { name: `Can’t delete ${DRAFT.name} yet` });
    expect(within(blocked).getByText('1 thing uses this agent.')).toBeInTheDocument();
    expect(within(blocked).getByRole('button', { name: 'Archive instead' })).toBeInTheDocument();
    expect(calls.filter((call) => call.key.startsWith('DELETE'))).toHaveLength(1);
  });
});
