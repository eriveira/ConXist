import { useId, type ReactNode } from 'react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import {
  type AgentDefinition, MAX_INSTRUCTIONS_LENGTH, MAX_NAME_LENGTH, MAX_PURPOSE_LENGTH, toggled,
} from '../../domain/agents';
import { type PinnedSkill, skillRows } from '../../domain/skills';
import { unknownVariables, type Variable } from '../../domain/variables';
import { characterCount } from '../../domain/workspaces';
import { toolDescription, toolLabel, useT } from '../../i18n/i18n';
import { settingsHref } from '../../navigation';
import type { Choices } from './AgentPage';
import { AgentMcpTools } from './AgentMcpTools';
import { AgentModelPicker } from './AgentModelPicker';
import { AgentSkills } from './AgentSkills';
import { AgentToolGroup } from './AgentToolGroup';
import { Counter, FieldError, Section } from './FormParts';

interface AgentSectionsProps {
  draft: AgentDefinition;
  /** What the server said about the skills of the saved draft. */
  pinned: PinnedSkill[];
  onChange: (change: Partial<AgentDefinition>) => void;
  disabled: boolean;
  choices: Choices;
  onRetryChoices: () => void;
}

/**
 * The parts of an agent, grouped the way a person thinks about them: what it is, what it does, what it uses and how it
 * thinks. Run limits are the workspace's (ADR 0023), in Settings › General.
 */
export function AgentSections({ draft, pinned, onChange, disabled, choices, onRetryChoices }: AgentSectionsProps) {
  const t = useT();
  const ids = {
    name: useId(), purpose: useId(), instructions: useId(), hint: useId(),
    nameError: useId(), nameCount: useId(), purposeError: useId(), purposeCount: useId(),
    instructionsError: useId(), instructionsCount: useId(),
  };
  const nameError = !draft.name.trim() ? t('agent.problem.name-required')
    : nameLength(draft) > MAX_NAME_LENGTH ? t('api.agent.name-too-long') : null;
  const purposeLength = characterCount(draft.purpose.trim());
  const instructionsLength = characterCount(draft.instructions.trimEnd());

  return (
    <fieldset className="agent-sections" disabled={disabled}>
      <legend className="sr-only">{t('agent.sections')}</legend>
      <Section number={1} title={t('agent.section.about')} lead={t('agent.section.about.lead')}>
        <div className="field">
          <label htmlFor={ids.name}>{t('agent.name')}</label>
          <input id={ids.name} type="text" value={draft.name} onChange={(event) => onChange({ name: event.target.value })}
            aria-invalid={nameError !== null || undefined}
            aria-describedby={`${nameError ? ids.nameError : ''} ${ids.nameCount}`.trim()} />
          {nameError && <FieldError id={ids.nameError}>{nameError}</FieldError>}
          <Counter id={ids.nameCount} count={nameLength(draft)} max={MAX_NAME_LENGTH} />
        </div>
        <div className="field">
          <label htmlFor={ids.purpose}>{t('agent.purpose')}</label>
          <textarea id={ids.purpose} rows={2} value={draft.purpose} placeholder={t('agent.purpose.placeholder')}
            onChange={(event) => onChange({ purpose: event.target.value })}
            aria-invalid={purposeLength > MAX_PURPOSE_LENGTH || undefined}
            aria-describedby={`${purposeLength > MAX_PURPOSE_LENGTH ? ids.purposeError : ''} ${ids.purposeCount}`.trim()} />
          {purposeLength > MAX_PURPOSE_LENGTH && <FieldError id={ids.purposeError}>{t('api.agent.purpose-too-long')}</FieldError>}
          <Counter id={ids.purposeCount} count={purposeLength} max={MAX_PURPOSE_LENGTH} />
        </div>
      </Section>

      <Section number={2} title={t('agent.section.instructions')} lead={t('agent.section.instructions.lead')}>
        <div className="field">
          <label htmlFor={ids.instructions} className="sr-only">{t('agent.section.instructions')}</label>
          <textarea id={ids.instructions} className="instructions" rows={9} value={draft.instructions}
            placeholder={t('agent.instructions.placeholder')}
            aria-describedby={`${instructionsLength > MAX_INSTRUCTIONS_LENGTH ? ids.instructionsError : ''} ${ids.hint} ${ids.instructionsCount}`.trim()}
            onChange={(event) => onChange({ instructions: event.target.value })}
            aria-invalid={instructionsLength > MAX_INSTRUCTIONS_LENGTH || undefined} />
          {instructionsLength > MAX_INSTRUCTIONS_LENGTH
            && <FieldError id={ids.instructionsError}>{t('api.agent.instructions-too-long')}</FieldError>}
          <span id={ids.hint} className="hint"><Icon name="lock" size={14} /> {t('agent.instructions.safety')}</span>
          <Counter id={ids.instructionsCount} count={instructionsLength} max={MAX_INSTRUCTIONS_LENGTH} />
        </div>
        {choices.status === 'ready' && choices.variables && (
          <UnknownVariables text={`${draft.purpose}\n${draft.instructions}`} variables={choices.variables} />
        )}
      </Section>

      <Section number={3} title={t('agent.section.skills')} lead={t('agent.section.skills.lead')}>
        <ChoicesState choices={choices} onRetry={onRetryChoices}>
          {({ skills, tools }) => <AgentSkills draft={draft} pinned={pinned} skills={skills} tools={tools} onChange={onChange} />}
        </ChoicesState>
      </Section>

      <Section number={4} title={t('agent.section.tools')} lead={t('agent.section.tools.lead')}>
        <ChoicesState choices={choices} onRetry={onRetryChoices}>
          {({ tools, skills, mcps }) => {
            const rows = skillRows(draft.skills, pinned, skills);
            const alone = mcps !== null && !mcps.some((server) => server.tools.some((tool) => tool.allowed));
            const picked = tools.filter((tool) => draft.tools.includes(tool.id)).length;
            return (
          <>
          <AgentToolGroup title={t('agent.tools.conxist')} picked={picked} total={tools.length} changing={0}
            startOpen={alone || picked > 0} locked={disabled}>
            {tools.map((tool) => {
              const on = draft.tools.includes(tool.id);
              const neededBy = rows.flatMap((row) => row.skill?.needs.includes(tool.id) ? [row.skill.name] : []);
              return (
              <label key={tool.id}>
                <input type="checkbox" checked={on}
                  onChange={() => onChange({ tools: toggled(draft.tools, tool.id, tools.map((each) => each.id)) })} />
                <span className="grow">
                  <b>{toolLabel(t, tool.id)}</b>
                  <small>{toolDescription(t, tool.id)}</small>
                  {neededBy.length > 0 && (
                    <small className={on ? 'needed-by' : 'needed-by off'}>
                      <Icon name={on ? 'skill' : 'alert'} size={14} />
                      {' '}{t(on ? 'agent.tool.neededBy' : 'agent.tool.offNeededBy', { names: neededBy.join(', '), count: neededBy.length })}
                    </small>
                  )}
                </span>
                {tool.readOnly && <Pill tone="live">{t('agent.tool.readOnly')}</Pill>}
              </label>
              );
            })}
          </AgentToolGroup>
          {mcps === null ? (
            <p className="alert error" role="alert">
              <Icon name="alert" /> <span className="grow">{t('agent.tools.mcpError')}</span>
              <button type="button" className="link" onClick={onRetryChoices}>{t('sources.retry')}</button>
            </p>
          ) : <AgentMcpTools draft={draft} servers={mcps} onChange={onChange} locked={disabled} />}
          </>
            );
          }}
        </ChoicesState>
        <p className="note"><Icon name="info" size={14} /> {t('agent.tools.changeNote')}</p>
      </Section>

      <Section number={5} title={t('agent.section.model')} lead={t('agent.section.model.lead')}>
        <ChoicesState choices={choices} onRetry={onRetryChoices}>
          {({ providers }) => <AgentModelPicker value={draft.model} providers={providers} onChange={(model) => onChange({ model })} />}
        </ChoicesState>
      </Section>
    </fieldset>
  );
}

function nameLength(draft: AgentDefinition): number {
  return characterCount(draft.name.trim());
}

/** Sources and tools load with the page; until they do, or if they cannot, nothing is shown as missing. */
function ChoicesState({ choices, onRetry, children }: {
  choices: Choices;
  onRetry: () => void;
  children: (ready: Extract<Choices, { status: 'ready' }>) => ReactNode;
}) {
  const t = useT();
  if (choices.status === 'loading') return <p className="muted" role="status">{t('agent.loadingChoices')}</p>;
  if (choices.status === 'error') {
    return (
      <p className="alert error" role="alert">
        <Icon name="alert" /> <span className="grow">{t('agent.choices.error')}</span>
        <button type="button" className="link" onClick={onRetry}>{t('sources.retry')}</button>
      </p>
    );
  }
  return <>{children(choices)}</>;
}

/** The {{name}}s the agent writes that the workspace has no variable for: a run leaves them as written. */
function UnknownVariables({ text, variables }: { text: string; variables: Variable[] }) {
  const t = useT();
  const unknown = unknownVariables(text, variables);
  if (unknown.length === 0) return null;
  return (
    <p className="alert info variables-unknown">
      <Icon name="info" />
      <span className="grow">
        {t(unknown.length === 1 ? 'agent.variables.unknown_one' : 'agent.variables.unknown',
          { names: unknown.map((name) => `{{${name}}}`).join(', '), count: unknown.length })}{' '}
        <a href={settingsHref('variables')}>{t('agent.variables.settings')}</a>.
      </span>
    </p>
  );
}
