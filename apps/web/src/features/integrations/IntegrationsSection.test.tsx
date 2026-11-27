import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { Integration } from '../../domain/integrations';
import type { McpTool } from '../../domain/mcps';
import { stubApi } from '../../testing';
import { SettingsPage } from '../settings/SettingsPage';

const WORKSPACE = { id: 'w1', name: 'Acme Product', description: '', sourceCount: 0, createdAt: '2026-11-18T12:00:00Z' };
const ID = '77777777-7777-7777-7777-777777777777';
const TOKEN = 'github_pat_11ABCDEFG0123456789_9kQ2';
const tool = (name: string, allowed = false, users: string[] = []): McpTool => ({
  name, title: name, description: `About ${name}.`, readOnly: true, allowed,
  usedBy: users.map((agentName) => ({ agentId: agentName, agentName, where: 'DRAFT', archived: false })),
});
const NAMES = ['search_code', 'get_file', 'list_issues', 'get_issue', 'get_pull_request'];
const GITHUB: Integration = {
  id: ID, kind: 'GITHUB', name: 'GitHub', account: 'alex-acme', repositories: ['acme/api', 'acme/web'],
  tokenEnding: '9kQ2', status: 'WORKS', tools: NAMES.map((name, index) => tool(name, index < 2, index === 0 ? ['Code reader'] : [])),
  addedAt: '2026-11-27T10:00:00Z', checkedAt: '2026-11-27T10:00:00Z',
};

function open(hash: string) {
  window.location.hash = hash;
  return render(<SettingsPage workspace={WORKSPACE} onSaved={vi.fn()} />);
}

const bodyOf = (calls: ReturnType<typeof stubApi>, key: string) =>
  JSON.parse(String(calls.filter((call) => call.key === key).at(-1)?.init?.body)) as Record<string, unknown>;

describe('Settings › Integrations', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.location.hash = '';
  });

  it('connects GitHub with a fine-grained token, shows what it reaches, and adds the ticked tools', async () => {
    const calls = stubApi({
      'GET /api/integrations': () => ({ body: [] }),
      'POST /api/integrations/github/check': () => ({
        body: { account: 'alex-acme', repositories: ['acme/api', 'acme/web'], tools: NAMES.map((name) => tool(name)) },
      }),
      'POST /api/integrations/github': (init) => {
        const allowed = (JSON.parse(String(init?.body)) as { allowedTools: string[] }).allowedTools;
        return { status: 201, body: { ...GITHUB, tools: NAMES.map((name) => tool(name, allowed.includes(name))) } };
      },
    });
    const { container } = open('#/settings/integrations');

    expect(await screen.findByRole('heading', { name: 'No integrations yet' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    const dialog = screen.getByRole('dialog', { name: 'Connect GitHub' });
    expect(within(dialog).getByRole('link', { name: 'Open GitHub’s page (new tab)' }))
      .toHaveAttribute('href', 'https://github.com/settings/personal-access-tokens/new');
    await userEvent.type(within(dialog).getByLabelText('Token'), 'ghp_classic123');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Connect' }));
    expect(within(dialog).getByRole('alert')).toHaveTextContent('Use a fine-grained token. It starts with github_pat_.');
    expect(calls.map((call) => call.key)).not.toContain('POST /api/integrations/github/check');

    await userEvent.clear(within(dialog).getByLabelText('Token'));
    await userEvent.type(within(dialog).getByLabelText('Token'), TOKEN);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Connect' }));
    expect(await within(dialog).findByRole('heading', { name: 'GitHub is connected' })).toBeInTheDocument();
    expect(within(dialog).getByText(/alex-acme’s token reaches 2 repositories: acme\/api, acme\/web\./)).toBeInTheDocument();
    expect(within(dialog).getByRole('checkbox', { name: 'Allow get_pull_request' })).toBeChecked();
    expect(await accessibilityViolations(container)).toEqual([]);

    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Allow get_pull_request' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add' }));
    expect(await screen.findByText('Connected GitHub.')).toBeInTheDocument();
    expect(bodyOf(calls, 'POST /api/integrations/github')).toEqual({
      token: TOKEN, allowedTools: ['search_code', 'get_file', 'list_issues', 'get_issue'],
    });
    expect(screen.getByText(/alex-acme · 2 repositories · token ending 9kQ2 · 4 of 5 tools allowed/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Manage GitHub' })).toHaveFocus();
    expect(document.body).not.toHaveTextContent(TOKEN);
  });

  it('says why GitHub refused a token', async () => {
    stubApi({
      'GET /api/integrations': () => ({ body: [] }),
      'POST /api/integrations/github/check': () => ({ status: 422, body: { code: 'integration.token-refused', message: 'x' } }),
    });
    open('#/settings/integrations');
    await userEvent.click(await screen.findByRole('button', { name: 'Add' }));
    const dialog = screen.getByRole('dialog', { name: 'Connect GitHub' });
    await userEvent.type(within(dialog).getByLabelText('Token'), TOKEN);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Connect' }));
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('GitHub refused this token.');
    expect(within(dialog).getByLabelText('Token')).toHaveAttribute('aria-invalid', 'true');
  });

  it('manages GitHub: ticks save at once, a used tool stays, and Remove names the agents that use it', async () => {
    const calls = stubApi({
      [`GET /api/integrations/${ID}`]: () => ({ body: GITHUB }),
      'GET /api/integrations': () => ({ body: [GITHUB] }),
      [`PUT /api/integrations/${ID}`]: (init) => {
        const allowed = (JSON.parse(String(init?.body)) as { allowedTools: string[] }).allowedTools;
        return { body: { ...GITHUB, tools: GITHUB.tools.map((each) => ({ ...each, allowed: allowed.includes(each.name) })) } };
      },
      [`POST /api/integrations/${ID}/check`]: () => ({ body: { works: true, error: null, integration: GITHUB } }),
    });
    const { container } = open(`#/settings/integrations/${ID}`);

    expect(await screen.findByRole('heading', { name: /GitHub/ })).toBeInTheDocument();
    expect(screen.getByText('acme/web')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Allow search_code' })).toBeDisabled();
    expect(screen.getByText('Used by Code reader.', { exact: false })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Allow list_issues' }));
    expect(bodyOf(calls, `PUT /api/integrations/${ID}`)).toEqual({ allowedTools: ['search_code', 'get_file', 'list_issues'] });
    expect(await screen.findByRole('checkbox', { name: 'Allow list_issues' })).toBeChecked();

    await userEvent.click(screen.getByRole('button', { name: 'Check now' }));
    expect(await screen.findByText('GitHub answered. The token reaches 2 repositories.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Remove' }));
    const confirm = screen.getByRole('dialog', { name: 'Remove GitHub?' });
    expect(confirm).toHaveTextContent('An agent uses its tools: Code reader.');
    expect(within(confirm).getByRole('button', { name: 'Remove' })).toBeDisabled();
    expect(await accessibilityViolations(container)).toEqual([]);
  });
});
