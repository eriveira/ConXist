import { useId, useState } from 'react';
import { api } from '../../api/client';
import { Dialog } from '../../components/Dialog';
import { Icon } from '../../components/Icon';
import { MAX_SKILL_FILE_BYTES } from '../../domain/skills';
import { useT } from '../../i18n/i18n';
import { problemMessage } from '../../i18n/problems';

interface ImportSkillDialogProps {
  onClose: () => void;
  onImported: (skillId: string) => void;
}

type Picked = { name: string; text: string } | null;

/**
 * Bring in a SKILL.md the team already uses with Claude Code, Codex or another tool. It arrives as a draft to check;
 * nothing follows it until it is published.
 */
export function ImportSkillDialog({ onClose, onImported }: ImportSkillDialogProps) {
  const t = useT();
  const [picked, setPicked] = useState<Picked>(null);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const fileId = useId();
  const hintId = useId();

  async function pick(file: File | undefined) {
    setProblem(null);
    setPicked(null);
    if (!file) return;
    if (file.size > MAX_SKILL_FILE_BYTES) {
      setProblem(t('api.skill.import-too-large'));
      return;
    }
    try {
      setPicked({ name: file.name, text: await readText(file) });
    } catch {
      setProblem(t('error.generic'));
    }
  }

  async function importSkill() {
    if (!picked) return;
    setBusy(true);
    setProblem(null);
    try {
      onImported((await api.importSkill(picked.text)).id);
    } catch (error) {
      setProblem(problemMessage(error, t));
      setBusy(false);
    }
  }

  return (
    <Dialog title={t('skills.import.title')} subtitle={t('skills.import.subtitle')} icon={<Icon name="upload" />}
      closeLabel={t('ask.close')} onClose={onClose} step="file" closable={!busy} focusFirst="input"
      footer={(
        <>
          <span className="grow" />
          <button type="button" className="btn ghost" onClick={onClose} disabled={busy}>{t('agents.cancel')}</button>
          <button type="button" className="btn primary" onClick={() => void importSkill()} disabled={busy || !picked}>
            {busy ? <><span className="spin" aria-hidden="true" /> {t('skills.import.importing')}</> : t('skills.import')}
          </button>
        </>
      )}>
      <div className="m-body">
        {problem && <p className="alert error" role="alert"><Icon name="alert" /> {problem}</p>}
        <div className="field">
          <label htmlFor={fileId}>{t('skills.import.file')}</label>
          <input id={fileId} type="file" accept=".md,.markdown,text/markdown,text/plain" aria-describedby={hintId}
            onChange={(event) => void pick(event.target.files?.[0])} disabled={busy} />
          <span id={hintId} className="hint">{t('skills.import.hint')}</span>
        </div>
        {picked && <p className="picked"><Icon name="file" size={16} /> {picked.name}</p>}
        <p className="note"><Icon name="lock" size={14} /> {t('skills.import.safety')}</p>
      </div>
    </Dialog>
  );
}

/** The file as text. FileReader works in every browser the app supports, and in tests. */
function readText(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error ?? new Error('Could not read the file'));
    reader.readAsText(file);
  });
}
