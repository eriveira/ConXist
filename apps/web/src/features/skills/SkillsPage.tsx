import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { AgentSummary } from '../../domain/agents';
import { SKILL_FILTERS, type SkillFilter, type SkillSummary, sortSkills, usersOf } from '../../domain/skills';
import { useLanguage, toolLabel, useT } from '../../i18n/i18n';
import { NEW_SKILL_HREF, SKILLS_HREF, skillHref } from '../../navigation';
import { AgentStatePill } from '../agents/AgentStatePill';
import { ImportSkillDialog } from './ImportSkillDialog';
import { NewSkillDialog } from './NewSkillDialog';

type ListState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; skills: SkillSummary[]; agents: AgentSummary[] };

/**
 * Every skill the workspace can use: the built-in ones, the team's own and imported ones. New writes one; Import
 * brings in a SKILL.md.
 */
export function SkillsPage({ creating }: { creating: boolean }) {
  const t = useT();
  const language = useLanguage();
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [importing, setImporting] = useState(false);
  const [filter, setFilter] = useState<SkillFilter>('ALL');
  const [showArchived, setShowArchived] = useState(false);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      const [skills, agents] = await Promise.all([api.skills(), api.agents()]);
      setState({ status: 'ready', skills, agents });
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  // Built-in skills come in the person's language, so a new language reloads them.
  useEffect(() => { void load(); }, [load, language]);

  const open = (skillId: string) => { window.location.hash = skillHref(skillId); };

  return (
    <section className="page agents-page" aria-labelledby="skills-title">
      <div className="page-h">
        <h1 id="skills-title">{t('skills.title')}</h1>
        <div className="page-h-actions">
          <button type="button" className="btn" onClick={() => setImporting(true)}>
            <Icon name="upload" /> {t('skills.import')}
          </button>
          <a className="btn primary" href={NEW_SKILL_HREF}><Icon name="plus" /> {t('skills.new')}</a>
        </div>
        <p>{t('skills.lead')}</p>
      </div>
      {state.status === 'loading' && <div className="page-state" role="status"><Icon name="sync" /> {t('skills.loading')}</div>}
      {state.status === 'error' && (
        <div className="page-state" role="alert">
          <div className="panel">
            <h2>{t('sources.error.title')}</h2>
            <p>{t('sources.error.body')}</p>
            <button type="button" className="btn primary" onClick={() => void load()}>{t('sources.retry')}</button>
          </div>
        </div>
      )}
      {state.status === 'ready' && (
        <SkillList skills={state.skills} agents={state.agents} filter={filter} onFilter={setFilter}
          showArchived={showArchived} onShowArchived={setShowArchived} />
      )}
      {creating && <NewSkillDialog onClose={() => { window.location.hash = SKILLS_HREF; }} onCreated={open} />}
      {importing && <ImportSkillDialog onClose={() => setImporting(false)} onImported={open} />}
    </section>
  );
}

interface SkillListProps {
  skills: SkillSummary[];
  agents: AgentSummary[];
  filter: SkillFilter;
  onFilter: (filter: SkillFilter) => void;
  showArchived: boolean;
  onShowArchived: (show: boolean) => void;
}

function SkillList({ skills, agents, filter, onFilter, showArchived, onShowArchived }: SkillListProps) {
  const t = useT();
  const archived = skills.filter((skill) => skill.status === 'ARCHIVED').length;
  const shown = sortSkills(skills).filter((skill) =>
    (filter === 'ALL' || skill.source === filter) && (showArchived || skill.status !== 'ARCHIVED'));

  return (
    <>
      <div className="list-tools">
        <div className="filters" role="group" aria-label={t('skills.filter')}>
          {SKILL_FILTERS.map((each) => (
            <button key={each} type="button" aria-pressed={filter === each} onClick={() => onFilter(each)}>
              {t(`skills.filter.${each}`)}
            </button>
          ))}
        </div>
        {archived > 0 && (
          <label className="show-archived">
            <input type="checkbox" checked={showArchived} onChange={(event) => onShowArchived(event.target.checked)} />
            {t('skills.showArchived', { count: archived })}
          </label>
        )}
      </div>
      {shown.length === 0 && <p className="muted">{t('skills.none')}</p>}
      <ul className="agent-list">
        {shown.map((skill) => {
          const users = usersOf(skill.id, agents).length;
          return (
            <li key={skill.id}>
              <a className="agent-card" href={skillHref(skill.id)}>
                <span className="nico"><Icon name="skill" /></span>
                <span className="agent-card-text">
                  <span className="agent-card-top">
                    <b>{skill.name}</b>
                    <Pill tone={skill.source === 'BUILT_IN' ? 'soon' : 'info'}>{t(`skill.source.${skill.source}`)}</Pill>
                    {skill.source === 'BUILT_IN' ? <Pill tone="live">v{skill.publishedVersion}</Pill> : <AgentStatePill agent={skill} kind="skill" />}
                  </span>
                  {skill.description && <span className="agent-card-purpose">{skill.description}</span>}
                  <span className="agent-card-meta">
                    {skill.needs.length > 0
                      ? t('skills.needs', { tools: skill.needs.map((need) => toolLabel(t, need)).join(', ') })
                      : t('skills.needsNothing')}
                    {' · '}
                    {users > 0 ? t('skills.usedBy', { count: users }) : t('skills.notUsed')}
                  </span>
                </span>
                <Icon name="chevron" />
              </a>
            </li>
          );
        })}
      </ul>
    </>
  );
}
