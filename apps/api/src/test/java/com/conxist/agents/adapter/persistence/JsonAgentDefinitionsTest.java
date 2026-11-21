package com.conxist.agents.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonAgentDefinitionsTest {

    private final JsonAgentDefinitions definitions = new JsonAgentDefinitions();

    @Test
    void an_agent_saved_before_skills_existed_reads_as_following_none() {
        String saved = """
                {"v":1,"name":"Summary","purpose":"Sums up","instructions":"Sum it up.","knowledgeSources":[],
                "tools":["GET_TRANSCRIPT"],"model":"FAST","style":"PRECISE","accepts":["TRANSCRIPT"],
                "givesBack":"SUMMARY","limits":{"maxSteps":6,"timeoutSeconds":120,"maxToolCalls":10}}
                """;

        var definition = definitions.read(saved);

        assertThat(definition.skills()).isEmpty();
        assertThat(definition.name()).isEqualTo("Summary");
        assertThat(definitions.read(definitions.write(definition))).isEqualTo(definition);
    }
}
