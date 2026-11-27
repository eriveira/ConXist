import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { GmailSignIn, Integration } from '../../domain/integrations';
import type { McpTool } from '../../domain/mcps';
import { stubApi } from '../../testing';
import { SettingsPage } from '../settings/SettingsPage';

const WORKSPACE = { id: 'w1', name: 'Acme Product', description: '', sourceCount: 0, createdAt: '2026-11-18T12:00:00Z' };
const ID = '99999999-9999-9999-9999-999999999999';
const NAMES = ['search_emails', 'get_email', 'get_thread', 'list_labels'];
const tool = (name: string, allowed = false): McpTool => ({
  name, title: name, description: `About ${name}.`, readOnly: true, allowed, usedBy: [],
});
const SIGNING_IN: GmailSignIn = {
  id: 's1', status: 'SIGNING_IN', signInUrl: 'https://accounts.google.test/auth?x=1', account: null, labels: null, tools: [], error: null,
};
const READY: GmailSignIn = {
  ...SIGNING_IN, status: 'READY', signInUrl: null, account: 'al…@acme.test', labels: ['Customers', 'Personal', 'Product feedback'],
  tools: NAMES.map((name) => tool(name)),
};
const GMAIL: Integration = {
  id: ID, kind: 'GMAIL', name: 'Gmail', account: 'al…@acme.test', repositories: ['*'], tokenEnding: 'abcd', status: 'WORKS',
  tools: NAMES.map((name) => tool(name, true)), addedAt: '2026-11-27T10:00:00Z', checkedAt: '2026-11-27T10:00:00Z',
};

function open(hash: string) {
  window.location.hash = hash;
  return render(<SettingsPage workspace={WORKSPACE} onSaved={vi.fn()} />);
}

const bodyOf = (calls: ReturnType<typeof stubApi>, key: string) =>
  JSON.parse(String(calls.filter((call) => call.key === key).at(-1)?.init?.body)) as Record<string, unknown>;

describe('Settings › Integrations › Gmail', () => {
  let popup: { location: { href: string }; close: ReturnType<typeof vi.fn>; opener: unknown };

  beforeEach(() => {
    popup = { location: { href: '' }, close: vi.fn(), opener: {} };
    vi.spyOn(window, 'open').mockReturnValue(popup as unknown as Window);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    window.location.hash = '';
  });

  it('signs in with Google, then agents read only the labels picked', async () => {
    let asked = 0;
    const calls = stubApi({
      'GET /api/integrations/kinds': () => ({ body: ['GITHUB', 'BITBUCKET', 'GMAIL'] }),
      'GET /api/integrations': () => ({ body: [] }),
      'POST /api/integrations/gmail/sign-in': () => ({ body: SIGNING_IN }),
      'GET /api/integrations/gmail/sign-in/s1': () => ({ body: ++asked < 2 ? SIGNING_IN : READY }),
      'POST /api/integrations/gmail': () => ({ status: 201, body: { ...GMAIL, repositories: ['Customers'] } }),
    });
    const { container } = open('#/settings/integrations');

    await userEvent.click(await screen.findByRole('button', { name: 'Add' }));
    await userEvent.click(screen.getByRole('button', { name: /Gmail/ }));
    const dialog = screen.getByRole('dialog', { name: 'Connect Gmail' });
    expect(within(dialog).getByText(/Every agent in Acme Product that you give Gmail tools/)).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sign in with Google' }));

    expect(window.open).toHaveBeenCalledWith('about:blank', 'google-sign-in', expect.stringContaining('popup'));
    expect(popup.opener).toBeNull();
    expect(await within(dialog).findByRole('heading', { name: 'Gmail is connected' }, { timeout: 5000 })).toBeInTheDocument();
    expect(popup.location.href).toBe(SIGNING_IN.signInUrl);
    expect(within(dialog).getByText('Signed in as al…@acme.test · read only')).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: 'Next' })).toBeDisabled();
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Customers' }));
    expect(await accessibilityViolations(container)).toEqual([]);
    await userEvent.click(within(dialog).getByRole('button', { name: 'Next' }));
    expect(within(dialog).getByRole('heading', { name: 'Choose Gmail’s tools' })).toBeInTheDocument();
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add' }));

    expect(await screen.findByText('Connected Gmail.')).toBeInTheDocument();
    expect(bodyOf(calls, 'POST /api/integrations/gmail')).toEqual({ signInId: 's1', labels: ['Customers'], allowedTools: NAMES });
  }, 10_000);

  it('stops waiting when the Google window is closed before the sign-in ends, and can cancel', async () => {
    stubApi({
      'GET /api/integrations/kinds': () => ({ body: ['GMAIL'] }),
      'GET /api/integrations': () => ({ body: [] }),
      'POST /api/integrations/gmail/sign-in': () => ({ body: SIGNING_IN }),
      'GET /api/integrations/gmail/sign-in/s1': () => ({ body: SIGNING_IN }),
    });
    open('#/settings/integrations');
    await userEvent.click(await screen.findByRole('button', { name: 'Add' }));
    await userEvent.click(screen.getByRole('button', { name: /Gmail/ }));
    const dialog = screen.getByRole('dialog', { name: 'Connect Gmail' });
    await userEvent.click(within(dialog).getByRole('button', { name: 'Sign in with Google' }));
    expect(await within(dialog).findByRole('button', { name: 'Cancel sign-in' })).toBeInTheDocument();

    (popup as unknown as { closed: boolean }).closed = true;
    expect(await within(dialog).findByRole('alert', {}, { timeout: 5000 }))
      .toHaveTextContent('The Google window was closed before you finished. Try again.');
    expect(within(dialog).getByRole('button', { name: 'Sign in with Google' })).toBeEnabled();
  }, 10_000);

  it('says so when Google allowed more than reading email', async () => {
    stubApi({
      'GET /api/integrations/kinds': () => ({ body: ['GITHUB', 'BITBUCKET', 'GMAIL'] }),
      'GET /api/integrations': () => ({ body: [] }),
      'POST /api/integrations/gmail/sign-in': () => ({ body: SIGNING_IN }),
      'GET /api/integrations/gmail/sign-in/s1': () => ({ body: { ...SIGNING_IN, status: 'FAILED', error: 'scope-refused' } }),
    });
    open('#/settings/integrations');

    await userEvent.click(await screen.findByRole('button', { name: 'Add' }));
    await userEvent.click(screen.getByRole('button', { name: /Gmail/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Sign in with Google' }));

    expect(await screen.findByRole('alert', {}, { timeout: 5000 })).toHaveTextContent('Google allowed more than reading email');
    expect(screen.getByRole('button', { name: 'Sign in with Google' })).toBeEnabled();
  });

  it('reads all email but Spam and Trash, and the labels can be changed', async () => {
    const calls = stubApi({
      'GET /api/integrations/kinds': () => ({ body: ['GITHUB', 'BITBUCKET', 'GMAIL'] }),
      [`GET /api/integrations/${ID}`]: () => ({ body: GMAIL }),
      'GET /api/integrations': () => ({ body: [GMAIL] }),
      [`POST /api/integrations/${ID}/reachable`]: () => ({ body: { account: 'al…@acme.test', repositories: ['Customers', 'Personal'], tools: [] } }),
      [`PUT /api/integrations/${ID}/repositories`]: () => ({ body: { ...GMAIL, repositories: ['Personal'] } }),
    });
    open(`#/settings/integrations/${ID}`);

    expect(await screen.findByText('Agents read every email but Spam and Trash.')).toBeInTheDocument();
    expect(screen.getByText(/al…@acme.test · read only/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Sign in again' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Change labels' }));
    const dialog = screen.getByRole('dialog', { name: 'Change labels' });
    expect(await within(dialog).findByRole('radio', { name: 'All my email' })).toBeChecked();
    await userEvent.click(within(dialog).getByRole('radio', { name: /Only emails with these labels/ }));
    expect(within(dialog).getByRole('button', { name: 'Save' })).toBeDisabled();
    await userEvent.click(within(dialog).getByRole('checkbox', { name: 'Personal' }));
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Labels saved.')).toBeInTheDocument();
    expect(bodyOf(calls, `PUT /api/integrations/${ID}/repositories`)).toEqual({ repositories: ['Personal'] });
  });
});
