import { useId, useState } from 'react';
import { Pill } from '../../components/Pill';
import { type McpServer, type McpStatus, type McpTool, safeIcon } from '../../domain/mcps';
import { useT } from '../../i18n/i18n';

/** The server's logo, or the first letter of its name when it gives none. Decorative: its name is beside it. */
export function McpLogo({ name, icon, size = 'md' }: { name: string; icon: string | null; size?: 'sm' | 'md' }) {
  const [broken, setBroken] = useState<string | null>(null);
  const src = safeIcon(icon);
  return (
    <span className={`mcp-logo ${size}`} aria-hidden="true">
      {src && broken !== src ? <img src={src} alt="" onError={() => setBroken(src)} /> : (name.trim().charAt(0) || 'M').toUpperCase()}
    </span>
  );
}

export function McpStatusPill({ status }: { status: McpStatus }) {
  const t = useT();
  return <Pill tone={status === 'WORKS' ? 'live' : 'error'}>{t(`mcp.status.${status}`)}</Pill>;
}

/**
 * The tools a server lists, with a box to allow each. Only read tools can be ticked; a tool agents use stays ticked
 * until it is turned off in those agents. Names and descriptions are the server's text, shown as text only.
 */
export function McpToolTable({ server, tools, allowed, busy, fresh, onToggle }: {
  server: Pick<McpServer, 'name'>; tools: readonly McpTool[]; allowed: ReadonlySet<string>; busy?: string | null;
  fresh?: ReadonlySet<string>; onToggle: (tool: McpTool) => void;
}) {
  const t = useT();
  const idBase = useId();
  return (
    <div className="presets-wrap">
      <table className="presets mcp-tools">
        <caption className="sr-only">{t('mcp.tools.title', { name: server.name, count: tools.length })}</caption>
        <thead>
          <tr>
            <th scope="col" className="allow">{t('mcp.table.allowed')}</th>
            <th scope="col">{t('mcp.table.tool')}</th>
            <th scope="col" className="kind">{t('mcp.table.kind')}</th>
          </tr>
        </thead>
        <tbody>
          {tools.map((tool, index) => {
            // The name is the server's text, so ids come from React and the row's place instead.
            const box = `${idBase}-${index}`;
            const users = Array.from(new Set(tool.usedBy.map((use) => use.agentName)));
            const locked = !tool.readOnly || (allowed.has(tool.name) && users.length > 0);
            const about = tool.description || tool.title;
            return (
              <tr key={tool.name}>
                <td className="allow">
                  <input type="checkbox" id={box} checked={allowed.has(tool.name)} disabled={locked || Boolean(busy)}
                    aria-label={t('mcp.table.allow', { name: tool.name })} aria-describedby={`${box}-about`}
                    aria-busy={busy === tool.name || undefined} onChange={() => onToggle(tool)} />
                </td>
                <th scope="row">
                  <label htmlFor={box}><b className="mono">{tool.name}</b></label>
                  {fresh?.has(tool.name) && <> <Pill tone="info">{t('mcp.tool.new')}</Pill></>}
                  <span id={`${box}-about`}>
                    {about && <small>{about}</small>}
                    {!tool.readOnly && <small className="warn">{t('mcp.tool.changesNote')}</small>}
                    {tool.readOnly && users.length > 0 && allowed.has(tool.name) && (
                      <small>{t('mcp.tool.inUse', { names: users.join(', ') })}</small>
                    )}
                  </span>
                </th>
                <td className="kind">
                  {tool.readOnly ? <Pill tone="live">{t('mcp.tool.read')}</Pill> : <Pill tone="preview">{t('mcp.tool.changes')}</Pill>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
