import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { McpConnection, McpServer, McpTool } from '../../domain/mcps';
import { stubApi } from '../../testing';
import { SettingsPage } from '../settings/SettingsPage';

const WORKSPACE = { id: 'w1', name: 'Acme Product', description: '', sourceCount: 0, createdAt: '2026-11-18T12:00:00Z' };
const SERVER_ID = '66666666-6666-6666-6666-666666666666';
const ICON = 'data:image/png;base64,iVBORw0KGgo=';
const tool = (name: string, readOnly: boolean, allowed = false, users: string[] = []): McpTool => ({
  name, title: name, description: `About ${name}.`, readOnly, allowed,
  usedBy: users.map((agentName) => ({ agentId: agentName, agentName, where: 'DRAFT', archived: false })),
});
const TOOLS = [tool('search_docs', true), tool('get_doc', true), tool('create_doc', false)];
const ready = (overrides: Partial<McpConnection> = {}): McpConnection => ({
  id: 'c1', serverId: null, status: 'READY', signInUrl: null, auth: 'NONE', suggestedName: 'Acme Docs', icon: ICON,
  tools: TOOLS, error: null, ...overrides,
});
const SERVER: McpServer = {
  id: SERVER_ID, name: 'Acme Docs', address: 'https://mcp.acme.test/mcp', auth: 'OAUTH', icon: ICON, logo: null, status: 'WORKS',
  tools: [tool('search_docs', true, true, ['Researcher']), tool('get_doc', true, true), tool('create_doc', false)],
  addedAt: '2026-11-27T10:00:00Z', checkedAt: '2026-11-27T10:00:00Z',
};

function open(hash: string) {
  window.location.hash = hash;
  return render(<SettingsPage workspace={WORKSPACE} onSaved={vi.fn()} canDelete onDeleted={vi.fn()} />);
}

const bodyOf = (calls: ReturnType<typeof stubApi>, key: string) =>
  JSON.parse(String(calls.filter((call) => call.key === key).at(-1)?.init?.body)) as Record<string, unknown>;

describe('Settings › MCPs', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.location.hash = '';
  });

  it('adds a public server with its read tools ticked, and asks first before a tool that changes things', async () => {
    const calls = stubApi({
      'GET /api/mcps': () => ({ body: [] }),
      'POST /api/mcps/connections': () => ({ body: ready() }),
      'POST /api/mcps': (init) => ({ status: 201, body: {
        ...SERVER, auth: 'NONE', tools: TOOLS.map((each) => ({ ...each, allowed: (JSON.parse(String(init?.body)) as { allowedTools: string[] }).allowedTools.includes(each.name) })),
      } }),
    });
    const { container } = open('#/settings/mcps');

    expect(await screen.findByRole('heading', { name: 'No MCPs yet' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    const dialog = screen.getByRole('dialog', { name: 'Add an MCP' });
    await userEvent.type(within(dialog).getByLabelText('Server address'), 'https://mcp.acme.test/mcp');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));

    expect(await within(dialog).findByRole('heading', { name: 'Acme Docs offers 3 tools' })).toBeInTheDocument();
    expect(bodyOf(calls, 'POST /api/mcps/connections')).toEqual({ address: 'https://mcp.acme.test/mcp' });
    expect(within(dialog).getByRole('checkbox', { name: 'Allow search_docs' })).toBeChecked();
    expect(within(dialog).getByRole('checkbox', { name: 'Allow get_doc' })).toBeChecked();
    expect(within(dialog).getByRole('checkbox', { name: 'Allow create_doc' })).not.toBeChecked();
    expect(within(dialog).getByText('2 of 3 tools ticked')).toBeInTheDocument();
    expect(within(dialog).getByLabelText('Name in ConXist')).toHaveValue('Acme Docs');
    expect(await accessibilityViolations(container.ownerDocument.body)).toEqual([]);

    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Allow create_doc' }));
    const ask = within(dialog).getByRole('group', { name: 'Let agents use create_doc?' });
    expect(ask).toHaveFocus();
    expect(ask).toHaveTextContent('ConXist can’t undo what this tool does.');
    expect(await accessibilityViolations(container.ownerDocument.body)).toEqual([]);
    await userEvent.keyboard('{Escape}');
    expect(screen.getByRole('dialog', { name: 'Add an MCP' })).toBeInTheDocument();
    expect(within(dialog).queryByRole('group', { name: 'Let agents use create_doc?' })).not.toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Allow create_doc' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Keep it off' }));
    expect(within(dialog).getByRole('checkbox', { name: 'Allow create_doc' })).not.toBeChecked();
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Allow create_doc' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Allow this tool' }));
    expect(within(dialog).getByRole('checkbox', { name: 'Allow create_doc' })).toBeChecked();
    expect(within(dialog).queryByRole('group', { name: 'Let agents use create_doc?' })).not.toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Allow get_doc' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add' }));

    expect(bodyOf(calls, 'POST /api/mcps')).toEqual({ connectionId: 'c1', name: 'Acme Docs', allowedTools: ['search_docs', 'create_doc'] });
    expect(await screen.findByText(/Added Acme Docs\./)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Use it in an agent' })).toHaveAttribute('href', '#/agents');
    expect(screen.getByText(/2 of 3 tools allowed/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Manage Acme Docs' })).toHaveAttribute('href', `#/settings/mcps/${SERVER_ID}`);
  });

  it('says what is wrong with an address, before and after asking the server', async () => {
    stubApi({
      'GET /api/mcps': () => ({ body: [] }),
      'POST /api/mcps/connections': () => ({ status: 422, body: { code: 'mcp.address-private', detail: 'private' } }),
    });
    open('#/settings/mcps');
    await userEvent.click(await screen.findByRole('button', { name: 'Add' }));
    const dialog = screen.getByRole('dialog', { name: 'Add an MCP' });

    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Type the server’s address.');
    await userEvent.type(within(dialog).getByLabelText('Server address'), 'mcp.acme.test');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('It starts with https://');
    await userEvent.clear(within(dialog).getByLabelText('Server address'));
    await userEvent.type(within(dialog).getByLabelText('Server address'), 'https://10.0.0.8/mcp');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('private network');
  });

  it('signs in on the server’s own page, or with a token when it asks for one', async () => {
    let polls = 0;
    const calls = stubApi({
      'GET /api/mcps': () => ({ body: [] }),
      'POST /api/mcps/connections': (init) => {
        const body = JSON.parse(String(init?.body)) as { token?: string };
        return { body: body.token ? ready({ id: 'c2', auth: 'TOKEN' })
          : ready({ status: 'SIGNING_IN', signInUrl: 'https://auth.acme.test/authorize?x=1', tools: [], suggestedName: null, icon: null }) };
      },
      'GET /api/mcps/connections/c1': () => ({ body: polls++ === 0 ? ready({ status: 'SIGNING_IN', tools: [] }) : ready({ auth: 'OAUTH' }) }),
    });
    const popup = vi.fn(() => ({ opener: window }) as unknown as Window);
    vi.stubGlobal('open', popup);
    open('#/settings/mcps');
    await userEvent.click(await screen.findByRole('button', { name: 'Add' }));
    const dialog = screen.getByRole('dialog', { name: 'Add an MCP' });
    await userEvent.type(within(dialog).getByLabelText('Server address'), 'https://mcp.acme.test/mcp');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));

    expect(await within(dialog).findByRole('heading', { name: 'Sign in to mcp.acme.test' })).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Use a token instead' }));
    expect(within(dialog).getByRole('heading', { name: 'mcp.acme.test needs a token' })).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sign in instead' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sign in' }));
    expect(popup).toHaveBeenCalledWith('https://auth.acme.test/authorize?x=1', 'mcp-sign-in', expect.stringContaining('popup'));
    expect(within(dialog).getByText(/Waiting for you to sign in/)).toBeInTheDocument();
    expect(await within(dialog).findByRole('heading', { name: 'Acme Docs offers 3 tools' }, { timeout: 5000 })).toBeInTheDocument();

    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    const again = screen.getByRole('dialog', { name: 'Add an MCP' });
    await userEvent.type(within(again).getByLabelText('Server address'), 'https://mcp.acme.test/mcp');
    await userEvent.click(within(again).getByRole('button', { name: 'Next' }));
    await userEvent.click(await within(again).findByRole('button', { name: 'Use a token instead' }));
    await userEvent.type(within(again).getByLabelText('Token'), 'tok-123');
    await userEvent.click(within(again).getByRole('button', { name: 'Connect' }));
    expect(await within(again).findByRole('heading', { name: 'Acme Docs offers 3 tools' })).toBeInTheDocument();
    expect(bodyOf(calls, 'POST /api/mcps/connections')).toEqual({ address: 'https://mcp.acme.test/mcp', token: 'tok-123' });
  }, 15_000);

  it('goes back to the address, and offers to try again when a server has no tools', async () => {
    stubApi({
      'GET /api/mcps': () => ({ body: [] }),
      'POST /api/mcps/connections': () => ({ body: ready({ tools: [] }) }),
    });
    open('#/settings/mcps');
    await userEvent.click(await screen.findByRole('button', { name: 'Add' }));
    const dialog = screen.getByRole('dialog', { name: 'Add an MCP' });
    await userEvent.type(within(dialog).getByLabelText('Server address'), 'https://mcp.acme.test/mcp');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));

    expect(await within(dialog).findByText(/offers no tools agents can use yet/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Retry' })).toBeEnabled();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Back' }));
    expect(within(dialog).getByLabelText('Server address')).toHaveValue('https://mcp.acme.test/mcp');
  });

  it('a server’s page saves each tick, keeps a tool agents use, checks for new tools and refuses removal in use', async () => {
    const calls = stubApi({
      'GET /api/mcps': () => ({ body: [SERVER] }),
      [`GET /api/mcps/${SERVER_ID}`]: () => ({ body: SERVER }),
      [`PUT /api/mcps/${SERVER_ID}`]: () => ({ body: { ...SERVER, tools: [SERVER.tools[0], { ...SERVER.tools[1], allowed: false }, SERVER.tools[2]] } }),
      [`POST /api/mcps/${SERVER_ID}/check`]: () => ({ body: { works: true, error: null, newTools: ['list_spaces'], server: SERVER } }),
    });
    const { container } = open(`#/settings/mcps/${SERVER_ID}`);

    expect(await screen.findByRole('heading', { name: /Acme Docs/, level: 2 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'All MCPs' })).toHaveAttribute('href', '#/settings/mcps');
    expect(screen.getByRole('checkbox', { name: 'Allow search_docs' })).toBeDisabled();
    expect(screen.getByText('Used by Researcher. Turn it off in those agents first.')).toBeInTheDocument();
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.click(screen.getByRole('checkbox', { name: 'Allow get_doc' }));
    expect(bodyOf(calls, `PUT /api/mcps/${SERVER_ID}`)).toEqual({ name: 'Acme Docs', allowedTools: ['search_docs'] });
    await waitFor(() => expect(screen.getByRole('checkbox', { name: 'Allow get_doc' })).not.toBeChecked());

    await userEvent.click(screen.getByRole('button', { name: 'Check now' }));
    expect(await screen.findByText(/lists a new tool: list_spaces/)).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Remove' }));
    const dialog = screen.getByRole('dialog', { name: 'Remove Acme Docs?' });
    expect(within(dialog).getByText(/Researcher uses its tools/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Remove' })).toBeDisabled();
  });

  it('a server’s page shows a logo fetched from an address, says when it is not an image, and goes back', async () => {
    const BRAND = 'data:image/svg+xml;base64,PHN2Zy8+';
    const chosen = { ...SERVER, icon: BRAND, logo: { address: 'https://acme.test/logo.svg', fetchedAt: '2026-11-27T16:00:00Z' } };
    const calls = stubApi({
      'GET /api/mcps': () => ({ body: [SERVER] }),
      [`GET /api/mcps/${SERVER_ID}`]: () => ({ body: SERVER }),
      [`PUT /api/mcps/${SERVER_ID}/logo`]: (init) => (String(init?.body).includes('page')
        ? { status: 422, body: { code: 'mcp.logo-not-image', message: 'Not an image.' } }
        : { body: chosen }),
      [`DELETE /api/mcps/${SERVER_ID}/logo`]: () => ({ body: SERVER }),
    });
    const { container } = open(`#/settings/mcps/${SERVER_ID}`);

    const field = await screen.findByLabelText('Logo address');
    expect(screen.getByText(/ConXist found this logo/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Use' })).toBeDisabled();
    await userEvent.type(field, 'https://acme.test/page');
    await userEvent.click(screen.getByRole('button', { name: 'Use' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('That address isn’t an image.');
    expect(field).toHaveAttribute('aria-invalid', 'true');

    await userEvent.clear(field);
    await userEvent.type(field, 'https://acme.test/logo.svg');
    await userEvent.click(screen.getByRole('button', { name: 'Use' }));
    expect(await screen.findByText(/Saved\. Fetched once on/)).toBeInTheDocument();
    expect(bodyOf(calls, `PUT /api/mcps/${SERVER_ID}/logo`)).toEqual({ address: 'https://acme.test/logo.svg' });
    expect(screen.getByText(/Your logo\. ConXist keeps its own copy/)).toBeInTheDocument();
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: 'Use the logo ConXist found' }));
    expect(await screen.findByText(/ConXist found this logo/)).toBeInTheDocument();
    expect(screen.getByLabelText('Logo address')).toHaveValue('');
  });
});
