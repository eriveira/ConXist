import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { Provider } from '../../domain/model';
import { PROVIDERS } from '../../testing';
import { AddMenu } from './AddMenu';

/** A second category that can be added, so the menu asks for the category first. */
const WITH_REPO: Provider[] = [...PROVIDERS, { provider: 'BITBUCKET', category: 'REPO', availability: 'LIVE', displayName: 'Bitbucket', signInRequired: true }];

describe('AddMenu', () => {
  it('goes straight to the providers when only one category can be added', async () => {
    const onChoose = vi.fn();
    render(<AddMenu providers={PROVIDERS} onChoose={onChoose} />);

    await userEvent.click(screen.getByRole('button', { name: 'Add source' }));
    expect(screen.queryByRole('button', { name: /Repo/ })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Back' })).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Upload files/ }));
    expect(onChoose).toHaveBeenCalledWith(PROVIDERS[0]);
  });

  it('has no accessibility violations with the menu open', async () => {
    const { container } = render(<AddMenu providers={PROVIDERS} onChoose={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Add source' }));

    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('leaves out providers that are not available yet', async () => {
    render(<AddMenu providers={PROVIDERS} onChoose={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Add source' }));

    expect(screen.queryByRole('button', { name: /Email forwarding/ })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Meeting recorder.*Preview/ })).toBeEnabled();
  });

  it('asks for the category first when more than one can be added, and steps back with Escape', async () => {
    render(<AddMenu providers={WITH_REPO} onChoose={vi.fn()} />);
    const add = screen.getByRole('button', { name: 'Add source' });

    await userEvent.click(add);
    expect(screen.queryByRole('button', { name: /Storage/ })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Repo/ }));
    await userEvent.keyboard('{Escape}');
    expect(screen.getByRole('button', { name: /Capture/ })).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('group')).not.toBeInTheDocument();
    expect(add).toHaveFocus();
  });
});
