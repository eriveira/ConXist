import { type FormEvent, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import {
  type ApiConnection, type ApiImport, type ConnectionProblem, connectionProblem, type Integration, nameFromAddress,
} from '../../domain/integrations';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';
import { McpLogo } from '../mcps/McpParts';
import { ApiConnectionFields, connectionOf, sentConnection } from './ApiConnectionFields';
import { ApiToolTable } from './ApiToolTable';
import { ImportDescription, ImportSummary } from './ImportDescription';

type Step = { step: 'connect' } | { step: 'tools'; integration: Integration }
  | { step: 'pick'; integration: Integration; result: ApiImport; allowed: Set<string> };

/**
 * Add API (ADR 0027): first the address and sign-in, which ConXist checks with the API and then saves with no tools;
 * then its tools, imported from its OpenAPI description (ticked: the ones that only read) or written by hand on its
 * page. Once connected the API is saved, so closing the dialog keeps it.
 */
export function AddApiDialog({ onClose, onDone }: {
  onClose: () => void;
  /** The API as saved; `byHand` opens its page with a new tool. */
  onDone: (integration: Integration, byHand?: boolean) => void;
}) {
  const t = useT();
  const [step, setStep] = useState<Step>({ step: 'connect' });
  const [connection, setConnection] = useState<ApiConnection>(connectionOf());
  const [problem, setProblem] = useState<ConnectionProblem>(null);
  const [named, setNamed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function connect(event?: FormEvent) {
    event?.preventDefault();
    const wrong = connectionProblem(connection, false);
    setProblem(wrong);
    setError(null);
    if (wrong) return;
    setBusy(true);
    try {
      setStep({ step: 'tools', integration: await api.addIntegration(sentConnection(connection)) });
    } catch (failed) {
      setError(problemMessage(failed, t));
    } finally {
      setBusy(false);
    }
  }

  async function finish() {
    if (step.step !== 'pick') return;
    setBusy(true);
    setError(null);
    try {
      onDone(await api.allowIntegrationTools(step.integration.id,
        step.integration.tools.map((tool) => tool.name).filter((name) => step.allowed.has(name))));
    } catch (failed) {
      setError(problemMessage(failed, t));
      setBusy(false);
    }
  }

  const saved = step.step === 'connect' ? null : step.integration;
  const close = () => (saved ? onDone(saved) : onClose());
  const icon = saved ? <McpLogo name={saved.name} icon={saved.icon} size="sm" /> : <Icon name="plus" size={18} />;
  const steps = (
    <ol className="steps" aria-label={t('api.add.steps')}>
      <li aria-current={step.step === 'connect' ? 'step' : undefined}>{t('api.add.step.connect')}</li>
      <li aria-current={step.step !== 'connect' ? 'step' : undefined}>{t('api.add.step.tools')}</li>
    </ol>
  );

  if (step.step === 'connect') {
    return (
      <Dialog title={t('api.add.title')} subtitle={t('api.add.subtitle')} icon={icon} closeLabel={t('dialog.close')}
        onClose={onClose} step="connect" closable={!busy} focusFirst="input"
        footer={(
          <>
            <span className="grow" />
            <button type="button" className="btn" onClick={onClose} disabled={busy}>{t('dialog.cancel')}</button>
            <button type="submit" form="api-connect" className="btn primary" disabled={busy}>
              {busy ? <><span className="spin" aria-hidden="true" /> {t('api.add.checking')}</> : t('api.add.connect')}
            </button>
          </>
        )}>
        <form id="api-connect" className="m-body" onSubmit={(event) => void connect(event)} noValidate>
          {steps}
          {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
          <ApiConnectionFields value={connection} problem={problem} busy={busy}
            onChange={(next) => {
              setProblem(null);
              if (next.name !== connection.name) setNamed(true);
              setConnection(next);
            }}
            onAddressBlur={() => {
              if (!named || connection.name.trim() === '') {
                setConnection((before) => ({ ...before, name: nameFromAddress(before.address) || before.name }));
              }
            }} />
        </form>
      </Dialog>
    );
  }

  if (step.step === 'tools') {
    const { integration } = step;
    return (
      <Dialog title={t('api.add.title')} subtitle={t('api.add.connected', { name: integration.name })} icon={icon}
        closeLabel={t('dialog.close')} onClose={close} step="tools" closable={!busy} wide
        footer={(
          <>
            <span className="grow" />
            <button type="button" className="btn" onClick={close} disabled={busy}>{t('api.add.later')}</button>
          </>
        )}>
        <div className="m-body">
          {steps}
          <h3>{t('api.add.which')}</h3>
          <p className="lead">{t('api.add.whichHelp')}</p>
          <section className="api-way" aria-labelledby="api-way-import">
            <h4 id="api-way-import">{t('api.add.import')}</h4>
            <ImportDescription integration={integration} busy={busy} setBusy={setBusy}
              onImported={(result) => setStep({
                step: 'pick', integration: result.integration, result,
                allowed: new Set(result.integration.tools.filter((tool) => tool.allowed).map((tool) => tool.name)),
              })} />
          </section>
          <section className="api-way" aria-labelledby="api-way-hand">
            <h4 id="api-way-hand">{t('api.add.byHand')}</h4>
            <p className="hint">{t('api.add.byHandHelp')}</p>
            <button type="button" className="btn" disabled={busy} onClick={() => onDone(integration, true)}>
              <Icon name="edit" size={16} /> {t('api.add.byHandGo')}
            </button>
          </section>
        </div>
      </Dialog>
    );
  }

  const { integration, result, allowed } = step;
  return (
    <Dialog title={t('api.add.title')} subtitle={t('api.add.connected', { name: integration.name })} icon={icon}
      closeLabel={t('dialog.close')} onClose={close} step="pick" closable={!busy} wide
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn" onClick={() => setStep({ step: 'tools', integration })} disabled={busy}>
            {t('api.add.back')}
          </button>
          <button type="button" className="btn primary" onClick={() => void finish()} disabled={busy}>
            {busy ? t('api.add.saving') : t(allowed.size === 1 ? 'api.add.finish_one' : 'api.add.finish', {
              name: integration.name, count: allowed.size,
            })}
          </button>
        </>
      )}>
      <div className="m-body">
        {steps}
        <h3>{t('api.add.pick')}</h3>
        <ImportSummary result={result} />
        <p className="hint">{t('api.add.pickHelp')}</p>
        {error && <p className="alert error" role="alert"><Icon name="alert" /> {error}</p>}
        <p className="mcp-selrow" aria-live="polite">
          <span>{t('mcp.tools.ticked', { count: allowed.size, total: integration.tools.length })}</span>
        </p>
        <ApiToolTable apiName={integration.name} tools={integration.tools} allowed={allowed} busy={busy}
          onToggle={(names, allow) => {
            const next = new Set(allowed);
            names.forEach((name) => (allow ? next.add(name) : next.delete(name)));
            setStep({ ...step, allowed: next });
          }} />
      </div>
    </Dialog>
  );
}
