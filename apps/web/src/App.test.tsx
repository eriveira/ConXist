import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from './a11y';
import { App } from './App';
import { PROVIDERS, stubApi } from './testing';

const ACME = { id: 'w1', name: 'Acme Product', description: '', sourceCount: 2, createdAt: '2026-11-18T12:00:00Z' };
const PAYMENTS = { id: 'w2', name: 'Payments Platform', description: '', sourceCount: 0, createdAt: '2026-11-19T12:00:00Z' };

function api(workspaces: object[], routes: Parameters<typeof stubApi>[0] = {}) {
  return stubApi({
    'GET /api/workspaces': () => ({ body: workspaces }),
    'GET /api/providers': () => ({ body: PROVIDERS }),
    'GET /api/sources': () => ({ body: [] }),
    'GET /api/recorders/plaud/connections': () => ({ body: [] }),
    ...routes,
  });
}

const workspaceOf = (calls: ReturnType<typeof stubApi>, key: string) =>
  new Headers(calls.filter((call) => call.key === key).at(-1)?.init?.headers).get('X-Workspace-Id');

describe('App', () => {
  beforeEach(() => {
    window.localStorage.clear();
    window.location.hash = '';
  });
  afterEach(() => vi.unstubAllGlobals());

  it('asks a first-time person to create a workspace, then opens Sources in it', async () => {
    const calls = api([], { 'POST /api/workspaces': () => ({ status: 201, body: { ...PAYMENTS, sourceCount: 0 } }) });
    render(<App />);

    await userEvent.type(await screen.findByLabelText('Workspace name'), 'Payments Platform');
    await userEvent.click(screen.getByRole('button', { name: 'Create' }));

    expect(await screen.findByRole('heading', { name: 'Sources', level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('Payments Platform is ready.');
    expect(screen.getByRole('button', { name: /Workspace: Payments Platform/ })).toBeInTheDocument();
    expect(workspaceOf(calls, 'GET /api/sources')).toBe('w2');
    expect(window.localStorage.getItem('conxist.workspace')).toBe('w2');
  });

  it('shows the menu in order, with the current page marked', async () => {
    api([ACME]);
    const { container } = render(<App />);
    await screen.findByRole('heading', { name: 'Sources', level: 1 });

    const menu = screen.getByRole('navigation', { name: 'Main' });
    expect(within(menu).getAllByRole('link').map((link) => link.textContent)).toEqual(['Home', 'Flow', 'Agents', 'Skills', 'Sources', 'Knowledge', 'Memories']);
    expect(within(menu).getByRole('link', { name: 'Sources' })).toHaveAttribute('aria-current', 'page');

    await userEvent.click(within(menu).getByRole('link', { name: 'Flow' }));
    expect(await screen.findByRole('heading', { name: 'Flow is coming soon' })).toBeInTheDocument();
    await userEvent.click(within(menu).getByRole('link', { name: 'Home' }));
    expect(screen.getByRole('heading', { name: 'Home' })).toBeInTheDocument();
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('opens Skills and Memories from the menu, and keeps Skills marked on its page', async () => {
    api([ACME], { 'GET /api/skills': () => ({ body: [] }) });
    render(<App />);
    await screen.findByRole('heading', { name: 'Sources', level: 1 });
    const menu = screen.getByRole('navigation', { name: 'Main' });

    await userEvent.click(within(menu).getByRole('link', { name: 'Memories' }));
    expect(await screen.findByRole('heading', { name: 'Memories is coming soon' })).toBeInTheDocument();

    await userEvent.click(within(menu).getByRole('link', { name: 'Skills' }));
    expect(await screen.findByRole('heading', { name: 'Skills', level: 1 })).toBeInTheDocument();
    expect(within(menu).getByRole('link', { name: 'Skills' })).toHaveAttribute('aria-current', 'page');
    expect(within(menu).getByRole('link', { name: 'Agents' })).not.toHaveAttribute('aria-current');
  });

  it('switches workspaces from the header, and every request then names the new one', async () => {
    const calls = api([ACME, PAYMENTS]);
    render(<App />);
    await screen.findByRole('heading', { name: 'Sources', level: 1 });
    expect(workspaceOf(calls, 'GET /api/sources')).toBe('w1');

    await userEvent.click(screen.getByRole('button', { name: /Workspace: Acme Product/ }));
    const list = screen.getByRole('group', { name: 'Your workspaces' });
    expect(within(list).getByRole('button', { name: /Acme Product/ })).toHaveAttribute('aria-current', 'true');
    expect(list).toHaveTextContent('2 sources');
    expect(list).toHaveTextContent('No sources yet');

    await userEvent.click(within(list).getByRole('button', { name: /Payments Platform/ }));
    expect(screen.getByRole('button', { name: /Workspace: Payments Platform/ })).toHaveFocus();
    await screen.findByRole('heading', { name: 'Sources', level: 1 });
    expect(workspaceOf(calls, 'GET /api/sources')).toBe('w2');
  });

  it('opens the New workspace pop-up from the switcher, and Cancel closes it', async () => {
    api([ACME]);
    render(<App />);
    await screen.findByRole('heading', { name: 'Sources', level: 1 });

    await userEvent.click(screen.getByRole('button', { name: /Workspace: Acme Product/ }));
    await userEvent.click(screen.getByRole('button', { name: 'New workspace' }));

    const dialog = screen.getByRole('dialog', { name: 'New workspace' });
    expect(within(dialog).getByLabelText('Workspace name')).toHaveFocus();
    expect(dialog).toHaveTextContent('0 / 500');
    await userEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('opens Settings from the workspace menu, and a renamed workspace shows its new name in the header', async () => {
    const calls = api([ACME], {
      'PUT /api/workspaces/w1': (init) => ({ body: { ...ACME, ...JSON.parse(String(init?.body)) } }),
    });
    render(<App />);
    await screen.findByRole('heading', { name: 'Sources', level: 1 });

    await userEvent.click(screen.getByRole('button', { name: /Workspace: Acme Product/ }));
    const menu = screen.getByRole('group', { name: 'Your workspaces' });
    expect(menu).toHaveTextContent('2 sources');
    await userEvent.click(within(menu).getByRole('link', { name: 'Settings' }));

    expect(await screen.findByRole('heading', { name: 'Settings', level: 1 })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'General' })).toHaveAttribute('aria-current', 'page');
    const save = screen.getByRole('button', { name: 'Save' });
    expect(save).toBeDisabled();
    const name = screen.getByLabelText('Workspace name');
    await userEvent.clear(name);
    await userEvent.type(name, 'Acme Checkout ');
    await userEvent.click(save);

    expect(await screen.findByRole('button', { name: /Workspace: Acme Checkout/ })).toBeInTheDocument();
    expect(screen.getByText('Saved')).toBeInTheDocument();
    expect(JSON.parse(String(calls.find((call) => call.key === 'PUT /api/workspaces/w1')?.init?.body)))
      .toEqual({ name: 'Acme Checkout', description: '' });
  });

  it('keeps theme and language in the account menu, and says sign-in is coming', async () => {
    api([ACME]);
    render(<App />);
    await screen.findByRole('heading', { name: 'Sources', level: 1 });

    await userEvent.click(screen.getByRole('button', { name: /Account and settings/ }));
    expect(screen.getByText(/Sign-in is coming soon/)).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Language'), 'es');
    expect(await screen.findByRole('link', { name: 'Fuentes' })).toHaveAttribute('aria-current', 'page');
  });

  it('opens Ask ConXist, which says the agent is coming soon', async () => {
    api([ACME]);
    render(<App />);
    await screen.findByRole('heading', { name: 'Sources', level: 1 });

    await userEvent.click(screen.getByRole('button', { name: 'Ask ConXist' }));
    expect(screen.getByRole('region', { name: 'Ask ConXist' })).toHaveTextContent(/Coming soon/);
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('region', { name: 'Ask ConXist' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ask ConXist' })).toHaveFocus();
  });

  it('offers Retry when ConXist cannot be reached', async () => {
    stubApi({ 'GET /api/workspaces': () => ({ status: 500, body: {} }) });
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'We could not reach ConXist.' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});
