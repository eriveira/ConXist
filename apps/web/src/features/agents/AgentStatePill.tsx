import { Pill, type PillTone } from '../../components/Pill';
import { type AgentState, type AgentSummary, stateOf } from '../../domain/agents';
import { useT } from '../../i18n/i18n';

const TONES: Record<AgentState, PillTone> = { live: 'live', changed: 'info', draft: 'soon', archived: 'soon' };

/** Live v2, Changes not published, Draft or Archived. */
export function AgentStatePill({ agent }: { agent: Pick<AgentSummary, 'status' | 'publishedVersion' | 'draftPublished'> }) {
  const t = useT();
  const state = stateOf(agent);
  return <Pill tone={TONES[state]}>{t(`agent.state.${state}`, { version: agent.publishedVersion })}</Pill>;
}
