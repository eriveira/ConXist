import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { stubApi } from '../../testing';
import { PlaudDialog } from './PlaudDialog';

describe('PlaudDialog', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('signs in, adds the recorder and imports its transcripts', async () => {
    const calls = stubApi({
      'POST /api/recorders/plaud/sign-in': () => ({ body: { label: 'Sample Plaud account' } }),
      'POST /api/sources': () => ({ status: 201, body: { id: 'rec-1' } }),
      'POST /api/sources/rec-1/sync': () => ({ body: { imported: 2, alreadyImported: 0 } }),
    });
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    expect(screen.getByText(/Preview: this signs in to a sample account/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Sample Plaud account')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('status')).toHaveTextContent('2 meetings are waiting for review');
    const added = calls.find((call) => call.key === 'POST /api/sources');
    expect(JSON.parse(String(added?.init?.body))).toEqual({
      provider: 'PLAUD', name: 'Team meetings', accountLabel: 'Sample Plaud account',
    });
  });

  it('retries only the import when the import failed after the source was added', async () => {
    let syncs = 0;
    const calls = stubApi({
      'POST /api/recorders/plaud/sign-in': () => ({ body: { label: 'Sample Plaud account' } }),
      'POST /api/sources': () => ({ status: 201, body: { id: 'rec-1' } }),
      'POST /api/sources/rec-1/sync': () => (++syncs === 1
        ? { status: 500, body: { code: 'request.failed' } }
        : { body: { imported: 2, alreadyImported: 0 } }),
    });
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await screen.findByText('Sample Plaud account');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong');

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(await screen.findByRole('status')).toHaveTextContent('2 meetings');
    expect(calls.filter((call) => call.key === 'POST /api/sources')).toHaveLength(1);
  });

  it('has no accessibility violations on the sign-in step', async () => {
    const { container } = render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('asks for the sign-in before continuing', async () => {
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Sign in to continue.');
  });
});
