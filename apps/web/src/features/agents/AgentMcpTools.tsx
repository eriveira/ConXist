import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import type { AgentDefinition } from '../../domain/agents';
import {
  allowedTools, MAX_MCP_TOOLS_PER_AGENT, type McpServer, type McpToolPick, pickAllowed, samePick,
} from '../../domain/mcps';
import { useT } from '../../i18n/i18n';
import { settingsHref } from '../../navigation';
import { McpLogo } from '../mcps/McpParts';

/**
 * The tools of the workspace's MCP servers and integrations that agents may use, one group per server (ADR 0017,
 * ADR 0019). Tools that change things carry a label: the workspace allowed them after a warning. A pick the workspace
 * no longer allows shows as a problem with a way to remove it. Descriptions come from the server: shown as text.
 */
export function AgentMcpTools({ draft, servers, onChange }: {
  draft: AgentDefinition; servers: McpServer[]; onChange: (change: Partial<AgentDefinition>) => void;
}) {
  const t = useT();
  const usable = servers.filter((server) => allowedTools(server).length > 0);
  const gone = draft.mcpTools.filter((pick) => !pickAllowed(servers, pick));
  const full = draft.mcpTools.length >= MAX_MCP_TOOLS_PER_AGENT;
  const has = (pick: McpToolPick) => draft.mcpTools.some((each) => samePick(each, pick));
  const toggle = (pick: McpToolPick) => onChange({
    mcpTools: has(pick) ? draft.mcpTools.filter((each) => !samePick(each, pick)) : [...draft.mcpTools, pick],
  });

  return (
    <>
      {usable.map((server) => (
        <fieldset key={server.id} className="choice mcp-group">
          <legend>
            <McpLogo name={server.name} icon={server.icon} size="sm" /> {server.name}{' '}
            <Pill tone="info">{t('kind' in server && server.kind === 'api' ? 'integration.kind' : 'agent.tools.mcp')}</Pill>
          </legend>
          {allowedTools(server).map((tool) => {
            const pick = { serverId: server.id, tool: tool.name };
            const on = has(pick);
            return (
              <label key={tool.name}>
                <input type="checkbox" checked={on} disabled={!on && full} onChange={() => toggle(pick)} />
                <span className="grow">
                  <b className="mono">{tool.name}</b>
                  {(tool.description || tool.title) && <small>{tool.description || tool.title}</small>}
                </span>
                {tool.readOnly
                  ? <Pill tone="live">{t('agent.tool.readOnly')}</Pill>
                  : <Pill tone="preview">{t('mcp.tool.changes')}</Pill>}
              </label>
            );
          })}
        </fieldset>
      ))}
      {gone.map((pick) => (
        <p key={`${pick.serverId}/${pick.tool}`} className="alert error">
          <Icon name="alert" /> <span className="grow">{t('agent.tools.mcpGone', { tool: pick.tool })}</span>
          <button type="button" className="link" onClick={() => toggle(pick)}>{t('agent.knowledge.remove')}</button>
        </p>
      ))}
      {full && <p className="hint">{t('agent.tools.mcpFull', { max: MAX_MCP_TOOLS_PER_AGENT })}</p>}
      <p className="hint">
        {t('agent.tools.mcpMore')} <a href={settingsHref('mcps')}>{t('agent.tools.mcpSettings')}</a>{' '}
        {t('agent.tools.integrationMore')} <a href={settingsHref('integrations')}>{t('agent.tools.integrationSettings')}</a>.
      </p>
    </>
  );
}
