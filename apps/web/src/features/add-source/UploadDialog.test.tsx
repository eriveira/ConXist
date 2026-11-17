import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { stubApi } from '../../testing';
import { UploadDialog } from './UploadDialog';

const SOURCE = { id: 'src-1', category: 'CAPTURE', provider: 'UPLOAD', name: 'Specs', accountLabel: '', addedAt: '' };
const pdf = () => new File(['%PDF'], 'spec.pdf', { type: 'application/pdf' });

async function continueTo() {
  await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
}

describe('UploadDialog', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('adds the source, sends each file and reports what reached the hub', async () => {
    const calls = stubApi({
      'POST /api/sources': () => ({ status: 201, body: SOURCE }),
      'POST /api/sources/src-1/files': () => ({ status: 201, body: { id: 'doc-1' } }),
    });
    const onCaptured = vi.fn();
    render(<UploadDialog onClose={vi.fn()} onCaptured={onCaptured} />);

    await userEvent.clear(screen.getByLabelText('Source name'));
    await userEvent.type(screen.getByLabelText('Source name'), 'Specs');
    await continueTo();
    await userEvent.upload(screen.getByTestId('file-input'), pdf());
    await continueTo();
    await userEvent.click(screen.getByRole('button', { name: 'Add to canvas' }));

    expect(await screen.findByRole('status')).toHaveTextContent('1 of 1 files are waiting for review');
    expect(JSON.parse(String(calls[0].init?.body))).toEqual({ provider: 'UPLOAD', name: 'Specs' });
    expect(calls.map((call) => call.key)).toEqual(['POST /api/sources', 'POST /api/sources/src-1/files']);
    expect(onCaptured).toHaveBeenCalled();
  });

  it('does not continue without a file and says why', async () => {
    render(<UploadDialog existing={SOURCE as never} onClose={vi.fn()} onCaptured={vi.fn()} />);

    await continueTo();

    expect(screen.getByRole('alert')).toHaveTextContent('Add at least one file.');
  });

  it('marks a file it cannot take and leaves it out', async () => {
    render(<UploadDialog existing={SOURCE as never} onClose={vi.fn()} onCaptured={vi.fn()} />);

    await userEvent.upload(screen.getByTestId('file-input'), new File(['MZ'], 'setup.exe'), { applyAccept: false });

    expect(screen.getByText('Not a PDF, DOCX, MD or TXT file.')).toBeInTheDocument();
    await continueTo();
    expect(screen.getByRole('alert')).toHaveTextContent('Add at least one file.');
  });

  it('shows the reason the server gives for a refused file', async () => {
    stubApi({
      'POST /api/sources/src-1/files': () => ({ status: 422, body: { detail: 'We could not read the text in this file.' } }),
    });
    render(<UploadDialog existing={SOURCE as never} onClose={vi.fn()} onCaptured={vi.fn()} />);

    await userEvent.upload(screen.getByTestId('file-input'), pdf());
    await continueTo();
    await userEvent.click(screen.getByRole('button', { name: 'Add to canvas' }));

    expect(await screen.findByText('We could not read the text in this file.')).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('0 of 1 files');
  });

  it('keeps the person on the review step when the source cannot be added', async () => {
    stubApi({ 'POST /api/sources': () => ({ status: 422, body: { detail: 'A source named "Specs" already exists.' } }) });
    render(<UploadDialog onClose={vi.fn()} onCaptured={vi.fn()} />);

    await continueTo();
    await userEvent.upload(screen.getByTestId('file-input'), pdf());
    await continueTo();
    await userEvent.click(screen.getByRole('button', { name: 'Add to canvas' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('already exists');
    expect(screen.getByRole('button', { name: 'Add to canvas' })).toBeEnabled();
  });

  it('closes with Escape', async () => {
    const onClose = vi.fn();
    render(<UploadDialog onClose={onClose} onCaptured={vi.fn()} />);

    await userEvent.keyboard('{Escape}');

    expect(onClose).toHaveBeenCalled();
  });
});
