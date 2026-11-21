import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { AgentSummary, AgentToolInfo } from '../../domain/agents';
import { localSkillProblems, type SkillVersion } from '../../domain/skills';
import { useLanguage, useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { SKILLS_HREF, skillHref } from '../../navigation';
import { AgentStatePill } from '../agents/AgentStatePill';
import { HistoryDialog } from '../agents/HistoryDialog';
import { SaveStatus } from '../agents/SaveStatus';
import type { ActResult } from '../agents/useDraftEditor';
import { PublishSkillDialog } from './PublishSkillDialog';
import { SkillSections } from './SkillSections';
import { SkillSide } from './SkillSide';
import { skillBlockedBy, useSkillEditor } from './useSkillEditor';

/** What the skill page needs from the rest of the workspace: the tools agents may use, and the agents. */
export type SkillContext =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; tools: AgentToolInfo[]; agents: AgentSummary[] };

type Busy = 'archive' | 'copy' | null;

/**
 * One skill on one page: when to use it, its steps and the tools it expects. Changes save as a draft on their own;
 * Publish makes the next numbered version, which agents take when someone updates them. A built-in skill is read only
 * and can be copied.
 */
export function SkillPage({ skillId }: { skillId: string }) {
  const t = useT();
  const language = useLanguage();
  const editor = useSkillEditor(skillId);
  const { state } = editor;
  const [context, setContext] = useState<SkillContext>({ status: 'loading' });
  const [historyOpen, setHistoryOpen] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [actionProblem, setActionProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState<Busy>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const ready = state.status === 'ready';
  const { reload } = editor;

  const loadContext = useCallback(() => {
    setContext({ status: 'loading' });
    Promise.all([api.agentTools(), api.agents()])
      .then(([tools, agents]) => setContext({ status: 'ready', tools, agents }))
      .catch(() => setContext({ status: 'error' }));
  }, []);

  useEffect(loadContext, [loadContext]);

  // A built-in skill is written in the person's language; read it again when the language changes.
  const firstLanguage = useRef(language);
  useEffect(() => {
    if (language !== firstLanguage.current) void reload();
  }, [language, reload]);

  useEffect(() => {
    if (ready) headingRef.current?.focus();
  }, [ready, skillId]);

  if (state.status === 'loading') {
    return <div className="page-state" role="status"><Icon name="sync" /> {t('agent.loading')}</div>;
  }
  if (state.status === 'error') {
    return (
      <div className="page-state" role="alert">
        <div className="panel">
          <h2>{t(state.missing ? 'skill.missing.title' : 'sources.error.title')}</h2>
          <p>{t(state.missing ? 'skill.missing.body' : 'sources.error.body')}</p>
          {state.missing
            ? <a className="btn primary" href={SKILLS_HREF}>{t('skill.back')}</a>
            : <button type="button" className="btn primary" onClick={() => void editor.reload()}>{t('sources.retry')}</button>}
        </div>
      </div>
    );
  }

  const { item: skill, draft, save, problem } = state;
  const builtIn = skill.source === 'BUILT_IN';
  const archived = skill.status === 'ARCHIVED';
  const problems = builtIn ? [] : Array.from(new Set([...localSkillProblems(draft), ...skill.problems]));
  const blocked = skillBlockedBy(draft);
  const stuck = blocked !== null || save === 'conflict';
  const canPublish = !builtIn && !archived && problems.length === 0 && !stuck && save !== 'failed'
    && !(skill.draftPublished && save === 'saved');

  /** Runs an action and says what happened; a refused action leaves everything as it was. */
  async function run(kind: Busy, action: () => Promise<ActResult>, done: string): Promise<boolean> {
    setBusy(kind);
    setNotice(null);
    setActionProblem(null);
    const result = await action();
    setBusy(null);
    if (result === 'ok') setNotice(done);
    else if (result === 'blocked') setActionProblem(t('agent.action.blocked'));
    return result === 'ok';
  }

  async function copy() {
    setBusy('copy');
    setActionProblem(null);
    try {
      window.location.hash = skillHref((await api.copySkill(skill.id)).id);
    } catch (error) {
      setActionProblem(problemMessage(error, t));
      setBusy(null);
    }
  }

  /** Whatever happens, the dialog closes, so the result or the reason shows on the page. */
  const publish = async (note: string) => {
    await run(null, () => editor.publish(note), t('skill.published', { version: skill.publishedVersion + 1 }));
    setPublishing(false);
  };
  const restore = async (number: number) => {
    if (await run(null, () => editor.restore(number), t('agent.restored', { version: number }))) setHistoryOpen(false);
  };

  return (
    <section className="page agent-page skill-page" aria-labelledby="skill-title">
      <a className="back" href={SKILLS_HREF}><Icon name="back" size={16} /> {t('skill.back')}</a>
      <div className="agent-head">
        <div className="agent-head-text">
          <h1 id="skill-title" ref={headingRef} tabIndex={-1}>{draft.name.trim() || skill.draft.name}</h1>
          <div className="agent-head-meta">
            <Pill tone={builtIn ? 'soon' : 'info'}>{t(`skill.source.${skill.source}`)}</Pill>
            {builtIn ? <Pill tone="live">v{skill.publishedVersion}</Pill> : <AgentStatePill agent={skill} kind="skill" />}
            {!builtIn && (
              <SaveStatus save={save} problem={problem} blocked={blocked !== null} onRetry={editor.retry}
                onReload={() => void editor.reload()} />
            )}
          </div>
        </div>
        <div className="agent-actions">
          {builtIn ? (
            <button type="button" className="btn primary" onClick={() => void copy()} disabled={busy !== null}>
              {busy === 'copy' ? <><span className="spin" aria-hidden="true" /> {t('skill.copying')}</> : t('skill.copy')}
            </button>
          ) : (
            <>
              {archived ? (
                <button type="button" className="btn" disabled={busy !== null}
                  onClick={() => void run('archive', editor.unarchive, t('skill.unarchived'))}>
                  {busy === 'archive' ? <><span className="spin" aria-hidden="true" /> {t('agent.unarchiving')}</> : t('agent.unarchive')}
                </button>
              ) : (
                <button type="button" className="btn ghost" disabled={busy !== null || stuck}
                  onClick={() => void run('archive', editor.archive, t('skill.archivedNotice'))}>
                  {busy === 'archive' ? <><span className="spin" aria-hidden="true" /> {t('agent.archiving')}</> : t('agent.archive')}
                </button>
              )}
              <button type="button" className="btn" onClick={() => setHistoryOpen(true)} disabled={skill.versions.length === 0}>
                {t('agent.history')}{skill.versions.length > 0 && ` (${skill.versions.length})`}
              </button>
              <button type="button" className="btn primary" onClick={() => setPublishing(true)}
                disabled={!canPublish || busy !== null} aria-describedby={problems.length ? 'skill-problems' : undefined}>
                {t('agent.publish')}
              </button>
            </>
          )}
        </div>
      </div>
      <p className="notice" role="status">{notice && <><Icon name="check" size={16} /> {notice}</>}</p>
      {builtIn && <p className="alert info"><Icon name="info" /> {t('skill.builtIn.note')}</p>}
      {archived && <p className="alert info"><Icon name="info" /> {t('skill.archived.note')}</p>}
      {(actionProblem ?? (problem !== null && save !== 'conflict' && save !== 'failed')) && (
        <p className="alert error" role="alert"><Icon name="alert" /> {actionProblem ?? problemMessage(problem, t)}</p>
      )}
      {problems.length > 0 && !archived && (
        <div id="skill-problems" className="alert info agent-problems">
          <Icon name="info" />
          <div>
            <b>{t('agent.problems.title')}</b>
            <ul>{problems.map((code) => <li key={code}>{t(`skill.problem.${code}` as 'skill.problem.name-required')}</li>)}</ul>
          </div>
        </div>
      )}
      <div className="agent-layout">
        <SkillSections draft={draft} onChange={editor.edit} readOnly={builtIn}
          disabled={archived || save === 'conflict'} context={context} onRetryContext={loadContext} />
        <SkillSide skill={skill} draft={draft} context={context} />
      </div>
      {historyOpen && (
        <HistoryDialog name={skill.draft.name} versions={skill.versions} publishedVersion={skill.publishedVersion}
          loadVersion={(number) => api.skillVersion(skill.id, number)}
          renderVersion={(version: SkillVersion) => <pre className="skill-md">{version.definition.instructions || '—'}</pre>}
          archivedNote={t('skill.history.archived')} hasUnpublished={!skill.draftPublished || save !== 'saved'}
          archived={archived} blocked={stuck} onRestore={restore} onClose={() => setHistoryOpen(false)} />
      )}
      {publishing && (
        <PublishSkillDialog skill={skill} context={context} onPublish={publish} onClose={() => setPublishing(false)} />
      )}
    </section>
  );
}

