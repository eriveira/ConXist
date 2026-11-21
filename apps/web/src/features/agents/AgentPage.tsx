import { useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import type { AgentToolInfo } from '../../domain/agents';
import { localProblems } from '../../domain/agents';
import type { Source } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { AGENTS_HREF } from '../../navigation';
import { AgentSections } from './AgentSections';
import { AgentStatePill } from './AgentStatePill';
import { HistoryDialog } from './HistoryDialog';
import { SaveStatus } from './SaveStatus';
import { blockedBy, useAgentEditor } from './useAgentEditor';

/** What the agent's fields need from the rest of the workspace: its sources, and the tools agents may use. */
interface Choices {
  sources: Source[];
  tools: AgentToolInfo[];
}

/**
 * One agent on one page: what it is, what it uses and how it works. Changes save as a draft on their own; Publish
 * makes the next numbered version, and History puts an earlier version back into the draft.
 */
export function AgentPage({ agentId }: { agentId: string }) {
  const t = useT();
  const editor = useAgentEditor(agentId);
  const { state } = editor;
  const [choices, setChoices] = useState<Choices | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [publishing, setPublishing] = useState(false);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const ready = state.status === 'ready';

  useEffect(() => {
    Promise.all([api.sources(), api.agentTools()])
      .then(([sources, tools]) => setChoices({ sources, tools }))
      .catch(() => setChoices({ sources: [], tools: [] }));
  }, []);

  useEffect(() => {
    if (ready) headingRef.current?.focus();
  }, [ready, agentId]);

  if (state.status === 'loading') {
    return <div className="page-state" role="status"><Icon name="sync" /> {t('agent.loading')}</div>;
  }
  if (state.status === 'error') {
    return (
      <div className="page-state" role="alert">
        <div className="panel">
          <h2>{t(state.missing ? 'agent.missing.title' : 'sources.error.title')}</h2>
          <p>{t(state.missing ? 'agent.missing.body' : 'sources.error.body')}</p>
          {state.missing
            ? <a className="btn primary" href={AGENTS_HREF}>{t('agent.back')}</a>
            : <button type="button" className="btn primary" onClick={() => void editor.reload()}>{t('sources.retry')}</button>}
        </div>
      </div>
    );
  }

  const { agent, draft, save, problem } = state;
  const archived = agent.status === 'ARCHIVED';
  const problems = Array.from(new Set([...localProblems(draft), ...agent.problems]));
  const blocked = blockedBy(draft);
  const canPublish = !archived && problems.length === 0 && !blocked && save !== 'conflict'
    && !(agent.draftPublished && save === 'saved');

  async function publish() {
    setPublishing(true);
    setNotice(null);
    const next = agent.publishedVersion + 1;
    if (await editor.publish()) setNotice(t('agent.published', { version: next }));
    setPublishing(false);
  }

  async function restore(number: number) {
    if (await editor.restore(number)) {
      setHistoryOpen(false);
      setNotice(t('agent.restored', { version: number }));
    }
  }

  return (
    <section className="page agent-page" aria-labelledby="agent-title">
      <a className="back" href={AGENTS_HREF}><Icon name="back" size={16} /> {t('agent.back')}</a>
      <div className="agent-head">
        <div className="agent-head-text">
          <h1 id="agent-title" ref={headingRef} tabIndex={-1}>{draft.name.trim() || agent.draft.name}</h1>
          <div className="agent-head-meta">
            <AgentStatePill agent={agent} />
            <SaveStatus save={save} blocked={blocked !== null} onRetry={editor.retry} onReload={() => void editor.reload()} />
          </div>
        </div>
        <div className="agent-actions">
          {archived
            ? <button type="button" className="btn" onClick={() => void editor.unarchive()}>{t('agent.unarchive')}</button>
            : <button type="button" className="btn ghost" onClick={() => void editor.archive()}>{t('agent.archive')}</button>}
          <button type="button" className="btn" onClick={() => setHistoryOpen(true)} disabled={agent.versions.length === 0}>
            {t('agent.history')}{agent.versions.length > 0 && ` (${agent.versions.length})`}
          </button>
          <button type="button" className="btn primary" onClick={() => void publish()} disabled={!canPublish || publishing}
            aria-describedby={problems.length ? 'agent-problems' : undefined}>
            {publishing ? <><span className="spin" aria-hidden="true" /> {t('agent.publishing')}</> : t('agent.publish')}
          </button>
        </div>
      </div>
      <p className="notice" role="status">{notice && <><Icon name="check" size={16} /> {notice}</>}</p>
      {archived && <p className="alert info"><Icon name="info" /> {t('agent.archived.note')}</p>}
      {problem !== null && save !== 'conflict' && save !== 'failed' && (
        <p className="alert error" role="alert"><Icon name="alert" /> {problemMessage(problem, t)}</p>
      )}
      {problems.length > 0 && !archived && (
        <div id="agent-problems" className="alert info agent-problems">
          <Icon name="info" />
          <div>
            <b>{t('agent.problems.title')}</b>
            <ul>{problems.map((code) => <li key={code}>{t(`agent.problem.${code}` as 'agent.problem.name-required')}</li>)}</ul>
          </div>
        </div>
      )}
      <div className="agent-layout">
        <AgentSections draft={draft} onChange={editor.edit} disabled={archived || save === 'conflict'}
          sources={choices?.sources ?? null} tools={choices?.tools ?? null} />
        <aside className="agent-side">
          <div className="side-card">
            <h2><Icon name="chat" size={18} /> {t('agent.test.title')}</h2>
            <p>{t('agent.test.soon')}</p>
          </div>
          <div className="side-card">
            <h2><Icon name="file" size={18} /> {t('agent.skills.title')}</h2>
            <p>{t('agent.skills.soon')}</p>
          </div>
        </aside>
      </div>
      {historyOpen && (
        <HistoryDialog agent={agent} hasUnpublished={!agent.draftPublished || save !== 'saved'}
          onRestore={restore} onClose={() => setHistoryOpen(false)} />
      )}
    </section>
  );
}
