import { useId } from 'react';
import { Icon } from '../../components/Icon';
import {
  type ApiAuthKind, type ApiConnection, AUTH_KINDS, type ConnectionProblem, type Integration, MAX_API_NAME,
} from '../../domain/integrations';
import { useT } from '../../i18n/i18n';

/**
 * The address, name, sign-in and check path of a custom API (ADR 0027). On an edit the secret may stay empty, which
 * keeps the stored one; the stored one is never shown, only its last 4 characters.
 */
export function ApiConnectionFields({ value, onChange, problem, busy, editing, onAddressBlur }: {
  value: ApiConnection; onChange: (next: ApiConnection) => void; problem: ConnectionProblem; busy: boolean;
  editing?: Integration; onAddressBlur?: () => void;
}) {
  const t = useT();
  const ids = {
    address: useId(), addressHelp: useId(), name: useId(), nameHelp: useId(), auth: useId(), place: useId(),
    keyName: useId(), secret: useId(), secretHelp: useId(), username: useId(), check: useId(), checkHelp: useId(),
    problem: useId(),
  };
  const set = (patch: Partial<ApiConnection>) => onChange({ ...value, ...patch });
  /** Marks a field the problem is about, and points it at the problem and its help text. */
  const field = (help: string | null, ...names: ConnectionProblem[]) => {
    const wrong = problem !== null && names.includes(problem);
    const describedBy = [help, wrong ? ids.problem : null].filter(Boolean).join(' ');
    return { 'aria-invalid': wrong || undefined, 'aria-describedby': describedBy || undefined };
  };
  const keeps = editing && editing.auth.kind === value.auth && editing.ending;
  const message = problem ? <p id={ids.problem} className="alert error" role="alert"><Icon name="alert" /> {t(`api.connect.problem.${problem}`)}</p> : null;

  return (
    <>
      <div className="field">
        <label htmlFor={ids.address}>{t('api.connect.address')}</label>
        <input id={ids.address} type="text" inputMode="url" autoComplete="off" spellCheck={false} className="mono"
          placeholder="https://api.example.com/v1" value={value.address} maxLength={2000} disabled={busy}
          {...field(ids.addressHelp, 'address-empty', 'address-invalid')}
          onChange={(event) => set({ address: event.target.value })} onBlur={onAddressBlur} />
        <small id={ids.addressHelp} className="hint">{t('api.connect.addressHelp')}</small>
        {(problem === 'address-empty' || problem === 'address-invalid') && message}
      </div>
      <div className="field">
        <label htmlFor={ids.name}>{t('api.connect.name')}</label>
        <input id={ids.name} type="text" autoComplete="off" value={value.name} maxLength={MAX_API_NAME} disabled={busy}
          {...field(ids.nameHelp, 'name-empty')} onChange={(event) => set({ name: event.target.value })} />
        <small id={ids.nameHelp} className="hint">{t('api.connect.nameHelp')}</small>
        {problem === 'name-empty' && message}
      </div>
      <fieldset className="choice api-auth">
        <legend className="field-legend">{t('api.connect.auth')}</legend>
        {AUTH_KINDS.map((kind) => (
          <label key={kind}>
            <input type="radio" name={ids.auth} value={kind} checked={value.auth === kind} disabled={busy}
              onChange={() => set({ auth: kind as ApiAuthKind, secret: '' })} />
            <span><b>{t(`api.auth.${kind}`)}</b><small>{t(`api.auth.${kind}.about`)}</small></span>
          </label>
        ))}
      </fieldset>
      {value.auth === 'API_KEY' && (
        <>
          <fieldset className="choice api-place">
            <legend className="field-legend">{t('api.connect.place')}</legend>
            {(['HEADER', 'QUERY'] as const).map((place) => (
              <label key={place}>
                <input type="radio" name={ids.place} checked={(value.keyPlace ?? 'HEADER') === place} disabled={busy}
                  onChange={() => set({ keyPlace: place })} />
                <span><b>{t(`api.place.${place}`)}</b></span>
              </label>
            ))}
          </fieldset>
          <div className="field">
            <label htmlFor={ids.keyName}>{t('api.connect.keyName')}</label>
            <input id={ids.keyName} type="text" autoComplete="off" spellCheck={false} className="mono" maxLength={64}
              placeholder={(value.keyPlace ?? 'HEADER') === 'HEADER' ? 'X-Api-Key' : 'api_key'} value={value.keyName ?? ''}
              disabled={busy} {...field(null, 'key-name-empty', 'key-name-reserved')}
              onChange={(event) => set({ keyName: event.target.value })} />
            {(problem === 'key-name-empty' || problem === 'key-name-reserved') && message}
          </div>
        </>
      )}
      {value.auth === 'BASIC' && (
        <div className="field">
          <label htmlFor={ids.username}>{t('api.connect.username')}</label>
          <input id={ids.username} type="text" autoComplete="off" spellCheck={false} maxLength={200}
            value={value.username ?? ''} disabled={busy} {...field(null, 'username-empty')}
            onChange={(event) => set({ username: event.target.value })} />
          {problem === 'username-empty' && message}
        </div>
      )}
      {value.auth !== 'NONE' && (
        <div className="field">
          <label htmlFor={ids.secret}>{t(`api.connect.secret.${value.auth}`)}</label>
          <input id={ids.secret} type="password" autoComplete="off" spellCheck={false} maxLength={4000}
            value={value.secret ?? ''} disabled={busy} {...field(ids.secretHelp, 'secret-empty')}
            placeholder={keeps ? t('api.connect.keepSecret', { ending: editing.ending ?? '' }) : undefined}
            onChange={(event) => set({ secret: event.target.value })} />
          <small id={ids.secretHelp} className="hint">
            {value.auth === 'BEARER' ? t('api.connect.secretHelp.BEARER') : t('api.connect.secretHelp')}
          </small>
          {problem === 'secret-empty' && message}
        </div>
      )}
      {value.auth === 'NONE' && <p className="note"><Icon name="info" size={14} /> {t('api.connect.public')}</p>}
      <div className="field">
        <label htmlFor={ids.check}>{t('api.connect.check')} <span className="muted">{t('api.connect.optional')}</span></label>
        <input id={ids.check} type="text" autoComplete="off" spellCheck={false} className="mono" placeholder="/me"
          maxLength={500} value={value.checkPath ?? ''} disabled={busy}
          {...field(ids.checkHelp, 'check-path-invalid')} onChange={(event) => set({ checkPath: event.target.value })} />
        <small id={ids.checkHelp} className="hint">{t('api.connect.checkHelp')}</small>
        {problem === 'check-path-invalid' && message}
      </div>
    </>
  );
}

/** The form's starting values: empty for a new API, else its connection with no secret. */
export function connectionOf(integration?: Integration): ApiConnection {
  if (!integration) {
    return { name: '', address: '', auth: 'API_KEY', keyPlace: 'HEADER', keyName: 'X-Api-Key', username: '', secret: '', checkPath: '' };
  }
  return {
    name: integration.name,
    address: integration.address,
    auth: integration.auth.kind,
    keyPlace: integration.auth.place ?? 'HEADER',
    keyName: integration.auth.name ?? '',
    username: integration.account ?? '',
    secret: '',
    checkPath: integration.checkPath ?? '',
  };
}

/** What the API is sent: only the fields of the chosen sign-in. */
export function sentConnection(value: ApiConnection): ApiConnection {
  const auth = value.auth;
  return {
    name: value.name.trim(),
    address: value.address.trim(),
    auth,
    keyPlace: auth === 'API_KEY' ? value.keyPlace ?? 'HEADER' : null,
    keyName: auth === 'API_KEY' ? (value.keyName ?? '').trim() : null,
    username: auth === 'BASIC' ? (value.username ?? '').trim() : null,
    secret: auth === 'NONE' ? null : value.secret ?? '',
    checkPath: (value.checkPath ?? '').trim() || null,
  };
}
