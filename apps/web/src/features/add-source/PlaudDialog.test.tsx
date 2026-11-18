import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { stubApi } from '../../testing';
import { PlaudDialog } from './PlaudDialog';

const KEY = { keyId: 'cx-0123456789abcdef', pem: '-----BEGIN PUBLIC KEY-----\nMIIB\n-----END PUBLIC KEY-----\n' };

function plaudRoutes(overrides: Record<string, Parameters<typeof stubApi>[0][string]> = {}) {
  return stubApi({
    'POST /api/recorders/plaud/keys': () => ({ status: 201, body: KEY }),
    'POST /api/recorders/plaud/test': () => ({ body: { teamName: 'Acme Engineering' } }),
    'POST /api/sources': () => ({ status: 201, body: { id: 'rec-1' } }),
    'POST /api/sources/rec-1/plaud-team': () => ({ status: 201, body: {} }),
    'POST /api/sources/rec-1/sync': () => ({ body: { imported: 3, alreadyImported: 0, waitingForTranscript: 1 } }),
    ...overrides,
  });
}

async function openTeamAndTest() {
  await userEvent.click(screen.getByRole('button', { name: /Connect a Plaud Team/ }));
  expect(await screen.findByText(/BEGIN PUBLIC KEY/)).toBeInTheDocument();
  await userEvent.type(screen.getByLabelText('Client ID'), '  client_acme ');
  await userEvent.click(screen.getByRole('button', { name: 'Test' }));
}

describe('PlaudDialog', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('offers both ways in: a personal sign-in and a Plaud Team', () => {
    stubApi({});
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    expect(screen.getByRole('button', { name: /Sign in with Plaud/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /Connect a Plaud Team/ })).toBeEnabled();
  });

  it('connects a Plaud Team with a ConXist key and imports its meetings', async () => {
    const calls = plaudRoutes();
    const onCaptured = vi.fn();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={onCaptured} />);

    await openTeamAndTest();
    expect(await screen.findByText('Acme Engineering')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('radio', { name: /Last 7 days/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /summaries/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByText('Not included')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('status')).toHaveTextContent('3 meetings are waiting for review');
    expect(screen.getByText(/1 recording is still being transcribed/)).toBeInTheDocument();
    const body = (key: string) => JSON.parse(String(calls.find((call) => call.key === key)?.init?.body));
    expect(body('POST /api/recorders/plaud/test')).toEqual({ clientId: 'client_acme', keyId: KEY.keyId });
    expect(body('POST /api/sources')).toEqual({ provider: 'PLAUD', name: 'Team meetings', accountLabel: 'Acme Engineering' });
    expect(body('POST /api/sources/rec-1/plaud-team')).toEqual({
      clientId: 'client_acme', keyId: KEY.keyId, startFrom: 'LAST_7_DAYS', includeSummaries: false,
    });
    expect(onCaptured).toHaveBeenCalled();
  });

  it('explains when Plaud does not accept the Client ID', async () => {
    plaudRoutes({ 'POST /api/recorders/plaud/test': () => ({ status: 422, body: { code: 'plaud.not-accepted' } }) });
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    await openTeamAndTest();

    expect(await screen.findByRole('alert')).toHaveTextContent('Plaud did not accept this Client ID and key.');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Press Test to check the Client ID first.');
  });

  it('retries only the import when the import failed after the source was connected', async () => {
    let syncs = 0;
    const calls = plaudRoutes({
      'POST /api/sources/rec-1/sync': () => (++syncs === 1
        ? { status: 503, body: { code: 'plaud.unavailable' } }
        : { body: { imported: 2, alreadyImported: 0, waitingForTranscript: 0 } }),
    });
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    await openTeamAndTest();
    await screen.findByText('Acme Engineering');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Plaud is not answering right now.');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('status')).toHaveTextContent('2 meetings are waiting for review');
    expect(calls.filter((call) => call.key === 'POST /api/sources')).toHaveLength(1);
    expect(calls.filter((call) => call.key === 'POST /api/sources/rec-1/plaud-team')).toHaveLength(1);
  });

  it('keeps Test off until a Client ID is pasted', async () => {
    plaudRoutes();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: /Connect a Plaud Team/ }));
    await screen.findByText(/BEGIN PUBLIC KEY/);

    expect(screen.getByRole('button', { name: 'Test' })).toBeDisabled();
  });

  it('has no accessibility violations on the two doors or the connect step', async () => {
    plaudRoutes();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    expect(await accessibilityViolations(document.body)).toEqual([]);

    await userEvent.click(screen.getByRole('button', { name: /Connect a Plaud Team/ }));
    await screen.findByText(/BEGIN PUBLIC KEY/);
    expect(await accessibilityViolations(document.body)).toEqual([]);
  });
});

describe('PlaudDialog key', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('offers a retry when ConXist could not make a key', async () => {
    let attempts = 0;
    stubApi({
      'POST /api/recorders/plaud/keys': () => (++attempts === 1
        ? { status: 500, body: { code: 'request.failed' } }
        : { status: 201, body: KEY }),
    });
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: /Connect a Plaud Team/ }));

    await userEvent.click(await screen.findByRole('button', { name: 'Retry' }));

    expect(await screen.findByText(/BEGIN PUBLIC KEY/)).toBeInTheDocument();
    expect(attempts).toBe(2);
  });
});

describe('PlaudDialog personal sign-in', () => {
  afterEach(() => vi.unstubAllGlobals());

  const START = { signInId: 'si-1', authorizationUrl: 'https://plaud.test/authorize?state=abc' };

  function signInRoutes(status: string, overrides: Record<string, Parameters<typeof stubApi>[0][string]> = {}) {
    return stubApi({
      'POST /api/recorders/plaud/sign-ins': () => ({ status: 201, body: START }),
      'GET /api/recorders/plaud/sign-ins/si-1': () => ({ body: { id: 'si-1', status, accountLabel: status === 'SIGNED_IN' ? 'alex@acme.test' : null } }),
      'POST /api/sources': () => ({ status: 201, body: { id: 'rec-1' } }),
      'POST /api/sources/rec-1/plaud-account': () => ({ status: 201, body: {} }),
      'POST /api/sources/rec-1/sync': () => ({ body: { imported: 3, alreadyImported: 0, waitingForTranscript: 0 } }),
      ...overrides,
    });
  }

  function stubPopup(opened = true) {
    const popup = { opener: {} as unknown, location: { href: '' }, close: vi.fn() };
    const open = vi.fn(() => (opened ? popup : null));
    vi.stubGlobal('open', open);
    return { popup, open };
  }

  it("signs in on Plaud's page in a new window, then connects the account and imports its meetings", async () => {
    const calls = signInRoutes('SIGNED_IN');
    const { popup, open } = stubPopup();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: /Sign in with Plaud/ }));
    expect(screen.getByText('Sign in', { selector: '.stepper *' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(open).toHaveBeenCalledWith('about:blank', 'plaud-sign-in', expect.any(String));
    expect(popup.opener).toBeNull();
    expect(await screen.findByText(/Finish signing in in the Plaud window/)).toBeInTheDocument();
    expect(popup.location.href).toBe(START.authorizationUrl);

    expect(await screen.findByText('alex@acme.test', {}, { timeout: 4000 })).toBeInTheDocument();
    expect(screen.getByText('Signed in')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByLabelText('Source name')).toHaveValue('My meetings');
    expect(screen.getByText('Plaud account')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('status')).toHaveTextContent('3 meetings are waiting for review');
    const body = (key: string) => JSON.parse(String(calls.find((call) => call.key === key)?.init?.body));
    expect(body('POST /api/sources')).toEqual({ provider: 'PLAUD', name: 'My meetings', accountLabel: 'alex@acme.test' });
    expect(body('POST /api/sources/rec-1/plaud-account')).toEqual({ signInId: 'si-1', startFrom: 'LAST_30_DAYS', includeSummaries: true });
  });

  it('says so when the person cancels on Plaud, and lets them try again', async () => {
    signInRoutes('CANCELLED');
    stubPopup();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: /Sign in with Plaud/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert', {}, { timeout: 4000 })).toHaveTextContent('Plaud sign-in was cancelled. Nothing was connected.');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByRole('alert')).toHaveTextContent('Sign in with Plaud first.');
  });

  it('offers a plain link when the browser blocks the Plaud window', async () => {
    signInRoutes('WAITING');
    stubPopup(false);
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: /Sign in with Plaud/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText(/Your browser blocked the Plaud window/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open Plaud sign-in' })).toHaveAttribute('href', START.authorizationUrl);
    expect(await accessibilityViolations(document.body)).toEqual([]);
  });
});
