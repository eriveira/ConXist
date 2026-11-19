import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import { PROVIDERS, stubApi } from '../../testing';
import { SourcePage } from './SourcePage';

const today = new Date();
today.setHours(10, 0, 0, 0);
const PLAUD = { id: 'p1', category: 'CAPTURE', provider: 'PLAUD', name: 'Team meetings', accountLabel: 'alex@acme.test', addedAt: '2026-11-18T10:00:00Z' };
const CONNECTION = { sourceId: 'p1', kind: 'ACCOUNT', label: 'alex@acme.test', status: 'ACTIVE', lastCheckedAt: today.toISOString(),
  problemCode: null, includeSummaries: true, importNew: true };
const ROW = { id: 'd1', sourceId: 'p1', title: 'Sprint planning', mediaType: 'text/markdown', sizeBytes: 10, origin: 'RECORDING',
  textLength: 10, capturedAt: today.toISOString(), recordedAt: today.toISOString(), lengthSeconds: 1800, speakerCount: 2, editedAt: null };
const MEETING = {
  recordedAt: today.toISOString(), lengthSeconds: 1800, language: 'en', summaries: [], truncated: false,
  lines: [
    { at: '00:00:04', speaker: 'Speaker 1', text: 'Con exist ships Friday.' },
    { at: '00:00:19', speaker: 'Speaker 2', text: 'Agreed.' },
  ],
};
const VIEW = { id: 'd1', sourceId: 'p1', title: 'Sprint planning', mediaType: 'text/markdown', origin: 'RECORDING',
  capturedAt: today.toISOString(), editedAt: null, text: '# Sprint planning', meeting: MEETING };
const EDITED_AT = '2026-11-19T12:00:00Z';
const EDITED = { ...VIEW, title: 'Sprint 42 planning', editedAt: EDITED_AT, meeting: { ...MEETING, lines: [
  { at: '00:00:04', speaker: 'Ana', text: 'ConXist ships Friday.' }, MEETING.lines[1]] } };

function workspace(routes: Parameters<typeof stubApi>[0] = {}) {
  return stubApi({
    'GET /api/providers': () => ({ body: PROVIDERS }),
    'GET /api/sources': () => ({ body: [PLAUD] }),
    'GET /api/recorders/plaud/connections': () => ({ body: [CONNECTION] }),
    'GET /api/sources/p1/documents': () => ({ body: [ROW] }),
    'GET /api/sources/p1/documents/d1': () => ({ body: VIEW }),
    'GET /api/sources/p1/syncs?all=true': () => ({ body: [] }),
    ...routes,
  });
}

async function openEditor() {
  const user = userEvent.setup();
  render(<SourcePage sourceId="p1" />);
  await user.click(await screen.findByRole('button', { name: /Sprint planning/ }));
  const panel = await screen.findByRole('dialog', { name: 'Sprint planning' });
  await user.click(within(panel).getByRole('button', { name: 'Edit meeting' }));
  return { user, panel };
}

describe('Editing a meeting', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('saves a new title, a speaker name for every line and a fixed line, then shows it as edited', async () => {
    const calls = workspace({ 'PUT /api/sources/p1/documents/d1/meeting': () => ({ body: EDITED }) });
    const { user, panel } = await openEditor();

    expect(within(panel).getByLabelText('Title')).toHaveFocus();
    expect(within(panel).getByText('No changes yet')).toBeInTheDocument();
    await user.clear(within(panel).getByLabelText('Title'));
    await user.type(within(panel).getByLabelText('Title'), 'Sprint 42 planning');
    await user.clear(within(panel).getByLabelText('New name for Speaker 1'));
    await user.type(within(panel).getByLabelText('New name for Speaker 1'), 'Ana');
    await user.clear(within(panel).getByLabelText('Line at 00:00:04'));
    await user.type(within(panel).getByLabelText('Line at 00:00:04'), 'ConXist ships Friday.');
    expect(within(panel).getByText('Unsaved changes')).toBeInTheDocument();
    // The new name shows on that speaker's lines while editing.
    expect(within(panel).getAllByText('Ana').length).toBeGreaterThan(0);
    expect(await accessibilityViolations(document.body)).toEqual([]);

    await user.click(within(panel).getByRole('button', { name: 'Save changes' }));

    const put = calls.find((call) => call.key === 'PUT /api/sources/p1/documents/d1/meeting');
    expect(JSON.parse(String(put?.init?.body))).toEqual({ editedAt: null, title: 'Sprint 42 planning',
      speakers: { 'Speaker 1': 'Ana' }, lines: { 0: 'ConXist ships Friday.' } });
    expect(await within(panel).findByRole('heading', { name: 'Sprint 42 planning' })).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Edit meeting' })).toHaveFocus();
    expect(within(panel).getByText(/Edited in ConXist on/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sprint 42 planning\s*Edited/ })).toBeInTheDocument();
  });

  it('asks before throwing away unsaved changes, on Cancel, Close or Escape', async () => {
    const calls = workspace();
    const { user, panel } = await openEditor();

    await user.keyboard('{Escape}');
    expect(within(panel).getByRole('button', { name: 'Edit meeting' })).toHaveFocus();

    await user.click(within(panel).getByRole('button', { name: 'Edit meeting' }));
    await user.type(within(panel).getByLabelText('Title'), ' v2');
    await user.keyboard('{Escape}');
    expect(within(panel).getByRole('heading', { name: 'Discard your changes?' })).toHaveFocus();
    await user.click(within(panel).getByRole('button', { name: 'Keep editing' }));
    expect(within(panel).getByLabelText('Title')).toHaveValue('Sprint planning v2');
    expect(within(panel).getByLabelText('Title')).toHaveFocus();

    await user.click(within(panel).getByRole('button', { name: 'Close without saving' }));
    await user.click(within(panel).getByRole('button', { name: 'Discard changes' }));
    expect(within(panel).getByRole('heading', { name: 'Sprint planning' })).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Edit meeting' })).toHaveFocus();
    expect(calls.some((call) => call.key.startsWith('PUT'))).toBe(false);
  });

  it('needs a title, and says when someone changed the meeting meanwhile', async () => {
    workspace({ 'PUT /api/sources/p1/documents/d1/meeting': () => ({ status: 422, body: { code: 'document.changed' } }) });
    const { user, panel } = await openEditor();

    await user.clear(within(panel).getByLabelText('Title'));
    await user.click(within(panel).getByRole('button', { name: 'Save changes' }));
    expect(within(panel).getByRole('alert')).toHaveTextContent('Give the meeting a title.');
    expect(within(panel).getByLabelText('Title')).toHaveAttribute('aria-invalid', 'true');

    await user.type(within(panel).getByLabelText('Title'), 'Retro');
    await user.click(within(panel).getByRole('button', { name: 'Save changes' }));
    expect(await within(panel).findByText(/Someone changed this meeting meanwhile. Open it again to see the latest; your unsaved changes will be lost./)).toBeInTheDocument();
    await user.click(within(panel).getByRole('button', { name: 'Open it again' }));
    expect(await within(panel).findByRole('heading', { name: 'Sprint planning' })).toBeInTheDocument();
  });

  it('puts back the original after a clear question', async () => {
    const calls = workspace({
      'GET /api/sources/p1/documents': () => ({ body: [{ ...ROW, title: 'Sprint 42 planning', editedAt: EDITED_AT }] }),
      'GET /api/sources/p1/documents/d1': () => ({ body: EDITED }),
      'POST /api/sources/p1/documents/d1/restore': () => ({ body: VIEW }),
    });
    const user = userEvent.setup();
    render(<SourcePage sourceId="p1" />);
    await user.click(await screen.findByRole('button', { name: /Sprint 42 planning/ }));
    const panel = await screen.findByRole('dialog', { name: 'Sprint 42 planning' });

    await user.click(within(panel).getByRole('button', { name: 'Restore original' }));
    expect(within(panel).getByRole('heading', { name: 'Restore the original?' })).toHaveFocus();
    expect(within(panel).queryByRole('button', { name: 'Delete meeting' })).not.toBeInTheDocument();
    await user.click(within(panel).getByRole('button', { name: 'Cancel' }));
    expect(within(panel).getByRole('button', { name: 'Restore original' })).toHaveFocus();
    await user.click(within(panel).getByRole('button', { name: 'Restore original' }));
    await user.keyboard('{Escape}');
    expect(screen.getByRole('dialog', { name: 'Sprint 42 planning' })).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Restore original' })).toHaveFocus();
    await user.click(within(panel).getByRole('button', { name: 'Restore original' }));
    expect(await accessibilityViolations(document.body)).toEqual([]);
    await user.click(within(panel).getAllByRole('button', { name: 'Restore original' }).at(-1)!);

    expect(JSON.parse(String(calls.find((call) => call.key.startsWith('POST'))?.init?.body))).toEqual({ editedAt: EDITED_AT });
    expect(await within(panel).findByRole('heading', { name: 'Sprint planning' })).toBeInTheDocument();
    expect(within(panel).queryByText(/Edited in ConXist/)).not.toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: 'Edit meeting' })).toHaveFocus();
    expect(screen.getByRole('button', { name: /^Sprint planning/ })).not.toHaveTextContent('Edited');
  });
});
