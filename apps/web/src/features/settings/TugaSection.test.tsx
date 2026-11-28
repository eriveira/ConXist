import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { accessibilityViolations } from '../../a11y';
import type { TugaChange, TugaSettings } from '../../domain/tuga';
import { LLM_PROVIDERS, stubApi } from '../../testing';
import { SettingsPage } from './SettingsPage';

const WORKSPACE = { id: 'w1', name: 'Acme Product', description: '', sourceCount: 0, createdAt: '2026-11-18T12:00:00Z' };
const CONXIST = 'Help the person with their work in this workspace.';
const TUGA: TugaSettings = {
  revision: 0, ready: true, model: { provider: 'anthropic', model: 'claude-sonnet-5-5', effort: 'MEDIUM' },
  modelPicked: false, prompt: CONXIST, conxistPrompt: CONXIST, promptEdited: false, promptChangedAt: null,
  maxPrompt: 4000, maxMessages: 40, maxMessage: 4000,
};

let revision = 0;

/** What the API answers to a save, as the server does: an empty or missing prompt is ConXist's. */
function saved(change: TugaChange): TugaSettings {
  revision += 1;
  const edited = change.prompt !== null && change.prompt.trim() !== '' && change.prompt !== CONXIST;
  const picked = change.provider !== null;
  return {
    ...TUGA, revision, modelPicked: picked,
    model: picked ? { provider: change.provider!, model: change.model!, effort: change.effort as 'LOW' } : TUGA.model,
    promptEdited: edited, prompt: edited ? change.prompt! : CONXIST,
    promptChangedAt: edited ? '2026-11-28T17:00:00Z' : null,
  };
}

function open() {
  revision = 0;
  const calls = stubApi({
    'GET /api/tuga': () => ({ body: TUGA }),
    'GET /api/llm-providers': () => ({ body: LLM_PROVIDERS }),
    'PUT /api/tuga': (init) => {
      const change = JSON.parse(String(init?.body)) as TugaChange;
      return change.revision === revision ? { body: saved(change) }
        : { status: 409, body: { code: 'tuga.changed', detail: 'changed' } };
    },
  });
  window.location.hash = '#/settings/tuga';
  const view = render(<SettingsPage workspace={WORKSPACE} onSaved={vi.fn()} canDelete onDeleted={vi.fn()} />);
  const puts = () => calls.filter((call) => call.key === 'PUT /api/tuga')
    .map((call) => JSON.parse(String(call.init?.body)) as TugaChange);
  return { ...view, puts };
}

const card = (name: RegExp) => screen.getByRole('heading', { name }).closest('section')!;

describe('Settings › Tuga', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    window.location.hash = '';
  });

  it('saves Tuga’s model, keeping ConXist’s prompt', async () => {
    const { container, puts } = open();
    expect(await screen.findByRole('heading', { name: /^Model/ })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Tuga' })).toHaveAttribute('aria-current', 'page');
    const model = within(card(/^Model/));
    expect(model.getByText(/Tuga uses where new agents start/)).toBeVisible();
    await userEvent.selectOptions(model.getByLabelText('Model'), 'claude-opus-5-5');
    await userEvent.click(model.getByRole('button', { name: 'Save' }));
    expect(await model.findByText('Saved')).toBeVisible();
    expect(puts()).toEqual([{ revision: 0, provider: 'anthropic', model: 'claude-opus-5-5', effort: 'MEDIUM', prompt: null }]);
    expect(model.queryByText(/Tuga uses where new agents start/)).not.toBeInTheDocument();
    expect(await accessibilityViolations(container)).toEqual([]);
  });

  it('warns before the prompt is edited, saves the workspace’s version, and restores ConXist’s', async () => {
    const { puts } = open();
    const prompt = within(await screen.findByRole('heading', { name: /^Prompt/ }).then((h) => h.closest('section')!));
    expect(prompt.getByText(CONXIST)).toBeVisible();
    expect(prompt.getByRole('link', { name: 'Guardrails' })).toHaveAttribute('href', '#/settings/guardrails');

    await userEvent.click(prompt.getByRole('button', { name: 'Edit' }));
    const warning = screen.getByRole('dialog', { name: /Edit Tuga’s prompt/ });
    expect(warning).toHaveTextContent('Answers may stop saying where they came from');
    await userEvent.click(within(warning).getByRole('button', { name: 'Edit anyway' }));

    const field = prompt.getByLabelText('Tuga’s prompt');
    await userEvent.clear(field);
    await userEvent.type(field, 'Answer in Spanish.');
    await userEvent.click(prompt.getByRole('button', { name: 'Save' }));
    expect(await prompt.findByText('Answer in Spanish.')).toBeVisible();
    expect(prompt.getByText('Edited')).toBeVisible();
    expect(puts().at(-1)).toEqual({ revision: 0, provider: null, model: null, effort: null, prompt: 'Answer in Spanish.' });

    await userEvent.click(prompt.getByRole('button', { name: 'Restore' }));
    await userEvent.click(within(screen.getByRole('dialog', { name: /Restore ConXist’s prompt/ }))
      .getByRole('button', { name: 'Restore' }));
    expect(await prompt.findByText(CONXIST)).toBeVisible();
    expect(puts().at(-1)).toEqual({ revision: 1, provider: null, model: null, effort: null, prompt: null });
  });
});
