import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { PROVIDERS, stubApi } from '../../testing';
import { SourcesCanvas } from './SourcesCanvas';

class NoResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

const HUB = { sources: 1, documents: 3, pendingReview: 3, latest: [] };
const SOURCE = { id: 's1', category: 'CAPTURE', provider: 'UPLOAD', name: 'Product specs', accountLabel: '', addedAt: '2026-11-17T10:00:00Z' };

describe('SourcesCanvas', () => {
  beforeEach(() => vi.stubGlobal('ResizeObserver', NoResizeObserver));
  afterEach(() => vi.unstubAllGlobals());

  it('shows each source wired to the hub with its counts', async () => {
    stubApi({
      'GET /api/providers': () => ({ body: PROVIDERS }),
      'GET /api/sources': () => ({ body: [SOURCE] }),
      'GET /api/hub': () => ({ body: HUB }),
      'GET /api/sources/s1/documents': () => ({ body: [{}, {}, {}] }),
    });
    render(<SourcesCanvas />);

    // React Flow hides nodes until it has measured them, which jsdom never does, so nodes are found by id.
    const source = await screen.findByTestId('rf__node-s1');
    expect(source).toHaveTextContent('Product specs');
    expect(source).toHaveTextContent('3 documents');
    expect(screen.getByTestId('rf__node-hub')).toHaveTextContent('Waiting for review3');
  });

  it('invites the first connection when nothing is connected', async () => {
    stubApi({
      'GET /api/providers': () => ({ body: PROVIDERS }),
      'GET /api/sources': () => ({ body: [] }),
      'GET /api/hub': () => ({ body: { ...HUB, sources: 0, documents: 0, pendingReview: 0 } }),
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
      'GET /api/hub': () => ({ body: HUB }),
    });
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('Connect your first source')).toBeInTheDocument();
  });
});
