import { Icon } from '../../components/Icon';
import { useT } from '../../i18n/i18n';
import { AGENTS_HREF, SKILLS_HREF } from '../../navigation';

/** Agents and Skills share one menu item; these tabs move between them. */
export function AgentsTabs({ current }: { current: 'agents' | 'skills' }) {
  const t = useT();
  return (
    <nav className="subtabs" aria-label={t('skills.tabs')}>
      <a href={AGENTS_HREF} aria-current={current === 'agents' ? 'page' : undefined}>
        <Icon name="agents" size={18} /> {t('agents.title')}
      </a>
      <a href={SKILLS_HREF} aria-current={current === 'skills' ? 'page' : undefined}>
        <Icon name="skill" size={18} /> {t('skills.title')}
      </a>
    </nav>
  );
}
