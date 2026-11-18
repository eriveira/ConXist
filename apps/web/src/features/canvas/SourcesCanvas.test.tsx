import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { PROVIDERS, stubApi } from '../../testing';
import { SourcesCanvas } from './SourcesCanvas';

class NoResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const SOURCE = { id: 's1', category: 'CAPTURE', provider: 'UPLOAD', name: 'Product specs', accountLabel: '', addedAt: '2026-11-17T10:00:00Z' };

describe('SourcesCanvas', () => {
  beforeEach(() => vi.stubGlobal('ResizeObserver', NoResizeObserver));
  afterEach(() => vi.unstubAllGlobals());

  it('shows each source with its document count', async () => {
    stubApi({
      'GET /api/providers': () => ({ body: PROVIDERS }),
      'GET /api/sources': () => ({ body: [SOURCE] }),
      'GET /api/sources/s1/documents': () => ({ body: [{}, {}, {}] }),
    });
    render(<SourcesCanvas />);

    // React Flow hides nodes until it has measured them, which jsdom never does, so nodes are found by id.
    const source = await screen.findByTestId('rf__node-s1');
    expect(source).toHaveTextContent('Product specs');
    expect(source).toHaveTextContent('3 documents');
    expect(screen.queryByTestId('rf__node-hub')).not.toBeInTheDocument();
  });

  it('tells the person when a sync fails', async () => {
    const plaud = { ...SOURCE, id: 'p1', provider: 'PLAUD', name: 'Team meetings', accountLabel: 'Sample' };
    stubApi({
      'GET /api/providers': () => ({ body: PROVIDERS }),
      'GET /api/sources': () => ({ body: [plaud] }),
      'GET /api/sources/p1/documents': () => ({ body: [] }),
      'POST /api/sources/p1/sync': () => ({ status: 500, body: { code: 'request.failed' } }),
    });
    render(<SourcesCanvas />);
    const node = await screen.findByTestId('rf__node-p1');

    fireEvent.click(within(node).getByText('Sync').closest('button')!);

    expect(await within(node).findByText('Sync failed. Try again.')).toBeInTheDocument();
    expect(within(node).getByText('Sync failed')).toHaveClass('pill', 'error');
  });

  it('has no accessibility violations on the empty canvas', async () => {
    stubApi({
      'GET /api/providers': () => ({ body: PROVIDERS }),
      'GET /api/sources': () => ({ body: [] }),
    });
    const { container } = render(<SourcesCanvas />);
    await screen.findByText('Connect your first source');

    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('invites the first connection when nothing is connected', async () => {
    stubApi({
      'GET /api/providers': () => ({ body: PROVIDERS }),
      'GET /api/sources': () => ({ body: [] }),
    });
    render(<SourcesCanvas />);

    expect(await screen.findByText('Connect your first source')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add source' })).toBeInTheDocument();
  });

  it('explains when the API cannot be reached and offers a retry', async () => {
    stubApi({});
    render(<SourcesCanvas />);

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not reach ConXist.');
    stubApi({
      'GET /api/providers': () => ({ body: PROVIDERS }),
      'GET /api/sources': () => ({ body: [] }),
    });
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Connect your first source')).toBeInTheDocument();
  });
});
