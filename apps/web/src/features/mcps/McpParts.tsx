import { Fragment, useEffect, useId, useRef, useState } from 'react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { type McpServer, type McpStatus, type McpTool, safeIcon } from '../../domain/mcps';
import { useT } from '../../i18n/i18n';

/** The server's logo, or the first letter of its name when it gives none. Decorative: its name is beside it. */
export function McpLogo({ name, icon, size = 'md' }: { name: string; icon: string | null; size?: 'sm' | 'md' | 'lg' }) {
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
 * The tools a server lists, with a box to allow each. A tool that changes things starts unticked and asks first:
 * the warning is answered in the table, so it works inside a dialog too, and the tick is the approval for every later
 * use. A tool agents use stays ticked until it is turned off in those agents. Names and descriptions are the
 * server's text, shown as text only.
 */
export function McpToolTable({ server, tools, allowed, busy, fresh, onToggle }: {
  server: Pick<McpServer, 'name'>; tools: readonly McpTool[]; allowed: ReadonlySet<string>; busy?: string | null;
  fresh?: ReadonlySet<string>; onToggle: (tool: McpTool) => void;
}) {
  const t = useT();
  const idBase = useId();
  const [asking, setAsking] = useState<string | null>(null);
  const confirmRef = useRef<HTMLDivElement>(null);

  useEffect(() => { if (asking) confirmRef.current?.focus(); }, [asking]);

  function boxOf(name: string): HTMLInputElement | null {
    const index = tools.findIndex((tool) => tool.name === name);
    return index < 0 ? null : document.getElementById(`${idBase}-${index}`) as HTMLInputElement | null;
  }

  function toggle(tool: McpTool) {
    if (!tool.readOnly && !allowed.has(tool.name)) setAsking(tool.name);
    else onToggle(tool);
  }

  function answer(tool: McpTool, allow: boolean) {
    setAsking(null);
    if (allow) onToggle(tool);
    // Back to the box the question came from, once the question has gone.
    requestAnimationFrame(() => boxOf(tool.name)?.focus());
  }

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
            const locked = allowed.has(tool.name) && users.length > 0;
            const about = tool.description || tool.title;
            const isAsking = asking === tool.name;
            return (
              <Fragment key={tool.name}>
                <tr>
                  <td className="allow">
                    <input type="checkbox" id={box} checked={allowed.has(tool.name)} disabled={locked || Boolean(busy) || isAsking}
                      aria-label={t('mcp.table.allow', { name: tool.name })} aria-describedby={`${box}-about`}
                      aria-busy={busy === tool.name || undefined} onChange={() => toggle(tool)} />
                  </td>
                  <th scope="row">
                    <label htmlFor={box}><b className="mono">{tool.name}</b></label>
                    {fresh?.has(tool.name) && <> <Pill tone="info">{t('mcp.tool.new')}</Pill></>}
                    <span id={`${box}-about`}>
                      {about && <small>{about}</small>}
                      {!tool.readOnly && <small className="warn">{t('mcp.tool.changesNote')}</small>}
                      {users.length > 0 && allowed.has(tool.name) && (
                        <small>{t('mcp.tool.inUse', { names: users.join(', ') })}</small>
                      )}
                    </span>
                  </th>
                  <td className="kind">
                    {tool.readOnly ? <Pill tone="live">{t('mcp.tool.read')}</Pill> : <Pill tone="preview">{t('mcp.tool.changes')}</Pill>}
                  </td>
                </tr>
                {isAsking && (
                  <tr className="tool-ask-row">
                    <td colSpan={3}>
                      <div ref={confirmRef} className="tool-ask" role="group" tabIndex={-1} data-own-escape
                        onKeyDown={(event) => { if (event.key === 'Escape') { event.preventDefault(); answer(tool, false); } }}
                        aria-labelledby={`${box}-ask`} aria-describedby={`${box}-ask-body`}>
                        <b id={`${box}-ask`}>{t('mcp.warn.title', { name: tool.name })}</b>
                        <span id={`${box}-ask-body`}>
                          <span className="tool-ask-text">{t('mcp.warn.body', { server: server.name })}</span>
                          <span className="tool-ask-text warn"><Icon name="alert" size={14} /> {t('mcp.warn.undo', { server: server.name })}</span>
                        </span>
                        <span className="tool-ask-actions">
                          <button type="button" className="btn sm" onClick={() => answer(tool, false)}>{t('mcp.warn.keep')}</button>
                          <button type="button" className="btn sm primary" onClick={() => answer(tool, true)}>{t('mcp.warn.allow')}</button>
                        </span>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
