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

const panel = (block: Parameters<typeof AgentTestPanel>[0]['block'] = null) =>
  <AgentTestPanel agentId={ID} reads={[SOURCE.id]} givesBack="REQUIREMENTS" sources={[SOURCE]} block={block} />;

describe('AgentTestPanel', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('runs the draft on the picked meeting and shows the answer, its shape and what it did', async () => {
    const calls = api();
    const { container } = render(panel());

    const meeting = await screen.findByRole('combobox', { name: 'Meeting' });
    expect(within(meeting).getByRole('option', { name: /Weekly sync/ })).toBeInTheDocument();
    expect(screen.getByText(/The meeting is sent to the AI provider the agent runs on/)).toBeInTheDocument();
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

  it('keeps Run off while the page lists problems, and says why', async () => {
    api();
    render(panel('problems'));
    expect(await screen.findByRole('button', { name: 'Run' })).toBeDisabled();
    expect(screen.getByText('Fix what the page lists, then run a test.')).toBeInTheDocument();
  });

  it('points to Sources when there is no meeting to run on', async () => {
    api({ [`GET /api/sources/${SOURCE.id}/documents`]: () => ({ body: [] }) });
    render(panel());
    expect(await screen.findByRole('link', { name: 'Go to Sources' })).toHaveAttribute('href', '#/sources');
    expect(screen.queryByRole('button', { name: 'Run' })).not.toBeInTheDocument();
  });

  it('says why a run was refused, and stopped runs say which limit', async () => {
    api({ [`POST /api/agents/${ID}/test-runs`]: () => ({ status: 422, body: { code: 'run.model-key-rejected' } }) });
    const { unmount } = render(panel());
    await userEvent.click(await screen.findByRole('button', { name: 'Run' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The agent’s provider did not accept the key');
    unmount();

    api({ [`POST /api/agents/${ID}/test-runs`]: () => ({ body: { ...RESULT, outcome: 'TOOL_LIMIT', answer: null,
      structured: null, matchesShape: null } }) });
    render(panel());
    await userEvent.click(await screen.findByRole('button', { name: 'Run' }));
    expect(await screen.findByText(/reached the Tool calls limit/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'Answer' })).not.toBeInTheDocument();
  });

  it('shows text that misses the shape as the model wrote it', async () => {
    api({ [`POST /api/agents/${ID}/test-runs`]: () => ({ body: { ...RESULT, answer: 'Just some words', structured: null,
      matchesShape: false } }) });
    render(panel());
    await userEvent.click(await screen.findByRole('button', { name: 'Run' }));
    expect(await screen.findByText('Does not match Requirements')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'What the model wrote' })).toHaveTextContent('Just some words');
  });
});
