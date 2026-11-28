import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { ApiTool, Integration } from '../../domain/integrations';
import { stubApi } from '../../testing';
import { SettingsPage } from '../settings/SettingsPage';

const WORKSPACE = { id: 'w1', name: 'Acme Product', description: '', sourceCount: 0, createdAt: '2026-11-18T12:00:00Z' };
const ID = '77777777-7777-7777-7777-777777777777';
const KEY = 'nw_live_0123456789a9F2';
const tool = (name: string, patch: Partial<ApiTool> = {}): ApiTool => ({
  name, section: 'Contacts', method: 'GET', path: `/${name}`, description: `About ${name}.`, params: [], reads: true,
  imported: true, allowed: false, usedBy: [], ...patch,
});
const TOOLS: ApiTool[] = [
  tool('get_me', { section: 'Account', path: '/me' }),
  tool('list_contacts', { path: '/contacts' }),
  tool('delete_contact', { method: 'DELETE', path: '/contacts/{id}', reads: false,
    params: [{ name: 'id', in: 'PATH', type: 'string', schema: { type: 'string' }, required: true, description: '' }] }),
  tool('search_deals', { section: 'Deals', method: 'POST', path: '/deals/search', reads: false }),
];
const NORTHWIND: Integration = {
  id: ID, name: 'Northwind', address: 'https://api.northwind.test/v1',
  auth: { kind: 'API_KEY', place: 'HEADER', name: 'X-Api-Key' }, account: null, ending: 'a9F2', checkPath: '/me',
  status: 'WORKS', icon: null, logo: null, description: null, tools: [],
  addedAt: '2026-11-28T10:00:00Z', checkedAt: '2026-11-28T10:00:00Z',
};
const IMPORTED: Integration = {
  ...NORTHWIND,
  description: { title: 'Northwind CRM API', version: '1.4.0', address: 'https://api.northwind.test/v1/openapi.json', importedAt: '2026-11-28T10:01:00Z' },
  tools: TOOLS.map((each) => ({ ...each, allowed: each.reads })),
};

function open(hash: string) {
  window.location.hash = hash;
  return render(<SettingsPage workspace={WORKSPACE} onSaved={vi.fn()} canDelete onDeleted={vi.fn()} />);
}

const bodyOf = (calls: ReturnType<typeof stubApi>, key: string) =>
  JSON.parse(String(calls.filter((call) => call.key === key).at(-1)?.init?.body)) as Record<string, unknown>;

describe('Settings › Integrations', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.location.hash = '';
  });

  it('adds any API by its address and key, imports its description, and adds the ticked tools', async () => {
    let refused = true;
    const calls = stubApi({
      'GET /api/integrations': () => ({ body: [] }),
      'POST /api/integrations': () => (refused
        ? { status: 422, body: { code: 'integration.sign-in-refused', detail: 'The API refused the sign-in (HTTP 401).' } }
        : { status: 201, body: NORTHWIND }),
      [`POST /api/integrations/${ID}/import`]: () => ({
        body: { found: 4, added: TOOLS.map((each) => each.name), kept: [], integration: IMPORTED,
          leftOut: [{ operation: 'uploadAttachment', reason: 'it sends a file.' }] },
      }),
      [`PUT /api/integrations/${ID}/allowed`]: (init) => {
        const allowed = (JSON.parse(String(init?.body)) as { allowedTools: string[] }).allowedTools;
        return { body: { ...IMPORTED, tools: IMPORTED.tools.map((each) => ({ ...each, allowed: allowed.includes(each.name) })) } };
      },
    });
    const { container } = open('#/settings/integrations');

    expect(await screen.findByRole('heading', { name: 'No APIs yet' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add API' }));
    const dialog = screen.getByRole('dialog', { name: 'Add API' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Connect' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Enter the API’s address.');
    expect(calls.map((call) => call.key)).not.toContain('POST /api/integrations');

    await userEvent.type(within(dialog).getByLabelText('Address'), 'https://api.northwind.test/v1');
    await userEvent.tab();
    expect(within(dialog).getByLabelText('Name')).toHaveValue('Northwind');
    expect(within(dialog).getByRole('radio', { name: /API key/ })).toBeChecked();
    expect(within(dialog).getByLabelText('Its name')).toHaveValue('X-Api-Key');
    await userEvent.type(within(dialog).getByLabelText('Key'), 'wrong');
    await userEvent.type(within(dialog).getByLabelText(/Check the sign-in with/), '/me');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Connect' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('The API refused the sign-in.');

    refused = false;
    await userEvent.clear(within(dialog).getByLabelText('Key'));
    await userEvent.type(within(dialog).getByLabelText('Key'), KEY);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Connect' }));
    expect(await within(dialog).findByRole('heading', { name: 'Which operations should be tools?' })).toBeInTheDocument();
    expect(bodyOf(calls, 'POST /api/integrations')).toEqual({
      name: 'Northwind', address: 'https://api.northwind.test/v1', auth: 'API_KEY', keyPlace: 'HEADER',
      keyName: 'X-Api-Key', username: null, secret: KEY, checkPath: '/me',
    });

    await userEvent.type(within(dialog).getByLabelText('OpenAPI address'), 'https://api.northwind.test/v1/openapi.json');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Import' }));
    expect(await within(dialog).findByText(/Northwind CRM API 1.4.0: 4 operations ConXist can call/)).toBeInTheDocument();
    expect(within(dialog).getByText('1 operation left out')).toBeInTheDocument();
    expect(within(dialog).getByRole('checkbox', { name: 'Allow list_contacts' })).toBeChecked();
    expect(within(dialog).getByRole('checkbox', { name: 'Allow delete_contact' })).not.toBeChecked();
    expect(await accessibilityViolations(container)).toEqual([]);

    // A tool that changes things asks first; keeping it off leaves it off.
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Allow search_deals' }));
    expect(within(dialog).getByRole('group', { name: 'Let agents use search_deals?' })).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Allow this tool' }));
    expect(within(dialog).getByRole('checkbox', { name: 'Allow search_deals' })).toBeChecked();
    // A section's box unticks its tools, and ticks back only the ones that read.
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Allow the tools of Contacts that only read' }));
    expect(within(dialog).getByRole('checkbox', { name: 'Allow list_contacts' })).not.toBeChecked();
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Allow the tools of Contacts that only read' }));
    expect(within(dialog).getByRole('checkbox', { name: 'Allow list_contacts' })).toBeChecked();
    expect(within(dialog).getByRole('checkbox', { name: 'Allow delete_contact' })).not.toBeChecked();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Add Northwind with 3 tools' }));
    expect(await screen.findByText('Added Northwind.')).toBeInTheDocument();
    expect(bodyOf(calls, `PUT /api/integrations/${ID}/allowed`)).toEqual({
      allowedTools: ['get_me', 'list_contacts', 'search_deals'],
    });
    expect(screen.getByText(/api.northwind.test\/v1 · API key ending a9F2 · 3 of 4 tools allowed · No agents use it yet/))
      .toBeInTheDocument();
  });

  it('shows an API’s page, writes a tool by hand, tries it, and keeps a tool agents use', async () => {
    const used: Integration = {
      ...IMPORTED,
      tools: IMPORTED.tools.map((each) => (each.name === 'list_contacts'
        ? { ...each, usedBy: [{ agentId: 'a1', agentName: 'Deal reader', where: 'DRAFT', archived: false }] } : each)),
    };
    const calls = stubApi({
      'GET /api/integrations': () => ({ body: [used] }),
      [`GET /api/integrations/${ID}`]: () => ({ body: used }),
      [`POST /api/integrations/${ID}/try`]: () => ({
        body: { status: 200, contentType: 'application/json', bytes: 40, millis: 12, request: 'GET /v1/contacts',
          text: '{"contacts":[{"name":"Dana Globex"}]}' },
      }),
      [`POST /api/integrations/${ID}/tools`]: (init) => ({
        status: 201,
        body: { ...used, tools: [...used.tools, { ...tool('recent_contacts'), ...JSON.parse(String(init?.body)), params: [], usedBy: [], imported: false }] },
      }),
      [`POST /api/integrations/${ID}/check`]: () => ({
        body: { works: true, error: null, status: 200, millis: 30, request: 'GET /v1/me', integration: used },
      }),
    });
    const { container } = open(`#/settings/integrations/${ID}`);

    expect(await screen.findByRole('heading', { name: /Northwind/ })).toBeInTheDocument();
    expect(screen.getByText('API key in the X-Api-Key header, ending a9F2')).toBeInTheDocument();
    expect(screen.getByText(/OpenAPI description Northwind CRM API 1.4.0, imported/)).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Allow list_contacts' })).toBeDisabled();
    expect(screen.getByText('Used by Deal reader. Turn it off in those agents first.')).toBeInTheDocument();
    expect(screen.queryByText(KEY)).not.toBeInTheDocument();
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: 'Check now' }));
    expect(await screen.findByText('Connected. GET /v1/me answered 200 in 30 ms.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Add tool' }));
    const editor = screen.getByRole('dialog', { name: 'New tool for Northwind' });
    await userEvent.type(within(editor).getByLabelText('Tool name'), 'recent_contacts');
    await userEvent.clear(within(editor).getByLabelText('Path'));
    await userEvent.type(within(editor).getByLabelText('Path'), '/contacts');
    await userEvent.type(within(editor).getByLabelText('What it does, for the agent'), 'The newest contacts.');
    expect(within(editor).getByRole('checkbox', { name: /It only reads/ })).toBeDisabled();
    await userEvent.click(within(editor).getByRole('button', { name: 'Add input' }));
    await userEvent.type(within(editor).getByLabelText('Name of input 1'), 'limit');
    await userEvent.selectOptions(within(editor).getByLabelText('Type of limit'), 'integer');
    await userEvent.type(within(editor).getByRole('textbox', { name: /^limit Query/ }), '2');
    await userEvent.click(within(editor).getByRole('button', { name: 'Send' }));
    expect(await within(editor).findByText('HTTP 200')).toBeInTheDocument();
    expect(within(editor).getByText(/Dana Globex/)).toBeInTheDocument();
    expect(bodyOf(calls, `POST /api/integrations/${ID}/try`)).toMatchObject({
      tool: { name: 'recent_contacts', method: 'GET', path: '/contacts', reads: true }, inputs: { limit: 2 },
    });

    await userEvent.click(within(editor).getByRole('button', { name: 'Save tool' }));
    expect(await screen.findByText('Added recent_contacts.')).toBeInTheDocument();
    expect(bodyOf(calls, `POST /api/integrations/${ID}/tools`)).toMatchObject({
      name: 'recent_contacts', section: null, allowed: true,
      inputs: [{ name: 'limit', in: 'QUERY', type: 'integer', required: false }],
    });
  });

  it('refuses a tool name the API has, and a path change adds its path input', async () => {
    stubApi({
      'GET /api/integrations': () => ({ body: [IMPORTED] }),
      [`GET /api/integrations/${ID}`]: () => ({ body: IMPORTED }),
    });
    open(`#/settings/integrations/${ID}`);

    await userEvent.click(await screen.findByRole('button', { name: 'Add tool' }));
    const editor = screen.getByRole('dialog', { name: 'New tool for Northwind' });
    await userEvent.type(within(editor).getByLabelText('Tool name'), 'list_contacts');
    await userEvent.type(within(editor).getByLabelText('Path'), 'contacts/{{id}');
    expect(within(editor).getByLabelText('Name of input 1')).toHaveValue('id');
    await userEvent.type(within(editor).getByLabelText('What it does, for the agent'), 'One contact.');
    await userEvent.click(within(editor).getByRole('button', { name: 'Save tool' }));
    expect(within(editor).getByRole('alert')).toHaveTextContent('This API already has a tool with that name.');
  });
});
