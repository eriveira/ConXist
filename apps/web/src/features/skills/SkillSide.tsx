import { useId, useState } from 'react';
import { Icon } from '../../components/Icon';
import { Pill } from '../../components/Pill';
import { type Skill, type SkillDefinition, skillMarkdown, slug, usersOf } from '../../domain/skills';
import { useT } from '../../i18n/i18n';
import { agentHref } from '../../navigation';
import type { SkillContext } from './SkillPage';

type Tab = 'used' | 'file';

/** Beside the form: which agents follow the skill, and the skill as a SKILL.md to export. */
export function SkillSide({ skill, draft, context }: { skill: Skill; draft: SkillDefinition; context: SkillContext }) {
  const t = useT();
  const [tab, setTab] = useState<Tab>('used');
  const ids = { used: useId(), file: useId(), panel: useId() };
  const markdown = skillMarkdown(draft);

  function download() {
    const url = URL.createObjectURL(new Blob([markdown], { type: 'text/markdown' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `${slug(draft.name) || 'skill'}.SKILL.md`;
    link.click();
    // Some browsers start the download after this returns, so the file is let go on the next turn.
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  const select = (next: Tab) => () => setTab(next);
  return (
    <aside className="agent-side">
      <div className="side-card">
        <div className="side-tabs" role="tablist" aria-label={t('skill.side')}>
          <button id={ids.used} type="button" role="tab" aria-selected={tab === 'used'} aria-controls={ids.panel}
            tabIndex={tab === 'used' ? 0 : -1} onClick={select('used')}
            onKeyDown={(event) => { if (event.key === 'ArrowRight') { setTab('file'); document.getElementById(ids.file)?.focus(); } }}>
            {t('skill.side.used')}
          </button>
          <button id={ids.file} type="button" role="tab" aria-selected={tab === 'file'} aria-controls={ids.panel}
            tabIndex={tab === 'file' ? 0 : -1} onClick={select('file')}
            onKeyDown={(event) => { if (event.key === 'ArrowLeft') { setTab('used'); document.getElementById(ids.used)?.focus(); } }}>
            SKILL.md
          </button>
        </div>
        <div id={ids.panel} role="tabpanel" aria-labelledby={tab === 'used' ? ids.used : ids.file} className="side-panel">
          {tab === 'used' ? <UsedBy skill={skill} context={context} /> : (
            <>
              <p className="hint">{t('skill.side.file.lead')}</p>
              <pre className="skill-md">{markdown}</pre>
              <button type="button" className="btn sm" onClick={download}><Icon name="download" size={16} /> {t('skill.export')}</button>
            </>
          )}
        </div>
      </div>
    </aside>
  );
}

function UsedBy({ skill, context }: { skill: Skill; context: SkillContext }) {
  const t = useT();
  if (context.status === 'loading') return <p className="muted" role="status">{t('agent.loadingChoices')}</p>;
  if (context.status === 'error') return <p className="muted">{t('skill.context.error')}</p>;
  const users = usersOf(skill.id, context.agents);
  return (
    <>
      {users.length === 0 && <p className="muted">{t('skill.side.used.none')}</p>}
      {users.length > 0 && (
        <ul className="used-by">
          {users.map(({ agent, version }) => (
            <li key={agent.id}>
              <Icon name="agents" size={16} />
              <a className="grow" href={agentHref(agent.id)}>{agent.name}</a>
              <Pill tone={version < skill.publishedVersion ? 'info' : 'soon'}>
                {version < skill.publishedVersion
                  ? t('skill.side.used.behind', { version, newest: skill.publishedVersion })
                  : `v${version}`}
              </Pill>
            </li>
          ))}
        </ul>
      )}
      <p className="hint">{t('skill.side.used.note')}</p>
    </>
  );
}
