import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { type AgentSummary, sortAgents } from '../../domain/agents';
import { useT } from '../../i18n/i18n';
import { agentHref } from '../../navigation';
import { AgentStatePill } from './AgentStatePill';
import { NewAgentDialog } from './NewAgentDialog';

type ListState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; agents: AgentSummary[] };

/** The workspace's agents, each opening its page, and New to start one from a template. */
export function AgentsPage() {
  const t = useT();
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [creating, setCreating] = useState(false);
  const [showArchived, setShowArchived] = useState(false);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', agents: await api.agents() });
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const created = (agentId: string) => {
    setCreating(false);
    window.location.hash = agentHref(agentId);
  };

  return (
    <section className="page agents-page" aria-labelledby="agents-title">
      <div className="page-h">
        <h1 id="agents-title">{t('agents.title')}</h1>
        <button type="button" className="btn primary" onClick={() => setCreating(true)}>
          <Icon name="plus" /> {t('agents.new')}
        </button>
        <p>{t('agents.lead')}</p>
      </div>
      {state.status === 'loading' && <div className="page-state" role="status"><Icon name="sync" /> {t('agents.loading')}</div>}
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
        <AgentList agents={state.agents} showArchived={showArchived} onShowArchived={setShowArchived}
          onNew={() => setCreating(true)} />
      )}
      {creating && <NewAgentDialog onClose={() => setCreating(false)} onCreated={created} />}
    </section>
  );
}

interface AgentListProps {
  agents: AgentSummary[];
  showArchived: boolean;
  onShowArchived: (show: boolean) => void;
  onNew: () => void;
}

function AgentList({ agents, showArchived, onShowArchived, onNew }: AgentListProps) {
  const t = useT();
  const archived = agents.filter((agent) => agent.status === 'ARCHIVED').length;
  const shown = sortAgents(agents).filter((agent) => showArchived || agent.status !== 'ARCHIVED');

  if (agents.length === 0) {
    return (
      <button type="button" className="empty empty-action" onClick={onNew}>
        <span className="nico"><Icon name="agents" /></span>
        <span className="empty-title">{t('agents.empty.title')}</span>
        <span className="empty-body">{t('agents.empty.body')}</span>
      </button>
    );
  }
  return (
    <>
      {archived > 0 && (
        <label className="show-archived">
          <input type="checkbox" checked={showArchived} onChange={(event) => onShowArchived(event.target.checked)} />
          {t('agents.showArchived', { count: archived })}
        </label>
      )}
      <ul className="agent-list">
        {shown.map((agent) => (
          <li key={agent.id}>
            <a className="agent-card" href={agentHref(agent.id)}>
              <span className="nico"><Icon name="agents" /></span>
              <span className="agent-card-text">
                <span className="agent-card-top">
                  <b>{agent.name}</b>
                  <AgentStatePill agent={agent} />
                </span>
                {agent.purpose && <span className="agent-card-purpose">{agent.purpose}</span>}
                <span className="agent-card-meta">{t('agents.givesBack', { kind: t(`agent.output.${agent.givesBack}`) })}</span>
              </span>
              <Icon name="chevron" />
            </a>
          </li>
        ))}
      </ul>
    </>
  );
}
