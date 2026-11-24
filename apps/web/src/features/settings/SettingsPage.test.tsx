import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { AgentSummary } from '../../domain/agents';
import type { ModelSettings } from '../../domain/settings';
import { stubApi } from '../../testing';
import { SettingsPage } from './SettingsPage';

const WORKSPACE = { id: 'w1', name: 'Acme Product', description: 'Checkout', sourceCount: 2, createdAt: '2026-11-18T12:00:00Z' };
const MODELS: ModelSettings = {
  vendors: [{ id: 'anthropic', connected: true }],
  presets: [
    { preset: 'FAST', model: 'claude-haiku-5-5', defaultModel: 'claude-haiku-5-5', effort: 'LOW' },
    { preset: 'BALANCED', model: 'claude-sonnet-5-5', defaultModel: 'claude-sonnet-5-5', effort: 'MEDIUM' },
    { preset: 'THOROUGH', model: 'claude-opus-5-5', defaultModel: 'claude-opus-5-5', effort: 'HIGH' },
  ],
  models: [
    { id: 'claude-haiku-5-5', label: 'Claude Haiku 5.5' },
    { id: 'claude-sonnet-5-5', label: 'Claude Sonnet 5.5' },
    { id: 'claude-opus-5-5', label: 'Claude Opus 5.5' },
  ],
};
const agent = (name: string, model: AgentSummary['model']): AgentSummary => ({
  id: name, name, purpose: '', status: 'ACTIVE', publishedVersion: 0, draftPublished: false, givesBack: 'TOPICS', model,
  skills: [], updatedAt: '2026-11-24T00:00:00Z',
});
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

  it('shows each setting with its model and the agents that use it, and saves a new pick', async () => {
    const calls = stubApi({
      'GET /api/model-settings': () => ({ body: MODELS }),
      'GET /api/agents': () => ({ body: [agent('Topic classifier', 'FAST'), agent('Analyst', 'THOROUGH')] }),
      'PUT /api/model-settings/presets/FAST': () => ({
        body: { ...MODELS, presets: [{ ...MODELS.presets[0], model: 'claude-sonnet-5-5' }, ...MODELS.presets.slice(1)] },
      }),
    });
    const { container } = open('#/settings/connectors');

    const fast = await screen.findByRole('combobox', { name: 'Fast model' });
    expect(screen.getByText('Connected')).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Fast/ })).toHaveTextContent('Topic classifier');
    expect(screen.getByRole('row', { name: /Balanced/ })).toHaveTextContent('No agents yet');
    expect(screen.getByRole('heading', { name: /Storage/ })).toHaveTextContent('Coming soon');
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

    await userEvent.selectOptions(fast, 'claude-sonnet-5-5');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Saved')).toBeInTheDocument();
    expect(JSON.parse(String(calls.find((call) => call.key === 'PUT /api/model-settings/presets/FAST')?.init?.body)))
      .toEqual({ model: 'claude-sonnet-5-5' });
    expect(fast).toHaveValue('claude-sonnet-5-5');
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('says how to add a key when no vendor is connected', async () => {
    stubApi({
      'GET /api/model-settings': () => ({ body: { ...MODELS, vendors: [{ id: 'anthropic', connected: false }] } }),
      'GET /api/agents': () => ({ body: [] }),
    });
    open('#/settings/connectors');

    expect(await screen.findByText('No key')).toBeInTheDocument();
    expect(screen.getByText(/Add CONXIST_ANTHROPIC_API_KEY/)).toBeInTheDocument();
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
