import { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { useNotice } from '../../components/useNotice';
import { type AgentDefinition, type AgentToolInfo, localProblems } from '../../domain/agents';
import { type PinnedSkill, SKILL_PROBLEMS, type SkillSummary, skillRowProblems, skillRows } from '../../domain/skills';
import type { Source } from '../../domain/model';
import { type LlmProviders, modelAvailable } from '../../domain/settings';
import { asToolServer } from '../../domain/integrations';
import { changingPicks, type McpServer, pickAllowed } from '../../domain/mcps';
import type { Variable } from '../../domain/variables';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { AGENTS_HREF, goWithNotice } from '../../navigation';
import { AgentSections } from './AgentSections';
import { AgentStatePill } from './AgentStatePill';
import { AgentTestPanel, type RunBlock } from './AgentTestPanel';
import { DeleteDialog } from './DeleteDialog';
import { AgentVersionSummary, HistoryDialog } from './HistoryDialog';
import { SaveStatus } from './SaveStatus';
import { type ActResult, blockedBy, useAgentEditor } from './useAgentEditor';

/** What the agent's fields need from the rest of the workspace: its sources, its skills and the tools agents may use. */
export type Choices =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; sources: Source[]; tools: AgentToolInfo[]; skills: SkillSummary[]; providers: LlmProviders; mcps: McpServer[] | null;
      variables: Variable[] | null };

type Busy = 'publish' | 'archive' | null;

/**
 * One agent on one page: what it is, what it uses and how it works. Changes save as a draft on their own; Publish
 * makes the next numbered version, and History puts an earlier version back into the draft.
 */
export function AgentPage({ agentId }: { agentId: string }) {
  const t = useT();
  const editor = useAgentEditor(agentId);
  const { state } = editor;
  const [choices, setChoices] = useState<Choices>({ status: 'loading' });
  const [historyOpen, setHistoryOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [notice, setNotice] = useNotice();
  const [actionProblem, setActionProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState<Busy>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const ready = state.status === 'ready';

  const loadChoices = useCallback(() => {
    setChoices({ status: 'loading' });
    Promise.all([api.sources(), api.agentTools(), api.skills(), api.llmProviders(),
      // MCP servers and integrations load on their own terms: when they can't, the rest of the agent still edits.
      // Agents pick an integration's tools as they pick an MCP server's (ADR 0019), so both list as tool servers.
      // Both or neither: a pick is judged allowed only against the full list, never half of it.
      Promise.all([api.integrations(), api.mcps()])
        .then(([integrations, mcps]) => [...integrations.map(asToolServer), ...mcps])
        .catch(() => null),
      api.variables().catch(() => null)])
      .then(([sources, tools, skills, providers, mcps, variables]) =>
        setChoices({ status: 'ready', sources, tools, skills, providers, mcps, variables }))
      .catch(() => setChoices({ status: 'error' }));
  }, []);

  useEffect(loadChoices, [loadChoices]);
  const loadAgentUses = useCallback(() => api.agentUses(agentId), [agentId]);

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

  const { item: agent, draft, save, problem } = state;
  const archived = agent.status === 'ARCHIVED';
  const problems = Array.from(new Set([...localProblems(draft), ...currentProblems(agent.problems, draft, agent.skills, choices)]));
  const blocked = blockedBy(draft);
  const stuck = blocked !== null || save === 'conflict';
  const runBlock: RunBlock = archived ? 'archived' : problems.length > 0 ? 'problems'
    : save === 'waiting' || save === 'saving' ? 'saving' : save !== 'saved' || blocked !== null ? 'unsaved' : null;
  const canPublish = !archived && problems.length === 0 && !stuck && save !== 'failed'
    && !(agent.draftPublished && save === 'saved');

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

  const publish = () => run('publish', editor.publish, t('agent.published', { version: agent.publishedVersion + 1 }));
  const restore = async (number: number) => {
    if (await run(null, () => editor.restore(number), t('agent.restored', { version: number }))) setHistoryOpen(false);
  };
  // A change on its way to the server would change the revision under the delete, so it waits for it.
  const canDelete = busy === null && save !== 'conflict' && save !== 'saving' && (save !== 'waiting' || blocked !== null);

  return (
    <section className="page agent-page" aria-labelledby="agent-title">
      <a className="back" href={AGENTS_HREF}><Icon name="back" size={16} /> {t('agent.back')}</a>
      <div className="agent-head">
        <div className="agent-head-text">
          <h1 id="agent-title" ref={headingRef} tabIndex={-1}>{draft.name.trim() || agent.draft.name}</h1>
          <div className="agent-head-meta">
            <AgentStatePill agent={agent} />
            <SaveStatus save={save} problem={problem} blocked={blocked !== null} onRetry={editor.retry}
              onReload={() => void editor.reload()} />
          </div>
        </div>
        <div className="agent-actions">
          <button type="button" className="btn ghost danger-text" disabled={!canDelete} onClick={() => setDeleting(true)}>
            <Icon name="trash" size={16} /> {t('delete.button')}
          </button>
          {archived ? (
            <button type="button" className="btn" disabled={busy !== null}
              onClick={() => void run('archive', editor.unarchive, t('agent.unarchived'))}>
              {busy === 'archive' ? <><span className="spin" aria-hidden="true" /> {t('agent.unarchiving')}</> : t('agent.unarchive')}
            </button>
          ) : (
            <button type="button" className="btn ghost" disabled={busy !== null || stuck}
              onClick={() => void run('archive', editor.archive, t('agent.archivedNotice'))}>
              {busy === 'archive' ? <><span className="spin" aria-hidden="true" /> {t('agent.archiving')}</> : t('agent.archive')}
            </button>
          )}
          <button type="button" className="btn" onClick={() => setHistoryOpen(true)} disabled={agent.versions.length === 0}>
            {t('agent.history')}{agent.versions.length > 0 && ` (${agent.versions.length})`}
          </button>
          <button type="button" className="btn primary" onClick={() => void publish()} disabled={!canPublish || busy !== null}
            aria-describedby={problems.length ? 'agent-problems' : undefined}>
            {busy === 'publish' ? <><span className="spin" aria-hidden="true" /> {t('agent.publishing')}</> : t('agent.publish')}
          </button>
        </div>
      </div>
      <p className="notice" role="status">{notice && <><Icon name="check" size={16} /> {notice}</>}</p>
      {archived && <p className="alert info"><Icon name="info" /> {t('agent.archived.note')}</p>}
      {(actionProblem ?? (problem !== null && save !== 'conflict' && save !== 'failed')) && (
        <p className="alert error" role="alert"><Icon name="alert" /> {actionProblem ?? problemMessage(problem, t)}</p>
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
        <AgentSections draft={draft} pinned={agent.skills} onChange={editor.edit} disabled={archived || save === 'conflict'}
          choices={choices} onRetryChoices={loadChoices} />
        <aside className="agent-side">
          <AgentTestPanel agentId={agent.id} reads={draft.knowledgeSources} givesBack={draft.givesBack}
            sources={choices.status === 'ready' ? choices.sources : null} block={runBlock}
            provider={draft.model?.provider ?? null}
            changes={choices.status === 'ready' && choices.mcps ? changingPicks(choices.mcps, draft.mcpTools) : []} />
        </aside>
      </div>
      {historyOpen && (
        <HistoryDialog name={agent.draft.name} versions={agent.versions} publishedVersion={agent.publishedVersion}
          loadVersion={(number) => api.agentVersion(agent.id, number)}
          renderVersion={(version) => <AgentVersionSummary version={version}
            providers={choices.status === 'ready' ? choices.providers : null} />}
          archivedNote={t('agent.history.archived')} hasUnpublished={!agent.draftPublished || save !== 'saved'}
          archived={archived} blocked={stuck} onRestore={restore} onClose={() => setHistoryOpen(false)} />
      )}
      {deleting && (
        <DeleteDialog what="agent" name={agent.draft.name} versions={agent.versions.length} archived={archived}
          loadUses={loadAgentUses} remove={() => api.deleteAgent(agent.id, agent.revision)}
          onArchive={() => { setDeleting(false); void run('archive', editor.archive, t('agent.archivedNotice')); }}
          onDeleted={() => goWithNotice(AGENTS_HREF, t('delete.done', { name: agent.draft.name }))}
          onClose={() => setDeleting(false)} />
      )}
    </section>
  );
}

const MODEL_PROBLEMS = ['model-required', 'model-unavailable'];
const MCP_PROBLEM = 'mcp-tool-unavailable';

/**
 * The server's problems describe the saved draft. Once the choices are in, the skill, model and MCP tool problems are
 * worked out from what is on screen instead, so adding a tool, removing a skill or picking a model clears them at once.
 */
function currentProblems(saved: string[], draft: AgentDefinition, pinned: PinnedSkill[], choices: Choices): string[] {
  if (choices.status !== 'ready') return saved;
  const modelProblem = draft.model && !modelAvailable(choices.providers, draft.model) ? ['model-unavailable'] : [];
  const mcps = choices.mcps;
  if (mcps === null) return [...saved.filter((code) => !SKILL_PROBLEMS.includes(code) && !MODEL_PROBLEMS.includes(code)),
    ...modelProblem, ...skillRowProblems(skillRows(draft.skills, pinned, choices.skills), draft.tools)];
  const mcpProblem = draft.mcpTools.some((pick) => !pickAllowed(mcps, pick)) ? [MCP_PROBLEM] : [];
  return [...saved.filter((code) => !SKILL_PROBLEMS.includes(code) && !MODEL_PROBLEMS.includes(code) && code !== MCP_PROBLEM),
    ...modelProblem, ...mcpProblem, ...skillRowProblems(skillRows(draft.skills, pinned, choices.skills), draft.tools)];
}
