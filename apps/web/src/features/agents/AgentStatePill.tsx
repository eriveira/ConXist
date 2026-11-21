import { Pill, type PillTone } from '../../components/Pill';
import { type AgentState, type AgentSummary, stateOf } from '../../domain/agents';
import { useT } from '../../i18n/i18n';

const TONES: Record<AgentState, PillTone> = { live: 'live', changed: 'info', draft: 'soon', archived: 'soon' };

/** Live v2, v2 live · edited, Draft or Archived. */
export function AgentStatePill({ agent, kind = 'agent' }: {
  agent: Pick<AgentSummary, 'status' | 'publishedVersion' | 'draftPublished'>;
  /** Skills have their own words: in Spanish a skill is feminine. */
  kind?: 'agent' | 'skill';
}) {
  const t = useT();
  const state = stateOf(agent);
  return <Pill tone={TONES[state]}>{t(`${kind}.state.${state}`, { version: agent.publishedVersion })}</Pill>;
}
