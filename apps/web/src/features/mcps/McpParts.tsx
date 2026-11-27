import { useId, useState } from 'react';
import { Pill } from '../../components/Pill';
import { type McpServer, type McpStatus, type McpTool, safeIcon } from '../../domain/mcps';
import { GITHUB_ICON } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';

/** GitHub's mark (ADR 0019), drawn in the text color. */
const GITHUB_MARK = 'M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z';

/** The server's logo, or the first letter of its name when it gives none. Decorative: its name is beside it. */
export function McpLogo({ name, icon, size = 'md' }: { name: string; icon: string | null; size?: 'sm' | 'md' }) {
  const [broken, setBroken] = useState<string | null>(null);
  if (icon === GITHUB_ICON) {
    return (
      <span className={`mcp-logo ${size}`} aria-hidden="true">
        <svg viewBox="0 0 16 16" width="62%" height="62%" fill="currentColor" focusable="false">
          <path d={GITHUB_MARK} />
        </svg>
      </span>
    );
  }
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
