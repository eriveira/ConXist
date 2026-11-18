import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { PROVIDERS, stubApi } from '../../testing';
import { SourcesPage } from './SourcesPage';

const UPLOAD = { id: 's1', category: 'CAPTURE', provider: 'UPLOAD', name: 'Product specs', accountLabel: '', addedAt: '2026-11-17T10:00:00Z' };
const PLAUD = { id: 'p1', category: 'CAPTURE', provider: 'PLAUD', name: 'Team meetings', accountLabel: 'Acme Engineering', addedAt: '2026-11-18T10:00:00Z' };
const CONNECTION = {
  sourceId: 'p1', teamName: 'Acme Engineering', startFrom: 'LAST_30_DAYS', includeSummaries: true,
  status: 'ACTIVE', lastCheckedAt: new Date(Date.now() - 2 * 60_000).toISOString(),
};

function workspace(routes: Parameters<typeof stubApi>[0] = {}, connection: object = CONNECTION) {
  return stubApi({
    'GET /api/providers': () => ({ body: PROVIDERS }),
    'GET /api/sources': () => ({ body: [PLAUD, UPLOAD] }),
    'GET /api/recorders/plaud/connections': () => ({ body: [connection] }),
    'GET /api/sources/s1/documents': () => ({ body: [{}, {}, {}] }),
    'GET /api/sources/p1/documents': () => ({ body: [{}, {}] }),
    ...routes,
  });
}

const card = (name: string) => screen.getByRole('heading', { name }).closest('article')!;

describe('SourcesPage', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows each source as a card, oldest first, with its count and last check', async () => {
    workspace();
    render(<SourcesPage />);

    await screen.findByRole('heading', { name: 'Team meetings' });
    const titles = screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent);
    expect(titles).toEqual(['Product specs', 'Team meetings']);
    expect(card('Product specs')).toHaveTextContent('3 documents');
    const plaud = card('Team meetings');
    expect(plaud).toHaveTextContent('Acme Engineering');
    expect(plaud).toHaveTextContent('2 meetings');
    expect(plaud).toHaveTextContent('Checked 2 min ago');
    expect(within(plaud).getByText('Live')).toHaveClass('pill', 'live');
  });

  it('shows a paused Plaud Team and lets the person retry', async () => {
    workspace({ 'POST /api/sources/p1/sync': () => ({ body: { imported: 1, alreadyImported: 0, waitingForTranscript: 0 } }) },
      { ...CONNECTION, status: 'PAUSED' });
    render(<SourcesPage />);
    await screen.findByRole('heading', { name: 'Team meetings' });
    const plaud = card('Team meetings');

    expect(plaud).toHaveClass('err');
    expect(within(plaud).getByText('Paused')).toBeInTheDocument();
    expect(plaud).toHaveTextContent('Plaud stopped accepting the key.');
    await userEvent.click(within(plaud).getByRole('button', { name: 'Retry' }));
    expect(await within(card('Team meetings')).findByText('1 new')).toBeInTheDocument();
  });

  it('shows a recorder whose Plaud connection never finished as not connected, without Sync', async () => {
    workspace({ 'GET /api/recorders/plaud/connections': () => ({ body: [] }) });
    render(<SourcesPage />);
    await screen.findByRole('heading', { name: 'Team meetings' });
    const plaud = card('Team meetings');

    expect(within(plaud).getByText('Not connected')).toHaveClass('pill', 'soon');
    expect(plaud).toHaveTextContent('Add the meeting recorder again');
    expect(within(plaud).queryByRole('button', { name: 'Sync' })).not.toBeInTheDocument();
  });

  it('tells the person when a sync fails', async () => {
    workspace({ 'POST /api/sources/p1/sync': () => ({ status: 503, body: { code: 'plaud.unavailable' } }) });
    render(<SourcesPage />);
    await screen.findByRole('heading', { name: 'Team meetings' });

    await userEvent.click(within(card('Team meetings')).getByRole('button', { name: 'Sync' }));

    expect(await within(card('Team meetings')).findByText('Plaud is not answering right now. Try again in a minute.')).toBeInTheDocument();
    expect(within(card('Team meetings')).getByText('Sync failed')).toHaveClass('pill', 'error');
  });

  it('invites the first connection when nothing is connected', async () => {
    workspace({ 'GET /api/sources': () => ({ body: [] }), 'GET /api/recorders/plaud/connections': () => ({ body: [] }) });
    const { container } = render(<SourcesPage />);

    expect(await screen.findByText('Connect your first source')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 1, name: 'Sources' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add source' })).toBeInTheDocument();
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('has no accessibility violations with sources', async () => {
    workspace();
    const { container } = render(<SourcesPage />);
    await screen.findByRole('heading', { name: 'Team meetings' });

    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('explains when the API cannot be reached and offers a retry', async () => {
    stubApi({});
    render(<SourcesPage />);

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not reach ConXist.');
    workspace({ 'GET /api/sources': () => ({ body: [] }), 'GET /api/recorders/plaud/connections': () => ({ body: [] }) });
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Connect your first source')).toBeInTheDocument();
  });
});
