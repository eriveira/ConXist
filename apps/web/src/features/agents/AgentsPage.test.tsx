import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { AgentSummary } from '../../domain/agents';
import { I18nProvider } from '../../i18n/i18n';
import { stubApi } from '../../testing';
import { AgentsPage } from './AgentsPage';

const LIVE: AgentSummary = {
  id: '11111111-1111-1111-1111-111111111111', name: 'Requirements analyst', purpose: 'Finds requirements.',
  status: 'ACTIVE', publishedVersion: 2, draftPublished: false, givesBack: 'REQUIREMENTS', model: { provider: 'anthropic', model: 'claude-opus-5-5', effort: 'HIGH' }, skills: [], updatedAt: '2026-11-20T10:00:00Z',
};
const OLD: AgentSummary = { ...LIVE, id: '22222222-2222-2222-2222-222222222222', name: 'Old summarizer', status: 'ARCHIVED', givesBack: 'SUMMARY' };

describe('AgentsPage', () => {
  beforeEach(() => { window.location.hash = '#/agents'; });
  afterEach(() => vi.unstubAllGlobals());

  it('lists active agents with where each stands, and archived ones on request', async () => {
    stubApi({ 'GET /api/agents': () => ({ body: [LIVE, OLD] }) });
    const { container } = render(<AgentsPage />);

    const card = await screen.findByRole('link', { name: /Requirements analyst/ });
    expect(card).toHaveAttribute('href', `#/agents/${LIVE.id}`);
    expect(card).toHaveTextContent('v2 live · edited');
    expect(card).toHaveTextContent('Gives back: Requirements');
    expect(screen.queryByRole('link', { name: /Old summarizer/ })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Show 1 archived' }));
    expect(screen.getByRole('link', { name: /Old summarizer/ })).toHaveTextContent('Archived');
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('creates an empty agent with the name the person gives and opens it', async () => {
    const calls = stubApi({
      'GET /api/agents': () => ({ body: [] }),
      'POST /api/agents': () => ({ status: 201, body: { id: LIVE.id } }),
    });
    render(<I18nProvider language="es"><AgentsPage /></I18nProvider>);

    await userEvent.click(await screen.findByRole('button', { name: /Crea tu primer agente/ }));
    const dialog = screen.getByRole('dialog', { name: 'Nuevo agente' });
    expect(within(dialog).queryByRole('radio')).not.toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Crear' })).toBeDisabled();
    await userEvent.type(within(dialog).getByLabelText('Nombre'), ' Analista de requisitos ');
    expect(await accessibilityViolations(dialog)).toEqual([]);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Crear' }));

    const sent = calls.find((call) => call.key === 'POST /api/agents');
    expect(JSON.parse(String(sent?.init?.body))).toEqual({ name: 'Analista de requisitos' });
    expect(window.location.hash).toBe(`#/agents/${LIVE.id}`);
  });

  it('shows a refusal and keeps the name the person typed', async () => {
    stubApi({
      'GET /api/agents': () => ({ body: [LIVE] }),
      'POST /api/agents': () => ({ status: 422, body: { code: 'agent.name-taken', detail: 'taken' } }),
    });
    render(<AgentsPage />);

    await userEvent.click(await screen.findByRole('button', { name: 'New' }));
    const dialog = screen.getByRole('dialog', { name: 'New agent' });
    const name = within(dialog).getByLabelText('Name');
    await userEvent.type(name, 'Requirements analyst');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Create' }));

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Another agent in this workspace has this name.');
    expect(name).toHaveValue('Requirements analyst');
  });
});
