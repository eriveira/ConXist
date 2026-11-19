import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { stubApi } from '../../testing';
import { PlaudDialog } from './PlaudDialog';

describe('PlaudDialog', () => {
  afterEach(() => vi.unstubAllGlobals());

  const START = { signInId: 'si-1', authorizationUrl: 'https://plaud.test/authorize?state=abc' };
  /** 30 recordings, three a day at noon UTC from 2026-10-01 back, so there are two pages of 25. */
  const RECORDINGS = Array.from({ length: 30 }, (_, index) => ({
    id: `of_${index + 1}`,
    title: `Meeting ${String(index + 1).padStart(2, '0')}`,
    recordedAt: new Date(Date.UTC(2026, 9, 1 - Math.floor(index / 3), 12 - (index % 3))).toISOString(),
    lengthSeconds: 1800,
    inConxist: null as 'HELD' | 'DELETED' | null,
  }));

  function signInRoutes(status: string, overrides: Record<string, Parameters<typeof stubApi>[0][string]> = {}) {
    return stubApi({
      'POST /api/recorders/plaud/sign-ins': () => ({ status: 201, body: START }),
      'GET /api/recorders/plaud/sign-ins/si-1': () => ({ body: { id: 'si-1', status, accountLabel: status === 'SIGNED_IN' ? 'alex@acme.test' : null } }),
      'GET /api/recorders/plaud/sign-ins/si-1/recordings': () => ({ body: { recordings: RECORDINGS, capped: false, max: 500 } }),
      'POST /api/sources': () => ({ status: 201, body: { id: 'rec-1' } }),
      'POST /api/sources/rec-1/plaud-account': () => ({ status: 201, body: {} }),
      'POST /api/sources/rec-1/sync': () => ({ body: { imported: 3, alreadyImported: 0, waitingForTranscript: 0, queued: 0 } }),
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
    await userEvent.click(await screen.findByRole('checkbox', { name: /Meeting 02/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByLabelText('Source name')).toHaveValue('My meetings');
    expect(screen.getByText('1 recording')).toBeInTheDocument();
    expect(screen.getByText('Imported automatically')).toBeInTheDocument();
    expect(screen.getByText('Plaud account')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('status')).toHaveTextContent('3 meetings are waiting for review');
    expect(screen.getByText('New recordings will arrive on their own.')).toBeInTheDocument();
    const body = (key: string) => JSON.parse(String(calls.find((call) => call.key === key)?.init?.body));
    expect(body('POST /api/sources')).toEqual({ provider: 'PLAUD', name: 'My meetings', accountLabel: 'alex@acme.test' });
    expect(body('POST /api/sources/rec-1/plaud-account')).toEqual({ signInId: 'si-1', recordingIds: ['of_2'], importNew: true, includeSummaries: true });
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
        : { body: { imported: 2, alreadyImported: 0, waitingForTranscript: 0, queued: 0 } }),
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

  async function toRecordings() {
    signInRoutes('SIGNED_IN');
    stubPopup();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByText('alex@acme.test', {}, { timeout: 4000 });
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await screen.findByRole('checkbox', { name: /Meeting 01/ });
  }

  it('shows the newest 25 recordings grouped by day, with a second page', async () => {
    await toRecordings();

    expect(screen.getAllByRole('checkbox', { name: /^Meeting/ })).toHaveLength(25);
    expect(screen.getAllByText('3 recordings')).not.toHaveLength(0);
    expect(screen.getByText('1–25 of 30')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(screen.getAllByRole('checkbox', { name: /^Meeting/ })).toHaveLength(5);
    expect(screen.getByRole('checkbox', { name: /Meeting 30/ })).toBeInTheDocument();
    expect(await accessibilityViolations(document.body)).toEqual([]);
  });

  it('selects every matching recording, on every page, with Select all', async () => {
    await toRecordings();

    await userEvent.click(screen.getByRole('checkbox', { name: 'Select all 30' }));
    expect(screen.getByText('30 selected')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(screen.getByRole('checkbox', { name: /Meeting 30/ })).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(screen.getByText('None selected')).toBeInTheDocument();
  });

  it('shows only the selected recordings with Selected only', async () => {
    await toRecordings();

    expect(screen.getByRole('button', { name: 'Selected only (0)' })).toBeDisabled();
    await userEvent.click(screen.getByRole('checkbox', { name: /Meeting 02/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /Meeting 05/ }));
    const only = screen.getByRole('button', { name: 'Selected only (2)' });
    await userEvent.click(only);
    expect(only).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getAllByRole('checkbox', { name: /^Meeting/ })).toHaveLength(2);
    expect(screen.getByText('1–2 of 2')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Clear' }));
    expect(screen.getByRole('button', { name: 'Selected only (0)' })).toHaveAttribute('aria-pressed', 'false');
    expect(screen.getAllByRole('checkbox', { name: /^Meeting/ })).toHaveLength(25);
  });

  it('tags recordings already in ConXist, which cannot be picked again, and ones deleted there, which can', async () => {
    const tagged = RECORDINGS.map((recording, index) => ({
      ...recording, inConxist: index === 0 ? 'HELD' as const : index === 1 ? 'DELETED' as const : null,
    }));
    signInRoutes('SIGNED_IN', {
      'GET /api/recorders/plaud/sign-ins/si-1/recordings': () => ({ body: { recordings: tagged, capped: false, max: 500 } }),
    });
    stubPopup();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByText('alex@acme.test', {}, { timeout: 4000 });
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    const held = await screen.findByRole('checkbox', { name: /Meeting 01 Already in ConXist/ });
    expect(held).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: /Meeting 02 Deleted in ConXist/ })).toBeEnabled();
    expect(screen.getByRole('checkbox', { name: /^Select all 2: / })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Select all 29' }));
    expect(screen.getByText('29 selected')).toBeInTheDocument();
    expect(held).not.toBeChecked();
    expect(await accessibilityViolations(document.body)).toEqual([]);
  });

  it("selects a whole day with the day's checkbox", async () => {
    await toRecordings();

    const days = screen.getAllByRole('checkbox', { name: /^Select all 3: / });
    await userEvent.click(days[0]);
    expect(screen.getByText('3 selected')).toBeInTheDocument();
    for (const name of [/Meeting 01/, /Meeting 02/, /Meeting 03/]) expect(screen.getByRole('checkbox', { name })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /Meeting 04/ })).not.toBeChecked();
  });

  it('searches by name and sorts A to Z without day groups, keeping picks', async () => {
    await toRecordings();

    await userEvent.click(screen.getByRole('checkbox', { name: /Meeting 01/ }));
    await userEvent.type(screen.getByLabelText('Search'), 'meeting 1');
    // Every word must match: Meeting 01, 10 to 19 and 21.
    expect(screen.getAllByRole('checkbox', { name: /^Meeting/ })).toHaveLength(12);
    await userEvent.selectOptions(screen.getByLabelText('Sort'), 'NAME_ZA');
    const list = screen.getByRole('list', { name: '1–12 of 12' });
    expect(within(list).getAllByRole('checkbox')[0]).toHaveAccessibleName(/Meeting 21/);
    expect(within(list).queryByRole('checkbox', { name: /^Select all 3: / })).not.toBeInTheDocument();
    expect(screen.getByText('1 selected')).toBeInTheDocument();

    await userEvent.clear(screen.getByLabelText('Search'));
    await userEvent.type(screen.getByLabelText('Search'), 'standup');
    expect(screen.getByText('No recordings match')).toBeInTheDocument();
  });

  it('needs a pick or new recordings before Next', async () => {
    await toRecordings();

    await userEvent.click(screen.getByRole('checkbox', { name: /Also import new recordings/ }));
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Next' })).toHaveAccessibleDescription('Pick a recording, or turn on new recordings.');
    await userEvent.click(screen.getByRole('checkbox', { name: /Meeting 05/ }));
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled();
    expect(screen.getByText('1 recording to import now')).toBeInTheDocument();
  });

  it('says how many recordings are picked next to Next, and Change on Review goes back to them', async () => {
    await toRecordings();

    expect(screen.getByText('Only new recordings')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: /Meeting 02/ }));
    await userEvent.click(screen.getByRole('checkbox', { name: /Meeting 03/ }));
    expect(screen.getByText('2 recordings to import now')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Change' }));
    expect(screen.getByRole('checkbox', { name: /Meeting 02/ })).toBeChecked();
    expect(screen.getByText('2 recordings to import now')).toBeInTheDocument();
  });

  it('says when the From date is after the To date, without asking Plaud', async () => {
    await toRecordings();

    await userEvent.type(screen.getByLabelText('From'), '2026-10-02');
    await userEvent.type(screen.getByLabelText('To'), '2026-10-01');
    expect(screen.getByRole('alert')).toHaveTextContent('The From date is after the To date.');
    expect(screen.getByLabelText('To')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByRole('button', { name: 'Retry' })).not.toBeInTheDocument();
  });

  it('moves focus to the list when the page changes', async () => {
    await toRecordings();

    await userEvent.click(screen.getByRole('button', { name: 'Next page' }));
    expect(screen.getByRole('list', { name: '26–30 of 30' })).toHaveFocus();
  });

  it('says so and offers Retry when Plaud does not answer', async () => {
    let lists = 0;
    signInRoutes('SIGNED_IN', {
      'GET /api/recorders/plaud/sign-ins/si-1/recordings': () => (++lists === 1
        ? { status: 503, body: { code: 'plaud.unavailable' } }
        : { body: { recordings: RECORDINGS, capped: true, max: 500 } }),
    });
    stubPopup();
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByText('alex@acme.test', {}, { timeout: 4000 });
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Plaud is not answering right now.');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText(/Showing your newest 500 recordings/)).toBeInTheDocument();
  });
});
