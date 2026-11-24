import { useCallback, useEffect, useState } from 'react';
import { api } from '../../api/client';
import { Icon } from '../../components/Icon';
import { MAX_TOPICS, type Topic } from '../../domain/settings';
import { useT } from '../../i18n/i18n';
import { RemoveTopicDialog, TopicDialog } from './TopicDialogs';
import { ErrorState, LoadingState, SettingsCard } from './SettingsParts';

type ListState = { status: 'loading' } | { status: 'error' } | { status: 'ready'; topics: Topic[] };
type Editing = { kind: 'new' } | { kind: 'edit'; topic: Topic } | { kind: 'remove'; topic: Topic } | null;

const byName = (a: Topic, b: Topic) => a.name.localeCompare(b.name, undefined, { sensitivity: 'base' });

/** The subjects the topic classifier sorts meetings into: add, edit and remove them. */
export function TopicsSection() {
  const t = useT();
  const [state, setState] = useState<ListState>({ status: 'loading' });
  const [editing, setEditing] = useState<Editing>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = useCallback(async () => {
    setState({ status: 'loading' });
    try {
      setState({ status: 'ready', topics: await api.topics() });
    } catch {
      setState({ status: 'error' });
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const topics = state.status === 'ready' ? state.topics : [];
  const full = topics.length >= MAX_TOPICS;

  function saved(topic: Topic, isNew: boolean) {
    setEditing(null);
    setState({ status: 'ready', topics: [...topics.filter((other) => other.id !== topic.id), topic].sort(byName) });
    setNotice(t(isNew ? 'settings.topic.added' : 'settings.topic.changed', { name: topic.name }));
  }

  function removed(topic: Topic) {
    setEditing(null);
    setState({ status: 'ready', topics: topics.filter((other) => other.id !== topic.id) });
    setNotice(t('settings.topic.removed', { name: topic.name }));
  }

  return (
    <SettingsCard id="settings-topics" title={t('settings.topics')} lead={t('settings.topics.lead')}
      action={state.status === 'ready' && (
        <button type="button" className="btn primary sm" disabled={full} onClick={() => { setNotice(null); setEditing({ kind: 'new' }); }}>
          <Icon name="plus" size={16} /> {t('settings.topics.add')}
        </button>
      )}>
      <p className="save-status" role="status">{notice && <><Icon name="check" size={16} /> {notice}</>}</p>
      {state.status === 'loading' && <LoadingState />}
      {state.status === 'error' && <ErrorState onRetry={() => void load()} />}
      {state.status === 'ready' && topics.length === 0 && (
        <div className="topics-empty">
          <h3>{t('settings.topics.empty.title')}</h3>
          <p>{t('settings.topics.empty.body')}</p>
        </div>
      )}
      {topics.length > 0 && (
        <>
          <ul className="topics">
            {topics.map((topic) => (
              <li key={topic.id} className="topic">
                <span className="topic-dot" aria-hidden="true" />
                <span className="grow">
                  <span className="topic-name">{topic.name}</span>
                  <span className={topic.description ? 'topic-desc' : 'topic-desc muted'}>
                    {topic.description || t('settings.topics.noDescription')}
                  </span>
                </span>
                <button type="button" className="icon-btn" aria-label={t('settings.topics.edit', { name: topic.name })}
                  onClick={() => { setNotice(null); setEditing({ kind: 'edit', topic }); }}>
                  <Icon name="edit" size={18} />
                </button>
                <button type="button" className="icon-btn" aria-label={t('settings.topics.remove', { name: topic.name })}
                  onClick={() => { setNotice(null); setEditing({ kind: 'remove', topic }); }}>
                  <Icon name="trash" size={18} />
                </button>
              </li>
            ))}
          </ul>
          <p className="topics-count">
            {full ? t('settings.topics.full', { max: MAX_TOPICS }) : t('settings.topics.count', { count: topics.length, max: MAX_TOPICS })}
          </p>
        </>
      )}
      {editing?.kind === 'new' && <TopicDialog onClose={() => setEditing(null)} onSaved={(topic) => saved(topic, true)} />}
      {editing?.kind === 'edit' && (
        <TopicDialog topic={editing.topic} onClose={() => setEditing(null)} onSaved={(topic) => saved(topic, false)} />
      )}
      {editing?.kind === 'remove' && (
        <RemoveTopicDialog topic={editing.topic} onClose={() => setEditing(null)} onRemoved={() => removed(editing.topic)} />
      )}
    </SettingsCard>
  );
}
