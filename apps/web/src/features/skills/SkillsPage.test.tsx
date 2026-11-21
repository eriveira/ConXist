import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { AgentSummary } from '../../domain/agents';
import type { SkillSummary } from '../../domain/skills';
import { stubApi } from '../../testing';
import { SkillsPage } from './SkillsPage';

const BUILT_IN: SkillSummary = {
  id: '11111111-1111-1111-1111-111111111111', name: 'Meeting summary', description: 'When summarizing a meeting.',
  source: 'BUILT_IN', status: 'ACTIVE', publishedVersion: 1, draftPublished: true, needs: ['GET_TRANSCRIPT'],
  updatedAt: '2026-11-21T00:00:00Z',
};
const OURS: SkillSummary = {
  ...BUILT_IN, id: '22222222-2222-2222-2222-222222222222', name: 'Acme style', description: 'When writing for customers.',
  source: 'YOURS', publishedVersion: 3, draftPublished: false, needs: [],
};
const IMPORTED: SkillSummary = {
  ...OURS, id: '33333333-3333-3333-3333-333333333333', name: 'Release notes', source: 'IMPORTED', publishedVersion: 0,
  status: 'ARCHIVED',
};
const AGENT = {
  id: '44444444-4444-4444-4444-444444444444', name: 'Analyst', skills: [{ skillId: OURS.id, version: 2 }],
} as AgentSummary;

function api(routes: Parameters<typeof stubApi>[0] = {}) {
  return stubApi({
    'GET /api/skills': () => ({ body: [OURS, IMPORTED, BUILT_IN] }),
    'GET /api/agents': () => ({ body: [AGENT] }),
    ...routes,
  });
}

describe('SkillsPage', () => {
  beforeEach(() => { window.location.hash = '#/skills'; });
  afterEach(() => vi.unstubAllGlobals());

  it('lists built-in skills first, with where each stands and who uses it, accessibly', async () => {
    api();
    const { container } = render(<SkillsPage creating={false} />);

    const cards = await screen.findAllByRole('link', { name: /Meeting summary|Acme style/ });
    expect(cards.map((card) => within(card).getByText(/Meeting summary|Acme style/).textContent)).toEqual(['Meeting summary', 'Acme style']);
    expect(cards[0]).toHaveTextContent('Built-in');
    expect(cards[0]).toHaveTextContent('Needs Read a transcript');
    expect(cards[1]).toHaveTextContent('v3 live · edited');
    expect(cards[1]).toHaveTextContent('Used by 1 agent');
    expect(screen.queryByText('Release notes')).not.toBeInTheDocument();
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('filters by where skills come from, and shows archived ones on request', async () => {
    api();
    render(<SkillsPage creating={false} />);
    await screen.findByText('Acme style');

    await userEvent.click(screen.getByRole('button', { name: 'Built-in' }));
    expect(screen.getByRole('button', { name: 'Built-in' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByText('Acme style')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Imported' }));
    expect(screen.getByText('No skills here yet.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Show 1 archived' }));
    expect(screen.getByText('Release notes')).toBeInTheDocument();
  });

  it('names a new skill and opens it', async () => {
    const calls = api({ 'POST /api/skills': () => ({ status: 201, body: { id: OURS.id } }) });
    render(<SkillsPage creating />);

    const dialog = await screen.findByRole('dialog', { name: 'New skill' });
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Ticket format');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    await waitFor(() => expect(window.location.hash).toBe(`#/skills/${OURS.id}`));
    expect(JSON.parse(String(calls.find((call) => call.key === 'POST /api/skills')?.init?.body))).toEqual({ name: 'Ticket format' });
  });

  it('imports a SKILL.md as a draft, and refuses a file that is too large', async () => {
    const calls = api({ 'POST /api/skills/import': () => ({ status: 201, body: { id: IMPORTED.id } }) });
    render(<SkillsPage creating={false} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Import' }));
    const dialog = screen.getByRole('dialog', { name: 'Import a skill' });
    const input = within(dialog).getByLabelText('SKILL.md file');

    await userEvent.upload(input, new File(['x'.repeat(70 * 1024)], 'big.md', { type: 'text/markdown' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('too large');
    expect(within(dialog).getByRole('button', { name: 'Import' })).toBeDisabled();

    await userEvent.upload(input, new File(['---\nname: release-notes\n---\nGroup changes.'], 'SKILL.md', { type: 'text/markdown' }));
    expect(await within(dialog).findByText('SKILL.md')).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Import' }));

    await waitFor(() => expect(window.location.hash).toBe(`#/skills/${IMPORTED.id}`));
    expect(JSON.parse(String(calls.find((call) => call.key === 'POST /api/skills/import')?.init?.body)))
      .toEqual({ markdown: '---\nname: release-notes\n---\nGroup changes.' });
  });
});
