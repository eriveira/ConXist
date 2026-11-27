import { Fragment, useEffect, useId, useRef, useState } from 'react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { type McpServer, type McpStatus, type McpTool, safeIcon } from '../../domain/mcps';
import { BITBUCKET_ICON, GMAIL_ICON, GITHUB_ICON } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';

/** GitHub's mark (ADR 0019), drawn in the text color. */
const GITHUB_MARK = 'M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.013 8.013 0 0016 8c0-4.42-3.58-8-8-8z';

/** Bitbucket's bucket (ADR 0020), drawn in the text color with its window cut out. */
const BITBUCKET_MARK = 'M1.2 1.5h13.6a.5.5 0 0 1 .5.58l-2 12.3a.6.6 0 0 1-.6.5H3.3a.6.6 0 0 1-.6-.5L.7 2.08a.5.5 0 0 1 .5-.58ZM6.1 5.6l.6 4h2.6l.7-4Z';
/** An envelope with an M cut out, in the text color: Gmail's shape without its colors. */
const GMAIL_MARK = 'M1 3.6A1.6 1.6 0 0 1 2.6 2h.5L8 5.7 12.9 2h.5A1.6 1.6 0 0 1 15 3.6v8.8a1.6 1.6 0 0 1-1.6 1.6H12V6.4L8 9.4 4 6.4V14H2.6A1.6 1.6 0 0 1 1 12.4Z';

/** The server's logo, or the first letter of its name when it gives none. Decorative: its name is beside it. */
export function McpLogo({ name, icon, size = 'md' }: { name: string; icon: string | null; size?: 'sm' | 'md' }) {
  const [broken, setBroken] = useState<string | null>(null);
  if (icon === GITHUB_ICON || icon === BITBUCKET_ICON || icon === GMAIL_ICON) {
    return (
      <span className={`mcp-logo ${size}`} aria-hidden="true">
        <svg viewBox="0 0 16 16" width="62%" height="62%" fill="currentColor" fillRule="evenodd" focusable="false">
          <path d={icon === GITHUB_ICON ? GITHUB_MARK : icon === GMAIL_ICON ? GMAIL_MARK : BITBUCKET_MARK} />
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
                      <div ref={confirmRef} className="tool-ask" role="group" tabIndex={-1}
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
