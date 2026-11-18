import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Dialog } from './Dialog';

function renderDialog({ closable = true, step = 'one', onClose = vi.fn() } = {}) {
  const view = render(
    <Dialog title="Title" subtitle="Subtitle" icon={null} closeLabel="Close" onClose={onClose} step={step} closable={closable}
      footer={<button type="button">Next</button>}>
      <div className="m-body"><h3>{`Heading ${step}`}</h3></div>
    </Dialog>,
  );
  return { ...view, onClose };
}

describe('Dialog', () => {
  it('moves focus to the heading of each new step', () => {
    const { rerender, onClose } = renderDialog();
    expect(screen.getByRole('heading', { name: 'Heading one' })).toHaveFocus();

    rerender(
      <Dialog title="Title" subtitle="Subtitle" icon={null} closeLabel="Close" onClose={onClose} step="two"
        footer={<button type="button">Next</button>}>
        <div className="m-body"><h3>Heading two</h3></div>
      </Dialog>,
    );
    expect(screen.getByRole('heading', { name: 'Heading two' })).toHaveFocus();
  });

  it('closes on Escape but not on a click outside', () => {
    const { onClose, container } = renderDialog();
    fireEvent.click(container.querySelector('.scrim')!);
    expect(onClose).not.toHaveBeenCalled();

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalledOnce();
  });

  it('cannot be closed while work is running', () => {
    const { onClose } = renderDialog({ closable: false });
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: 'Close' })).toBeDisabled();
  });

  it('keeps Tab inside the dialog', async () => {
    renderDialog();
    screen.getByRole('button', { name: 'Next' }).focus();
    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Close' })).toHaveFocus();
  });

  it('is described by its subtitle', () => {
    renderDialog();
    expect(screen.getByRole('dialog', { name: 'Title' })).toHaveAccessibleDescription('Subtitle');
  });
});
