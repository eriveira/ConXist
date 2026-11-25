-- Agents pick their own provider, model and effort (ADR 0014). Until now a definition named FAST, BALANCED or THOROUGH
-- in "model"; each becomes the model and effort that setting ran on by default.

update agents set draft = draft || jsonb_build_object(
        'provider', 'anthropic',
        'model', case draft->>'model' when 'FAST' then 'claude-haiku-5-5' when 'THOROUGH' then 'claude-opus-5-5'
                                      else 'claude-sonnet-5-5' end,
        'effort', case draft->>'model' when 'FAST' then 'LOW' when 'THOROUGH' then 'HIGH' else 'MEDIUM' end)
where not draft ? 'provider';

update agent_versions set definition = definition || jsonb_build_object(
        'provider', 'anthropic',
        'model', case definition->>'model' when 'FAST' then 'claude-haiku-5-5' when 'THOROUGH' then 'claude-opus-5-5'
                                           else 'claude-sonnet-5-5' end,
        'effort', case definition->>'model' when 'FAST' then 'LOW' when 'THOROUGH' then 'HIGH' else 'MEDIUM' end)
where not definition ? 'provider';
