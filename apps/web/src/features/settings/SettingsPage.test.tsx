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
const COMPANY = { id: 'v1', name: 'company_name', value: 'Acme Inc.' };

function open(section: string) {
  window.location.hash = section;
  return render(<SettingsPage workspace={WORKSPACE} onSaved={vi.fn()} canDelete onDeleted={vi.fn()} />);
}

describe('SettingsPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.location.hash = '';
  });

  it('lists each model with who uses it at what effort, and saves the provider\'s default model', async () => {
    const calls = stubApi({
      'GET /api/llm-providers': () => ({ body: LLM_PROVIDERS }),
      'GET /api/llm-providers/anthropic/models': () => ({ body: OFFERED }),
      'GET /api/agents': () => ({ body: [
        agent('Topic classifier', { provider: 'anthropic', model: 'claude-haiku-5-5', effort: 'LOW' }),
        agent('Analyst', { provider: 'anthropic', model: 'claude-opus-5-5', effort: 'HIGH' }),
      ] }),
      'PUT /api/llm-providers/default': (init) => ({
        body: { ...LLM_PROVIDERS, providers: [{ ...ANTHROPIC, defaultModel: JSON.parse(String(init?.body)) }] },
      }),
    });
    const { container } = open('#/settings/llm');

    const model = await screen.findByRole('combobox', { name: 'Anthropic default model' });
    await screen.findByText('3 allowed of 4');
    expect(screen.queryByRole('checkbox', { name: 'Claude Haiku 5.5' })).not.toBeInTheDocument();
    const list = screen.getByRole('button', { name: 'Anthropic models' });
    expect(list).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(list);
    expect(list).toHaveAttribute('aria-expanded', 'true');
    await screen.findByRole('checkbox', { name: 'Claude Haiku 5.5' });
    expect(screen.getByText('Connected')).toBeInTheDocument();
    expect(screen.getByRole('row', { name: /Claude Haiku 5.5/ })).toHaveTextContent('Topic classifier (low)');
    expect(screen.getByRole('row', { name: /Claude Opus 5.5/ })).toHaveTextContent('Analyst (high)');
    expect(screen.getByRole('row', { name: /Claude Sonnet 5.5/ })).toHaveTextContent('No agents yet');
    expect(screen.queryByText(/More providers/)).not.toBeInTheDocument();
    expect(model).toHaveValue('claude-sonnet-5-5');
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

    await userEvent.selectOptions(model, 'claude-opus-5-5');
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Anthropic default effort' }), 'HIGH');
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

    await userEvent.click(await screen.findByRole('button', { name: 'Anthropic models' }));
    const fable = await screen.findByRole('checkbox', { name: 'Claude Fable 5.2' });
    expect(screen.getByText('3 allowed of 4')).toBeInTheDocument();
    expect(fable).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Claude Haiku 5.5' })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: 'Claude Haiku 5.5' })).toHaveAccessibleDescription(/Agents run on it/);
    expect(screen.getByRole('checkbox', { name: 'Claude Sonnet 5.5' })).toHaveAccessibleDescription(/this provider’s default/);
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

  it('shows OpenAI beside Anthropic, connects it with its own key, and picks the provider new agents start on', async () => {
    const openAiOff = { id: 'openai', connected: false, connection: { ...NO_KEY }, models: [], defaultModel: null };
    const openAiOn = {
      id: 'openai', connected: true,
      connection: { source: 'WORKSPACE' as const, keyEnding: 'k2Mw', addedAt: '2026-11-27T01:00:00Z', checkedAt: '2026-11-27T01:00:00Z', refused: false },
      models: [{ id: 'gpt-5.2', label: 'GPT-5.2' }],
      defaultModel: { provider: 'openai', model: 'gpt-5.2', effort: 'MEDIUM' as const },
    };
    let connected = false;
    const calls = stubApi({
      'GET /api/llm-providers': () => ({ body: { ...LLM_PROVIDERS, providers: [ANTHROPIC, connected ? openAiOn : openAiOff] } }),
      'GET /api/llm-providers/anthropic/models': () => ({ body: OFFERED }),
      'GET /api/llm-providers/openai/models': () => ({ body: { provider: 'openai', connected: true, listError: null, models: [
        { id: 'gpt-5.2', label: 'GPT-5.2', allowed: true, listed: true },
        { id: 'gpt-5.2-mini', label: 'GPT-5.2 mini', allowed: false, listed: true },
      ] } }),
      'GET /api/agents': () => ({ body: [] }),
      'PUT /api/llm-providers/openai/key': () => {
        connected = true;
        return { body: openAiOn };
      },
      'PUT /api/llm-providers/new-agents': () => ({ body: {
        providers: [ANTHROPIC, openAiOn], defaultForNewAgents: openAiOn.defaultModel,
      } }),
    });
    const { container } = open('#/settings/llm');

    expect(await screen.findByText('OpenAI (GPT)')).toBeInTheDocument();
    expect(screen.getByText('Not connected')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Anthropic models' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'OpenAI models' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Connect' }));
    const dialog = screen.getByRole('dialog', { name: 'Connect OpenAI' });
    const field = within(dialog).getByLabelText('API key');
    expect(field).toHaveAttribute('placeholder', 'sk-...');
    await userEvent.type(field, 'sk-proj-new-key-k2Mw');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Check and connect' }));

    expect(await screen.findByRole('heading', { name: 'OpenAI models' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'OpenAI models' }));
    expect(await screen.findByText(/Image, audio, speech and embedding models are left out/)).toBeInTheDocument();
    expect(await screen.findByRole('checkbox', { name: 'GPT-5.2 mini' })).not.toBeChecked();
    expect(calls.some((call) => call.key === 'PUT /api/llm-providers/openai/key')).toBe(true);
    const model = await screen.findByRole('combobox', { name: 'OpenAI default model' });
    expect(within(model).getByRole('option', { name: 'GPT-5.2' })).toHaveValue('gpt-5.2');
    expect(screen.getByRole('combobox', { name: 'Anthropic default model' })).toHaveValue('claude-sonnet-5-5');
    expect(screen.getByRole('radio', { name: 'New agents start on Anthropic' })).toBeChecked();
    await userEvent.click(screen.getByRole('radio', { name: 'New agents start on OpenAI' }));
    expect(await screen.findByRole('radio', { name: 'New agents start on OpenAI' })).toBeChecked();
    expect(calls.find((call) => call.key === 'PUT /api/llm-providers/new-agents')?.init?.body).toBe('{"provider":"openai"}');
    expect(await accessibilityViolations(container)).toEqual([]);
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
    expect(screen.queryByRole('combobox', { name: 'Anthropic default model' })).not.toBeInTheDocument();

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
    expect(await screen.findByRole('combobox', { name: 'Anthropic default model' })).toBeInTheDocument();
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
    expect(within(nav).getAllByRole('link').map((link) => link.textContent)).toEqual(['General', 'LLM providers', 'MCPs', 'Integrations', 'Variables']);
    expect(within(nav).getByRole('link', { name: 'General' })).toHaveAttribute('aria-current', 'page');
  });

  it('starts with no variables, adds one in place, and refuses a name that is taken or not made for braces', async () => {
    let added = 0;
    const calls = stubApi({
      'GET /api/variables': () => ({ body: [] }),
      'POST /api/variables': (init) => (added++ === 0
        ? { status: 201, body: { id: 'v1', ...JSON.parse(String(init?.body)) } }
        : { status: 422, body: { code: 'variable.name-taken', detail: 'taken' } }),
    });
    const { container } = open('#/settings/variables');

    expect(await screen.findByText('No variables yet')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    let form = screen.getByRole('form', { name: 'New variable' });
    expect(within(form).getByLabelText('Name')).toHaveFocus();
    await userEvent.type(within(form).getByLabelText('Name'), 'company name');
    await userEvent.click(within(form).getByRole('button', { name: 'Save' }));
    expect(within(form).getByText('Use letters, digits and underscores, starting with a letter.')).toBeInTheDocument();
    expect(calls.filter((call) => call.key === 'POST /api/variables')).toHaveLength(0);

    await userEvent.clear(within(form).getByLabelText('Name'));
    await userEvent.type(within(form).getByLabelText('Name'), ' company_name ');
    await userEvent.click(within(form).getByRole('button', { name: 'Save' }));
    expect(within(form).getByText('Give the variable a value.')).toBeInTheDocument();
    await userEvent.type(within(form).getByLabelText('Value'), 'Acme Inc.');
    expect(form).toHaveTextContent('9 / 2000');
    await userEvent.click(within(form).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('company_name added.')).toBeInTheDocument();
    expect(JSON.parse(String(calls.find((call) => call.key === 'POST /api/variables')?.init?.body)))
      .toEqual({ name: 'company_name', value: 'Acme Inc.' });
    expect(screen.getByRole('list', { name: 'Variables' })).toHaveTextContent('company_nameAcme Inc.');
    expect(screen.getByText('1 of 100 variables.')).toBeInTheDocument();
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    form = screen.getByRole('form', { name: 'New variable' });
    await userEvent.type(within(form).getByLabelText('Name'), 'Company_Name');
    await userEvent.type(within(form).getByLabelText('Value'), 'x');
    await userEvent.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await within(form).findByText('This workspace already has a variable with this name.')).toBeInTheDocument();
    expect(within(form).getByLabelText('Name')).toHaveFocus();
  });

  it('edits a variable in its row and deletes one after asking', async () => {
    const calls = stubApi({
      'GET /api/variables': () => ({ body: [COMPANY] }),
      'PUT /api/variables/v1': (init) => ({ body: { id: 'v1', ...JSON.parse(String(init?.body)) } }),
      'DELETE /api/variables/v1': () => ({ status: 204, body: null }),
    });
    open('#/settings/variables');

    await userEvent.click(await screen.findByRole('button', { name: 'Edit company_name' }));
    const form = screen.getByRole('form', { name: 'Edit company_name' });
    expect(within(form).getByLabelText('Value')).toHaveValue('Acme Inc.');
    await userEvent.clear(within(form).getByLabelText('Value'));
    await userEvent.type(within(form).getByLabelText('Value'), 'Acme Corporation');
    await userEvent.click(within(form).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('company_name saved.')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Variables' })).toHaveTextContent('Acme Corporation');
    expect(screen.getByRole('button', { name: 'Edit company_name' })).toHaveFocus();

    await userEvent.click(screen.getByRole('button', { name: 'Edit company_name' }));
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument();
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('form', { name: 'Edit company_name' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Edit company_name' })).toHaveFocus();

    await userEvent.click(screen.getByRole('button', { name: 'Delete company_name' }));
    const confirm = screen.getByRole('dialog', { name: 'Delete company_name?' });
    expect(confirm).toHaveTextContent('write {{company_name}} get the text as written');
    await userEvent.click(within(confirm).getByRole('button', { name: 'Delete' }));
    expect(await screen.findByText('company_name deleted.')).toBeInTheDocument();
    expect(screen.getByText('No variables yet')).toBeInTheDocument();
    expect(calls.map((call) => call.key)).toContain('DELETE /api/variables/v1');
  });

  it('turns Add off at a hundred variables and says why', async () => {
    stubApi({
      'GET /api/variables': () => ({ body: Array.from({ length: 100 }, (_, i) => ({ id: `v${i}`, name: `variable_${i}`, value: 'x' })) }),
    });
    open('#/settings/variables');

    expect(await screen.findByText(/has 100 variables, the most it can keep/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add' })).toBeDisabled();
  });

  it('deletes the workspace only once its name is typed exactly, and keeps the only workspace', async () => {
    const onDeleted = vi.fn();
    const calls = stubApi({
      'DELETE /api/workspaces/w1': (init) => (JSON.parse(String(init?.body)).name === 'Acme Product'
        ? { status: 204, body: null } : { status: 422, body: { code: 'workspace.name-mismatch' } }),
    });
    window.location.hash = '#/settings';
    const { container, unmount } = render(
      <SettingsPage workspace={WORKSPACE} onSaved={vi.fn()} canDelete onDeleted={onDeleted} />);

    await userEvent.click(screen.getByRole('button', { name: 'Delete workspace…' }));
    const dialog = screen.getByRole('dialog', { name: 'Delete Acme Product?' });
    const field = within(dialog).getByLabelText('Type Acme Product to confirm');
    const remove = within(dialog).getByRole('button', { name: 'Delete workspace' });
    expect(field).toHaveFocus();
    expect(remove).toBeDisabled();
    await userEvent.type(field, 'acme product');
    expect(remove).toBeDisabled();
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.clear(field);
    await userEvent.type(field, 'Acme Product{Enter}');

    await vi.waitFor(() => expect(onDeleted).toHaveBeenCalledWith(WORKSPACE));
    expect(calls.filter((call) => call.key === 'DELETE /api/workspaces/w1')).toHaveLength(1);
    unmount();

    render(<SettingsPage workspace={WORKSPACE} onSaved={vi.fn()} canDelete={false} onDeleted={onDeleted} />);
    expect(screen.getByRole('button', { name: 'Delete workspace…' })).toBeDisabled();
    expect(screen.getByText(/is your only workspace, so it stays/)).toBeInTheDocument();
  });
});
