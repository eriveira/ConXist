import { useState } from 'react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { usePopover } from '../../components/usePopover';
import type { AgentDefinition, AgentToolInfo } from '../../domain/agents';
import { MAX_SKILLS, type PinnedSkill, type SkillRow, skillRows, type SkillSummary } from '../../domain/skills';
import { useT } from '../../i18n/i18n';
import { NEW_SKILL_HREF, SKILLS_HREF, skillHref } from '../../navigation';

interface AgentSkillsProps {
  draft: AgentDefinition;
  /** What the server said about the pins it saved. */
  pinned: PinnedSkill[];
  skills: SkillSummary[];
  tools: AgentToolInfo[];
  onChange: (change: Partial<AgentDefinition>) => void;
}

/**
 * The skills an agent follows, each at a fixed version. Adding one turns on the tools it needs, since they only read;
 * a newer version shows as an update the person takes when ready.
 */
export function AgentSkills({ draft, pinned, skills, tools, onChange }: AgentSkillsProps) {
  const t = useT();
  const popover = usePopover();
  const [notice, setNotice] = useState<string | null>(null);
  const rows = skillRows(draft.skills, pinned, skills);
  const toolName = (id: string) => t(`agent.tool.${id}` as 'agent.tool.GET_TRANSCRIPT');
  const addable = skills.filter((skill) => skill.status === 'ACTIVE' && skill.publishedVersion > 0
    && !draft.skills.some((pin) => pin.skillId === skill.id));
  const full = draft.skills.length >= MAX_SKILLS;

  function add(skill: SkillSummary) {
    popover.setOpen(false);
    popover.button.current?.focus();
    const turnedOn = skill.needs.filter((need) => !draft.tools.includes(need) && tools.some((tool) => tool.id === need));
    onChange({
      skills: [...draft.skills, { skillId: skill.id, version: skill.publishedVersion }],
      tools: tools.map((tool) => tool.id).filter((id) => draft.tools.includes(id) || turnedOn.includes(id)),
    });
    setNotice(turnedOn.length
      ? t('agent.skills.addedWithTools', { name: skill.name, tools: turnedOn.map(toolName).join(', ') })
      : t('agent.skills.added', { name: skill.name }));
  }

  function remove(row: SkillRow) {
    onChange({ skills: draft.skills.filter((pin) => pin.skillId !== row.pin.skillId) });
    setNotice(row.skill ? t('agent.skills.removed', { name: row.skill.name }) : null);
  }

  function update(row: SkillRow) {
    if (!row.skill) return;
    const newest = row.skill.newestVersion;
    onChange({ skills: draft.skills.map((pin) => pin.skillId === row.pin.skillId ? { ...pin, version: newest } : pin) });
    setNotice(t('agent.skills.updated', { name: row.skill.name, version: newest }));
  }

  function turnOn(need: string) {
    onChange({ tools: tools.map((tool) => tool.id).filter((id) => draft.tools.includes(id) || id === need) });
  }

  return (
    <>
      {rows.length === 0 && <p className="muted">{t('agent.skills.none')}</p>}
      {rows.length > 0 && (
        <ul className="skill-rows">
          {rows.map((row) => (
            <li key={row.pin.skillId} className="skill-row">
              <span className="nico sm"><Icon name="skill" size={18} /></span>
              {row.skill ? (
                <span className="grow">
                  <span className="skill-row-top">
                    <b>{row.skill.name}</b>
                    <Pill tone={row.skill.source === 'BUILT_IN' ? 'soon' : 'info'}>
                      {t(`skill.source.${row.skill.source}`)} · v{row.pin.version}
                    </Pill>
                  </span>
                  <small>{row.skill.description}</small>
                  {row.skill.needs.filter((need) => !draft.tools.includes(need)).map((need) => (
                    <span key={need} className="skill-warn">
                      <Icon name="alert" size={14} /> {t('agent.skills.needs', { tool: toolName(need) })}
                      <button type="button" className="link" onClick={() => turnOn(need)}
                        aria-label={t('agent.skills.turnOnLabel', { tool: toolName(need) })}>{t('agent.skills.turnOn')}</button>
                    </span>
                  ))}
                  <span className="skill-row-links">
                    {row.skill.newestVersion > row.pin.version && (
                      <>
                        <span>{t('agent.skills.available', { version: row.skill.newestVersion })}</span>
                        <button type="button" className="link" onClick={() => update(row)}
                          aria-label={t('agent.skills.updateLabel', { name: row.skill.name, version: row.skill.newestVersion })}>
                          {t('agent.skills.update')}
                        </button>
                        <span aria-hidden="true">·</span>
                      </>
                    )}
                    <a href={skillHref(row.pin.skillId)} aria-label={t('agent.skills.openLabel', { name: row.skill.name })}>
                      {t('agent.skills.open')}
                    </a>
                  </span>
                </span>
              ) : (
                <span className="grow skill-warn"><Icon name="alert" size={14} /> {t('agent.skills.missing')}</span>
              )}
              <button type="button" className="icon-btn" onClick={() => remove(row)}
                aria-label={t('agent.skills.removeLabel', { name: row.skill?.name ?? t('agent.skills.missingName') })}>
                <Icon name="close" size={18} />
              </button>
            </li>
          ))}
        </ul>
      )}
      <div className="add skill-add" ref={popover.root} onKeyDown={popover.onKeyDown} onBlur={popover.onBlur}>
        <button ref={popover.button} type="button" className="btn sm" aria-expanded={popover.open} aria-haspopup="dialog"
          onClick={popover.toggle} disabled={full}>
          <Icon name="plus" size={16} /> {t('agent.skills.add')}
        </button>
        {full && <span className="hint">{t('agent.skills.full', { max: MAX_SKILLS })}</span>}
        {popover.open && (
          <div className="menu skill-menu" role="group" aria-label={t('agent.skills.menu')} data-popover>
            <div className="menu-h">{t('agent.skills.menu')}</div>
            {addable.length === 0 && <p className="muted menu-empty">{t('agent.skills.allAdded')}</p>}
            {addable.map((skill) => (
              <button key={skill.id} type="button" className="mitem" onClick={() => add(skill)}>
                <span className="mtext">
                  <span className="mname">{skill.name}</span>
                  <span className="mdesc">{t(`skill.source.${skill.source}`)} · {skill.description}</span>
                </span>
              </button>
            ))}
            <div className="menu-sep" />
            <a className="mitem" href={NEW_SKILL_HREF}>
              <span className="mtext"><span className="mname accent">{t('agent.skills.new')}</span>
                <span className="mdesc">{t('agent.skills.new.desc')}</span></span>
            </a>
            <a className="mitem" href={SKILLS_HREF}>
              <span className="mtext"><span className="mname">{t('agent.skills.browse')}</span>
                <span className="mdesc">{t('agent.skills.browse.desc')}</span></span>
            </a>
          </div>
        )}
      </div>
      <p className="notice inline" role="status">{notice}</p>
      <p className="note"><Icon name="lock" size={14} /> {t('agent.skills.note')}</p>
    </>
  );
}
