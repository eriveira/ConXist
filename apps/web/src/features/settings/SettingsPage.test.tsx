import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { AgentSummary } from '../../domain/agents';
import type { ConnectionCheck, OfferedModels } from '../../domain/settings';
import { LLM_PROVIDERS, stubApi } from '../../testing';
import { SettingsPage } from './SettingsPage';

const WORKSPACE = { id: 'w1', name: 'Acme Product', description: 'Checkout', sourceCount: 2, createdAt: '2026-11-18T12:00:00Z' };
const agent = (name: string, model: AgentSummary['model']): AgentSummary => ({
  id: name, name, purpose: '', status: 'ACTIVE', publishedVersion: 0, draftPublished: false, givesBack: 'TOPICS', model,
  skills: [], updatedAt: '2026-11-24T00:00:00Z',
});
const OFFERED: OfferedModels = {
  provider: 'anthropic', connected: true, listError: null, models: [
    { id: 'claude-fable-5-2', label: 'Claude Fable 5.2', allowed: false, listed: true },
    { id: 'claude-opus-5-5', label: 'Claude Opus 5.5', allowed: true, listed: true },
    { id: 'claude-sonnet-5-5', label: 'Claude Sonnet 5.5', allowed: true, listed: true },
    { id: 'claude-haiku-5-5', label: 'Claude Haiku 5.5', allowed: true, listed: true },
  ],
};
const ANTHROPIC = LLM_PROVIDERS.providers[0];
const NO_KEY = { source: 'NONE' as const, keyEnding: null, addedAt: null, checkedAt: null, refused: false };
const CHECKOUT = { id: 't1', name: 'Checkout', description: 'Cart and payment' };

function open(section: string) {
  window.location.hash = section;
  return render(<SettingsPage workspace={WORKSPACE} onSaved={vi.fn()} />);
}

describe('SettingsPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.location.hash = '';
  });

  it('lists each model with who uses it at what effort, and saves where new agents start', async () => {
    const calls = stubApi({
      'GET /api/llm-providers': () => ({ body: LLM_PROVIDERS }),
      'GET /api/llm-providers/anthropic/models': () => ({ body: OFFERED }),
      'GET /api/agents': () => ({ body: [
        agent('Topic classifier', { provider: 'anthropic', model: 'claude-haiku-5-5', effort: 'LOW' }),
        agent('Analyst', { provider: 'anthropic', model: 'claude-opus-5-5', effort: 'HIGH' }),
      ] }),
      'PUT /api/llm-providers/default': (init) => ({
        body: { ...LLM_PROVIDERS, defaultForNewAgents: JSON.parse(String(init?.body)) },
      }),
    });
    const { container } = open('#/settings/llm');

    const model = await screen.findByRole('combobox', { name: 'Model for new agents' });
    await screen.findByRole('checkbox', { name: 'Claude Haiku 5.5' });
    expect(screen.getByText('Connected')).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Claude Haiku 5.5/ })).toHaveTextContent('Topic classifier (low)');
    expect(screen.getByRole('row', { name: /Claude Opus 5.5/ })).toHaveTextContent('Analyst (high)');
    expect(screen.getByRole('row', { name: /Claude Sonnet 5.5/ })).toHaveTextContent('No agents yet');
    expect(screen.queryByText(/More providers/)).not.toBeInTheDocument();
    expect(model).toHaveValue('anthropic/claude-sonnet-5-5');
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

    await userEvent.selectOptions(model, 'anthropic/claude-opus-5-5');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Effort for new agents' }), 'HIGH');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Saved')).toBeInTheDocument();
    expect(JSON.parse(String(calls.find((call) => call.key === 'PUT /api/llm-providers/default')?.init?.body)))
      .toEqual({ provider: 'anthropic', model: 'claude-opus-5-5', effort: 'HIGH' });
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('ticks a model the provider offers, keeps the ones in use ticked, and says why an untick was refused', async () => {
    let offered = OFFERED;
    const calls = stubApi({
      'GET /api/llm-providers': () => ({ body: LLM_PROVIDERS }),
      'GET /api/llm-providers/anthropic/models': () => ({ body: offered }),
      'GET /api/llm-providers/anthropic/models?refresh=true': () => ({ body: { ...offered, listError: 'FAILED' } }),
      'GET /api/agents': () => ({ body: [agent('Topic classifier', { provider: 'anthropic', model: 'claude-haiku-5-5', effort: 'LOW' })] }),
      'PUT /api/llm-providers/anthropic/models/claude-fable-5-2': () => {
        offered = { ...offered, models: offered.models.map((m) => (m.id === 'claude-fable-5-2' ? { ...m, allowed: true } : m)) };
        return { body: offered };
      },
      'PUT /api/llm-providers/anthropic/models/claude-opus-5-5': () => ({
        status: 422, body: { code: 'model.in-use', detail: 'Agents run on this model: Analyst. Switch them to another model first.' },
      }),
    });
    const { container } = open('#/settings/llm');

    const fable = await screen.findByRole('checkbox', { name: 'Claude Fable 5.2' });
    expect(screen.getByText('Offered: 4. Allowed: 3.')).toBeInTheDocument();
    expect(fable).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Claude Haiku 5.5' })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: 'Claude Haiku 5.5' })).toHaveAccessibleDescription(/Agents run on it/);
    expect(screen.getByRole('checkbox', { name: 'Claude Sonnet 5.5' })).toHaveAccessibleDescription(/New agents start on it/);
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.click(fable);
    expect(await screen.findByText('Saved')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Claude Fable 5.2' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Claude Fable 5.2' })).toHaveFocus();
    expect(calls.find((call) => call.key === 'PUT /api/llm-providers/anthropic/models/claude-fable-5-2')?.init?.body)
      .toBe('{"allowed":true}');

    await userEvent.click(screen.getByRole('checkbox', { name: 'Claude Opus 5.5' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('An agent runs on this model now.');
    expect(screen.getByRole('checkbox', { name: 'Claude Opus 5.5' })).toBeChecked();

    await userEvent.click(screen.getByRole('button', { name: 'Refresh list' }));
    expect(await screen.findByText(/could not be fetched just now/)).toHaveTextContent('ConXist could not reach the provider.');
    expect(screen.queryByText('An agent runs on this model now.', { exact: false })).not.toBeInTheDocument();
  });

  it('connects Anthropic with a pasted key, says when the key is refused, and offers no default until then', async () => {
    const none = { ...ANTHROPIC, connected: false, connection: { ...NO_KEY } };
    let connected = false;
    const calls = stubApi({
      'GET /api/llm-providers': () => ({ body: { ...LLM_PROVIDERS, providers: [connected ? ANTHROPIC : none] } }),
      'GET /api/llm-providers/anthropic/models': () => ({ body: OFFERED }),
      'GET /api/agents': () => ({ body: [] }),
      'PUT /api/llm-providers/anthropic/key': (init) => {
        const { apiKey } = JSON.parse(String(init?.body)) as { apiKey: string };
        if (apiKey === 'sk-ant-wrong') return { status: 422, body: { code: 'provider.key-refused' } };
        connected = true;
        return { body: ANTHROPIC };
      },
    });
    const { container } = open('#/settings/llm');

    expect(await screen.findByText('Not connected')).toBeInTheDocument();
    expect(screen.getByText(/Connect a provider to see its models/)).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Model for new agents' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Connect' }));
    const dialog = screen.getByRole('dialog', { name: 'Connect Anthropic' });
    const field = within(dialog).getByLabelText('API key');
    expect(field).toHaveAttribute('type', 'password');
    expect(field).toHaveFocus();
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.type(field, 'sk-ant-wrong');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Check and connect' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('The provider refused this key.');

    await userEvent.clear(field);
    await userEvent.type(field, 'sk-ant-good-key{Enter}');
    expect(await screen.findByText(/Key ending 3fQa/)).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(await screen.findByRole('combobox', { name: 'Model for new agents' })).toBeInTheDocument();
    expect(calls.filter((call) => call.key === 'PUT /api/llm-providers/anthropic/key')).toHaveLength(2);
  });

  it('tests the connection, and disconnects after naming the agents that stop', async () => {
    let check = { works: true, error: null, millis: 412, provider: ANTHROPIC } as ConnectionCheck;
    stubApi({
      'GET /api/llm-providers': () => ({ body: LLM_PROVIDERS }),
      'GET /api/llm-providers/anthropic/models': () => ({ body: OFFERED }),
      'GET /api/agents': () => ({ body: [
        agent('Topic classifier', { provider: 'anthropic', model: 'claude-haiku-5-5', effort: 'LOW' }),
        agent('Analyst', { provider: 'anthropic', model: 'claude-opus-5-5', effort: 'HIGH' }),
      ] }),
      'POST /api/llm-providers/anthropic/test': () => ({ body: check }),
      'DELETE /api/llm-providers/anthropic/key': () => ({
        body: { ...LLM_PROVIDERS, providers: [{ ...ANTHROPIC, connected: false, connection: { ...NO_KEY } }] },
      }),
    });
    open('#/settings/llm');

    await userEvent.click(await screen.findByRole('button', { name: 'Test connection' }));
    expect(await screen.findByText('Connection works. Anthropic answered in 0.4 s.')).toBeInTheDocument();

    check = { works: false, error: 'BUSY', millis: 90, provider: ANTHROPIC };
    await userEvent.click(screen.getByRole('button', { name: 'Test connection' }));
    expect(await screen.findByText(/Connection failed. The provider is busy/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Disconnect' }));
    const dialog = screen.getByRole('dialog', { name: 'Disconnect Anthropic?' });
    expect(dialog).toHaveTextContent('Analyst, Topic classifier');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Disconnect' }));
    expect(await screen.findByText('Not connected')).toBeInTheDocument();
  });

  it('shows a refused key with Replace first and Test still there, and a computer key with Connect', async () => {
    const refused = { ...ANTHROPIC, connection: { ...ANTHROPIC.connection, refused: true } };
    stubApi({
      'GET /api/llm-providers': () => ({ body: { ...LLM_PROVIDERS, providers: [refused] } }),
      'GET /api/llm-providers/anthropic/models': () => ({ body: { ...OFFERED, listError: 'KEY_REJECTED' } }),
      'GET /api/agents': () => ({ body: [] }),
    });
    const first = open('#/settings/llm');

    expect(await screen.findByText('Key refused')).toBeInTheDocument();
    expect(screen.getByText(/Anthropic refused key ending 3fQa/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Replace key' })).toHaveClass('primary');
    expect(screen.getByRole('button', { name: 'Test connection' })).toBeInTheDocument();
    first.unmount();

    const computer = { ...ANTHROPIC, connection: { ...NO_KEY, source: 'COMPUTER' as const } };
    stubApi({
      'GET /api/llm-providers': () => ({ body: { ...LLM_PROVIDERS, providers: [computer] } }),
      'GET /api/llm-providers/anthropic/models': () => ({ body: OFFERED }),
      'GET /api/agents': () => ({ body: [] }),
    });
    open('#/settings/llm');
    expect(await screen.findByText(/Using the key set on this computer/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Connect' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Disconnect' })).not.toBeInTheDocument();
  });

  it('lists only the sections that work, and opens General for an address it does not know', async () => {
    open('#/settings/connectors');

    const nav = screen.getByRole('navigation', { name: 'Settings sections' });
    expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual(['General', 'LLM providers', 'Topics']);
    expect(within(nav).getByRole('link', { name: 'General' })).toHaveAttribute('aria-current', 'page');
  });

  it('starts with no topics, adds one with its description, and refuses a taken name under the field', async () => {
    let added = 0;
    stubApi({
      'GET /api/topics': () => ({ body: [] }),
      'POST /api/topics': (init) => (added++ === 0
        ? { status: 201, body: { id: 't1', ...JSON.parse(String(init?.body)) } }
        : { status: 422, body: { code: 'topic.name-taken', detail: 'taken' } }),
    });
    const { container } = open('#/settings/topics');

    expect(await screen.findByText('No topics yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    let dialog = screen.getByRole('dialog', { name: 'New topic' });
    expect(within(dialog).getByLabelText('Name')).toHaveFocus();
    await userEvent.type(within(dialog).getByLabelText('Name'), ' Checkout ');
    await userEvent.type(within(dialog).getByLabelText(/What belongs here/), 'Cart and payment');
    expect(dialog).toHaveTextContent('16 / 300');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Checkout added.')).toBeInTheDocument();
    expect(screen.getByRole('list')).toHaveTextContent('CheckoutCart and payment');
    expect(screen.getByText('1 of 50 topics')).toBeInTheDocument();
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    dialog = screen.getByRole('dialog', { name: 'New topic' });
    await userEvent.type(within(dialog).getByLabelText('Name'), 'checkout');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));
    expect(await within(dialog).findByText('There is already a topic with this name.')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Name')).toHaveFocus();
  });

  it('edits a topic and removes one after asking', async () => {
    const calls = stubApi({
      'GET /api/topics': () => ({ body: [CHECKOUT] }),
      'PUT /api/topics/t1': (init) => ({ body: { id: 't1', ...JSON.parse(String(init?.body)) } }),
      'DELETE /api/topics/t1': () => ({ status: 204, body: null }),
    });
    open('#/settings/topics');

    await userEvent.click(await screen.findByRole('button', { name: 'Edit Checkout' }));
    const name = within(screen.getByRole('dialog', { name: 'Edit topic' })).getByLabelText('Name');
    expect(name).toHaveValue('Checkout');
    await userEvent.clear(name);
    await userEvent.type(name, 'Payments');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Payments saved.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Remove Payments' }));
    const confirm = screen.getByRole('dialog', { name: 'Remove Payments?' });
    await userEvent.click(within(confirm).getByRole('button', { name: 'Remove' }));
    expect(await screen.findByText('Payments removed.')).toBeInTheDocument();
    expect(screen.getByText('No topics yet')).toBeInTheDocument();
    expect(calls.map((call) => call.key)).toContain('DELETE /api/topics/t1');
  });

  it('turns Add off at fifty topics and says why', async () => {
    stubApi({
      'GET /api/topics': () => ({ body: Array.from({ length: 50 }, (_, i) => ({ id: `t${i}`, name: `Topic ${i}`, description: '' })) }),
    });
    open('#/settings/topics');

    expect(await screen.findByText(/has 50 topics, the most it can keep/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled();
  });
});
