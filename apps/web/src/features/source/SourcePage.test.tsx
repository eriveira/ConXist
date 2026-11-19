import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { routeOf } from '../../navigation';
import { PROVIDERS, stubApi } from '../../testing';
import { SourcePage } from './SourcePage';

const PLAUD = { id: 'p1', category: 'CAPTURE', provider: 'PLAUD', name: 'Team meetings', accountLabel: 'alex@acme.test', addedAt: '2026-11-18T10:00:00Z' };
const UPLOAD = { id: 's1', category: 'CAPTURE', provider: 'UPLOAD', name: 'Product specs', accountLabel: '', addedAt: '2026-11-17T10:00:00Z' };
const CONNECTION = {
  sourceId: 'p1', kind: 'ACCOUNT', label: 'alex@acme.test', startFrom: 'NEW_ONLY', includeSummaries: true,
  status: 'ACTIVE', lastCheckedAt: new Date(Date.now() - 2 * 60_000).toISOString(),
};
const today = new Date();
today.setHours(10, 0, 0, 0);
const MEETINGS = [
  { id: 'd1', sourceId: 'p1', title: 'Sprint planning', mediaType: 'text/markdown', sizeBytes: 10, origin: 'RECORDING', textLength: 10,
    capturedAt: today.toISOString(), recordedAt: today.toISOString(), lengthSeconds: 1800, speakerCount: 2, editedAt: null },
  { id: 'd2', sourceId: 'p1', title: 'Customer call: Contoso', mediaType: 'text/markdown', sizeBytes: 10, origin: 'RECORDING', textLength: 10,
    capturedAt: today.toISOString(), recordedAt: '2026-10-02T15:00:00Z', lengthSeconds: null, speakerCount: null, editedAt: null },
];
const MEETING = {
  id: 'd1', sourceId: 'p1', title: 'Sprint planning', mediaType: 'text/markdown', origin: 'RECORDING', capturedAt: today.toISOString(), editedAt: null,
  text: '# Sprint planning',
  meeting: {
    recordedAt: today.toISOString(), lengthSeconds: 1800, language: 'en', summaries: ['Ship sign-in first.\n- Owner: Alex'], truncated: false,
    lines: [
      { at: '00:00:04', speaker: 'Alex', text: 'Ship sign-in first.' },
      { at: '00:00:19', speaker: 'Sam', text: 'Agreed, then reviews. Sign-in matters.' },
    ],
  },
};
const RUNS = [
  { id: 'r1', startedAt: today.toISOString(), finishedAt: today.toISOString(), startedBy: 'SCHEDULE', outcome: 'IMPORTED',
    imported: 2, alreadyImported: 0, waiting: 1, queued: 0, problemCode: null },
  { id: 'r2', startedAt: today.toISOString(), finishedAt: today.toISOString(), startedBy: 'PERSON', outcome: 'FAILED',
    imported: 0, alreadyImported: 0, waiting: 0, queued: 0, problemCode: 'plaud.unavailable' },
];

function workspace(routes: Parameters<typeof stubApi>[0] = {}) {
  return stubApi({
    'GET /api/providers': () => ({ body: PROVIDERS }),
    'GET /api/sources': () => ({ body: [PLAUD, UPLOAD] }),
    'GET /api/recorders/plaud/connections': () => ({ body: [CONNECTION] }),
    'GET /api/sources/p1/documents': () => ({ body: MEETINGS }),
    'GET /api/sources/s1/documents': () => ({ body: [] }),
    'GET /api/sources/p1/documents/d1': () => ({ body: MEETING }),
    'GET /api/sources/p1/syncs': () => ({ body: RUNS }),
    'GET /api/sources/p1/syncs?all=true': () => ({ body: [...RUNS, { ...RUNS[1], id: 'r3', startedAt: '2026-11-18T10:00:30Z', finishedAt: '2026-11-18T10:00:31Z', outcome: 'UP_TO_DATE', problemCode: null }] }),
    ...routes,
  });
}

describe('SourcePage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows the source, its meetings grouped by day, and opens one with its summary and transcript', async () => {
    workspace();
    render(<SourcePage sourceId="p1" />);

    expect(await screen.findByRole('heading', { level: 1, name: 'Team meetings' })).toHaveFocus();
    expect(screen.getByText('alex@acme.test')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Meetings/ })).toHaveAttribute('aria-selected', 'true');
    expect(await screen.findByRole('heading', { level: 2, name: 'Today' })).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 2 })).toHaveLength(2);

    const row = screen.getByRole('button', { name: /Sprint planning/ });
    expect(row).toHaveTextContent('30 min');
    expect(row).toHaveTextContent('2 speakers');
    expect(screen.getByRole('button', { name: /Customer call: Contoso/ })).not.toHaveTextContent('min');
    await userEvent.click(row);
    const panel = await screen.findByRole('dialog', { name: 'Sprint planning' });
    expect(within(panel).getByRole('button', { name: 'Close' })).toHaveFocus();
    expect(within(panel).getByText('Length: 30 min')).toBeInTheDocument();
    expect(within(panel).getByText(/Ship sign-in first\.\s+- Owner: Alex/)).toBeInTheDocument();
    expect(within(panel).getAllByRole('listitem').map((item) => item.textContent)).toContain('Sam');
    // Each speaker keeps one colour, in the list and on their lines.
    expect(panel.querySelectorAll('.speakers .dot.s0, .who .dot.s0')).toHaveLength(2);
    expect(panel.querySelectorAll('.speakers .dot.s1, .who .dot.s1')).toHaveLength(2);
    await userEvent.type(within(panel).getByLabelText('Find in transcript'), 'sign-in');
    expect(within(panel).getByText('2 lines match')).toBeInTheDocument();
    expect(within(panel).getAllByText(/sign-in/i, { selector: 'mark' })).toHaveLength(2);
    expect(await accessibilityViolations(document.body)).toEqual([]);

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('narrows the meetings to recent dates and says what Plaud is still transcribing', async () => {
    workspace();
    render(<SourcePage sourceId="p1" />);

    expect(await screen.findByText('1 recording is still being transcribed by Plaud. It appears here when it is ready.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Customer call: Contoso/ })).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Recorded'), 'LAST_7_DAYS');
    expect(screen.queryByRole('button', { name: /Customer call: Contoso/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sprint planning/ })).toBeInTheDocument();

    await userEvent.type(screen.getByLabelText('Search by title'), 'contoso');
    expect(screen.getByRole('heading', { name: 'No meetings match "contoso"' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Show all' }));
    expect(screen.getByLabelText('Recorded')).toHaveValue('ANY');
    expect(screen.getByRole('button', { name: /Customer call: Contoso/ })).toBeInTheDocument();
    expect(await accessibilityViolations(document.body)).toEqual([]);
  });

  it('deletes a meeting after a clear question, says it will not come back, and can back out', async () => {
    let documents = MEETINGS;
    const calls = workspace({
      'GET /api/sources/p1/documents': () => ({ body: documents }),
      'DELETE /api/sources/p1/documents/d1': () => { documents = [MEETINGS[1]]; return { status: 204, body: null }; },
    });
    const user = userEvent.setup();
    render(<SourcePage sourceId="p1" />);
    await user.click(await screen.findByRole('button', { name: /Sprint planning/ }));
    const panel = await screen.findByRole('dialog', { name: 'Sprint planning' });

    await user.click(within(panel).getByRole('button', { name: 'Delete meeting' }));
    expect(within(panel).getByRole('heading', { name: 'Delete "Sprint planning"?' })).toHaveFocus();
    expect(within(panel).getByText('ConXist will not import this recording again.')).toBeInTheDocument();
    expect(within(panel).getByText('Your Plaud account keeps the recording.')).toBeInTheDocument();
    expect(await accessibilityViolations(document.body)).toEqual([]);

    await user.keyboard('{Escape}');
    expect(within(panel).queryByRole('heading', { name: /Delete/ })).not.toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Delete meeting' })).toHaveFocus();
    expect(calls.some((call) => call.key.startsWith('DELETE'))).toBe(false);

    await user.click(within(panel).getByRole('button', { name: 'Delete meeting' }));
    await user.click(within(panel).getAllByRole('button', { name: 'Delete meeting' }).at(-1)!);

    expect((await screen.findByText('Deleted "Sprint planning". It will not be imported again.')).closest('p')).toHaveFocus();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Sprint planning/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Customer call: Contoso/ })).toBeInTheDocument();
  });

  it('keeps the meeting and says why when the delete fails, and nothing closes while it runs', async () => {
    let answer: (value: { status: number; body: unknown }) => void = () => {};
    workspace({ 'DELETE /api/sources/p1/documents/d1': () => new Promise((resolve) => { answer = resolve; }) });
    const user = userEvent.setup();
    render(<SourcePage sourceId="p1" />);
    await user.click(await screen.findByRole('button', { name: /Sprint planning/ }));
    const panel = await screen.findByRole('dialog', { name: 'Sprint planning' });

    await user.click(within(panel).getByRole('button', { name: 'Delete meeting' }));
    await user.click(within(panel).getAllByRole('button', { name: 'Delete meeting' }).at(-1)!);
    expect(within(panel).getByRole('button', { name: 'Close' })).toBeDisabled();
    expect(within(panel).getByRole('heading', { name: 'Delete "Sprint planning"?' })).toHaveFocus();
    expect(within(panel).getByRole('button', { name: 'Cancel' })).toBeDisabled();
    await user.keyboard('{Escape}');
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    answer({ status: 500, body: {} });
    expect(await within(panel).findByRole('alert')).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Cancel' })).toBeEnabled();
    expect(within(panel).getAllByRole('button', { name: 'Delete meeting' }).at(-1)).toHaveFocus();
    expect(screen.getByRole('button', { name: /Sprint planning/ })).toBeInTheDocument();
  });

  it('counts a meeting someone already deleted as deleted', async () => {
    workspace({ 'DELETE /api/sources/p1/documents/d1': () => ({ status: 404, body: { code: 'document.not-found' } }) });
    const user = userEvent.setup();
    render(<SourcePage sourceId="p1" />);
    await user.click(await screen.findByRole('button', { name: /Sprint planning/ }));
    const panel = await screen.findByRole('dialog', { name: 'Sprint planning' });

    await user.click(within(panel).getByRole('button', { name: 'Delete meeting' }));
    await user.click(within(panel).getAllByRole('button', { name: 'Delete meeting' }).at(-1)!);

    expect(await screen.findByText('Deleted "Sprint planning". It will not be imported again.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Sprint planning/ })).not.toBeInTheDocument();
  });

  it('deletes an uploaded document and says it can be uploaded again', async () => {
    const SPEC = { id: 'u1', sourceId: 's1', title: 'spec.md', mediaType: 'text/markdown', sizeBytes: 10, origin: 'UPLOAD',
      textLength: 10, capturedAt: today.toISOString(), recordedAt: null, lengthSeconds: null, speakerCount: null, editedAt: null };
    let documents = [SPEC];
    workspace({
      'GET /api/sources/s1/documents': () => ({ body: documents }),
      'GET /api/sources/s1/documents/u1': () => ({ body: { ...SPEC, text: 'The spec.', meeting: null } }),
      'DELETE /api/sources/s1/documents/u1': () => { documents = []; return { status: 204, body: null }; },
    });
    const user = userEvent.setup();
    render(<SourcePage sourceId="s1" />);
    await user.click(await screen.findByRole('button', { name: /spec\.md/ }));
    const panel = await screen.findByRole('dialog', { name: 'spec.md' });

    await user.click(within(panel).getByRole('button', { name: 'Delete document' }));
    expect(within(panel).getByText('You can upload the file again later.')).toBeInTheDocument();
    expect(within(panel).queryByText('ConXist will not import this recording again.')).not.toBeInTheDocument();
    await user.click(within(panel).getAllByRole('button', { name: 'Delete document' }).at(-1)!);

    expect((await screen.findByText('Deleted "spec.md".')).closest('p')).toHaveFocus();
  });

  it('searches meetings by title', async () => {
    workspace();
    render(<SourcePage sourceId="p1" />);
    await screen.findByRole('button', { name: /Sprint planning/ });

    await userEvent.type(screen.getByLabelText('Search by title'), 'contoso');
    expect(screen.queryByRole('button', { name: /Sprint planning/ })).not.toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Search by title'));
    await userEvent.type(screen.getByLabelText('Search by title'), 'retro');
    expect(screen.getByRole('heading', { name: 'No meetings match "retro"' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Clear search' }));
    expect(screen.getByRole('button', { name: /Sprint planning/ })).toBeInTheDocument();
  });

  it('shows the import history, imports and problems first, with all checks on request', async () => {
    workspace();
    render(<SourcePage sourceId="p1" />);
    await screen.findByRole('heading', { level: 1, name: 'Team meetings' });

    const tab = screen.getByRole('tab', { name: 'Import history' });
    tab.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(tab).toHaveAttribute('aria-selected', 'true');
    const table = await screen.findByRole('table');
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    expect(within(table).getByText('Plaud is not answering right now. Try again in a minute.')).toBeInTheDocument();
    expect(within(table).getByText('Automatic')).toBeInTheDocument();
    // The check that imported names its meetings; a name opens the meeting.
    expect(within(table).getByRole('button', { name: 'Customer call: Contoso' })).toBeInTheDocument();
    await userEvent.click(within(table).getByRole('button', { name: 'Sprint planning' }));
    const panel = await screen.findByRole('dialog', { name: 'Sprint planning' });
    await userEvent.click(within(panel).getByRole('button', { name: 'Close' }));

    await userEvent.selectOptions(screen.getByLabelText('Show'), 'all');
    expect(await screen.findByText('Up to date')).toBeInTheDocument();
    expect(screen.getByText('First sync')).toBeInTheDocument();
    expect(await accessibilityViolations(document.body)).toEqual([]);
  });

  it('deletes a meeting opened from the history and says so', async () => {
    workspace({ 'DELETE /api/sources/p1/documents/d1': () => ({ status: 204, body: null }) });
    const user = userEvent.setup();
    render(<SourcePage sourceId="p1" />);
    await user.click(await screen.findByRole('tab', { name: 'Import history' }));
    await user.click(await screen.findByRole('button', { name: 'Sprint planning' }));
    const panel = await screen.findByRole('dialog', { name: 'Sprint planning' });
    await user.click(within(panel).getByRole('button', { name: 'Delete meeting' }));
    await user.click(within(panel).getAllByRole('button', { name: 'Delete meeting' }).at(-1)!);

    expect((await screen.findByText('Deleted "Sprint planning". It will not be imported again.')).closest('p')).toHaveFocus();
    expect(screen.getByText('1 deleted since')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Customer call: Contoso' })).toBeInTheDocument();
  });

  it('shows the history counts without meeting names when the meetings do not load', async () => {
    let down = false;
    workspace({ 'GET /api/sources/p1/documents': () => (down ? { status: 500, body: {} } : { body: MEETINGS }) });
    render(<SourcePage sourceId="p1" />);
    const tab = await screen.findByRole('tab', { name: 'Import history' });
    down = true;
    await userEvent.click(tab);

    const table = await screen.findByRole('table');
    expect(within(table).getAllByRole('row')).toHaveLength(3);
    expect(within(table).queryByRole('button')).not.toBeInTheDocument();
    expect(within(table).queryByText(/deleted since/)).not.toBeInTheDocument();
  });

  it('offers Sync now on an empty recorder, and only documents for an upload source', async () => {
    const calls = workspace({
      'GET /api/sources/p1/documents': () => ({ body: [] }),
      'POST /api/sources/p1/sync': () => ({ body: { imported: 0, alreadyImported: 0, waitingForTranscript: 0, queued: 0 } }),
    });
    const { unmount } = render(<SourcePage sourceId="p1" />);
    expect(await screen.findByRole('heading', { name: 'No meetings yet' })).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: /Sync now/ })[0]);
    expect(calls.filter((call) => call.key === 'POST /api/sources/p1/sync')).toHaveLength(1);
    unmount();

    render(<SourcePage sourceId="s1" />);
    expect(await screen.findByRole('heading', { name: 'No documents yet' })).toBeInTheDocument();
    expect(screen.queryByRole('tab', { name: 'Import history' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Sync now/ })).not.toBeInTheDocument();
  });

  it('says so when the source is gone', async () => {
    workspace();
    render(<SourcePage sourceId="gone" />);
    expect(await screen.findByRole('heading', { name: 'This source is gone' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to Sources' })).toHaveAttribute('href', '#/sources');
  });

  it('reads the page from the address', () => {
    expect(routeOf('#/sources/0b8f1c2e-1111-4222-8333-944455556666')).toEqual({ page: 'source', sourceId: '0b8f1c2e-1111-4222-8333-944455556666' });
    expect(routeOf('#/sources/not-an-id')).toEqual({ page: 'sources' });
    expect(routeOf('')).toEqual({ page: 'sources' });
  });
});
