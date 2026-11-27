import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { Source } from '../../domain/model';
import type { TestRunResult } from '../../domain/testRuns';
import { stubApi } from '../../testing';
import { AgentTestPanel } from './AgentTestPanel';

const ID = '11111111-1111-1111-1111-111111111111';
const SOURCE = { id: '33333333-3333-3333-3333-333333333333', category: 'CAPTURE', provider: 'UPLOAD', name: 'Specs',
  accountLabel: '', addedAt: '2026-11-01T00:00:00Z' } as Source;
const MEETING = { id: '66666666-6666-6666-6666-666666666666', sourceId: SOURCE.id, title: 'Weekly sync', mediaType: 'text/plain',
  sizeBytes: 10, origin: 'RECORDING', textLength: 500, capturedAt: '2026-11-20T10:00:00Z', recordedAt: '2026-11-20T09:00:00Z',
  lengthSeconds: 1800 };
const RESULT: TestRunResult = {
  agentId: ID, outcome: 'ANSWERED', model: 'claude-opus-5-5', effort: 'HIGH', answer: '{}', matchesShape: true,
  structured: { requirements: [{ title: 'Export to CSV', type: 'FEATURE', who: 'Sales', why: 'Reports', doneWhen: 'A file downloads', quote: '' }] },
  steps: [
    { kind: 'MODEL', name: 'claude-opus-5-5', detail: 'TOOL_USE', ok: true, millis: 1200 },
    { kind: 'TOOL', name: 'list_sources', detail: '', ok: true, millis: 4 },
    { kind: 'MODEL', name: 'claude-opus-5-5', detail: 'DONE', ok: true, millis: 2300 },
  ],
  toolCalls: 1, inputTokens: 1000, outputTokens: 234, millis: 3504,
};

function api(routes: Parameters<typeof stubApi>[0] = {}) {
  return stubApi({
    'GET /api/test-runs/status': () => ({ body: { ready: true } }),
    [`GET /api/sources/${SOURCE.id}/documents`]: () => ({ body: [MEETING] }),
    [`POST /api/agents/${ID}/test-runs`]: () => ({ body: RESULT }),
    ...routes,
  });
}

const panel = (block: Parameters<typeof AgentTestPanel>[0]['block'] = null, changes: string[] = []) =>
  <AgentTestPanel agentId={ID} reads={[SOURCE.id]} givesBack="REQUIREMENTS" sources={[SOURCE]} block={block}
    provider="openai" changes={changes} />;

describe('AgentTestPanel', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('runs the draft on the picked meeting and shows the answer, its shape and what it did', async () => {
    const calls = api();
    const { container } = render(panel());

    await userEvent.click(await screen.findByRole('radio', { name: 'A meeting' }));
    const meeting = await screen.findByRole('combobox', { name: 'Meeting' });
    expect(within(meeting).getByRole('option', { name: /Weekly sync/ })).toBeInTheDocument();
    expect(screen.getByText(/What it runs on is sent to OpenAI \(GPT\) to run the model/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Run' }));

    expect(await screen.findByText('Done. This is what it gives back.')).toBeInTheDocument();
    const body = JSON.parse(String(calls.find((call) => call.key === `POST /api/agents/${ID}/test-runs`)?.init?.body));
    expect(body).toEqual({ sourceId: SOURCE.id, meetingId: MEETING.id });
    expect(screen.getByText('Matches Requirements')).toBeInTheDocument();
    expect(screen.getByText('1 tool call')).toBeInTheDocument();
    expect(screen.getByText('1,234 tokens')).toBeInTheDocument();
    const answer = screen.getByRole('region', { name: 'Answer' });
    expect(within(answer).getByText('Export to CSV')).toBeInTheDocument();
    expect(within(answer).getByText('Feature')).toBeInTheDocument();
    expect(within(answer).getByText('Not said')).toBeInTheDocument();
    await userEvent.click(screen.getByText(/What it did/));
    expect(screen.getByText('Listed the sources')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Run again' })).toBeEnabled();
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('says where to connect a provider when the workspace has no key, and keeps Run off', async () => {
    api({ 'GET /api/test-runs/status': () => ({ body: { ready: false } }) });
    render(panel());
    expect(await screen.findByText('Connect an AI provider to run tests')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open LLM providers' })).toHaveAttribute('href', '#/settings/llm');
    expect(screen.queryByRole('button', { name: 'Run' })).not.toBeInTheDocument();
  });

  it('warns that a run may use picked tools that change things for real', async () => {
    api();
    render(panel(null, ['create_issue']));
    expect(await screen.findByText(/This test may use create_issue for real\. It changes things, and ConXist can’t undo it\. Untick it under Tools/))
      .toBeInTheDocument();
    expect(screen.queryByText(/A test only reads/)).not.toBeInTheDocument();
  });

  it('keeps Run off while the page lists problems, and says why', async () => {
    api();
    render(panel('problems'));
    expect(await screen.findByRole('button', { name: 'Run' })).toBeDisabled();
    expect(screen.getByText('Fix what the page lists, then run a test.')).toBeInTheDocument();
  });

  it('runs on pasted text, and offers no meeting when the workspace has none', async () => {
    const calls = api({ [`GET /api/sources/${SOURCE.id}/documents`]: () => ({ body: [] }) });
    const { container } = render(panel());
    const run = await screen.findByRole('button', { name: 'Run' });
    expect(screen.getByRole('radio', { name: 'Pasted text' })).toBeChecked();
    expect(screen.queryByRole('radio', { name: 'A meeting' })).not.toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
    expect(run).toBeDisabled();

    await userEvent.type(screen.getByRole('textbox', { name: 'Text to run on' }), 'Ana: export does nothing.');
    await userEvent.click(run);

    expect(await screen.findByText('Done. This is what it gives back.')).toBeInTheDocument();
    expect(JSON.parse(String(calls.find((call) => call.key === `POST /api/agents/${ID}/test-runs`)?.init?.body)))
      .toEqual({ title: '', text: 'Ana: export does nothing.' });
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('reads a text file for the run only, and says when a file cannot be used', async () => {
    const calls = api();
    render(panel());
    await userEvent.click(await screen.findByRole('radio', { name: 'A text file' }));
    const input = screen.getByLabelText('Text file');

    await userEvent.upload(input, new File(['%PDF'], 'notes.pdf', { type: 'application/pdf' }), { applyAccept: false });
    expect(await screen.findByRole('alert')).toHaveTextContent('Pick a .txt, .md, .vtt or .srt file.');
    expect(screen.getByRole('button', { name: 'Run' })).toBeDisabled();

    await userEvent.upload(input, new File(['Ana: guest checkout'], 'sync.md', { type: 'text/markdown' }));
    expect(await screen.findByText('sync.md: 19 characters, ready to run.')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Run' }));
    expect(await screen.findByText('Done. This is what it gives back.')).toBeInTheDocument();
    expect(JSON.parse(String(calls.find((call) => call.key === `POST /api/agents/${ID}/test-runs`)?.init?.body)))
      .toEqual({ title: 'sync.md', text: 'Ana: guest checkout' });
  });

  it('says why a run was refused, and stopped runs say which limit', async () => {
    api({ [`POST /api/agents/${ID}/test-runs`]: () => ({ status: 422, body: { code: 'run.model-key-rejected' } }) });
    const { unmount } = render(panel());
    await userEvent.click(await screen.findByRole('radio', { name: 'A meeting' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Run' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('OpenAI (GPT) refused this workspace’s key');
    unmount();

    api({ [`POST /api/agents/${ID}/test-runs`]: () => ({ body: { ...RESULT, outcome: 'TOOL_LIMIT', answer: null,
      structured: null, matchesShape: null } }) });
    render(panel());
    await userEvent.click(await screen.findByRole('radio', { name: 'A meeting' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Run' }));
    expect(await screen.findByText(/reached the Tool calls limit/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Answer' })).not.toBeInTheDocument();
  });

  it('shows text that misses the shape as the model wrote it', async () => {
    api({ [`POST /api/agents/${ID}/test-runs`]: () => ({ body: { ...RESULT, answer: 'Just some words', structured: null,
      matchesShape: false } }) });
    render(panel());
    await userEvent.type(await screen.findByRole('textbox', { name: 'Text to run on' }), 'Notes');
    await userEvent.click(screen.getByRole('button', { name: 'Run' }));
    expect(await screen.findByText('Does not match Requirements')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'What the model wrote' })).toHaveTextContent('Just some words');
  });
});
