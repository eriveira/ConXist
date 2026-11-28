import { Fragment, useEffect, useId, useRef, useState } from 'react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { type ApiTool, bySection } from '../../domain/integrations';
import { useT } from '../../i18n/i18n';

/**
 * A custom API's tools grouped by the API's own sections (ADR 0027), each with a box to allow it. A section folds, and
 * its box ticks the tools in it that only read; a tool that changes things asks first, in the table, and the tick is
 * the approval for every later use (ADR 0022). A tool agents use stays ticked until it is turned off in those agents.
 * Names, paths and descriptions are the API's or a person's text, shown as text only.
 */
export function ApiToolTable({ apiName, tools, allowed, busy, query = '', onToggle, onEdit }: {
  apiName: string; tools: readonly ApiTool[]; allowed: ReadonlySet<string>; busy?: boolean; query?: string;
  /** The tools whose tick changes, all at once, and whether they become allowed. */
  onToggle: (names: string[], allow: boolean) => void;
  onEdit?: (tool: ApiTool) => void;
}) {
  const t = useT();
  const idBase = useId();
  const [folded, setFolded] = useState<ReadonlySet<string>>(new Set());
  const [asking, setAsking] = useState<string | null>(null);
  const confirmRef = useRef<HTMLDivElement>(null);

  useEffect(() => { if (asking) confirmRef.current?.focus(); }, [asking]);

  const find = query.trim().toLowerCase();
  const shown = find === '' ? tools : tools.filter((tool) => `${tool.name} ${tool.path} ${tool.description} ${tool.section ?? ''}`
    .toLowerCase().includes(find));
  const groups = bySection(shown);
  const columns = onEdit ? 4 : 3;
  const usersOf = (tool: ApiTool) => Array.from(new Set(tool.usedBy.map((use) => use.agentName)));
  const locked = (tool: ApiTool) => allowed.has(tool.name) && usersOf(tool).length > 0;
  const boxId = (tool: ApiTool) => `${idBase}-${tools.indexOf(tool)}`;

  function toggle(tool: ApiTool) {
    if (!tool.reads && !allowed.has(tool.name)) setAsking(tool.name);
    else onToggle([tool.name], !allowed.has(tool.name));
  }

  function answer(tool: ApiTool, allow: boolean) {
    setAsking(null);
    if (allow) onToggle([tool.name], true);
    requestAnimationFrame(() => (document.getElementById(boxId(tool)) as HTMLInputElement | null)?.focus());
  }

  /** A section's box: on when every tool in it that only reads is ticked; it never ticks a tool that changes things. */
  function sectionState(list: readonly ApiTool[]): { checked: boolean; mixed: boolean; reads: ApiTool[] } {
    const reads = list.filter((tool) => tool.reads);
    const ticked = list.filter((tool) => allowed.has(tool.name));
    const checked = reads.length > 0 && reads.every((tool) => allowed.has(tool.name));
    return { checked, mixed: ticked.length > 0 && !(checked && ticked.length === reads.length), reads };
  }

  function toggleSection(list: readonly ApiTool[]) {
    const { checked, reads } = sectionState(list);
    if (checked) {
      const off = list.filter((tool) => allowed.has(tool.name) && !locked(tool)).map((tool) => tool.name);
      if (off.length) onToggle(off, false);
    } else {
      const on = reads.filter((tool) => !allowed.has(tool.name)).map((tool) => tool.name);
      if (on.length) onToggle(on, true);
    }
  }

  if (shown.length === 0) {
    return <p className="muted">{find ? t('api.tools.noMatch') : t('api.tools.none')}</p>;
  }

  return (
    <div className="presets-wrap">
      <table className="presets mcp-tools api-tools">
        <caption className="sr-only">{t('api.tools.caption', { name: apiName, count: tools.length })}</caption>
        <thead>
          <tr>
            <th scope="col" className="allow">{t('mcp.table.allowed')}</th>
            <th scope="col">{t('mcp.table.tool')}</th>
            <th scope="col" className="kind">{t('mcp.table.kind')}</th>
            {onEdit && <th scope="col" className="act"><span className="sr-only">{t('api.tools.actions')}</span></th>}
          </tr>
        </thead>
        {groups.map((group, groupIndex) => {
          const key = group.section ?? '';
          const label = group.section ?? t('api.tools.otherSection');
          const isFolded = folded.has(key) && find === '';
          const state = sectionState(group.tools);
          const sectionBox = `${idBase}-s${groupIndex}`;
          const count = group.tools.filter((tool) => allowed.has(tool.name)).length;
          return (
            <tbody key={key}>
              <tr className="api-section">
                <td className="allow">
                  <input type="checkbox" id={sectionBox} checked={state.checked} disabled={busy || state.reads.length === 0}
                    ref={(box) => { if (box) box.indeterminate = state.mixed; }}
                    aria-label={t('api.tools.sectionAllow', { section: label })} onChange={() => toggleSection(group.tools)} />
                </td>
                <th scope="colgroup" colSpan={columns - 1}>
                  <button type="button" className="api-fold" aria-expanded={!isFolded}
                    onClick={() => setFolded((before) => {
                      const next = new Set(before);
                      if (next.has(key)) next.delete(key); else next.add(key);
                      return next;
                    })}>
                    <Icon name={isFolded ? 'chevron' : 'down'} size={16} />
                    <span className="api-section-name">{label}</span>
                    <span className="muted">{t('api.tools.sectionCount', { count, total: group.tools.length })}</span>
                  </button>
                </th>
              </tr>
              {!isFolded && group.tools.map((tool) => {
                const box = boxId(tool);
                const users = usersOf(tool);
                const isAsking = asking === tool.name;
                return (
                  <Fragment key={tool.name}>
                    <tr>
                      <td className="allow">
                        <input type="checkbox" id={box} checked={allowed.has(tool.name)}
                          disabled={locked(tool) || Boolean(busy) || isAsking}
                          aria-label={t('mcp.table.allow', { name: tool.name })} aria-describedby={`${box}-about`}
                          onChange={() => toggle(tool)} />
                      </td>
                      <th scope="row">
                        <label htmlFor={box}><b className="mono">{tool.name}</b></label>
                        <span id={`${box}-about`}>
                          <small className="mono api-route"><span className="api-method">{tool.method}</span> {tool.path}</small>
                          {tool.description && <small>{tool.description}</small>}
                          {!tool.reads && <small className="warn">{t('api.tool.changesNote', { name: apiName })}</small>}
                          {users.length > 0 && allowed.has(tool.name) && (
                            <small>{t('mcp.tool.inUse', { names: users.join(', ') })}</small>
                          )}
                        </span>
                      </th>
                      <td className="kind">
                        {tool.reads ? <Pill tone="live">{t('mcp.tool.read')}</Pill> : <Pill tone="preview">{t('mcp.tool.changes')}</Pill>}
                        {!tool.imported && <> <Pill tone="info">{t('api.tool.byHand')}</Pill></>}
                      </td>
                      {onEdit && (
                        <td className="act">
                          <button type="button" className="btn sm" onClick={() => onEdit(tool)}
                            aria-label={t('api.tool.edit.label', { name: tool.name })}>
                            {t('api.tool.edit')}
                          </button>
                        </td>
                      )}
                    </tr>
                    {isAsking && (
                      <tr className="tool-ask-row">
                        <td colSpan={columns}>
                          <div ref={confirmRef} className="tool-ask" role="group" tabIndex={-1} data-own-escape
                            onKeyDown={(event) => { if (event.key === 'Escape') { event.preventDefault(); answer(tool, false); } }}
                            aria-labelledby={`${box}-ask`} aria-describedby={`${box}-ask-body`}>
                            <b id={`${box}-ask`}>{t('mcp.warn.title', { name: tool.name })}</b>
                            <span id={`${box}-ask-body`}>
                              <span className="tool-ask-text">{t('mcp.warn.body', { server: apiName })}</span>
                              <span className="tool-ask-text warn"><Icon name="alert" size={14} /> {t('api.warn.undo', { name: apiName })}</span>
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
          );
        })}
      </table>
    </div>
  );
}
