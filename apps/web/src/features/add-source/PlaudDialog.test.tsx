import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { stubApi } from '../../testing';
import { PlaudDialog } from './PlaudDialog';

describe('PlaudDialog', () => {
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
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert', {}, { timeout: 4000 })).toHaveTextContent('Plaud sign-in was cancelled. Nothing was connected.');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
  });

  it('offers a plain link when the browser blocks the Plaud window', async () => {
    signInRoutes('WAITING');
    stubPopup(false);
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText(/Your browser blocked the Plaud window/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Open Plaud sign-in/ })).toHaveAttribute('href', START.authorizationUrl);
    expect(await accessibilityViolations(document.body)).toEqual([]);
  });

  it('stops waiting and says so when the sign-in has expired on ConXist', async () => {
    signInRoutes('WAITING', {
      'GET /api/recorders/plaud/sign-ins/si-1': () => ({ status: 404, body: { code: 'plaud.sign-in-not-found' } }),
    });
    stubPopup();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByRole('alert', {}, { timeout: 4000 })).toHaveTextContent('This Plaud sign-in has expired.');
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeEnabled();
  });

  async function signInAndContinue() {
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByText('alex@acme.test', {}, { timeout: 4000 });
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
  }

  it('opens on Sign in with Plaud, with Next off until the person has signed in', async () => {
    signInRoutes('WAITING');
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    expect(screen.getByRole('heading', { name: 'Sign in with Plaud' })).toBeInTheDocument();
    expect(screen.queryByText(/Plaud Team/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    expect(await accessibilityViolations(document.body)).toEqual([]);
  });

  it('retries only the import when the import failed after the source was connected', async () => {
    let syncs = 0;
    const calls = signInRoutes('SIGNED_IN', {
      'POST /api/sources/rec-1/sync': () => (++syncs === 1
        ? { status: 503, body: { code: 'plaud.unavailable' } }
        : { body: { imported: 2, alreadyImported: 0, waitingForTranscript: 0 } }),
    });
    stubPopup();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    await signInAndContinue();
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Plaud is not answering right now.');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('status')).toHaveTextContent('2 meetings are waiting for review');
    expect(calls.filter((call) => call.key === 'POST /api/sources')).toHaveLength(1);
    expect(calls.filter((call) => call.key === 'POST /api/sources/rec-1/plaud-account')).toHaveLength(1);
  });
});
