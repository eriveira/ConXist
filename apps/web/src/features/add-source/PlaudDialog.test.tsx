import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
    await userEvent.click(screen.getByRole('button', { name: 'Sign in to Plaud' }));
    expect(await screen.findByText('Sample Plaud account')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    await userEvent.click(screen.getByRole('button', { name: 'Add to canvas' }));

    expect(await screen.findByRole('status')).toHaveTextContent('2 meetings are waiting for review');
    const added = calls.find((call) => call.key === 'POST /api/sources');
    expect(JSON.parse(String(added?.init?.body))).toEqual({
      provider: 'PLAUD', name: 'Team meetings', accountLabel: 'Sample Plaud account',
    });
  });

  it('asks for the sign-in before continuing', async () => {
    render(<PlaudDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Sign in to continue.');
  });
});
