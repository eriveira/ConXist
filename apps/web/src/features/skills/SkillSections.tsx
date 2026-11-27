import { useId, useRef, useState } from 'react';
import { Icon } from '../../components/Icon';
import type { AgentToolInfo } from '../../domain/agents';
import {
  MAX_DESCRIPTION_LENGTH, MAX_SKILL_INSTRUCTIONS_LENGTH, MAX_SKILL_NAME_LENGTH, mentionedTools, type SkillDefinition,
} from '../../domain/skills';
import { characterCount } from '../../domain/workspaces';
import { toolLabel, useT } from '../../i18n/i18n';
import { Counter, FieldError, Section } from '../agents/FormParts';
import type { SkillContext } from './SkillPage';

interface SkillSectionsProps {
  draft: SkillDefinition;
  onChange: (change: Partial<SkillDefinition>) => void;
  disabled: boolean;
  context: SkillContext;
  onRetryContext: () => void;
}

/** The parts of a skill: when an agent should use it, the steps, and the tools those steps need. */
export function SkillSections({ draft, onChange, disabled, context, onRetryContext }: SkillSectionsProps) {
  const t = useT();
  const ids = {
    name: useId(), nameError: useId(), nameCount: useId(), description: useId(), descriptionHint: useId(),
    descriptionCount: useId(), instructions: useId(), instructionsHint: useId(), instructionsCount: useId(),
  };
  const nameLength = characterCount(draft.name.trim());
  const nameError = nameLength === 0 ? t('skill.problem.name-required')
    : nameLength > MAX_SKILL_NAME_LENGTH ? t('api.skill.name-too-long') : null;
  const descriptionLength = characterCount(draft.description.trim());
  const instructionsLength = characterCount(draft.instructions.trimEnd());

  return (
    <fieldset className="agent-sections" disabled={disabled}>
      <legend className="sr-only">{t('skill.sections')}</legend>
      <Section number={1} title={t('skill.section.about')} lead={t('skill.section.about.lead')}>
        <div className="field">
          <label htmlFor={ids.name}>{t('skill.name')}</label>
          <input id={ids.name} type="text" value={draft.name} onChange={(event) => onChange({ name: event.target.value })}
            aria-invalid={nameError !== null || undefined}
            aria-describedby={`${nameError ? ids.nameError : ''} ${ids.nameCount}`.trim()} />
          {nameError && <FieldError id={ids.nameError}>{nameError}</FieldError>}
          <Counter id={ids.nameCount} count={nameLength} max={MAX_SKILL_NAME_LENGTH} />
        </div>
        <div className="field">
          <label htmlFor={ids.description}>{t('skill.description')}</label>
          <textarea id={ids.description} rows={2} value={draft.description} placeholder={t('skill.description.placeholder')}
            onChange={(event) => onChange({ description: event.target.value })}
            aria-invalid={descriptionLength > MAX_DESCRIPTION_LENGTH || undefined}
            aria-describedby={`${ids.descriptionHint} ${ids.descriptionCount}`} />
          <span id={ids.descriptionHint} className={descriptionLength > MAX_DESCRIPTION_LENGTH ? 'hint error-text' : 'hint'}>
            {descriptionLength > MAX_DESCRIPTION_LENGTH ? t('api.skill.description-too-long') : t('skill.description.hint')}
          </span>
          <Counter id={ids.descriptionCount} count={descriptionLength} max={MAX_DESCRIPTION_LENGTH} />
        </div>
      </Section>

      <Section number={2} title={t('skill.section.instructions')} lead={t('skill.section.instructions.lead')}>
        <div className="field">
          <label htmlFor={ids.instructions} className="sr-only">{t('skill.section.instructions')}</label>
          <textarea id={ids.instructions} className="instructions" rows={14} value={draft.instructions}
            placeholder={t('skill.instructions.placeholder')}
            onChange={(event) => onChange({ instructions: event.target.value })}
            aria-invalid={instructionsLength > MAX_SKILL_INSTRUCTIONS_LENGTH || undefined}
            aria-describedby={`${ids.instructionsHint} ${ids.instructionsCount}`} />
          <span id={ids.instructionsHint} className={instructionsLength > MAX_SKILL_INSTRUCTIONS_LENGTH ? 'hint error-text' : 'hint'}>
            {instructionsLength > MAX_SKILL_INSTRUCTIONS_LENGTH ? t('api.skill.instructions-too-long')
              : <><Icon name="lock" size={14} /> {t('agent.instructions.safety')}</>}
          </span>
          <Counter id={ids.instructionsCount} count={instructionsLength} max={MAX_SKILL_INSTRUCTIONS_LENGTH} />
        </div>
        {context.status === 'loading' && <p className="muted" role="status">{t('agent.loadingChoices')}</p>}
        {context.status === 'error' && (
          <p className="alert error" role="alert">
            <Icon name="alert" /> <span className="grow">{t('skill.context.error')}</span>
            <button type="button" className="link" onClick={onRetryContext}>{t('sources.retry')}</button>
          </p>
        )}
        {context.status === 'ready' && (
          <SkillNeeds draft={draft} tools={context.tools} onChange={onChange} />
        )}
      </Section>
    </fieldset>
  );
}

interface SkillNeedsProps {
  draft: SkillDefinition;
  tools: AgentToolInfo[];
  onChange: (change: Partial<SkillDefinition>) => void;
}

/** The tools the steps use. A skill never gives an agent a tool: the agent shows what to turn on. */
function SkillNeeds({ draft, tools, onChange }: SkillNeedsProps) {
  const t = useT();
  const pickId = useId();
  const [notice, setNotice] = useState<string | null>(null);
  const name = (id: string) => toolLabel(t, id);
  const [picked, setPicked] = useState('');
  const pickRef = useRef<HTMLSelectElement>(null);
  /** The button pressed goes away, so focus moves to the tool picker, or the section when there is none. */
  const keepFocus = () => requestAnimationFrame(() => pickRef.current?.focus());
  const order = tools.map((tool) => tool.id);
  const others = tools.filter((tool) => !draft.needs.includes(tool.id));
  const mentioned = mentionedTools(draft.instructions, tools).filter((id) => !draft.needs.includes(id));
  const set = (needs: string[]) => onChange({ needs: order.filter((id) => needs.includes(id)) });

  return (
    <div className="skill-needs">
      <h3 className="grp-title">{t('skill.needs.title')}</h3>
      {draft.needs.length === 0 && <p className="muted">{t('skill.needs.none')}</p>}
      {draft.needs.length > 0 && (
        <ul className="need-list">
          {draft.needs.map((id) => (
            <li key={id}>
              <span className="grow">
                <b>{name(id)}</b>
                <small><code>{tools.find((tool) => tool.id === id)?.toolName ?? id}</code> · {t('agent.tool.readOnly')}</small>
              </span>
              <button type="button" className="icon-btn" onClick={() => { keepFocus(); set(draft.needs.filter((need) => need !== id)); }}
                aria-label={t('skill.needs.remove', { tool: name(id) })}>
                <Icon name="close" size={18} />
              </button>
            </li>
          ))}
        </ul>
      )}
      {mentioned.length > 0 && (
        <p className="alert info">
          <Icon name="info" />
          <span className="grow">{t('skill.needs.mentioned', { tools: mentioned.map(name).join(', ') })}</span>
          <button type="button" className="btn sm" onClick={() => {
            keepFocus();
            set([...draft.needs, ...mentioned]);
            setNotice(t('skill.needs.added', { tools: mentioned.map(name).join(', '), count: mentioned.length }));
          }}>{t('skill.needs.addMentioned')}</button>
        </p>
      )}
      {others.length > 0 && (
        <div className="field inline-field">
          <label htmlFor={pickId}>{t('skill.needs.add')}</label>
          <span className="pick-row">
            <select id={pickId} ref={pickRef} value={picked} onChange={(event) => setPicked(event.target.value)}>
              <option value="">{t('skill.needs.pick')}</option>
              {others.map((tool) => <option key={tool.id} value={tool.id}>{name(tool.id)}</option>)}
            </select>
            <button type="button" className="btn sm" disabled={!picked} onClick={() => {
              set([...draft.needs, picked]);
              setNotice(t('skill.needs.added', { tools: name(picked), count: 1 }));
              setPicked('');
              keepFocus();
            }}>{t('skill.needs.addPicked')}</button>
          </span>
        </div>
      )}
      <p className="inline-status" role="status">{notice}</p>
      <p className="note"><Icon name="info" size={14} /> {t('skill.needs.note')}</p>
    </div>
  );
}
