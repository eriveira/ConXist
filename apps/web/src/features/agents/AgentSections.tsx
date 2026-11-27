import { useId, type ReactNode } from 'react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import {
  type AgentDefinition, INPUT_KINDS, LIMIT_RANGES, limitInRange, type Limits,
  MAX_INSTRUCTIONS_LENGTH, MAX_KNOWLEDGE_SOURCES, MAX_NAME_LENGTH, MAX_PURPOSE_LENGTH, OUTPUT_KINDS,
  STYLES, toggled,
} from '../../domain/agents';
import type { Source } from '../../domain/model';
import { type PinnedSkill, skillRows } from '../../domain/skills';
import { characterCount } from '../../domain/workspaces';
import { type Translate, toolDescription, toolLabel, useT } from '../../i18n/i18n';
import { MESSAGES, type MessageKey } from '../../i18n/messages';
import { SOURCES_HREF } from '../../navigation';
import type { Choices } from './AgentPage';
import { AgentMcpTools } from './AgentMcpTools';
import { AgentModelPicker } from './AgentModelPicker';
import { AgentSkills } from './AgentSkills';
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

const LIMIT_KEYS = Object.keys(LIMIT_RANGES) as (keyof Limits)[];

/** The parts of an agent, grouped the way a person thinks about them: what it is, what it uses, how it works. */
export function AgentSections({ draft, pinned, onChange, disabled, choices, onRetryChoices }: AgentSectionsProps) {
  const t = useT();
  const ids = {
    name: useId(), purpose: useId(), instructions: useId(), givesBack: useId(), hint: useId(),
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
      </Section>

      <Section number={3} title={t('agent.section.io')} lead={t('agent.section.io.lead')}>
        <fieldset className="checks-row">
          <legend>{t('agent.accepts')}</legend>
          {INPUT_KINDS.map((kind) => (
            <label key={kind} className="check-chip">
              <input type="checkbox" checked={draft.accepts.includes(kind)}
                onChange={() => onChange({ accepts: toggled(draft.accepts, kind, INPUT_KINDS) })} />
              {t(`agent.input.${kind}`)}
            </label>
          ))}
        </fieldset>
        <div className="field">
          <label htmlFor={ids.givesBack}>{t('agent.givesBack')}</label>
          <select id={ids.givesBack} value={draft.givesBack}
            onChange={(event) => onChange({ givesBack: event.target.value as AgentDefinition['givesBack'] })}>
            {OUTPUT_KINDS.map((kind) => <option key={kind} value={kind}>{t(`agent.output.${kind}`)}</option>)}
          </select>
          <span className="hint">{t(`agent.output.${draft.givesBack}.desc`)}</span>
        </div>
      </Section>

      <Section number={4} title={t('agent.section.knowledge')} lead={t('agent.section.knowledge.lead')}>
        <ChoicesState choices={choices} onRetry={onRetryChoices}>
          {(ready) => <Knowledge draft={draft} sources={ready.sources} onChange={onChange} />}
        </ChoicesState>
      </Section>

      <Section number={5} title={t('agent.section.skills')} lead={t('agent.section.skills.lead')}>
        <ChoicesState choices={choices} onRetry={onRetryChoices}>
          {({ skills, tools }) => <AgentSkills draft={draft} pinned={pinned} skills={skills} tools={tools} onChange={onChange} />}
        </ChoicesState>
      </Section>

      <Section number={6} title={t('agent.section.tools')} lead={t('agent.section.tools.lead')}>
        <ChoicesState choices={choices} onRetry={onRetryChoices}>
          {({ tools, skills, mcps }) => {
            const rows = skillRows(draft.skills, pinned, skills);
            const grouped = mcps.some((server) => server.tools.some((tool) => tool.allowed));
            return (
          <>
          {grouped && <h4 className="tool-group">{t('agent.tools.conxist')}</h4>}
          <div className="choice">
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
          </div>
          <AgentMcpTools draft={draft} servers={mcps} onChange={onChange} />
          </>
            );
          }}
        </ChoicesState>
        <p className="note"><Icon name="info" size={14} /> {t('agent.tools.changeNote')}</p>
      </Section>

      <Section number={7} title={t('agent.section.model')} lead={t('agent.section.model.lead')}>
        <ChoicesState choices={choices} onRetry={onRetryChoices}>
          {({ providers }) => <AgentModelPicker value={draft.model} providers={providers} onChange={(model) => onChange({ model })} />}
        </ChoicesState>
        <fieldset className="choice segmented">
          <legend>{t('agent.style')}</legend>
          {STYLES.map((style) => (
            <label key={style}>
              <input type="radio" name="style" checked={draft.style === style} onChange={() => onChange({ style })} />
              <span><b>{t(`agent.style.${style}`)}</b><small>{t(`agent.style.${style}.desc`)}</small></span>
            </label>
          ))}
        </fieldset>
      </Section>

      <Section number={8} title={t('agent.section.limits')} lead={t('agent.section.limits.lead')}>
        <div className="limits">
          {LIMIT_KEYS.map((key) => <LimitField key={key} name={key} value={draft.limits[key]}
            onChange={(value) => onChange({ limits: { ...draft.limits, [key]: value } })} />)}
        </div>
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

function LimitField({ name, value, onChange }: { name: keyof Limits; value: number; onChange: (value: number) => void }) {
  const t = useT();
  const id = useId();
  const hintId = useId();
  const range = LIMIT_RANGES[name];
  const valid = limitInRange(name, value);
  return (
    <div className="field">
      <label htmlFor={id}>{t(`agent.limit.${name}`)}</label>
      <input id={id} type="number" inputMode="numeric" min={range.min} max={range.max} step={1}
        value={Number.isNaN(value) ? '' : value} aria-invalid={!valid || undefined} aria-describedby={hintId}
        onChange={(event) => onChange(event.target.value === '' ? Number.NaN : Number(event.target.value))} />
      <span id={hintId} className={valid ? 'hint' : 'hint error-text'}>
        {t('agent.limit.range', { min: range.min, max: range.max })}
      </span>
    </div>
  );
}

function Knowledge({ draft, sources, onChange }: Pick<AgentSectionsProps, 'draft' | 'onChange'> & { sources: Source[] }) {
  const t = useT();
  const known = new Set(sources.map((source) => source.id));
  const missing = draft.knowledgeSources.filter((id) => !known.has(id));
  const order = [...sources.map((source) => source.id), ...missing];
  const full = draft.knowledgeSources.length >= MAX_KNOWLEDGE_SOURCES;
  const toggle = (id: string) => onChange({ knowledgeSources: toggled(draft.knowledgeSources, id, order) });

  return (
    <>
      {sources.length === 0 ? (
        <p className="muted">{t('agent.knowledge.none')} <a href={SOURCES_HREF}>{t('agent.knowledge.addSource')}</a></p>
      ) : (
        <div className="choice">
          {sources.map((source) => {
            const checked = draft.knowledgeSources.includes(source.id);
            return (
              <label key={source.id}>
                <input type="checkbox" checked={checked} disabled={!checked && full} onChange={() => toggle(source.id)} />
                <span><b>{source.name}</b><small>{providerLabel(source, t)}</small></span>
              </label>
            );
          })}
        </div>
      )}
      {missing.map((id) => (
        <p key={id} className="alert error">
          <Icon name="alert" /> {t('agent.knowledge.missing')}
          <button type="button" className="link" onClick={() => toggle(id)}
            aria-label={t('agent.knowledge.removeMissing')}>{t('agent.knowledge.remove')}</button>
        </p>
      ))}
      <p className="note"><Icon name="lock" size={14} /> {t('agent.knowledge.readOnly')}</p>
    </>
  );
}

function providerLabel(source: Source, t: Translate): string {
  const key = `provider.${source.provider}` as MessageKey;
  return key in MESSAGES.en ? t(key) : source.provider;
}
