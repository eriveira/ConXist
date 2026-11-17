import { useId, useRef, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { Stepper } from '../../components/Stepper';
import { ACCEPT_ATTRIBUTE } from '../../domain/files';
import { MAX_SOURCE_NAME_LENGTH, type Source } from '../../domain/model';
import { useT } from '../../i18n/i18n';
import type { MessageKey } from '../../i18n/messages';
import { problemMessage } from '../../i18n/problems';
import { useUploads, type PickedFile } from './useUploads';

type Step = 'name' | 'files' | 'review' | 'result';

interface UploadDialogProps {
  /** Set when adding files to a source already on the canvas; the name step is then skipped. */
  existing?: Source;
  onClose: () => void;
  onCaptured: () => void;
}

export function UploadDialog({ existing, onClose, onCaptured }: UploadDialogProps) {
  const t = useT();
  const steps: Step[] = existing ? ['files', 'review'] : ['name', 'files', 'review'];
  const [step, setStep] = useState<Step>(steps[0]);
  const [name, setName] = useState(t('upload.name.default'));
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  /** Kept so a retry after a failed upload does not add the source a second time. */
  const [createdId, setCreatedId] = useState<string | null>(existing?.id ?? null);
  const [sentCount, setSentCount] = useState(0);
  const uploads = useUploads();
  const index = steps.indexOf(step as Step);

  function validate(): string | null {
    if (step === 'name' && name.trim() === '') return t('upload.name.required');
    if (step === 'files' && uploads.ready.length === 0) return t('upload.files.required');
    return null;
  }

  function next() {
    const reason = validate();
    setProblem(reason);
    if (!reason) setStep(steps[index + 1]);
  }

  async function submit() {
    setBusy(true);
    setProblem(null);
    try {
      const sourceId = createdId ?? (await api.addSource({ provider: 'UPLOAD', name: name.trim() })).id;
      setCreatedId(sourceId);
      setSentCount(await uploads.sendAll(sourceId));
      setStep('result');
      onCaptured();
    } catch (error) {
      setProblem(problemMessage(error, t));
    } finally {
      setBusy(false);
    }
  }

  const footer = step === 'result' ? (
    <><span className="grow" /><button type="button" className="btn primary" onClick={onClose}>{t('dialog.done')}</button></>
  ) : (
    <>
      {index > 0 && <button type="button" className="btn ghost" onClick={() => setStep(steps[index - 1])} disabled={busy}>{t('dialog.backStep')}</button>}
      <span className="grow" />
      {step === 'review'
        ? <button type="button" className="btn primary" onClick={submit} disabled={busy}>{busy ? t('dialog.adding') : t('dialog.add')}</button>
        : <button type="button" className="btn primary" onClick={next}>{t('dialog.next')}</button>}
    </>
  );

  return (
    <Dialog title={existing?.name ?? t('upload.title')} subtitle={t('upload.subtitle')} icon={<Icon name="upload" />}
      closeLabel={t('dialog.close')} onClose={onClose} footer={footer}>
      {step !== 'result' && <Stepper steps={steps.map((id) => t(`upload.step.${id}` as MessageKey))} current={index} />}
      <div className="m-body">
        {problem && <p className="alert error" role="alert"><Icon name="alert" size={18} />{problem}</p>}
        {step === 'name' && <NameStep name={name} onChange={setName} />}
        {step === 'files' && <FilesStep files={uploads.files} onAdd={uploads.add} onRemove={uploads.remove} />}
        {step === 'review' && <ReviewStep name={existing?.name ?? name.trim()} files={uploads.ready} />}
        {step === 'result' && <ResultStep files={uploads.files} sent={sentCount} />}
      </div>
    </Dialog>
  );
}

function NameStep({ name, onChange }: { name: string; onChange: (name: string) => void }) {
  const t = useT();
  const id = useId();
  return (
    <>
      <h3>{t('upload.name.heading')}</h3>
      <p className="lead">{t('upload.name.lead')}</p>
      <div className="field">
        <label htmlFor={id}>{t('upload.name.label')}</label>
        <input id={id} type="text" value={name} maxLength={MAX_SOURCE_NAME_LENGTH} onChange={(event) => onChange(event.target.value)} />
      </div>
    </>
  );
}

interface FilesStepProps {
  files: readonly PickedFile[];
  onAdd: (files: FileList) => void;
  onRemove: (key: string) => void;
}

function FilesStep({ files, onAdd, onRemove }: FilesStepProps) {
  const t = useT();
  const input = useRef<HTMLInputElement>(null);
  const [hot, setHot] = useState(false);
  return (
    <>
      <h3>{t('upload.files.heading')}</h3>
      <p className="lead">{t('upload.files.lead')}</p>
      <div
        className={hot ? 'drop hot' : 'drop'}
        onDragOver={(event) => { event.preventDefault(); setHot(true); }}
        onDragLeave={() => setHot(false)}
        onDrop={(event) => { event.preventDefault(); setHot(false); onAdd(event.dataTransfer.files); }}
      >
        {t('upload.files.drop')}{' '}
        <button type="button" className="btn sm" onClick={() => input.current?.click()}>{t('upload.files.choose')}</button>
        <input ref={input} type="file" multiple hidden accept={ACCEPT_ATTRIBUTE}
          data-testid="file-input" onChange={(event) => event.target.files && onAdd(event.target.files)} />
        <div className="limits">{t('upload.files.limits')}</div>
      </div>
      <ul className="files">
        {files.map((item) => (
          <li key={item.key} className={item.problem ? 'bad' : undefined}>
            <Icon name="file" size={16} />
            <span className="fname">{item.file.name}
              {item.problem && <small>{t(`upload.files.problem.${item.problem}` as MessageKey)}</small>}
            </span>
            <button type="button" className="btn sm ghost" onClick={() => onRemove(item.key)}
              aria-label={t('upload.files.remove', { name: item.file.name })}>
              <Icon name="close" size={16} />
            </button>
          </li>
        ))}
      </ul>
    </>
  );
}

function ReviewStep({ name, files }: { name: string; files: readonly PickedFile[] }) {
  const t = useT();
  return (
    <>
      <h3>{t('upload.review.heading')}</h3>
      <p className="lead">{t('upload.review.lead')}</p>
      <dl className="summary">
        <div><dt>{t('upload.name.label')}</dt><dd>{name}</dd></div>
        <div><dt>{t('upload.step.files')}</dt><dd>{t('upload.review.files', { count: files.length })}</dd></div>
        <div><dt>{t('dialog.feeds')}</dt><dd>{t('hub.title')}</dd></div>
      </dl>
    </>
  );
}

const STATUS_TEXT: Record<PickedFile['status'], MessageKey> = {
  waiting: 'upload.status.waiting', sending: 'upload.status.sending', done: 'upload.status.done', failed: 'error.generic',
};

function ResultStep({ files, sent }: { files: readonly PickedFile[]; sent: number }) {
  const t = useT();
  const attempted = files.filter((item) => item.problem === null);
  return (
    <>
      <h3>{t('upload.result.heading')}</h3>
      <p className="lead" role="status">{t('upload.result.lead', { done: sent, total: attempted.length })}</p>
      <ul className="files">
        {attempted.map((item) => (
          <li key={item.key} className={item.status === 'failed' ? 'bad' : undefined}>
            <Icon name={item.status === 'done' ? 'check' : 'alert'} size={16} />
            <span className="fname">{item.file.name}
              <small>{item.status === 'failed' ? problemMessage(item.failure, t) : t(STATUS_TEXT[item.status])}</small>
            </span>
          </li>
        ))}
      </ul>
    </>
  );
}
