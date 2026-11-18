import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { PROVIDERS } from '../../testing';
import { AddMenu } from './AddMenu';

describe('AddMenu', () => {
  it('offers the three categories first, then the providers in the one picked', async () => {
    const onChoose = vi.fn();
    render(<AddMenu providers={PROVIDERS} onChoose={onChoose} />);

    await userEvent.click(screen.getByRole('button', { name: 'Add source' }));
    expect(screen.getByRole('button', { name: /Capture/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Repo/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Storage/ })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /Capture/ }));
    await userEvent.click(screen.getByRole('button', { name: /Upload files/ }));

    expect(onChoose).toHaveBeenCalledWith(PROVIDERS[0]);
  });

  it('has no accessibility violations with the menu open', async () => {
    const { container } = render(<AddMenu providers={PROVIDERS} onChoose={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Add source' }));
    await userEvent.click(screen.getByRole('button', { name: /Capture/ }));

    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('shows coming-soon providers as unavailable', async () => {
    render(<AddMenu providers={PROVIDERS} onChoose={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Add source' }));
    await userEvent.click(screen.getByRole('button', { name: /Capture/ }));

    expect(screen.getByRole('button', { name: /Email forwarding/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Meeting recorder/ })).toBeEnabled();
  });

  it('steps back with Escape, then closes and returns focus to the Add button', async () => {
    render(<AddMenu providers={PROVIDERS} onChoose={vi.fn()} />);
    const add = screen.getByRole('button', { name: 'Add source' });

    await userEvent.click(add);
    await userEvent.click(screen.getByRole('button', { name: /Repo/ }));
    await userEvent.keyboard('{Escape}');
    expect(screen.getByRole('button', { name: /Storage/ })).toBeInTheDocument();

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('group')).not.toBeInTheDocument();
    expect(add).toHaveFocus();
  });
});
