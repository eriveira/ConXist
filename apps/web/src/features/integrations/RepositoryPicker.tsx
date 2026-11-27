import { useId } from 'react';
import { useT } from '../../i18n/i18n';

/**
 * The repositories a token reaches, each with a box: the ones ticked are the only ones agents may read (ADR 0020).
 * Gmail's labels use it too (ADR 0021).
 * Names come from the service and show as text only.
 */
export function RepositoryPicker({ repositories, picked, onChange, disabled, label, mono = true }: {
  repositories: readonly string[]; picked: ReadonlySet<string>; onChange: (picked: Set<string>) => void;
  disabled?: boolean;
  /** What the boxes are, when not repositories (Gmail's labels). */
  label?: string;
  mono?: boolean;
}) {
  const t = useT();
  const labelId = useId();

  function toggle(repository: string) {
    const next = new Set(picked);
    if (next.has(repository)) next.delete(repository);
    else next.add(repository);
    onChange(next);
  }

  return (
    <fieldset className="repo-picker" aria-labelledby={labelId} disabled={disabled}>
      <div className="mcp-selrow">
        <span id={labelId} className="hint" aria-live="polite">
          {label ?? t('repos.label')} · {t('repos.count', { count: picked.size, total: repositories.length })}
        </span>
        <span>
          <button type="button" className="link" onClick={() => onChange(new Set(repositories))}>{t('repos.all')}</button>
          {' · '}
          <button type="button" className="link" onClick={() => onChange(new Set())}>{t('repos.none')}</button>
        </span>
      </div>
      <ul className="repo-options">
        {repositories.map((repository) => (
          <li key={repository}>
            <label className="check">
              <input type="checkbox" checked={picked.has(repository)} onChange={() => toggle(repository)} />
              <span className={mono ? 'mono' : undefined}>{repository}</span>
            </label>
          </li>
        ))}
      </ul>
    </fieldset>
  );
}
